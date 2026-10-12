/**
 * Canonical string signed with HMAC-SHA256 for every call to the agent mesh's ServiceNow channel. Must match the
 * orchestrator's ChannelSignature.canonical() exactly (shared test vector in tests/client/channel-signature.test.ts).
 */
export function canonical(ts: string, nonce: string, method: string, path: string, user: string, body: string): string {
    return `${ts}\n${nonce}\n${method}\n${path}\n${user}\n${body}`
}
