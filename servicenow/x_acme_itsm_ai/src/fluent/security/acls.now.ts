import '@servicenow/sdk/global'
import { Acl } from '@servicenow/sdk/core'
import { auditor } from './roles.now'

// REST endpoint ACLs (per-API read/write, console, access) are generated in api-access.now.ts.

/** The operations console UI page. */
Acl({
    $id: Now.ID['acl-console-page'],
    type: 'ui_page',
    name: 'x_1320561_itsm_ai_agent_console',
    operation: 'read',
    roles: [auditor],
    description: 'Only auditors can open the operations console.',
})

// Audit table: auditors read; nobody but admin writes. AgentApi writes rows with GlideRecord.
Acl({
    $id: Now.ID['acl-audit-read'],
    type: 'record',
    table: 'x_1320561_itsm_ai_tool_audit',
    operation: 'read',
    roles: [auditor],
})

Acl({
    $id: Now.ID['acl-audit-field-read'],
    type: 'record',
    table: 'x_1320561_itsm_ai_tool_audit',
    field: '*',
    operation: 'read',
    roles: [auditor],
})

Acl({
    $id: Now.ID['acl-audit-create'],
    type: 'record',
    table: 'x_1320561_itsm_ai_tool_audit',
    operation: 'create',
    roles: ['admin'],
})

Acl({
    $id: Now.ID['acl-audit-write'],
    type: 'record',
    table: 'x_1320561_itsm_ai_tool_audit',
    operation: 'write',
    roles: ['admin'],
})

Acl({
    $id: Now.ID['acl-audit-delete'],
    type: 'record',
    table: 'x_1320561_itsm_ai_tool_audit',
    operation: 'delete',
    roles: ['admin'],
})
