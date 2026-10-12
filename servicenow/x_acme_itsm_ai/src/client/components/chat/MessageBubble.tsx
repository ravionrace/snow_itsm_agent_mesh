import React from 'react'
import type { Message } from '../../store/features/chat'
import Markdown from './Markdown'
import StepList, { AGENT_LABEL } from './StepList'

export default function MessageBubble({ message }: { message: Message }) {
    const assistant = message.role === 'assistant'
    const label = assistant ? AGENT_LABEL[message.agent ?? ''] ?? 'Assistant' : null
    return (
        <div className={`bubble bubble-${message.role}`}>
            {label && <div className="bubble-meta">{label}</div>}
            {message.steps?.length ? <StepList steps={message.steps} /> : null}
            {assistant ? <Markdown text={message.text} /> : <div className="bubble-text">{message.text}</div>}
        </div>
    )
}
