import { createHmac } from 'node:crypto'
import { describe, expect, it } from 'vitest'
import { canonical } from '../../src/server/lib/channel-signature'

// Same vector as orchestrator ChannelSignatureTest: ServiceNow (CertificateEncryption.generateMac over this canonical
// string) and the mesh (javax.crypto HmacSHA256) must produce the same signature.
const SECRET = 'AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8='
const VECTOR = 'fuI3gBx1HGUQrg2Wt3yjQvfBZaxxFVLWwgwag303AI8='

describe('ServiceNow channel signature', () => {
    it('builds the canonical string the mesh verifies', () => {
        const c = canonical('1791300000', 'nonce-0123456789abcdef', 'POST', '/channel/servicenow/v1/turns', 'abel.tuter',
            '{"message":"VPN is down"}')
        expect(createHmac('sha256', Buffer.from(SECRET, 'base64')).update(c, 'utf8').digest('base64')).toBe(VECTOR)
    })
})
