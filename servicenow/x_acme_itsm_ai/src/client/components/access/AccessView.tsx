import React, { useEffect, useState } from 'react'
import { Button } from '@servicenow/react-components/Button'
import { Loader } from '@servicenow/react-components/Loader'
import MyAccessCard from './MyAccessCard'
import ApiAccessCard from './ApiAccessCard'
import GrantModal from './GrantModal'
import GroupsPanel from './GroupsPanel'
import NewGroupModal from './NewGroupModal'
import RequestErrors from '../RequestErrors'
import { access, type ApiAccess } from '../../store/features/access'
import { selectPending } from '../../store/kit'
import { useAppDispatch, useAppSelector } from '../../store'

const WATCHED = [access.loadMe, access.loadApis, access.loadGroups, access.grant, access.revoke, access.createGroup,
    access.addMember, access.removeMember, access.loadMembers]

interface Props {
    groupId: string | null
    onSelectGroup: (groupId: string) => void
}

/** Every API, who can read and write it, and (for access admins) the controls to change that. */
export default function AccessView({ groupId, onSelectGroup }: Props) {
    const dispatch = useAppDispatch()
    const apis = useAppSelector((s) => s.access.apis)
    const canManage = useAppSelector((s) => !!s.access.me?.is_access_admin)
    const loading = useAppSelector((s) => selectPending(s, access.loadApis))
    const [granting, setGranting] = useState<ApiAccess | null>(null)
    const [creating, setCreating] = useState(false)

    useEffect(() => {
        dispatch(access.loadMe())
        dispatch(access.loadApis())
        dispatch(access.loadGroups())
    }, [dispatch])

    return (
        <div className="view">
            <RequestErrors commands={WATCHED} />
            <div className="toolbar toolbar-wide no-print">
                <Button label="Refresh" icon="sync-outline" variant="tertiary" onClicked={() => dispatch(access.changed())} />
                <Button label="Print" icon="printer-outline" variant="tertiary" onClicked={() => window.print()} />
            </div>
            <MyAccessCard />
            {loading && !apis.length && <div className="loading"><Loader label="Loading APIs" /></div>}
            <div className="api-grid">
                {apis.map((a) => <ApiAccessCard key={a.api} api={a} canManage={canManage} onGrant={setGranting} />)}
            </div>
            <div className="no-print">
                <GroupsPanel selected={groupId} onSelect={onSelectGroup} canManage={canManage} onNewGroup={() => setCreating(true)} />
            </div>
            {granting && <GrantModal api={granting} onClose={() => setGranting(null)} />}
            {creating && <NewGroupModal onClose={() => setCreating(false)} />}
        </div>
    )
}
