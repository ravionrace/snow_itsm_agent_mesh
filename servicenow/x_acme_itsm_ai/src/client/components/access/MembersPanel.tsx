import React, { useEffect } from 'react'
import { Card } from '@servicenow/react-components/Card'
import { CardHeader } from '@servicenow/react-components/CardHeader'
import { Typeahead } from '@servicenow/react-components/Typeahead'
import { ButtonIconic } from '@servicenow/react-components/ButtonIconic'
import { Loader } from '@servicenow/react-components/Loader'
import { access, type Group } from '../../store/features/access'
import { selectPending } from '../../store/kit'
import { useAppDispatch, useAppSelector } from '../../store'

export default function MembersPanel({ group, canManage }: { group: Group; canManage: boolean }) {
    const dispatch = useAppDispatch()
    const members = useAppSelector((s) => s.access.members[group.sys_id])
    const users = useAppSelector((s) => s.access.userResults)
    const loading = useAppSelector((s) => selectPending(s, access.loadMembers))
    useEffect(() => { dispatch(access.loadMembers(group.sys_id)) }, [dispatch, group.sys_id])

    const membership = (userId: string) => ({ groupId: group.sys_id, userId })
    return (
        <Card size="md">
            <CardHeader heading={{ label: `${group.name} - members`, level: 3, size: 'md' }}
                        caption={{ label: group.grants.map((g) => `${g.api} ${g.level}`).join(', ') }} />
            {canManage && (
                <Typeahead label="Add a member" placeholder="Name, user id or email" search="managed"
                           items={users.map((u) => ({ id: u.sys_id, label: `${u.name} (${u.user_name ?? u.email ?? ''})` }))}
                           onValueSet={(e) => dispatch(access.searchUsers(e.detail.payload.value))}
                           onSelectedItemSet={(e) => dispatch(access.addMember(membership(String(e.detail.payload.value))))} />
            )}
            {!members && loading && <Loader size="md" label="Loading members" />}
            <ul className="members">
                {(members ?? []).map((u) => (
                    <li key={u.sys_id}>
                        <span>{u.name}</span><span className="caption">{u.user_name}</span>
                        {canManage && <ButtonIconic icon="close-outline" size="sm" bare tooltipContent={`Remove ${u.name}`}
                                                    onClicked={() => dispatch(access.removeMember(membership(u.sys_id)))} />}
                    </li>
                ))}
                {members && !members.length && <li className="caption">No members yet.</li>}
            </ul>
        </Card>
    )
}
