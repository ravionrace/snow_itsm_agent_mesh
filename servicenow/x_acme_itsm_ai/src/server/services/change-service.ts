import { gs, GlideDateTime, GlideRecordSecure } from '@servicenow/glide'
import { AgentApi, fail, Json, requireSysId } from '../lib/agent-api.ts'
import { ChangeApis } from './change-apis.ts'
import { STATE } from './change-states.ts'

/**
 * /api/x_1320561_itsm_ai/v1/change - changes are created through the ChangeRequest script API so the change
 * model's defaults, templates and state rules apply. Emergency changes are never created or progressed here.
 */

const FIELDS = ['number', 'short_description', 'description', 'type', 'state', 'risk', 'impact', 'priority', 'cmdb_ci',
    'assignment_group', 'assigned_to', 'start_date', 'end_date', 'justification', 'implementation_plan', 'backout_plan',
    'test_plan', 'approval', 'cab_required', 'cab_date', 'close_code', 'close_notes', 'sys_updated_on']
const CHOICES = ['type', 'state', 'risk', 'impact', 'priority', 'approval', 'close_code']
const CLOSE_CODES = ['successful', 'successful_issues', 'unsuccessful']
/** Which state a change must be in before it can move to the target state. */
const PREVIOUS: Record<string, string> = { implement: STATE.scheduled, review: STATE.implement, closed: STATE.review }

export class ChangeService {
    constructor(private readonly api: AgentApi) {}

    /** GET /templates */
    templates(): Json[] {
        const api = this.api
        const gr: any = new GlideRecordSecure('std_change_record_producer')
        gr.addActiveQuery()
        const text = api.query('text')
        if (text) gr.addQuery('123TEXTQUERY321', text)
        gr.orderBy('name')
        gr.setLimit(api.queryInt('limit', 10, 1, 25))
        gr.query()
        const out: Json[] = []
        while (gr.next()) {
            out.push({ sys_id: String(gr.getUniqueValue()), name: String(gr.getValue('name')),
                       short_description: String(gr.getValue('short_description') ?? '') })
        }
        return out
    }

    /** POST / */
    create(): Json {
        const b = this.api.body()
        const type = String(b.type ?? '')
        if (type === 'emergency') fail(422, 'Emergency changes cannot be raised through the assistant.')
        if (type !== 'normal' && type !== 'standard') fail(400, 'type must be normal or standard.')
        if (!b.short_description) fail(400, 'short_description is required.')
        const probe: any = new GlideRecordSecure('change_request')
        if (!probe.canCreate()) fail(403, 'You are not allowed to create changes.')

        const values: Record<string, string> = { short_description: String(b.short_description).substring(0, 160) }
        for (const f of ['description', 'justification', 'implementation_plan', 'backout_plan', 'test_plan', 'start_date', 'end_date']) {
            if (b[f]) values[f] = String(b[f])
        }
        if (b.cmdb_ci) values.cmdb_ci = requireSysId(b.cmdb_ci, 'cmdb_ci')
        values.requested_by = gs.getUserID()

        const gr = type === 'standard'
            ? ChangeApis.newStandard(requireSysId(b.template, 'template'), values)
            : ChangeApis.newNormal(values)
        if (!gr) fail(422, 'The change was rejected by a ServiceNow rule.')
        this.api.created = true
        this.api.touch(gr)
        if (b.related_record) this.relate(gr, String(b.related_record))
        return this.full(gr)
    }

    /** GET /{id} */
    get(): Json {
        return this.full(this.api.findRecord('change_request', this.api.path('id')))
    }

    /** GET /{id}/risk - runs the risk calculator; the result is not saved. */
    risk(): Json {
        const gr = this.api.findRecord('change_request', this.api.path('id'))
        return { number: String(gr.getValue('number')), current_risk: String(gr.getDisplayValue('risk')),
                 current_impact: String(gr.getDisplayValue('impact')), calculated: ChangeApis.calculateRisk(gr) }
    }

    /** GET /{id}/conflicts */
    conflicts(): Json {
        const gr = this.api.findRecord('change_request', this.api.path('id'))
        ChangeApis.runConflictCheck(gr)
        const c: any = new GlideRecordSecure('conflict')
        c.addQuery('change', gr.getUniqueValue())
        c.setLimit(50)
        c.query()
        const conflicts: Json[] = []
        while (c.next()) {
            conflicts.push({ type: String(c.getDisplayValue('type')), configuration_item: String(c.getDisplayValue('configuration_item')),
                             conflicting_change: String(c.getDisplayValue('conflicting_change') ?? ''),
                             schedule: String(c.getDisplayValue('schedule') ?? '') })
        }
        return { number: String(gr.getValue('number')),
                 window: { start: gr.getValue('start_date'), end: gr.getValue('end_date') }, conflicts }
    }

    /** GET /cab_schedule */
    cabSchedule(): Json[] {
        const days = this.api.queryInt('days', 14, 1, 60)
        const now = new GlideDateTime()
        const until = new GlideDateTime()
        until.addDaysUTC(days)
        const m: any = new GlideRecordSecure('cab_meeting')
        m.addQuery('start', '>=', now.getValue())
        m.addQuery('start', '<=', until.getValue())
        m.orderBy('start')
        m.setLimit(20)
        m.query()
        const out: Json[] = []
        while (m.next()) {
            const agenda: any = new GlideRecordSecure('cab_agenda_item')
            agenda.addQuery('cab_meeting', m.getUniqueValue())
            agenda.query()
            out.push({ name: String(m.getValue('name')), start: `${m.getValue('start')} UTC`,
                       end: `${m.getValue('end')} UTC`, agenda_items: agenda.getRowCount() })
        }
        return out
    }

    /** POST /{id}/request_approval - normal changes only: New -> Assess triggers the approval workflow. */
    requestApproval(): Json {
        const gr = this.api.findRecord('change_request', this.api.path('id'))
        this.rejectEmergency(gr)
        if (String(gr.getValue('type')) !== 'normal') fail(409, 'Standard changes are pre-approved and do not need approval.')
        if (String(gr.getValue('state')) !== STATE.new) fail(409, `${gr.getValue('number')} is ${gr.getDisplayValue('state')}, not New.`)
        ChangeApis.moveTo(gr, 'assess', this.api)
        return this.full(gr)
    }

    /** POST /{id}/state - scheduled -> implement -> review -> closed. */
    moveToState(): Json {
        const b = this.api.body()
        const gr = this.api.findRecord('change_request', this.api.path('id'))
        this.rejectEmergency(gr)
        const target = String(b.state ?? '')
        if (!PREVIOUS[target]) fail(400, 'state must be implement, review or closed.')
        if (String(gr.getValue('state')) !== PREVIOUS[target]) {
            fail(409, `Cannot move ${gr.getValue('number')} from ${gr.getDisplayValue('state')} to ${target}.`)
        }
        if (target === 'closed') {
            if (!CLOSE_CODES.includes(String(b.close_code)) || !b.close_notes) fail(400, 'Closing needs close_code and close_notes.')
            gr.setValue('close_code', String(b.close_code))
            gr.setValue('close_notes', String(b.close_notes))
        }
        ChangeApis.moveTo(gr, target, this.api)
        return this.full(gr)
    }

    private full(gr: any): Json {
        const json = this.api.toJson(gr, FIELDS, CHOICES)
        const tasks: Json[] = []
        const t: any = new GlideRecordSecure('change_task')
        t.addQuery('change_request', gr.getUniqueValue())
        t.query()
        while (t.next()) {
            tasks.push({ number: String(t.getValue('number')), state: String(t.getDisplayValue('state')),
                         short_description: String(t.getValue('short_description')) })
        }
        const approvals: Json[] = []
        const a: any = new GlideRecordSecure('sysapproval_approver')
        a.addQuery('sysapproval', gr.getUniqueValue())
        a.query()
        while (a.next()) approvals.push({ approver: String(a.getDisplayValue('approver')), state: String(a.getDisplayValue('state')) })
        return { ...json, tasks, approvals }
    }

    /** Link the incident or problem this change fixes (rfc field). */
    private relate(change: any, number: string): void {
        const table = number.startsWith('PRB') ? 'problem' : number.startsWith('INC') ? 'incident' : null
        if (!table) return
        const rec: any = new GlideRecordSecure(table)
        if (!rec.get('number', number) || !rec.getElement('rfc').canWrite()) return
        rec.setValue('rfc', change.getUniqueValue())
        rec.update()
    }

    private rejectEmergency(gr: any): void {
        if (String(gr.getValue('type')) === 'emergency') fail(422, 'Emergency changes are handled manually, not through the assistant.')
    }
}
