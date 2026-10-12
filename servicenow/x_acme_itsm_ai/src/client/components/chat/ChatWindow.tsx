import React, { useEffect, useRef } from 'react'
import { Loader } from '@servicenow/react-components/Loader'
import MessageBubble from './MessageBubble'
import PendingCard from './PendingCard'
import StepList from './StepList'
import { useChatSelector } from '../../store/assistant-store'

const SUGGESTIONS = ['Show my open incidents', 'My laptop cannot connect to VPN', 'What is the status of my requests?']

export default function ChatWindow() {
    const { messages, pending, thinking, live } = useChatSelector((s) => s.chat)
    const end = useRef<HTMLDivElement>(null)
    useEffect(() => { end.current?.scrollIntoView({ behavior: 'smooth', block: 'end' }) }, [messages.length, pending, thinking, live.length])

    return (
        <div className="chat-window" role="log" aria-live="polite">
            {!messages.length && !thinking && (
                <div className="chat-empty">
                    <p>Ask about incidents, problems, changes or catalog requests. For example:</p>
                    <ul>{SUGGESTIONS.map((s) => <li key={s}>"{s}"</li>)}</ul>
                </div>
            )}
            {messages.map((m, i) => <MessageBubble key={i} message={m} />)}
            {pending && !thinking && <PendingCard pending={pending} />}
            {thinking && (
                <div className="chat-thinking">
                    <Loader size="md" label="The Assistant is working on it" />
                    <StepList steps={live} live />
                </div>
            )}
            <div ref={end} />
        </div>
    )
}
