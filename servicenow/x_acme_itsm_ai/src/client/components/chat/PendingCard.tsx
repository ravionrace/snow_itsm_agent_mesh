import React from 'react'
import { Card } from '@servicenow/react-components/Card'
import { CardHeader } from '@servicenow/react-components/CardHeader'
import { Button } from '@servicenow/react-components/Button'
import { chat, type Pending } from '../../store/features/chat'
import { useChatDispatch } from '../../store/assistant-store'

/** Nothing changes in ServiceNow until the user clicks Confirm here. */
export default function PendingCard({ pending }: { pending: Pending }) {
    const dispatch = useChatDispatch()
    const p = pending.preview ?? {}
    const decide = (decision: 'confirm' | 'reject') => dispatch(chat.decide({ pendingId: pending.id, decision }))
    return (
        <Card size="md" sidebar={{ color: 'warning', variant: 'primary' }} className="pending-card">
            <CardHeader heading={{ label: `${p.action ?? pending.tool}${p.record ? ` · ${p.record}` : ''}`, level: 3, size: 'md' }}
                        caption={{ label: 'Review the change. Nothing is saved until you confirm.' }} />
            {!!p.changes?.length && (
                <table className="changes">
                    <tbody>
                        {p.changes.map((c) => (
                            <tr key={c.field}>
                                <td>{c.field}</td>
                                <td>{c.from ? <><s>{c.from}</s> → </> : null}{c.to}</td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            )}
            {p.warnings?.map((w) => <p key={w} className="warning">{w}</p>)}
            <div className="pending-actions">
                <Button label="Confirm" variant="primary" icon="check-outline" onClicked={() => decide('confirm')} />
                <Button label="Reject" variant="secondary" onClicked={() => decide('reject')} />
            </div>
        </Card>
    )
}
