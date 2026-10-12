import { gs, GlideRecordSecure, GlideTableHierarchy } from '@servicenow/glide'
import { AgentApi, fail, Json, SYS_ID } from '../lib/agent-api.ts'

/** Read-only APIs /api/x_1320561_itsm_ai/v1/cmdb and /api/x_1320561_itsm_ai/v1/kb. */

const CI_FIELDS = ['name', 'sys_class_name', 'operational_status', 'install_status', 'support_group', 'managed_by',
    'owned_by', 'environment', 'location', 'short_description', 'ip_address']

export class CmdbKbService {
    constructor(private readonly api: AgentApi) {}

    /** GET /ci/{id} or GET /ci?name= */
    ci(): Json {
        const api = this.api
        const gr: any = new GlideRecordSecure('cmdb_ci')
        const id = api.path('id')
        let found = false
        if (id) {
            found = SYS_ID.test(id) && gr.get(id)
        } else {
            gr.addQuery('name', api.query('name', ''))
            gr.setLimit(1)
            gr.query()
            found = gr.next()
        }
        if (!found) fail(404, 'Configuration item not found or not visible to you.')
        api.touch(gr)
        // Re-read through the CI's own class so class-specific fields are available.
        const specific: any = new GlideRecordSecure(String(gr.getValue('sys_class_name')))
        specific.get(gr.getUniqueValue())
        return { ...api.toJson(specific, CI_FIELDS, ['operational_status', 'install_status', 'environment']),
                 class: String(gr.getDisplayValue('sys_class_name')) }
    }

    /** GET /ci/{id}/impacted_services?depth= - walks upstream relationships to services. */
    impactedServices(): Json {
        const api = this.api
        const start = api.path('id')
        if (!SYS_ID.test(start)) fail(400, 'ci must be a sys_id.')
        const depth = api.queryInt('depth', 2, 1, 4)
        const seen = new Set<string>([start])
        let frontier = [start]
        const services: Json[] = []
        for (let level = 1; level <= depth && frontier.length; level++) {
            const rel: any = new GlideRecordSecure('cmdb_rel_ci')
            rel.addQuery('child', 'IN', frontier.join(','))
            rel.setLimit(500)
            rel.query()
            const next: string[] = []
            while (rel.next()) {
                const parent = String(rel.getValue('parent'))
                if (seen.has(parent)) continue
                seen.add(parent)
                next.push(parent)
                const p: any = new GlideRecordSecure('cmdb_ci')
                if (!p.get(parent)) continue
                const cls = String(p.getValue('sys_class_name'))
                if (new GlideTableHierarchy(cls).getTables().includes('cmdb_ci_service')) {
                    services.push({ sys_id: parent, name: String(p.getValue('name')), class: String(p.getDisplayValue('sys_class_name')),
                                    relationship: String(rel.getDisplayValue('type')), level })
                }
            }
            frontier = next
        }
        return { ci: start, depth, services }
    }

    /** GET /users?q= */
    users(): Json[] {
        const api = this.api
        const q = api.query('q', '')
        const gr: any = new GlideRecordSecure('sys_user')
        gr.addActiveQuery()
        if (q === 'me') {
            gr.addQuery('sys_id', gs.getUserID())
        } else {
            const c = gr.addQuery('name', 'CONTAINS', q)
            c.addOrCondition('user_name', q)
            c.addOrCondition('email', q)
        }
        gr.setLimit(api.queryInt('limit', 5, 1, 10))
        gr.query()
        const out: Json[] = []
        while (gr.next()) out.push(api.toJson(gr, ['name', 'user_name', 'title', 'department', 'email']))
        return out
    }

    /** GET /groups?q=&mine= */
    groups(): Json[] {
        const api = this.api
        const gr: any = new GlideRecordSecure('sys_user_group')
        gr.addActiveQuery()
        const q = api.query('q')
        if (q) gr.addQuery('name', 'CONTAINS', q)
        if (api.query('mine') === 'true') {
            const ids: string[] = []
            const m: any = new GlideRecordSecure('sys_user_grmember')
            m.addQuery('user', gs.getUserID())
            m.query()
            while (m.next()) ids.push(String(m.getValue('group')))
            gr.addQuery('sys_id', 'IN', ids.join(','))
        }
        gr.orderBy('name')
        gr.setLimit(api.queryInt('limit', 10, 1, 20))
        gr.query()
        const out: Json[] = []
        while (gr.next()) out.push(api.toJson(gr, ['name', 'manager', 'type', 'description']))
        return out
    }

    /** GET (kb) /articles?text= - published articles the user can read. */
    articles(): Json[] {
        const api = this.api
        const gr: any = new GlideRecordSecure('kb_knowledge')
        gr.addQuery('workflow_state', 'published')
        gr.addQuery('123TEXTQUERY321', api.query('text', ''))
        gr.setLimit(api.queryInt('limit', 5, 1, 10))
        gr.query()
        const base = gs.getProperty('glide.servlet.uri', '/')
        const out: Json[] = []
        while (gr.next()) {
            if (!gr.canRead()) continue // kb_knowledge read ACLs evaluate knowledge base user criteria
            const text = String(gr.getValue('text') ?? '').replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ')
            out.push({ number: String(gr.getValue('number')), title: String(gr.getValue('short_description')),
                       snippet: text.substring(0, 400), link: `${base}kb_view.do?sysparm_article=${gr.getValue('number')}` })
        }
        return out
    }
}
