import '@servicenow/sdk/global'
import { Record } from '@servicenow/sdk/core'

/**
 * Authentication scope for the agent mesh's OAuth client. The client's registry (created per instance by an
 * admin, see docs/servicenow-oauth.md) is "Securely scoped" and mapped to this scope only, so the per-user
 * tokens it obtains can call this app's REST APIs and nothing else (no Table API, no other apps).
 *
 * Only the scope definition lives in the app: it holds no secrets and is identical on every instance.
 * The OAuth registry, its secret and the signing certificate are per-instance credentials kept out of source.
 */
export const meshAuthScope = Record({
    $id: Now.ID['auth-scope-mesh-api'],
    table: 'sys_auth_scope',
    data: {
        name: 'x_1320561_itsm_ai.mesh_api',
        description: 'ITSM AI agent mesh: may call only the x_1320561_itsm_ai REST APIs, on behalf of the signed-in user.',
    },
})
