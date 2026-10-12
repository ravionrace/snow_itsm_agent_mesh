import { marked } from 'marked'
import DOMPurify from 'dompurify'

/**
 * Renders the Assistant's Markdown reply as HTML. Replies can quote ServiceNow record text that anyone may have
 * written, so the HTML is sanitized: no scripts, styles, forms or images (an image URL could carry data out), and
 * links only to http(s), mailto or this instance, opening in a new tab.
 */
const SAFE_URL = /^(?:https?:|mailto:|\/(?!\/)|#)/i

DOMPurify.addHook('afterSanitizeAttributes', (node) => {
    if (node.tagName === 'A') {
        const href = node.getAttribute('href') ?? ''
        if (!SAFE_URL.test(href)) node.removeAttribute('href')
        node.setAttribute('target', '_blank')
        node.setAttribute('rel', 'noopener noreferrer')
    }
})

const PURIFY = {
    ALLOWED_TAGS: ['p', 'br', 'hr', 'strong', 'em', 'del', 'code', 'pre', 'blockquote', 'ul', 'ol', 'li', 'a',
        'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'table', 'thead', 'tbody', 'tr', 'th', 'td'],
    ALLOWED_ATTR: ['href', 'title', 'align', 'start'],
}

export function toSafeHtml(markdown: string): string {
    const html = marked.parse(markdown, { gfm: true, breaks: true, async: false }) as string
    return DOMPurify.sanitize(html, PURIFY)
}
