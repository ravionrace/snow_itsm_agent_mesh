import { gs, GlideRecordSecure } from '@servicenow/glide'
import { AgentApi, fail, Json, requireSysId } from '../lib/agent-api.ts'

/**
 * /api/x_1320561_itsm_ai/v1/problem - GlideRecordSecure on problem, incident (problem_id) and kb_knowledge.
 * Properties: x_1320561_itsm_ai.problem.cause_ci_field, x_1320561_itsm_ai.known_error.kb
 */

const FIELDS = ['number', 'short_description', 'description', 'state', 'priority', 'cmdb_ci', 'assignment_group',
    'assigned_to', 'cause_notes', 'fix_notes', 'workaround', 'known_error', 'opened_at', 'sys_updated_on']
const CHOICES = ['state', 'priority']
/** Default problem state model. */
const STATE = { new: '101', assess: '102', rca: '103', fix_in_progress: '104', resolved: '106', closed: '107' }

const escapeHtml = (s: unknown) =>
    String(s ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/\n/g, '<br>')

export class ProblemService {
    private readonly causeCiField: string

    constructor(private readonly api: AgentApi) {
        this.causeCiField = gs.getProperty('x_1320561_itsm_ai.problem.cause_ci_field', 'cmdb_ci')
    }

    /** POST / */
    create(): Json {
        const b = this.api.body()
        if (!b.short_description) fail(400, 'short_description is required.')
        const incidents = this.incidents((b.incidents as string[]) ?? [])
        const gr: any = new GlideRecordSecure('problem')
        gr.initialize()
        gr.setValue('short_description', String(b.short_description).substring(0, 160))
        if (b.description) gr.setValue('description', String(b.description))
        if (b.cmdb_ci) gr.setValue('cmdb_ci', requireSysId(b.cmdb_ci, 'cmdb_ci'))
        if (incidents.length) gr.setValue('first_reported_by_task', incidents[0].getUniqueValue())
        this.api.secureInsert(gr)
        return { ...this.full(gr), linked_now: this.link(gr, incidents) }
    }

    /** GET /{id} */
    get(): Json {
        return this.full(this.api.findRecord('problem', this.api.path('id')))
    }

    /** POST /{id}/incidents */
    linkIncidents(): Json {
        const b = this.api.body()
        const gr = this.open()
        const linked = this.link(gr, this.incidents((b.incidents as string[]) ?? []))
        return { number: String(gr.getValue('number')), linked }
    }

    /** POST /{id}/root_cause */
    rootCause(): Json {
        const b = this.api.body()
        const gr = this.open()
        if (!b.cause_notes) fail(400, 'cause_notes is required.')
        gr.setValue('cause_notes', String(b.cause_notes))
        if (b.cause_ci) gr.setValue(this.causeCiField, requireSysId(b.cause_ci, 'cause_ci'))
        if (parseInt(gr.getValue('state'), 10) < parseInt(STATE.rca, 10)) gr.setValue('state', STATE.rca)
        this.api.secureUpdate(gr)
        return this.full(gr)
    }

    /** POST /{id}/workaround */
    workaround(): Json {
        const b = this.api.body()
        const gr = this.open()
        if (!b.workaround) fail(400, 'workaround is required.')
        gr.setValue('workaround', String(b.workaround))
        this.api.secureUpdate(gr)
        return this.full(gr)
    }

    /** POST /{id}/known_error - marks the known error and drafts a KB article from cause and workaround. */
    knownError(): Json {
        const b = this.api.body()
        const gr = this.open()
        if (!gr.getValue('cause_notes') || !gr.getValue('workaround')) {
            fail(422, 'A known error needs both a root cause and a workaround.')
        }
        gr.setValue('known_error', true)
        this.api.secureUpdate(gr)
        const out = this.full(gr)
        if (b.publish_kb !== false) {
            const kbId = gs.getProperty('x_1320561_itsm_ai.known_error.kb', '')
            if (!kbId) fail(500, 'Known-error knowledge base is not configured.', 'Set x_1320561_itsm_ai.known_error.kb')
            const kb: any = new GlideRecordSecure('kb_knowledge')
            kb.initialize()
            kb.setValue('kb_knowledge_base', kbId)
            kb.setValue('short_description', String(b.kb_title || gr.getValue('short_description')).substring(0, 160))
            kb.setValue('text', `<h3>Symptoms</h3><p>${escapeHtml(gr.getValue('description') || gr.getValue('short_description'))}</p>` +
                `<h3>Cause</h3><p>${escapeHtml(gr.getValue('cause_notes'))}</p>` +
                `<h3>Workaround</h3><p>${escapeHtml(gr.getValue('workaround'))}</p>`)
            kb.setValue('workflow_state', 'draft')
            kb.setValue('source', gr.getUniqueValue())
            if (!kb.canCreate()) fail(403, 'You cannot create knowledge articles in that knowledge base.')
            const kbSys = kb.insert()
            if (kbSys) out.kb_article = { number: String(kb.getValue('number')), sys_id: String(kbSys), state: 'draft' }
        }
        return out
    }

    /** GET /known_errors */
    knownErrors(): Json[] {
        const api = this.api
        const gr: any = new GlideRecordSecure('problem')
        gr.addQuery('known_error', true)
        gr.addQuery('123TEXTQUERY321', api.query('text', ''))
        const ci = api.query('cmdb_ci')
        if (ci) gr.addQuery('cmdb_ci', requireSysId(ci, 'cmdb_ci'))
        gr.setLimit(api.queryInt('limit', 5, 1, 20))
        gr.query()
        const out: Json[] = []
        while (gr.next()) {
            out.push(api.toJson(gr, ['number', 'short_description', 'state', 'cmdb_ci', 'workaround', 'cause_notes'], CHOICES))
        }
        return out
    }

    private open(): any {
        const gr = this.api.findRecord('problem', this.api.path('id'))
        const s = String(gr.getValue('state'))
        if (s === STATE.resolved || s === STATE.closed) fail(409, `${gr.getValue('number')} is already ${gr.getDisplayValue('state')}.`)
        return gr
    }

    private full(gr: any): Json {
        const json = this.api.toJson(gr, FIELDS, CHOICES)
        if (this.causeCiField !== 'cmdb_ci' && gr.isValidField(this.causeCiField) && gr.getElement(this.causeCiField).canRead()) {
            const v = gr.getValue(this.causeCiField)
            json.cause_ci = v ? { value: String(v), display: String(gr.getDisplayValue(this.causeCiField)) } : null
        }
        const inc: any = new GlideRecordSecure('incident')
        inc.addQuery('problem_id', gr.getUniqueValue())
        inc.setLimit(50)
        inc.query()
        const incidents: Json[] = []
        while (inc.next()) incidents.push({ number: String(inc.getValue('number')), state: String(inc.getDisplayValue('state')) })
        json.incidents = incidents
        return json
    }

    private incidents(numbers: string[]): any[] {
        if (numbers.length > 50) fail(400, 'At most 50 incidents can be linked at once.')
        return numbers.map((n) => {
            const inc: any = new GlideRecordSecure('incident')
            if (!inc.get('number', String(n))) fail(404, `Incident ${n} was not found or is not visible to you.`)
            return inc
        })
    }

    private link(problem: any, incidents: any[]): string[] {
        const linked: string[] = []
        for (const inc of incidents) {
            if (!inc.getElement('problem_id').canWrite()) fail(403, `You cannot link ${inc.getValue('number')}.`)
            inc.setValue('problem_id', problem.getUniqueValue())
            if (inc.update()) linked.push(String(inc.getValue('number')))
        }
        return linked
    }
}
