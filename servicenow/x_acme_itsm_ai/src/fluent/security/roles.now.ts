import '@servicenow/sdk/global'
import { Role } from '@servicenow/sdk/core'

// Per-API read/write roles and the agent_user bundle are generated in api-access.now.ts.

/** Reads the tool-call audit and API access, and opens the operations console. */
export const auditor = Role({
    name: 'x_1320561_itsm_ai.auditor',
    description: 'Can read the agent tool-call audit and API access, and use the ITSM AI operations console.',
    grantable: true,
})

/**
 * Controls who can use each API: grants read/write on APIs to groups, creates groups and manages their
 * membership from the console. Changes are limited to this app's API roles and to groups that hold them.
 */
export const accessAdmin = Role({
    name: 'x_1320561_itsm_ai.access_admin',
    description: 'Can grant API read/write access to groups and manage those groups from the ITSM AI console.',
    containsRoles: [auditor],
    grantable: true,
})

/**
 * May use the in-app Assistant. What the agents can then do for the user is still decided per API by the
 * <api>_read / <api>_write roles (agent_user contains this role).
 */
export const chatUser = Role({
    name: 'x_1320561_itsm_ai.chat_user',
    description: 'Can chat with the ITSM AI Assistant inside ServiceNow.',
    grantable: true,
})
