import React, { useState } from 'react'
import { Modal } from '@servicenow/react-components/Modal'
import { Typeahead } from '@servicenow/react-components/Typeahead'
import { RadioButtons } from '@servicenow/react-components/RadioButtons'
import { access, type ApiAccess, type Level } from '../../store/features/access'
import { useAppDispatch, useAppSelector } from '../../store'

export default function GrantModal({ api, onClose }: { api: ApiAccess; onClose: () => void }) {
    const dispatch = useAppDispatch()
    const results = useAppSelector((s) => s.access.groupResults)
    const [group, setGroup] = useState<string>('')
    const [level, setLevel] = useState<Level>('read')

    const submit = () => {
        if (group) dispatch(access.grant({ group, api: api.api, level }))
        onClose()
    }

    return (
        <Modal opened size="md" headerLabel={`Grant access to the ${api.title} API`}
               footerActions={[{ label: 'Cancel', variant: 'secondary', clickActionType: 'cancel' },
                               { label: 'Grant', variant: 'primary', clickActionType: 'grant', disabled: !group }]}
               onFooterActionClicked={(e) => (e.detail.payload.action.clickActionType === 'grant' ? submit() : onClose())}
               onOpenedSet={onClose}>
            <div className="form-stack">
                <Typeahead label="Group" placeholder="Type a group name" search="managed"
                           items={results.map((g) => ({ id: g.sys_id, label: g.name }))}
                           onValueSet={(e) => dispatch(access.searchGroups(e.detail.payload.value))}
                           onSelectedItemSet={(e) => setGroup(String(e.detail.payload.value))} />
                <RadioButtons name="level" label="Access" layout="horizontal" value={level}
                              options={[{ id: 'read', label: 'Read' },
                                        { id: 'write', label: 'Read & write', disabled: !api.write_role }]}
                              onValueSet={(e: any) => setLevel(e.detail.payload.value as Level)} />
            </div>
        </Modal>
    )
}
