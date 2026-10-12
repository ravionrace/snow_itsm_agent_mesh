import { useCallback, useEffect, useState } from 'react'

// Every view is addressable by URL: ?view=overview | access&group=<id> | audit | record&id=<sys_id> | explorer
export type View = 'overview' | 'access' | 'audit' | 'record' | 'explorer'
const VIEWS: View[] = ['overview', 'access', 'audit', 'record', 'explorer']

export interface Route {
    view: View
    params: URLSearchParams
}

function read(): Route {
    const params = new URLSearchParams(window.location.search)
    const v = params.get('view') as View | null
    return { view: v && VIEWS.includes(v) ? v : 'overview', params }
}

const TITLES: Record<View, string> = {
    overview: 'Overview',
    access: 'API access',
    audit: 'Tool call audit',
    record: 'Audit record',
    explorer: 'API explorer',
}

/** Keeps the page title right both standalone and inside the Polaris (Next Experience) iframe. */
function setTitle(view: View) {
    const title = `ITSM AI Console - ${TITLES[view]}`
    document.title = title
    if (window.self !== window.top) {
        window.parent.postMessage({ type: 'SN_UPDATE_TITLE', title }, window.location.origin)
    }
}

export function useRoute(): [Route, (view: View, extra?: Record<string, string>) => void] {
    const [route, setRoute] = useState<Route>(read)

    useEffect(() => {
        const onPop = () => setRoute(read())
        window.addEventListener('popstate', onPop)
        return () => window.removeEventListener('popstate', onPop)
    }, [])

    useEffect(() => setTitle(route.view), [route.view])

    const navigate = useCallback((view: View, extra: Record<string, string> = {}) => {
        const params = new URLSearchParams({ view, ...extra })
        window.history.pushState(null, '', `${window.location.pathname}?${params.toString()}`)
        setRoute(read())
    }, [])

    return [route, navigate]
}
