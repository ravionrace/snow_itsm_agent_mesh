import React, { useState } from 'react'
import { Card } from '@servicenow/react-components/Card'
import { CardHeader } from '@servicenow/react-components/CardHeader'
import { Button } from '@servicenow/react-components/Button'
import GroupChip from './GroupChip'
import { access, type ApiAccess, type Level } from '../../store/features/access'
import { useAppDispatch } from '../../store'

interface Props {
    api: ApiAccess
    canManage: boolean
    onGrant: (api: ApiAccess) => void
}

export default function ApiAccessCard({ api, canManage, onGrant }: Props) {
    const dispatch = useAppDispatch()
    const [showRoutes, setShowRoutes] = useState(false)
    const revoke = (group: string, level: Level) => dispatch(access.revoke({ group, api: api.api, level }))

    const row = (label: string, level: Level, groups: ApiAccess['read_groups'], hint: string) => (
        <div className="access-row">
            <span className="access-label">{label}</span>
            <div className="chips">
                {groups.length ? groups.map((g) => (
                    <GroupChip key={g.sys_id} group={g} level={level}
                               onRemove={canManage ? () => revoke(g.sys_id, level) : undefined} />
                )) : <span className="caption">{hint}</span>}
            </div>
        </div>
    )

    return (
        <Card size="md" className="api-card">
            <CardHeader heading={{ label: api.title, level: 3, size: 'md' }}
                        caption={{ label: `${api.description} · ${api.base_path}` }} />
            {row('Write', 'write', api.write_groups, api.write_role ? 'No groups' : 'Read-only API')}
            {row('Read', 'read', api.read_groups, api.write_groups.length ? 'Write groups can also read' : 'No groups')}
            <div className="card-actions">
                <Button label={showRoutes ? 'Hide routes' : `Show ${api.routes.length} routes`} variant="tertiary" size="sm"
                        onClicked={() => setShowRoutes((v) => !v)} />
                {canManage && <Button label="Grant access" icon="plus-outline" size="sm" onClicked={() => onGrant(api)} />}
            </div>
            {showRoutes && (
                <table className="routes">
                    <thead><tr><th>Method</th><th>Path</th><th>Access</th><th>Purpose</th></tr></thead>
                    <tbody>
                        {api.routes.map((r) => (
                            <tr key={`${r.method} ${r.path}`}>
                                <td><code>{r.method}</code></td><td><code>{r.path}</code></td>
                                <td className={`level-${r.level}`}>{r.level}</td><td>{r.summary}</td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            )}
        </Card>
    )
}
