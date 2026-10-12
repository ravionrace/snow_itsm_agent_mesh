import { createSlice, type PayloadAction } from '@reduxjs/toolkit'
import { combineEpics } from 'redux-observable'
import { debounceTime, distinctUntilChanged, filter, map, of, switchMap, catchError } from 'rxjs'
import { get, post, send } from '../http'
import { commands, endpoint, none, payload, react, type AppEpic } from '../kit'

// ---------------------------------------------------------------------------------------------- model

export type Level = 'read' | 'write'
export interface GroupRef { sys_id: string; name: string; members?: number }
export interface ApiRoute { name: string; method: string; path: string; summary: string; level: Level }
export interface ApiAccess {
    api: string; title: string; description: string; base_path: string
    read_role: string; write_role: string | null; routes: ApiRoute[]
    read_groups: GroupRef[]; write_groups: GroupRef[]
}
export interface MyApi { api: string; title: string; read: boolean; write: boolean; granted_by: { group: string; level: Level }[] }
export interface Me { user: { sys_id: string; user_name: string; name: string }; is_access_admin: boolean; is_auditor: boolean; apis: MyApi[] }
export interface Group { sys_id: string; name: string; description: string; members: number; managed: boolean; grants: { api: string; level: Level }[] }
export interface User { sys_id: string; name: string; user_name?: string; email?: string }
export interface Grant { group: string; api: string; level: Level }
export interface Membership { groupId: string; userId: string }
export interface NewGroup { name: string; description: string; grants: { api: string; level: Level }[] }

// ---------------------------------------------------------------------------------------------- commands

export const access = commands('access', {
    loadMe: none,
    loadApis: none,
    loadGroups: none,
    loadMembers: payload<string>(),
    searchUsers: payload<string>(),
    searchGroups: payload<string>(),
    grant: payload<Grant>(),
    revoke: payload<Grant>(),
    createGroup: payload<NewGroup>(),
    addMember: payload<Membership>(),
    removeMember: payload<Membership>(),
    changed: none,
})

// ---------------------------------------------------------------------------------------------- state

interface AccessState {
    me: Me | null
    apis: ApiAccess[]
    groups: Group[]
    members: Record<string, User[]>
    userResults: User[]
    groupResults: GroupRef[]
}

const initialState: AccessState = { me: null, apis: [], groups: [], members: {}, userResults: [], groupResults: [] }

export const accessSlice = createSlice({
    name: 'accessData',
    initialState,
    reducers: {
        meLoaded: (s, a: PayloadAction<Me>) => { s.me = a.payload },
        apisLoaded: (s, a: PayloadAction<ApiAccess[]>) => { s.apis = a.payload },
        groupsLoaded: (s, a: PayloadAction<Group[]>) => { s.groups = a.payload },
        membersLoaded: (s, a: PayloadAction<{ groupId: string; members: User[] }>) => { s.members[a.payload.groupId] = a.payload.members },
        usersFound: (s, a: PayloadAction<User[]>) => { s.userResults = a.payload },
        groupsFound: (s, a: PayloadAction<GroupRef[]>) => { s.groupResults = a.payload },
    },
})
const loaded = accessSlice.actions

// ---------------------------------------------------------------------------------------------- epics
//            when ...             call ...                                                      then ...

const endpoints = combineEpics(
    endpoint(access.loadMe,       ()        => get('/access/me'),                                  loaded.meLoaded),
    endpoint(access.loadApis,     ()        => get('/access/apis'),                                loaded.apisLoaded),
    endpoint(access.loadGroups,   ()        => get('/access/groups', { managed: true }),           loaded.groupsLoaded),
    endpoint(access.loadMembers,  (groupId) => get(`/access/groups/${groupId}/members`),           (members: User[], groupId) => loaded.membersLoaded({ groupId, members }), 'merge'),
    endpoint(access.grant,        (g)       => post('/access/grants', g),                          access.changed, 'concat'),
    endpoint(access.revoke,       (g)       => post('/access/grants/revoke', g),                   access.changed, 'concat'),
    endpoint(access.createGroup,  (group)   => post('/access/groups', group),                      access.changed, 'exhaust'),
    endpoint(access.addMember,    (m)       => post(`/access/groups/${m.groupId}/members`, { user: m.userId }),        (_, m) => access.loadMembers(m.groupId), 'concat'),
    endpoint(access.removeMember, (m)       => post(`/access/groups/${m.groupId}/members/remove`, { user: m.userId }), (_, m) => access.loadMembers(m.groupId), 'concat'),
)

const reactions = combineEpics(
    // Any access change can alter the API table, the group list and the viewer's own permissions.
    react(access.changed, () => [access.loadApis(), access.loadGroups(), access.loadMe()]),
    react(access.addMember, () => [access.loadGroups()]),
    react(access.removeMember, () => [access.loadGroups()]),
)

/** Type-ahead search: debounce keystrokes, ignore repeats, cancel stale requests, clear on short input. */
const typeahead = <T>(trigger: typeof access.searchUsers, url: string, found: (items: T[]) => PayloadAction<T[]>): AppEpic =>
    (action$) => action$.pipe(
        filter(trigger.match),
        map((a) => a.payload.trim()),
        debounceTime(250),
        distinctUntilChanged(),
        switchMap((q) => q.length < 2 ? of(found([])) : send<T[]>(get(url, { q, limit: 10 })).pipe(
            map(found),
            catchError(() => of(found([]))),
        )),
    )

export const accessEpics = combineEpics(
    endpoints,
    reactions,
    typeahead<User>(access.searchUsers, '/access/users', loaded.usersFound),
    typeahead<GroupRef>(access.searchGroups, '/access/groups', loaded.groupsFound),
)
