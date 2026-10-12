import React from 'react'
import { NowRecordListConnected } from '@servicenow/react-components/NowRecordListConnected'
import { Select } from '@servicenow/react-components/Select'

import { AUDIT_TABLE as TABLE } from '../store/features/audit'
const COLUMNS = 'sys_created_on,user,api,resource,http_method,http_status,outcome,record_number,duration_ms'

// Each filter is an encoded query; it is passed as the list's React key, which re-mounts the list filtered.
export const AUDIT_FILTERS: Record<string, { label: string; query: string }> = {
    all: { label: 'All calls', query: 'ORDERBYDESCsys_created_on' },
    errors: { label: 'Errors', query: 'outcome=error^ORDERBYDESCsys_created_on' },
    writes: { label: 'Writes', query: 'http_method!=GET^ORDERBYDESCsys_created_on' },
    replays: { label: 'Idempotent replays', query: 'outcome=replay^ORDERBYDESCsys_created_on' },
}

interface Props {
    filter: string
    onFilterChange: (filter: string) => void
    onOpen: (sysId: string) => void
}

export default function AuditList({ filter, onFilterChange, onOpen }: Props) {
    const active = AUDIT_FILTERS[filter] ? filter : 'all'
    return (
        <div className="view">
            <div className="toolbar">
                <Select label="Show" selectedItem={active}
                        items={Object.entries(AUDIT_FILTERS).map(([id, f]) => ({ id, label: f.label }))}
                        onSelectedItemSet={(e) => onFilterChange(String(e.detail.payload.value))} />
            </div>
            <NowRecordListConnected
                key={AUDIT_FILTERS[active].query}
                table={TABLE}
                listTitle="Agent tool calls"
                columns={COLUMNS}
                limit={50}
                hideHeader
                hideInlineEditing
                hideQuickEdit
                hideRowSelector
                onRowClicked={(e: any) => {
                    const sysId = e.detail?.payload?.sys_id
                    if (sysId) onOpen(sysId)
                }}
            />
        </div>
    )
}
