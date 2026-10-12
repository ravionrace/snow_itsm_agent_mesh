import { CertificateEncryption, gs, GlideSecureRandomUtil } from '@servicenow/glide'
import { RESTMessageV2 } from '@servicenow/glide/sn_ws'
import { AgentApi, fail, Json } from '../lib/agent-api.ts'
import { canonical } from '../lib/channel-signature.ts'

/**
 * /api/x_1320561_itsm_ai/v1/chat - the in-app Assistant's server side. The browser only ever talks to this API,
 * on the user's own session; this service relays to the agent mesh on AWS, server to server:
 *   - the user is gs.getUserName(), never something the browser sends
 *   - each call is HMAC-SHA256 signed with a secret only this instance and the mesh hold (+ timestamp, nonce)
 *   - calls are short: a turn is started and then polled, so no REST transaction waits on the LLM
 */

const ENDPOINT_PROPERTY = 'x_1320561_itsm_ai.mesh.endpoint'
const SECRET_PROPERTY = 'x_1320561_itsm_ai.mesh.channel_secret'
const CHANNEL = '/channel/servicenow/v1'
const ID = /^[A-Za-z0-9_-]{8,64}$/
const TIMEOUT_MS = 15000

export class ChatProxyService {
    constructor(private readonly api: AgentApi) {}

    /** POST /turns {message, session_id?} */
    startTurn(): Json {
        const b = this.api.body()
        const message = String(b.message ?? '').trim()
        if (!message) fail(400, 'message is required.')
        if (message.length > 4000) fail(400, 'The message is too long (4000 characters at most).')
        const payload: Json = { message }
        if (b.session_id) payload.session_id = this.id(b.session_id, 'session_id')
        return this.call('POST', '/turns', payload)
    }

    /** GET /turns/{id} */
    turn(): Json {
        return this.call('GET', `/turns/${this.id(this.api.path('id'), 'turn id')}`)
    }

    /** POST /sessions/{session}/confirmations/{pending} {decision} */
    decide(): Json {
        const decision = String(this.api.body().decision ?? '')
        if (decision !== 'confirm' && decision !== 'reject') fail(400, 'decision must be confirm or reject.')
        const session = this.id(this.api.path('session'), 'session')
        const pending = this.id(this.api.path('pending'), 'pending id')
        return this.call('POST', `/sessions/${session}/confirmations/${pending}`, { decision })
    }

    /** GET /sessions/{session} */
    session(): Json {
        return this.call('GET', `/sessions/${this.id(this.api.path('session'), 'session')}`)
    }

    private call(method: 'GET' | 'POST', path: string, payload?: Json): Json {
        const endpoint = String(gs.getProperty(ENDPOINT_PROPERTY, '')).replace(/\/+$/, '')
        const secret = String(gs.getProperty(SECRET_PROPERTY, ''))
        if (!endpoint.startsWith('https://') || !secret) {
            fail(503, 'The Assistant is not configured yet.', `Set ${ENDPOINT_PROPERTY} (https) and ${SECRET_PROPERTY}.`)
        }
        const body = payload ? JSON.stringify(payload) : ''
        const fullPath = CHANNEL + path
        const ts = String(Math.floor(new Date().getTime() / 1000))
        const nonce = GlideSecureRandomUtil.getSecureRandomString(32)
        const user = gs.getUserName()
        const signature = CertificateEncryption.generateMac(secret, 'HmacSHA256', canonical(ts, nonce, method, fullPath, user, body))

        const rm = new RESTMessageV2()
        rm.setEndpoint(endpoint + fullPath)
        rm.setHttpMethod(method.toLowerCase())
        rm.setHttpTimeout(TIMEOUT_MS)
        rm.setRequestHeader('Accept', 'application/json')
        rm.setRequestHeader('Content-Type', 'application/json')
        rm.setRequestHeader('X-Mesh-Timestamp', ts)
        rm.setRequestHeader('X-Mesh-Nonce', nonce)
        rm.setRequestHeader('X-Mesh-User', user)
        rm.setRequestHeader('X-Mesh-Signature', signature)
        if (body) rm.setRequestBody(body)

        const res = rm.execute()
        const status = res.getStatusCode()
        if (!status) fail(502, 'The Assistant service is not reachable right now.', String(res.getErrorMessage() ?? ''))
        let json: Json = {}
        try {
            json = res.getBody() ? JSON.parse(res.getBody()) : {}
        } catch (e) {
            fail(502, 'The Assistant service sent an unreadable response.')
        }
        if (status === 401) fail(502, 'The Assistant service rejected this instance.', 'Check that the channel secret matches the mesh.')
        if (status >= 400) fail(status, String(json.message ?? json.error ?? 'The Assistant could not handle this request.'))
        return json
    }

    private id(value: unknown, name: string): string {
        const v = String(value ?? '')
        if (!ID.test(v)) fail(400, `Invalid ${name}.`)
        return v
    }
}
