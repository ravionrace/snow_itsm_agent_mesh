import { gs, GlideRecord, GlideRecordSecure } from '@servicenow/glide'
import { ServiceError } from '@servicenow/glide/sn_ws_err'
import { RESTAPIRequest, RESTAPIResponse } from '@servicenow/glide/sn_ws_int'

/**
 * Shared plumbing for every Scripted REST route in the app:
 *   - wraps each call in error handling and writes one audit row (x_1320561_itsm_ai_tool_audit)
 *   - replays the stored response for a repeated X-Idempotency-Key, so a retried write never runs twice
 *   - serialises records field by field, returning only fields the calling user can read
 *
 * Every business query uses GlideRecordSecure, so table and field ACLs apply to the calling user. Only the
 * audit table is written with GlideRecord, because users have no write access to it.
 */

export const AUDIT_TABLE = 'x_1320561_itsm_ai_tool_audit'
export const SYS_ID = /^[0-9a-f]{32}$/

export type HttpMethod = 'GET' | 'POST' | 'PATCH' | 'PUT' | 'DELETE'

/** A failure that becomes an HTTP error with a message the agent (or console) can act on. */
export class Failure {
    constructor(public status: number, public message: string, public detail = '') {}
}

export function fail(status: number, message: string, detail = ''): never {
    throw new Failure(status, message, detail)
}

export function requireSysId(value: unknown, name: string): string {
    const v = String(value ?? '')
    if (!SYS_ID.test(v)) fail(400, `${name} must be a sys_id.`)
    return v
}

export type Json = Record<string, unknown>

/** Run a route. `fn(api)` returns the response body; throw fail(...) for errors. */
export function handle(request: RESTAPIRequest, response: RESTAPIResponse, apiName: string, resource: string,
                       method: HttpMethod, fn: (api: AgentApi) => unknown): void {
    new AgentApi(request, response, apiName, resource, method).run(fn)
}

export class AgentApi {
    created = false
    private readonly started = new Date().getTime()
    private recordTable = ''
    private recordSysId = ''
    private recordNumber = ''

    constructor(private readonly request: RESTAPIRequest, private readonly response: RESTAPIResponse,
                private readonly apiName: string, private readonly resource: string,
                private readonly method: HttpMethod) {}

    run(fn: (api: AgentApi) => unknown): void {
        const key = this.header('X-Idempotency-Key')
        const isWrite = this.method !== 'GET'

        if (isWrite && key) {
            const previous = this.previousResponse(key)
            if (previous !== null) {
                this.response.setStatus(200)
                this.response.setBody(previous as any)
                this.audit(key, 200, 'replay', undefined)
                return
            }
        }

        let body: unknown
        let status = 200
        let outcome = 'success'
        try {
            body = fn(this)
            status = isWrite && this.created ? 201 : 200
            this.response.setStatus(status)
            this.response.setBody(body as any)
        } catch (e) {
            outcome = 'error'
            const failure = e instanceof Failure ? e
                : new Failure(500, `Unexpected error in ${this.resource}`, String((e as Error)?.message ?? e))
            status = failure.status
            if (!(e instanceof Failure)) {
                gs.error(`[x_1320561_itsm_ai] ${this.apiName}.${this.resource} failed: ${failure.detail}`)
            }
            const err = new ServiceError()
            err.setStatus(failure.status)
            err.setMessage(failure.message)
            err.setDetail(failure.status >= 500 ? '' : failure.detail)
            this.response.setError(err)
        }
        this.audit(key, status, outcome, body)
    }

    // ---------------------------------------------------------------------------------- request helpers

    header(name: string): string {
        const v = this.request.getHeader(name)
        return v ? String(v) : ''
    }

    /** Query params arrive as arrays in Scripted REST. */
    query(name: string): string | null
    query(name: string, fallback: string): string
    query(name: string, fallback: string | null = null): string | null {
        const v = this.request.queryParams?.[name]
        if (v === undefined || v === null || v.length === 0 || String(v[0]) === '') return fallback
        return String(v[0])
    }

    queryInt(name: string, fallback: number, min: number, max: number): number {
        let v = parseInt(this.query(name, String(fallback)), 10)
        if (isNaN(v)) v = fallback
        return Math.max(min, Math.min(max, v))
    }

    path(name: string): string {
        return String(this.request.pathParams?.[name] ?? '')
    }

    body(): Json {
        const data = this.request.body ? this.request.body.data : null
        if (!data || typeof data !== 'object') fail(400, 'A JSON object body is required.')
        return data as Json
    }

    // ---------------------------------------------------------------------------------- record helpers

    /** A record the user can read, by number or sys_id, or 404. */
    findRecord(table: string, ref: string): any {
        const gr: any = new GlideRecordSecure(table)
        const found = SYS_ID.test(ref) ? gr.get(ref) : gr.get('number', ref)
        if (!found || !gr.canRead()) fail(404, `${table} ${ref} was not found or is not visible to you.`)
        this.touch(gr)
        return gr
    }

    /** Remember which record this call is about, for the audit row. */
    touch(gr: any): void {
        this.recordTable = String(gr.getTableName())
        this.recordSysId = String(gr.getUniqueValue())
        this.recordNumber = gr.isValidField('number') ? String(gr.getValue('number') ?? '') : ''
    }

    /**
     * Serialise readable fields only. References become {value, display}; choice fields add "<field>_code" with
     * the raw value next to the display value; dates are labelled UTC.
     */
    toJson(gr: any, fields: string[], choiceFields: string[] = []): Json {
        const out: Json = { sys_id: String(gr.getUniqueValue()) }
        for (const f of fields) {
            if (!gr.isValidField(f)) continue
            const el = gr.getElement(f)
            if (!el.canRead()) continue
            const ed = el.getED()
            const type = ed ? String(ed.getInternalType()) : ''
            const raw = gr.getValue(f)
            if (type === 'reference') {
                out[f] = raw ? { value: String(raw), display: String(gr.getDisplayValue(f)) } : null
            } else if (choiceFields.includes(f)) {
                out[f] = String(gr.getDisplayValue(f))
                out[`${f}_code`] = raw === null ? null : String(raw)
            } else if (type === 'boolean') {
                out[f] = String(raw) === '1' || String(raw) === 'true'
            } else if (type === 'glide_date_time') {
                out[f] = raw ? `${raw} UTC` : null
            } else {
                out[f] = raw === null ? null : String(raw)
            }
        }
        return out
    }

    /** Recent journal entries (work notes / comments) the user can read, newest first. */
    journal(gr: any, field: string, limit = 5): Json[] {
        const out: Json[] = []
        if (!gr.isValidField(field) || !gr.getElement(field).canRead()) return out
        const j: any = new GlideRecordSecure('sys_journal_field')
        j.addQuery('element_id', gr.getUniqueValue())
        j.addQuery('element', field)
        j.orderByDesc('sys_created_on')
        j.setLimit(limit)
        j.query()
        while (j.next()) {
            out.push({ at: `${j.getValue('sys_created_on')} UTC`, by: String(j.getValue('sys_created_by')),
                       text: String(j.getValue('value')).substring(0, 2000) })
        }
        return out
    }

    /** Update through GlideRecordSecure; a denied write becomes 403, a rejected one 422. */
    secureUpdate(gr: any): any {
        if (!gr.canWrite()) fail(403, `You are not allowed to update ${gr.getDisplayValue()}.`)
        if (!gr.update()) fail(422, 'The update was rejected by a ServiceNow rule.', lastError())
        this.touch(gr)
        return gr
    }

    secureInsert(gr: any): any {
        if (!gr.canCreate()) fail(403, `You are not allowed to create ${gr.getTableName()} records.`)
        if (!gr.insert()) fail(422, 'The record was rejected by a ServiceNow rule.', lastError())
        this.created = true
        this.touch(gr)
        return gr
    }

    // ---------------------------------------------------------------------------------- audit + idempotency

    private previousResponse(key: string): Json | null {
        const a: any = new GlideRecord(AUDIT_TABLE)
        a.addQuery('idempotency_key', key)
        a.addQuery('user', gs.getUserID())
        a.addQuery('outcome', 'success')
        a.setLimit(1)
        a.query()
        if (!a.next()) return null
        try {
            return { ...JSON.parse(String(a.getValue('response'))), idempotent_replay: true }
        } catch (e) {
            return { idempotent_replay: true, number: String(a.getValue('record_number')) }
        }
    }

    private audit(key: string, status: number, outcome: string, body: unknown): void {
        try {
            const a: any = new GlideRecord(AUDIT_TABLE)
            a.initialize()
            a.setValue('api', this.apiName)
            a.setValue('resource', this.resource)
            a.setValue('http_method', this.method)
            a.setValue('http_status', status)
            a.setValue('outcome', outcome)
            a.setValue('user', gs.getUserID())
            a.setValue('request_id', this.header('X-Agent-Request-Id'))
            a.setValue('session_id', this.header('X-Agent-Session-Id'))
            a.setValue('idempotency_key', key)
            a.setValue('record_table', this.recordTable)
            a.setValue('record_sys_id', this.recordSysId)
            a.setValue('record_number', this.recordNumber)
            a.setValue('duration_ms', new Date().getTime() - this.started)
            if (key && outcome === 'success' && body !== undefined) {
                a.setValue('response', JSON.stringify(body).substring(0, 64000))
            }
            a.insert()
        } catch (e) {
            gs.error(`[x_1320561_itsm_ai] audit write failed: ${e}`)
        }
    }
}

function lastError(): string {
    const msgs = gs.getErrorMessages()
    return msgs && msgs.length ? String(msgs.join('; ')) : ''
}
