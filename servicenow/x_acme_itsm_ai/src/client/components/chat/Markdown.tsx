import React, { useMemo } from 'react'
import { toSafeHtml } from './safe-markdown'

/** The Assistant's reply, Markdown rendered as sanitized HTML (see safe-markdown.ts). */
export default function Markdown({ text }: { text: string }) {
    const html = useMemo(() => toSafeHtml(text), [text])
    return <div className="markdown" dangerouslySetInnerHTML={{ __html: html }} />
}
