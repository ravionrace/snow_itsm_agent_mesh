import '@servicenow/sdk/global'
import { ApplicationMenu, Record } from '@servicenow/sdk/core'
import { auditor, chatUser } from './security/roles.now'

const menu = ApplicationMenu({
    $id: Now.ID['menu-itsm-ai'],
    title: 'ITSM AI Agent',
    hint: 'Agent mesh operations',
    description: 'Operations console and audit trail for the ITSM AI agent mesh.',
    roles: [auditor, chatUser],
    active: true,
})

Record({
    $id: Now.ID['module-console'],
    table: 'sys_app_module',
    data: {
        title: 'Operations Console',
        application: menu,
        link_type: 'DIRECT',
        query: 'x_1320561_itsm_ai_agent_console.do',
        hint: 'Statistics, audit trail and API explorer',
        roles: ['x_1320561_itsm_ai.auditor'],
        active: true,
        order: 100,
    },
})

Record({
    $id: Now.ID['module-audit'],
    table: 'sys_app_module',
    data: {
        title: 'Tool Call Audit',
        application: menu,
        link_type: 'LIST',
        name: 'x_1320561_itsm_ai_tool_audit',
        hint: 'Every call to the agent REST APIs',
        roles: ['x_1320561_itsm_ai.auditor'],
        active: true,
        order: 200,
    },
})

Record({
    $id: Now.ID['module-access'],
    table: 'sys_app_module',
    data: {
        title: 'API Access',
        application: menu,
        link_type: 'DIRECT',
        query: 'x_1320561_itsm_ai_agent_console.do?view=access',
        hint: 'Which groups can read and write each API',
        roles: ['x_1320561_itsm_ai.auditor'],
        active: true,
        order: 150,
    },
})

Record({
    $id: Now.ID['module-assistant'],
    table: 'sys_app_module',
    data: {
        title: 'Assistant',
        application: menu,
        link_type: 'DIRECT',
        query: 'x_1320561_itsm_ai_assistant.do',
        hint: 'Chat with the ITSM AI Assistant',
        roles: ['x_1320561_itsm_ai.chat_user'],
        active: true,
        order: 50,
    },
})
