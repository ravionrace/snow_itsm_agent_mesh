import { gs, GlideRecordSecure } from '@servicenow/glide'
import { AgentApi, fail, Json, requireSysId } from '../lib/agent-api.ts'

/**
 * /api/x_1320561_itsm_ai/v1/incident - GlideRecordSecure on the global incident table, as the calling user.
 */

const FIELDS = ['number', 'short_description', 'description', 'state', 'priority', 'impact', 'urgency', 'category',
    'caller_id', 'assignment_group', 'assigned_to', 'cmdb_ci', 'opened_at', 'resolved_at', 'close_code', 'close_notes',
    'hold_reason', 'problem_id', 'sys_updated_on']
const CHOICES = ['state', 'priority', 'impact', 'urgency', 'hold_reason', 'close_code']
const SUMMARY = ['number', 'short_description', 'state', 'priority', 'assignment_group', 'cmdb_ci', 'opened_at']

export const STATE: Record<string, string> = { new: '1', in_progress: '2', on_hold: '3', resolved: '6', closed: '7', canceled: '8' }
const OPEN_STATES = '1,2,3'
const CLOSED_STATES = ['6', '7', '8']
const HOLD_REASON: Record<string, string> = { awaiting_caller: '1', awaiting_problem: '3', awaiting_vendor: '4', awaiting_change: '5' }

function range(value: unknown, name: string): string {
    const v = parseInt(String(value), 10)
    if (isNaN(v) || v < 1 || v > 3) fail(400, `${name} must be 1, 2 or 3.`)
    return String(v)
}

export class IncidentService {
    constructor(private readonly api: AgentApi) {}

    /** POST / */
    create(): Json {
        const b = this.api.body()
        if (!b.short_description) fail(400, 'short_description is required.')
        const gr: any = new GlideRecordSecure('incident')
        gr.initialize()
        gr.setValue('short_description', String(b.short_description).substring(0, 160))
        if (b.description) gr.setValue('description', String(b.description))
        gr.setValue('caller_id', b.caller ? requireSysId(b.caller, 'caller') : gs.getUserID())
        if (b.cmdb_ci) gr.setValue('cmdb_ci', requireSysId(b.cmdb_ci, 'cmdb_ci'))
        if (b.impact) gr.setValue('impact', range(b.impact, 'impact'))
        if (b.urgency) gr.setValue('urgency', range(b.urgency, 'urgency'))
        if (b.category) gr.setValue('category', String(b.category))
        gr.setValue('contact_type', 'virtual_agent')
        this.api.secureInsert(gr)
        return this.full(gr)
    }

    /** GET /{id} */
    get(): Json {
        return this.full(this.api.findRecord('incident', this.api.path('id')))
    }

    /** GET / */
    search(): Json[] {
        const api = this.api
        const gr: any = new GlideRecordSecure('incident')
        const caller = api.query('caller')
        if (caller === 'me') gr.addQuery('caller_id', gs.getUserID())
        else if (caller) gr.addQuery('caller_id', requireSysId(caller, 'caller'))
        const state = api.query('state')
        if (state === 'open') gr.addQuery('state', 'IN', OPEN_STATES)
        else if (state) gr.addQuery('state', STATE[state] ?? fail(400, `Unknown state ${state}`))
        const group = api.query('assignment_group')
        if (group) gr.addQuery('assignment_group', requireSysId(group, 'assignment_group'))
        const ci = api.query('cmdb_ci')
        if (ci) gr.addQuery('cmdb_ci', requireSysId(ci, 'cmdb_ci'))
        const after = api.query('opened_after')
        if (after) gr.addQuery('opened_at', '>=', after)
        const before = api.query('opened_before')
        if (before) gr.addQuery('opened_at', '<=', `${before} 23:59:59`)
        const text = api.query('text')
        if (text) gr.addQuery('123TEXTQUERY321', text)
        gr.orderByDesc('opened_at')
        gr.setLimit(api.queryInt('limit', 10, 1, 25))
        gr.query()
        const out: Json[] = []
        while (gr.next()) out.push(api.toJson(gr, SUMMARY, CHOICES))
        return out
    }

    /** PATCH /{id} */
    update(): Json {
        const b = this.api.body()
        const gr = this.api.findRecord('incident', this.api.path('id'))
        if (b.state) {
            const s = STATE[String(b.state)]
            if (!s || CLOSED_STATES.includes(s)) fail(400, 'Use the resolve resource to resolve incidents.')
            gr.setValue('state', s)
            if (s === STATE.on_hold) {
                const reason = HOLD_REASON[String(b.hold_reason)]
                if (!reason) fail(400, 'hold_reason is required when putting an incident on hold.')
                gr.setValue('hold_reason', reason)
            }
        }
        if (b.impact) gr.setValue('impact', range(b.impact, 'impact'))
        if (b.urgency) gr.setValue('urgency', range(b.urgency, 'urgency'))
        if (b.category) gr.setValue('category', String(b.category))
        this.api.secureUpdate(gr)
        return this.full(gr)
    }

    /** POST /{id}/notes */
    addNote(): Json {
        const b = this.api.body()
        const gr = this.api.findRecord('incident', this.api.path('id'))
        const field = b.type === 'comment' ? 'comments' : 'work_notes'
        if (!b.text) fail(400, 'text is required.')
        if (!gr.getElement(field).canWrite()) fail(403, `You cannot add ${field.replace('_', ' ')} to this incident.`)
        gr.setValue(field, String(b.text))
        this.api.secureUpdate(gr)
        return { number: String(gr.getValue('number')), added: field }
    }

    /** POST /{id}/assign - only to the caller's own groups (also enforced in the MCP server). */
    assign(): Json {
        const b = this.api.body()
        const gr = this.api.findRecord('incident', this.api.path('id'))
        const group = requireSysId(b.assignment_group, 'assignment_group')
        if (!gs.getUser().isMemberOf(group)) fail(403, 'You can only assign incidents to groups you are a member of.')
        gr.setValue('assignment_group', group)
        if (b.assigned_to) {
            const member: any = new GlideRecordSecure('sys_user_grmember')
            member.addQuery('group', group)
            member.addQuery('user', requireSysId(b.assigned_to, 'assigned_to'))
            member.setLimit(1)
            member.query()
            if (!member.hasNext()) fail(400, 'The assignee is not a member of that group.')
            gr.setValue('assigned_to', String(b.assigned_to))
        } else {
            gr.setValue('assigned_to', '')
        }
        this.api.secureUpdate(gr)
        return this.full(gr)
    }

    /** POST /{id}/resolve - P1s are never resolved through this API. */
    resolve(): Json {
        const b = this.api.body()
        const gr = this.api.findRecord('incident', this.api.path('id'))
        if (String(gr.getValue('priority')) === '1') {
            fail(422, 'P1 incidents must be resolved manually by the major incident manager.')
        }
        if (CLOSED_STATES.includes(String(gr.getValue('state')))) {
            fail(409, `${gr.getValue('number')} is already ${gr.getDisplayValue('state')}.`)
        }
        if (!b.close_code || !b.close_notes) fail(400, 'close_code and close_notes are required.')
        gr.setValue('state', STATE.resolved)
        gr.setValue('close_code', String(b.close_code))
        gr.setValue('close_notes', String(b.close_notes))
        this.api.secureUpdate(gr)
        return this.full(gr)
    }

    /** GET /similar */
    similar(): Json {
        const api = this.api
        const days = api.queryInt('days', 14, 1, 90)
        let ci = api.query('cmdb_ci')
        let text = api.query('text')
        let exclude = ''
        const number = api.query('number')
        if (number) {
            const base = api.findRecord('incident', number)
            exclude = String(base.getUniqueValue())
            ci = ci || base.getValue('cmdb_ci')
            text = text || base.getValue('short_description')
        }
        const gr: any = new GlideRecordSecure('incident')
        gr.addQuery('opened_at', '>=', gs.daysAgoStart(days))
        if (exclude) gr.addQuery('sys_id', '!=', exclude)
        if (ci && text) {
            gr.addQuery('cmdb_ci', ci).addOrCondition('123TEXTQUERY321', text)
        } else if (ci) {
            gr.addQuery('cmdb_ci', ci)
        } else if (text) {
            gr.addQuery('123TEXTQUERY321', text)
        }
        gr.orderByDesc('opened_at')
        gr.setLimit(api.queryInt('limit', 10, 1, 25))
        gr.query()
        const matches: Json[] = []
        const byCi: Record<string, number> = {}
        while (gr.next()) {
            matches.push(api.toJson(gr, SUMMARY, CHOICES))
            const c = String(gr.getValue('cmdb_ci') ?? '')
            if (c) byCi[c] = (byCi[c] ?? 0) + 1
        }
        return { window_days: days, matches, count: matches.length, by_ci: byCi }
    }

    /** GET /my_groups */
    myGroups(): Json[] {
        const out: Json[] = []
        const m: any = new GlideRecordSecure('sys_user_grmember')
        m.addQuery('user', gs.getUserID())
        m.query()
        while (m.next()) out.push({ sys_id: String(m.getValue('group')), name: String(m.getDisplayValue('group')) })
        return out
    }

    private full(gr: any): Json {
        return {
            ...this.api.toJson(gr, FIELDS, CHOICES),
            work_notes: this.api.journal(gr, 'work_notes', 5),
            comments: this.api.journal(gr, 'comments', 5),
        }
    }
}
