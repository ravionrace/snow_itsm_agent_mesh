import React, { Suspense } from 'react'
import { Heading } from '@servicenow/react-components/Heading'
import { Tabs } from '@servicenow/react-components/Tabs'
import Overview from './components/Overview'
import AuditRecord from './components/AuditRecord'
import ViewBoundary from './components/ViewBoundary'
import { Loader } from '@servicenow/react-components/Loader'
import { AUDIT_TABLE } from './store/features/audit'
import ApiExplorer from './components/ApiExplorer'
import AccessView from './components/access/AccessView'
import { RESOURCES } from './services/explorerResources'
import { useRoute, type View } from './utils/navigation'
import './app.css'

// The record list engine is loaded only when the audit tab is opened, in its own chunk, so a platform-side
// failure there cannot blank the rest of the console.
const AuditList = React.lazy(() => import('./components/AuditList'))

const TABS = [
    { id: 'overview', label: 'Overview', icon: 'chart-bar-vertical-outline' },
    { id: 'access', label: 'API access', icon: 'lock-outline' },
    { id: 'audit', label: 'Tool call audit', icon: 'list-outline' },
    { id: 'explorer', label: 'API explorer', icon: 'code-outline' },
]

/**
 * ITSM AI operations console. Navigation lives in the URL (?view=...), so every view can be bookmarked and
 * the browser back button works; the tabs only reflect and change that URL.
 */
export default function App() {
    const [route, navigate] = useRoute()
    const p = route.params
    const selectedTab = route.view === 'record' ? 'audit' : route.view

    return (
        <div className="console">
            <header className="console-header">
                <Heading label="ITSM AI Operations Console" level={1} variant="header-secondary" hasNoMargin />
                <span className="caption">Agent mesh activity for x_1320561_itsm_ai</span>
            </header>
            <Tabs items={TABS} selectedItem={selectedTab}
                  onSelectedItemSet={(e) => navigate(String(e.detail.payload.value) as View)} />
            <main>
                {route.view === 'overview' && (
                    <Overview hours={p.get('hours') ?? '24'} onHoursChange={(hours) => navigate('overview', { hours })} />
                )}
                {route.view === 'access' && (
                    <AccessView groupId={p.get('group')} onSelectGroup={(group) => navigate('access', { group })} />
                )}
                {route.view === 'audit' && (
                    <ViewBoundary title="The audit list" fallbackLabel="Open the audit list in the classic UI"
                                  fallbackHref={`/${AUDIT_TABLE}_list.do?sysparm_query=ORDERBYDESCsys_created_on`}>
                        <Suspense fallback={<div className="loading"><Loader label="Loading audit list" /></div>}>
                            <AuditList filter={p.get('filter') ?? 'all'}
                                       onFilterChange={(filter) => navigate('audit', { filter })}
                                       onOpen={(id) => navigate('record', { id })} />
                        </Suspense>
                    </ViewBoundary>
                )}
                {route.view === 'record' && p.get('id') && (
                    <AuditRecord sysId={p.get('id') as string} onBack={() => window.history.back()} />
                )}
                {route.view === 'explorer' && (
                    <ApiExplorer resourceId={p.get('resource') ?? RESOURCES[0].id}
                                 onResourceChange={(resource) => navigate('explorer', { resource })} />
                )}
            </main>
        </div>
    )
}
