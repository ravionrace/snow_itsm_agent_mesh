import React from 'react'
import { Card } from '@servicenow/react-components/Card'
import { CardHeader } from '@servicenow/react-components/CardHeader'
import { LabelValueStacked } from '@servicenow/react-components/LabelValueStacked'
import { useAppSelector } from '../../store'

/** What the viewer themselves may do - the same answer the Access agent gets for them. */
export default function MyAccessCard() {
    const me = useAppSelector((s) => s.access.me)
    if (!me) return null
    const role = me.is_access_admin ? 'Access admin' : me.is_auditor ? 'Auditor' : 'User'
    return (
        <Card size="md">
            <CardHeader heading={{ label: `My access - ${me.user.name}`, level: 3, size: 'md' }}
                        caption={{ label: `${role}. This is what the AI agents may do on your behalf.` }} />
            <LabelValueStacked align="horizontal-equal" items={me.apis.map((a) => ({
                label: a.title,
                value: { type: 'highlighted-value', label: a.write ? 'Read & write' : a.read ? 'Read' : 'No access',
                         color: a.write ? 'positive' : a.read ? 'info' : 'low' },
            }))} />
        </Card>
    )
}
