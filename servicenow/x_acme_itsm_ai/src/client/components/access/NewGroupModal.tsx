import React, { useState } from 'react'
import { Modal } from '@servicenow/react-components/Modal'
import { Input } from '@servicenow/react-components/Input'
import { Textarea } from '@servicenow/react-components/Textarea'
import { Select } from '@servicenow/react-components/Select'
import { RadioButtons } from '@servicenow/react-components/RadioButtons'
import { access, type Level } from '../../store/features/access'
import { useAppDispatch, useAppSelector } from '../../store'

/** A new group always starts with one API grant, so it is immediately manageable here. */
export default function NewGroupModal({ onClose }: { onClose: () => void }) {
    const dispatch = useAppDispatch()
    const apis = useAppSelector((s) => s.access.apis)
    const [name, setName] = useState('')
    const [description, setDescription] = useState('')
    const [api, setApi] = useState(apis[0]?.api ?? '')
    const [level, setLevel] = useState<Level>('read')
    const selected = apis.find((a) => a.api === api)
    const valid = name.trim().length >= 3 && !!api

    const submit = () => {
        dispatch(access.createGroup({ name: name.trim(), description, grants: [{ api, level }] }))
        onClose()
    }

    return (
        <Modal opened size="md" headerLabel="New group with API access"
               footerActions={[{ label: 'Cancel', variant: 'secondary', clickActionType: 'cancel' },
                               { label: 'Create', variant: 'primary', clickActionType: 'create', disabled: !valid }]}
               onFooterActionClicked={(e) => (e.detail.payload.action.clickActionType === 'create' ? submit() : onClose())}
               onOpenedSet={onClose}>
            <div className="form-stack">
                <Input label="Name" required maxlength={80} value={name} onInput={(e) => setName(e.detail.payload.fieldValue)} />
                <Textarea label="Description" rows={2} value={description}
                          onInput={(e) => setDescription(e.detail.payload.fieldValue)} />
                <Select label="API" items={apis.map((a) => ({ id: a.api, label: a.title }))} selectedItem={api}
                        onSelectedItemSet={(e) => { setApi(String(e.detail.payload.value)); setLevel('read') }} />
                <RadioButtons name="new-group-level" label="Access" layout="horizontal" value={level}
                              options={[{ id: 'read', label: 'Read' },
                                        { id: 'write', label: 'Read & write', disabled: !selected?.write_role }]}
                              onValueSet={(e: any) => setLevel(e.detail.payload.value as Level)} />
            </div>
        </Modal>
    )
}
