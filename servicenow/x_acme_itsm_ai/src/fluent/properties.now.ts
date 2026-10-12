import '@servicenow/sdk/global'
import { Property } from '@servicenow/sdk/core'

Property({
    $id: Now.ID['prop-cause-ci-field'],
    name: 'x_1320561_itsm_ai.problem.cause_ci_field',
    type: 'string',
    value: 'cmdb_ci',
    description: 'Problem field that stores the cause CI written by record_root_cause.',
})

Property({
    $id: Now.ID['prop-known-error-kb'],
    name: 'x_1320561_itsm_ai.known_error.kb',
    type: 'string',
    value: '',
    description: 'sys_id of the knowledge base that receives draft known-error articles.',
})

// The agent mesh (AWS) the in-app Assistant talks to. Set per instance by an admin; see docs/servicenow-assistant.md.
Property({
    $id: Now.ID['prop-mesh-endpoint'],
    name: 'x_1320561_itsm_ai.mesh.endpoint',
    type: 'string',
    value: '',
    description: 'HTTPS base URL of the agent mesh, e.g. https://mesh.example.com (no trailing slash).',
    roles: { read: ['admin'], write: ['admin'] },
})

Property({
    $id: Now.ID['prop-mesh-channel-secret'],
    name: 'x_1320561_itsm_ai.mesh.channel_secret',
    type: 'password2',
    value: '',
    description: 'Base64 shared secret that signs Assistant calls to the agent mesh (the mesh holds the same value). Encrypted.',
    roles: { read: ['admin'], write: ['admin'] },
    isPrivate: true,
})
