import { createSlice, type PayloadAction } from '@reduxjs/toolkit'
import { get } from '../http'
import { commands, endpoint, payload } from '../kit'
import type { Field } from '../../utils/fields'

export const AUDIT_TABLE = 'x_1320561_itsm_ai_tool_audit'
export const AUDIT_FIELDS = ['sys_created_on', 'user', 'api', 'resource', 'http_method', 'http_status', 'outcome',
    'record_table', 'record_number', 'duration_ms', 'request_id', 'session_id', 'idempotency_key', 'response']

export type AuditRecord = Record<string, Field>

export const audit = commands('audit', { loadRecord: payload<string>() })

export const auditSlice = createSlice({
    name: 'auditData',
    initialState: { record: null as AuditRecord | null },
    reducers: { recordLoaded: (s, a: PayloadAction<AuditRecord>) => { s.record = a.payload } },
})

// Table API, read as the viewer (audit read ACL: auditor).
export const auditEpics = endpoint(
    audit.loadRecord,
    (sysId) => get(`/api/now/table/${AUDIT_TABLE}/${encodeURIComponent(sysId)}`,
        { sysparm_display_value: 'all', sysparm_fields: AUDIT_FIELDS.join(',') }),
    auditSlice.actions.recordLoaded,
)
