import { gs, GlideRecordSecure } from '@servicenow/glide'
import { CartJS, CatItem } from '@servicenow/glide/sn_sc'
import { AgentApi, fail, Json, SYS_ID } from '../lib/agent-api.ts'

/**
 * /api/x_1320561_itsm_ai/v1/catalog - entitlements (user criteria) are checked with sn_sc.CatItem.canView() for
 * the calling user, and orders go through sn_sc.CartJS.orderNow() so catalog workflows, approvals and pricing
 * run exactly as they do in the portal.
 */

/** item_option_new.type values that hold answers (labels, containers, etc. are skipped). */
const VAR_TYPES: Record<string, string> = {
    '1': 'yes_no', '2': 'multi_line_text', '3': 'multiple_choice', '5': 'select_box', '6': 'single_line_text',
    '7': 'checkbox', '8': 'reference', '9': 'date', '10': 'date_time', '16': 'wide_single_line_text',
    '18': 'lookup_select_box', '21': 'list_collector', '22': 'lookup_multiple_choice', '26': 'email', '28': 'ip_address',
}
const RITM_FIELDS = ['number', 'cat_item', 'stage', 'state', 'approval', 'request', 'requested_for', 'opened_at',
    'due_date', 'quantity', 'price', 'active']

interface Variable {
    name: string
    label: string
    type: string
    mandatory: boolean
    help?: string
    choices?: { value: string; label: string }[]
    reference_table?: string
}

const truthy = (v: unknown) => String(v) === '1' || String(v) === 'true'

export class CatalogService {
    constructor(private readonly api: AgentApi) {}

    /** GET /items?text= */
    items(): Json[] {
        const api = this.api
        const limit = api.queryInt('limit', 8, 1, 20)
        const gr: any = new GlideRecordSecure('sc_cat_item')
        gr.addActiveQuery()
        gr.addQuery('123TEXTQUERY321', api.query('text', ''))
        gr.setLimit(limit * 4) // over-fetch; entitlement filtering drops some
        gr.query()
        const out: Json[] = []
        while (gr.next() && out.length < limit) {
            if (!entitled(String(gr.getUniqueValue()))) continue
            out.push({ sys_id: String(gr.getUniqueValue()), name: String(gr.getValue('name')),
                       short_description: String(gr.getValue('short_description') ?? ''),
                       category: String(gr.getDisplayValue('category') ?? ''), price: String(gr.getDisplayValue('price') ?? '') })
        }
        return out
    }

    /** GET /items/{id}/form */
    form(): Json {
        const item = this.item(this.api.path('id'))
        return { sys_id: String(item.getUniqueValue()), name: String(item.getValue('name')),
                 description: String(item.getValue('short_description') ?? ''), variables: variables(String(item.getUniqueValue())) }
    }

    /** POST /items/{id}/validate */
    validate(): Json {
        const item = this.item(this.api.path('id'))
        const answers = (this.api.body().variables ?? {}) as Json
        return { item_name: String(item.getValue('name')), price: String(item.getDisplayValue('price') ?? ''),
                 errors: check(variables(String(item.getUniqueValue())), answers) }
    }

    /** POST /items/{id}/order */
    order(): Json {
        const b = this.api.body()
        const item = this.item(this.api.path('id'))
        const answers = (b.variables ?? {}) as Json
        const errors = check(variables(String(item.getUniqueValue())), answers)
        if (errors.length) fail(400, 'The answers are not valid.', JSON.stringify(errors))
        const qty = parseInt(String(b.quantity ?? 1), 10)
        if (isNaN(qty) || qty < 1 || qty > 10) fail(400, 'quantity must be 1-10.')

        const request: Json = { sysparm_id: String(item.getUniqueValue()), sysparm_quantity: String(qty), variables: answers }
        if (b.requested_for) {
            if (!SYS_ID.test(String(b.requested_for))) fail(400, 'requested_for must be a sys_id.')
            request.sysparm_requested_for = String(b.requested_for)
        }
        const result = new CartJS().orderNow(request as any)
        if (!result || !result.request_id) fail(422, 'The order was not accepted.')

        const items: string[] = []
        const r: any = new GlideRecordSecure('sc_req_item')
        r.addQuery('request', result.request_id)
        r.query()
        while (r.next()) {
            items.push(String(r.getValue('number')))
            this.api.touch(r)
        }
        this.api.created = true
        return { request_number: String(result.request_number), request_sys_id: String(result.request_id), items }
    }

    /** GET /ritm/{id} */
    ritm(): Json {
        const gr = this.api.findRecord('sc_req_item', this.api.path('id'))
        const approvals: Json[] = []
        const a: any = new GlideRecordSecure('sysapproval_approver')
        a.addQuery('sysapproval', gr.getUniqueValue())
        a.query()
        while (a.next()) approvals.push({ approver: String(a.getDisplayValue('approver')), state: String(a.getDisplayValue('state')) })
        const tasks: Json[] = []
        const t: any = new GlideRecordSecure('sc_task')
        t.addQuery('request_item', gr.getUniqueValue())
        t.query()
        while (t.next()) {
            tasks.push({ number: String(t.getValue('number')), state: String(t.getDisplayValue('state')),
                         assignment_group: String(t.getDisplayValue('assignment_group')) })
        }
        return { ...this.api.toJson(gr, RITM_FIELDS, ['stage', 'state', 'approval']), approvals, tasks }
    }

    /** GET /my_requests?state=open|closed|all */
    myRequests(): Json[] {
        const api = this.api
        const gr: any = new GlideRecordSecure('sc_req_item')
        gr.addQuery('requested_for', gs.getUserID()).addOrCondition('opened_by', gs.getUserID())
        const state = api.query('state', 'open')
        if (state === 'open') gr.addActiveQuery()
        else if (state === 'closed') gr.addQuery('active', false)
        gr.orderByDesc('opened_at')
        gr.setLimit(api.queryInt('limit', 10, 1, 25))
        gr.query()
        const out: Json[] = []
        while (gr.next()) out.push(api.toJson(gr, ['number', 'cat_item', 'stage', 'state', 'opened_at'], ['stage', 'state']))
        return out
    }

    /** POST /ritm/{id}/cancel - only the requester (or opener), only while open. */
    cancel(): Json {
        const b = this.api.body()
        const gr = this.api.findRecord('sc_req_item', this.api.path('id'))
        const me = gs.getUserID()
        if (String(gr.getValue('requested_for')) !== me && String(gr.getValue('opened_by')) !== me) {
            fail(403, 'You can only cancel your own requests.')
        }
        if (!truthy(gr.getValue('active'))) fail(409, `${gr.getValue('number')} is no longer open.`)
        if (!b.reason) fail(400, 'reason is required.')
        gr.setValue('state', '4') // Closed Incomplete
        gr.setValue('stage', 'Request Cancelled')
        gr.setValue('comments', `Cancelled by requester: ${b.reason}`)
        this.api.secureUpdate(gr)
        return this.api.toJson(gr, ['number', 'stage', 'state'], ['stage', 'state'])
    }

    private item(id: string): any {
        if (!SYS_ID.test(id)) fail(400, 'item must be a sys_id.')
        const item: any = new GlideRecordSecure('sc_cat_item')
        if (!item.get(id) || !truthy(item.getValue('active')) || !entitled(id)) {
            fail(404, 'Catalog item not found or not available to you.')
        }
        return item
    }
}

function entitled(id: string): boolean {
    try {
        return new CatItem(id).canView()
    } catch (e) {
        return false
    }
}

function variables(itemId: string): Variable[] {
    const setIds: string[] = []
    const link: any = new GlideRecordSecure('io_set_item')
    link.addQuery('sc_cat_item', itemId)
    link.query()
    while (link.next()) setIds.push(String(link.getValue('variable_set')))

    const v: any = new GlideRecordSecure('item_option_new')
    const q = v.addQuery('cat_item', itemId)
    if (setIds.length) q.addOrCondition('variable_set', 'IN', setIds.join(','))
    v.addActiveQuery()
    v.orderBy('order')
    v.query()
    const out: Variable[] = []
    while (v.next()) {
        const type = VAR_TYPES[String(v.getValue('type'))]
        if (!type) continue
        const entry: Variable = {
            name: String(v.getValue('name')), label: String(v.getValue('question_text') || v.getValue('name')),
            type, mandatory: truthy(v.getValue('mandatory')),
        }
        const help = String(v.getValue('help_text') ?? '')
        if (help) entry.help = help
        if (type === 'select_box' || type === 'multiple_choice') {
            entry.choices = []
            const c: any = new GlideRecordSecure('question_choice')
            c.addQuery('question', v.getUniqueValue())
            c.addQuery('inactive', false)
            c.orderBy('order')
            c.query()
            while (c.next()) entry.choices.push({ value: String(c.getValue('value')), label: String(c.getValue('text')) })
        }
        if (type === 'reference') entry.reference_table = String(v.getValue('reference') ?? '')
        out.push(entry)
    }
    return out
}

function check(vars: Variable[], answers: Json): Json[] {
    const errors: Json[] = []
    const known = new Set<string>()
    for (const v of vars) {
        known.add(v.name)
        const a = answers[v.name]
        const empty = a === undefined || a === null || String(a) === ''
        if (v.mandatory && empty) {
            errors.push({ variable: v.name, error: 'required' })
            continue
        }
        if (empty) continue
        const s = String(a)
        if (v.choices && !v.choices.some((c) => c.value === s)) errors.push({ variable: v.name, error: 'must be one of the listed choices' })
        else if (v.type === 'email' && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(s)) errors.push({ variable: v.name, error: 'must be an email address' })
        else if (v.type === 'date' && !/^\d{4}-\d{2}-\d{2}$/.test(s)) errors.push({ variable: v.name, error: 'must be YYYY-MM-DD' })
        else if (v.type === 'yes_no' && s !== 'Yes' && s !== 'No') errors.push({ variable: v.name, error: 'must be Yes or No' })
        else if (v.type === 'reference' && !SYS_ID.test(s)) errors.push({ variable: v.name, error: 'must be a sys_id' })
    }
    for (const name of Object.keys(answers)) {
        if (!known.has(name)) errors.push({ variable: name, error: 'not a variable on this item' })
    }
    return errors
}
