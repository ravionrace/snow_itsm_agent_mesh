import '@servicenow/sdk/global'
import { ChoiceColumn, IntegerColumn, MultiLineTextColumn, ReferenceColumn, StringColumn, Table, TableNameColumn } from '@servicenow/sdk/core'

/**
 * One row per Scripted REST call (agent tool call or console request): who, what, which record, outcome.
 * Written only by AgentApi (src/server/lib/agent-api.ts); read by auditors and the operations console.
 */
export const x_1320561_itsm_ai_tool_audit = Table({
    name: 'x_1320561_itsm_ai_tool_audit',
    label: 'Agent Tool Call Audit',
    display: 'resource',
    schema: {
        api: StringColumn({ label: 'API', maxLength: 40 }),
        resource: StringColumn({ label: 'Resource', maxLength: 60 }),
        http_method: StringColumn({ label: 'HTTP method', maxLength: 10 }),
        http_status: IntegerColumn({ label: 'HTTP status' }),
        outcome: ChoiceColumn({
            label: 'Outcome',
            choices: {
                success: { label: 'Success', sequence: 0 },
                error: { label: 'Error', sequence: 1 },
                replay: { label: 'Idempotent replay', sequence: 2 },
            },
        }),
        user: ReferenceColumn({ label: 'User', referenceTable: 'sys_user' }),
        request_id: StringColumn({ label: 'Agent request id', maxLength: 64 }),
        session_id: StringColumn({ label: 'Agent session id', maxLength: 64 }),
        idempotency_key: StringColumn({ label: 'Idempotency key', maxLength: 80 }),
        record_table: TableNameColumn({ label: 'Record table' }),
        record_sys_id: StringColumn({ label: 'Record sys_id', maxLength: 32 }),
        record_number: StringColumn({ label: 'Record number', maxLength: 40 }),
        response: MultiLineTextColumn({ label: 'Stored response (idempotent writes)', maxLength: 65000 }),
        duration_ms: IntegerColumn({ label: 'Duration (ms)' }),
    },
    index: [
        { name: 'idx_idempotency', unique: false, element: ['idempotency_key', 'user'] },
        { name: 'idx_record', unique: false, element: 'record_number' },
        { name: 'idx_created_api', unique: false, element: ['sys_created_on', 'api'] },
    ],
    accessibleFrom: 'package_private',
    actions: { read: true },
    allowWebServiceAccess: true,
    callerAccess: 'none',
    createAccessControls: false,
})
