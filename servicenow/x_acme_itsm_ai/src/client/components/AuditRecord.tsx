import React, { useEffect } from 'react'
import { Button } from '@servicenow/react-components/Button'
import { LabelValueStacked } from '@servicenow/react-components/LabelValueStacked'
import { Loader } from '@servicenow/react-components/Loader'
import { TextLink } from '@servicenow/react-components/TextLink'
import { Card } from '@servicenow/react-components/Card'
import RequestErrors from './RequestErrors'
import { audit, AUDIT_FIELDS, AUDIT_TABLE } from '../store/features/audit'
import { useAppDispatch, useAppSelector } from '../store'
import { display } from '../utils/fields'

interface Props {
    sysId: string
    onBack: () => void
}

const LABELS: Record<string, string> = {
    sys_created_on: 'When (UTC)', user: 'User', api: 'API', resource: 'Resource', http_method: 'Method',
    http_status: 'HTTP status', outcome: 'Outcome', record_table: 'Record table', record_number: 'Record',
    duration_ms: 'Duration (ms)', request_id: 'Agent request id', session_id: 'Agent session id',
    idempotency_key: 'Idempotency key', response: 'Stored response',
}

/**
 * Audit rows are immutable, so this is a read-only view over the Table API. It deliberately avoids the
 * record-form engine (RecordProvider / FormColumnLayout), which failed to initialise on this instance.
 */
export default function AuditRecord({ sysId, onBack }: Props) {
    const dispatch = useAppDispatch()
    const record = useAppSelector((s) => (s.audit.record?.sys_id === undefined || display(s.audit.record.sys_id) === sysId
        ? s.audit.record : null))
    const failed = useAppSelector((s) => !!s.requests.errors[audit.loadRecord.type])
    useEffect(() => { dispatch(audit.loadRecord(sysId)) }, [dispatch, sysId])

    return (
        <div className="view">
            <div className="toolbar toolbar-wide">
                <Button label="Back to audit" icon="arrow-left-outline" variant="tertiary" onClicked={onBack} />
                <TextLink label="Open classic form" href={`/${AUDIT_TABLE}.do?sys_id=${encodeURIComponent(sysId)}`} opensWindow />
            </div>
            <RequestErrors commands={[audit.loadRecord]} />
            {!record && !failed && <div className="loading"><Loader label="Loading audit record" /></div>}
            {record && (
                <Card size="md">
                    <LabelValueStacked align="horizontal" items={AUDIT_FIELDS
                        .filter((f) => display(record[f]) !== '')
                        .map((f) => ({ label: LABELS[f] ?? f, value: display(record[f]) }))} />
                </Card>
            )}
        </div>
    )
}
