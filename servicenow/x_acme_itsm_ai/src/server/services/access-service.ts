import { gs, GlideAggregate, GlideRecord, GlideRecordSecure } from '@servicenow/glide'
import { AgentApi, fail, Json, requireSysId } from '../lib/agent-api.ts'
import { API_CATALOG, AccessLevel, CatalogApi, SCOPE } from '../lib/api-catalog.ts'

/**
 * /api/<scope>/v1/access - who can read and write each agent API.
 *
 * Access is plain ServiceNow: each API has a <api>_read and <api>_write role (write contains read); an access
 * admin grants those roles to groups and manages the groups' members. The REST ACL on every agent route
 * requires the matching role, so the platform enforces it no matter who calls.
 *
 * Writes here use GlideRecord because access admins are not expected to hold user_admin. They are limited in
 * code to (a) this app's API roles and (b) groups that hold at least one of them, and are checked against
 * access_admin here as well as by the route ACL. Every call is audited by AgentApi.
 */

const ACCESS_ADMIN = `${SCOPE}.access_admin`
const AUDITOR = `${SCOPE}.auditor`
const APP_ROLES = API_CATALOG.flatMap((a) => [a.read_role, a.write_role].filter((r): r is string => !!r))

interface GroupRef { sys_id: string; name: string }
interface Grant { group: GroupRef; api: string; level: AccessLevel; role: string }

function catalogApi(api: unknown): CatalogApi {
    const found = API_CATALOG.find((a) => a.api === api)
    if (!found) fail(400, `Unknown API '${api}'.`, `Known APIs: ${API_CATALOG.map((a) => a.api).join(', ')}`)
    return found
}

function roleFor(api: CatalogApi, level: unknown): string {
    if (level === 'read') return api.read_role
    if (level === 'write') return api.write_role ?? fail(400, `The ${api.title} API is read-only.`)
    return fail(400, 'level must be read or write.')
}

function requireAccessAdmin(): void {
    if (!gs.hasRole(ACCESS_ADMIN)) fail(403, 'Only access admins can change API access.')
}

/** sys_group_has_role rows for this app's API roles, optionally limited to some groups. */
function grants(groupIds?: string[]): Grant[] {
    const gr: any = new GlideRecord('sys_group_has_role')
    gr.addQuery('role.name', 'IN', APP_ROLES.join(','))
    if (groupIds) gr.addQuery('group', 'IN', groupIds.join(','))
    gr.query()
    const out: Grant[] = []
    while (gr.next()) {
        const role = String(gr.getDisplayValue('role'))
        const api = API_CATALOG.find((a) => a.read_role === role || a.write_role === role)
        if (!api) continue
        out.push({ group: { sys_id: String(gr.getValue('group')), name: String(gr.getDisplayValue('group')) },
                   api: api.api, level: role === api.write_role ? 'write' : 'read', role })
    }
    return out
}

function memberCounts(groupIds: string[]): Record<string, number> {
    const counts: Record<string, number> = {}
    if (!groupIds.length) return counts
    const ga: any = new GlideAggregate('sys_user_grmember')
    ga.addQuery('group', 'IN', groupIds.join(','))
    ga.addAggregate('COUNT', 'group')
    ga.groupBy('group')
    ga.query()
    while (ga.next()) counts[String(ga.getValue('group'))] = parseInt(ga.getAggregate('COUNT', 'group'), 10)
    return counts
}

/** A group may be managed here only if it already holds one of this app's API roles. */
function requireManagedGroup(groupId: string): any {
    const group: any = new GlideRecord('sys_user_group')
    if (!group.get(groupId)) fail(404, 'Group not found.')
    if (!grants([groupId]).length) {
        fail(403, `${group.getValue('name')} does not hold any ITSM AI API access, so it cannot be managed here.`,
             'Grant the group read or write on an API first.')
    }
    return group
}

function userRef(gr: any, field = 'sys_id'): Json {
    const id = field === 'sys_id' ? gr.getUniqueValue() : gr.getValue(field)
    return { sys_id: String(id), name: String(field === 'sys_id' ? gr.getValue('name') : gr.getDisplayValue(field)) }
}

export class AccessService {
    constructor(private readonly api: AgentApi) {}

    /** GET /me - the caller's permissions per API, and which of their groups grant them. */
    me(): Json {
        const userId = gs.getUserID()
        const myGroups: string[] = []
        const m: any = new GlideRecord('sys_user_grmember')
        m.addQuery('user', userId)
        m.query()
        while (m.next()) myGroups.push(String(m.getValue('group')))
        const mine = myGroups.length ? grants(myGroups) : []

        return {
            user: { sys_id: userId, user_name: gs.getUserName(), name: gs.getUserDisplayName() },
            is_access_admin: gs.hasRole(ACCESS_ADMIN),
            is_auditor: gs.hasRole(AUDITOR),
            apis: API_CATALOG.map((a) => {
                const write = !!a.write_role && gs.hasRole(a.write_role)
                return {
                    api: a.api, title: a.title, description: a.description,
                    read: write || gs.hasRole(a.read_role),
                    write,
                    granted_by: mine.filter((g) => g.api === a.api).map((g) => ({ group: g.group.name, level: g.level })),
                }
            }),
        }
    }

    /** GET /apis - every API, its routes and the groups that can read / write it. */
    apis(): Json[] {
        const all = grants()
        const counts = memberCounts([...new Set(all.map((g) => g.group.sys_id))])
        const withCount = (g: Grant) => ({ ...g.group, members: counts[g.group.sys_id] ?? 0 })
        return API_CATALOG.map((a) => ({
            ...a,
            read_groups: all.filter((g) => g.api === a.api && g.level === 'read').map(withCount),
            write_groups: all.filter((g) => g.api === a.api && g.level === 'write').map(withCount),
        }))
    }

    /** GET /groups?q=&managed=true - groups, with the API access each holds. */
    groups(): Json[] {
        const q = this.api.query('q', '')
        const managedOnly = this.api.query('managed') === 'true'
        const all = grants()
        const managedIds = [...new Set(all.map((g) => g.group.sys_id))]
        const gr: any = new GlideRecordSecure('sys_user_group')
        gr.addActiveQuery()
        if (q) gr.addQuery('name', 'CONTAINS', q)
        if (managedOnly) gr.addQuery('sys_id', 'IN', managedIds.join(','))
        gr.orderBy('name')
        gr.setLimit(this.api.queryInt('limit', 50, 1, 200))
        gr.query()
        const rows: Json[] = []
        const ids: string[] = []
        while (gr.next()) {
            const id = String(gr.getUniqueValue())
            ids.push(id)
            rows.push({ sys_id: id, name: String(gr.getValue('name')), description: String(gr.getValue('description') ?? ''),
                        grants: all.filter((g) => g.group.sys_id === id).map((g) => ({ api: g.api, level: g.level })) })
        }
        const counts = memberCounts(ids)
        return rows.map((r) => ({ ...r, members: counts[r.sys_id as string] ?? 0, managed: managedIds.includes(r.sys_id as string) }))
    }

    /** POST /groups {name, description, grants:[{api, level}]} - a new group always starts with some API access. */
    createGroup(): Json {
        requireAccessAdmin()
        const b = this.api.body()
        const name = String(b.name ?? '').trim()
        if (name.length < 3 || name.length > 80) fail(400, 'name must be 3-80 characters.')
        const initial = (b.grants as { api: string; level: string }[] | undefined) ?? []
        if (!initial.length) fail(400, 'A new group needs at least one API grant.')
        const roles = initial.map((g) => roleFor(catalogApi(g.api), g.level))

        const existing: any = new GlideRecord('sys_user_group')
        existing.addQuery('name', name)
        existing.query()
        if (existing.hasNext()) fail(409, `A group named ${name} already exists.`)

        const group: any = new GlideRecord('sys_user_group')
        group.initialize()
        group.setValue('name', name)
        group.setValue('description', String(b.description ?? `ITSM AI API access group (created by ${gs.getUserName()})`))
        if (!group.insert()) fail(422, 'The group could not be created.')
        this.api.created = true
        for (const role of roles) this.addRole(group.getUniqueValue(), role)
        return { sys_id: String(group.getUniqueValue()), name, grants: grants([String(group.getUniqueValue())]) }
    }

    /** GET /groups/{id}/members */
    members(): Json[] {
        const groupId = requireSysId(this.api.path('id'), 'group')
        const m: any = new GlideRecord('sys_user_grmember')
        m.addQuery('group', groupId)
        m.orderBy('user.name')
        m.setLimit(500)
        m.query()
        const out: Json[] = []
        while (m.next()) {
            out.push({ ...userRef(m, 'user'), user_name: String(m.getDisplayValue('user.user_name') ?? ''),
                       email: String(m.getDisplayValue('user.email') ?? '') })
        }
        return out
    }

    /** POST /groups/{id}/members {user} */
    addMember(): Json {
        requireAccessAdmin()
        const groupId = requireSysId(this.api.path('id'), 'group')
        const userId = requireSysId(this.api.body().user, 'user')
        const group = requireManagedGroup(groupId)
        const user: any = new GlideRecord('sys_user')
        if (!user.get(userId) || String(user.getValue('active')) !== '1' && String(user.getValue('active')) !== 'true') {
            fail(404, 'User not found or inactive.')
        }
        const m: any = new GlideRecord('sys_user_grmember')
        m.addQuery('group', groupId)
        m.addQuery('user', userId)
        m.query()
        if (!m.hasNext()) {
            m.initialize()
            m.setValue('group', groupId)
            m.setValue('user', userId)
            if (!m.insert()) fail(422, 'The membership could not be added.')
            this.api.created = true
        }
        return { group: String(group.getValue('name')), user: String(user.getValue('name')), member: true }
    }

    /** POST /groups/{id}/members/remove {user} */
    removeMember(): Json {
        requireAccessAdmin()
        const groupId = requireSysId(this.api.path('id'), 'group')
        const userId = requireSysId(this.api.body().user, 'user')
        const group = requireManagedGroup(groupId)
        const m: any = new GlideRecord('sys_user_grmember')
        m.addQuery('group', groupId)
        m.addQuery('user', userId)
        m.query()
        let removed = 0
        while (m.next()) removed += m.deleteRecord() ? 1 : 0
        return { group: String(group.getValue('name')), removed }
    }

    /** GET /users?q= - active users to add to a group. */
    users(): Json[] {
        const q = this.api.query('q', '').trim()
        if (q.length < 2) return []
        const gr: any = new GlideRecordSecure('sys_user')
        gr.addActiveQuery()
        gr.addQuery('name', 'CONTAINS', q).addOrCondition('user_name', 'STARTSWITH', q).addOrCondition('email', 'STARTSWITH', q)
        gr.orderBy('name')
        gr.setLimit(this.api.queryInt('limit', 10, 1, 25))
        gr.query()
        const out: Json[] = []
        while (gr.next()) {
            out.push({ sys_id: String(gr.getUniqueValue()), name: String(gr.getValue('name')),
                       user_name: String(gr.getValue('user_name')), email: String(gr.getValue('email') ?? '') })
        }
        return out
    }

    /** POST /grants {group, api, level} */
    grant(): Json {
        requireAccessAdmin()
        const b = this.api.body()
        const groupId = requireSysId(b.group, 'group')
        const role = roleFor(catalogApi(b.api), b.level)
        const group: any = new GlideRecord('sys_user_group')
        if (!group.get(groupId)) fail(404, 'Group not found.')
        this.addRole(groupId, role)
        return { group: String(group.getValue('name')), api: b.api, level: b.level, granted: true }
    }

    /** POST /grants/revoke {group, api, level} */
    revoke(): Json {
        requireAccessAdmin()
        const b = this.api.body()
        const groupId = requireSysId(b.group, 'group')
        const role = roleFor(catalogApi(b.api), b.level)
        const gr: any = new GlideRecord('sys_group_has_role')
        gr.addQuery('group', groupId)
        gr.addQuery('role.name', role)
        gr.query()
        let removed = 0
        while (gr.next()) removed += gr.deleteRecord() ? 1 : 0
        return { api: b.api, level: b.level, revoked: removed > 0 }
    }

    private addRole(groupId: string, roleName: string): void {
        if (!APP_ROLES.includes(roleName)) fail(400, 'Only ITSM AI API roles can be granted here.')
        const role: any = new GlideRecord('sys_user_role')
        if (!role.get('name', roleName)) fail(500, `Role ${roleName} is missing; reinstall the app.`)
        const gr: any = new GlideRecord('sys_group_has_role')
        gr.addQuery('group', groupId)
        gr.addQuery('role', role.getUniqueValue())
        gr.query()
        if (gr.hasNext()) return
        gr.initialize()
        gr.setValue('group', groupId)
        gr.setValue('role', role.getUniqueValue())
        gr.setValue('inherits', true)
        if (!gr.insert()) fail(422, `Could not grant ${roleName}; check the app's cross-scope privileges.`)
        this.api.created = true
    }
}
