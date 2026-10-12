import '@servicenow/sdk/global'
import { UiPage } from '@servicenow/sdk/core'
import consolePage from '../../client/index.html'

/** React operations console: /x_1320561_itsm_ai_agent_console.do (auditors only, see acls.now.ts). */
export const agentConsolePage = UiPage({
    $id: Now.ID['agent-console-page'],
    endpoint: 'x_1320561_itsm_ai_agent_console.do',
    description: 'ITSM AI operations console: tool-call statistics, audit trail and an API explorer for the agent REST layer.',
    category: 'general',
    html: consolePage,
    direct: true,
})
