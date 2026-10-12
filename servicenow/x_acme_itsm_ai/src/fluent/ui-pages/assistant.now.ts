import '@servicenow/sdk/global'
import { UiPage, Acl } from '@servicenow/sdk/core'
import assistantPage from '../../client/assistant.html'
import { chatUser } from '../security/roles.now'

/** The in-app Assistant: /x_1320561_itsm_ai_assistant.do (users with x_1320561_itsm_ai.chat_user). */
export const assistantUiPage = UiPage({
    $id: Now.ID['assistant-page'],
    endpoint: 'x_1320561_itsm_ai_assistant.do',
    description: 'ITSM AI Assistant: chat with the agent mesh about incidents, problems, changes and requests.',
    category: 'general',
    html: assistantPage,
    direct: true,
})

Acl({
    $id: Now.ID['acl-assistant-page'],
    type: 'ui_page',
    name: 'x_1320561_itsm_ai_assistant',
    operation: 'read',
    roles: [chatUser],
    description: 'Only Assistant users can open the Assistant page.',
})
