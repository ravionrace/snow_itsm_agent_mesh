// Read-only resources of the agent REST layer that the explorer can call. Writes are left out on purpose:
// in the mesh they only happen after a preview and the user's Confirm click.
export interface ExplorerResource {
    id: string
    api: string
    path: string          // may contain {id}
    label: string
    params: string[]      // query parameters offered as inputs
}

export const RESOURCES: ExplorerResource[] = [
    { id: 'incident.search', api: 'incident', path: '/', label: 'Incident - search', params: ['caller', 'state', 'text', 'limit'] },
    { id: 'incident.get', api: 'incident', path: '/{id}', label: 'Incident - get', params: [] },
    { id: 'incident.similar', api: 'incident', path: '/similar', label: 'Incident - find similar', params: ['number', 'text', 'days'] },
    { id: 'incident.my_groups', api: 'incident', path: '/my_groups', label: 'Incident - my groups', params: [] },
    { id: 'problem.get', api: 'problem', path: '/{id}', label: 'Problem - get', params: [] },
    { id: 'problem.known_errors', api: 'problem', path: '/known_errors', label: 'Problem - known errors', params: ['text', 'limit'] },
    { id: 'change.templates', api: 'change', path: '/templates', label: 'Change - standard templates', params: ['text'] },
    { id: 'change.get', api: 'change', path: '/{id}', label: 'Change - get', params: [] },
    { id: 'change.risk', api: 'change', path: '/{id}/risk', label: 'Change - risk', params: [] },
    { id: 'change.conflicts', api: 'change', path: '/{id}/conflicts', label: 'Change - conflicts', params: [] },
    { id: 'change.cab_schedule', api: 'change', path: '/cab_schedule', label: 'Change - CAB schedule', params: ['days'] },
    { id: 'catalog.items', api: 'catalog', path: '/items', label: 'Catalog - search items', params: ['text', 'limit'] },
    { id: 'catalog.form', api: 'catalog', path: '/items/{id}/form', label: 'Catalog - item form', params: [] },
    { id: 'catalog.my_requests', api: 'catalog', path: '/my_requests', label: 'Catalog - my requests', params: ['state'] },
    { id: 'catalog.ritm', api: 'catalog', path: '/ritm/{id}', label: 'Catalog - requested item', params: [] },
    { id: 'cmdb.ci', api: 'cmdb', path: '/ci/{id}', label: 'CMDB - CI', params: [] },
    { id: 'cmdb.ci_by_name', api: 'cmdb', path: '/ci', label: 'CMDB - CI by name', params: ['name'] },
    { id: 'cmdb.impacted', api: 'cmdb', path: '/ci/{id}/impacted_services', label: 'CMDB - impacted services', params: ['depth'] },
    { id: 'cmdb.users', api: 'cmdb', path: '/users', label: 'CMDB - users', params: ['q'] },
    { id: 'cmdb.groups', api: 'cmdb', path: '/groups', label: 'CMDB - groups', params: ['q', 'mine'] },
    { id: 'kb.articles', api: 'kb', path: '/articles', label: 'Knowledge - search', params: ['text'] },
]
