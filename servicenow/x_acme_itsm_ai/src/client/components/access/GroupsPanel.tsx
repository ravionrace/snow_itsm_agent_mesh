import React from 'react'
import { Card } from '@servicenow/react-components/Card'
import { CardHeader } from '@servicenow/react-components/CardHeader'
import { Button } from '@servicenow/react-components/Button'
import MembersPanel from './MembersPanel'
import { useAppSelector } from '../../store'

interface Props {
    selected: string | null
    onSelect: (groupId: string) => void
    canManage: boolean
    onNewGroup: () => void
}

/** Groups that hold API access; selecting one shows (and, for admins, edits) its members. */
export default function GroupsPanel({ selected, onSelect, canManage, onNewGroup }: Props) {
    const groups = useAppSelector((s) => s.access.groups)
    const current = groups.find((g) => g.sys_id === selected)
    return (
        <div className="groups-layout">
            <Card size="md">
                <CardHeader heading={{ label: 'Groups with API access', level: 3, size: 'md' }}
                            caption={{ label: 'Members inherit the group\'s API roles.' }} />
                <ul className="group-list">
                    {groups.map((g) => (
                        <li key={g.sys_id}>
                            <Button variant={g.sys_id === selected ? 'primary' : 'tertiary'} size="sm"
                                    label={`${g.name} (${g.members})`} onClicked={() => onSelect(g.sys_id)} />
                            <span className="caption">{g.grants.map((x) => `${x.api}:${x.level}`).join(' · ')}</span>
                        </li>
                    ))}
                    {!groups.length && <li className="caption">No group has API access yet.</li>}
                </ul>
                {canManage && <Button label="New group" icon="plus-outline" size="sm" onClicked={onNewGroup} />}
            </Card>
            {current && <MembersPanel key={current.sys_id} group={current} canManage={canManage} />}
        </div>
    )
}
