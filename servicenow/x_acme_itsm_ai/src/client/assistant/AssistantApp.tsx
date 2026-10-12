import React, { useEffect } from 'react'
import { Heading } from '@servicenow/react-components/Heading'
import { Button } from '@servicenow/react-components/Button'
import ChatWindow from '../components/chat/ChatWindow'
import Composer from '../components/chat/Composer'
import { chat } from '../store/features/chat'
import { useChatDispatch, useChatSelector } from '../store/assistant-store'
import './assistant.css'

/**
 * The in-app Assistant. The conversation is addressable as ?session=<id>, so a reload (or a shared link to
 * yourself) resumes it; the session belongs to the signed-in user and nobody else can open it.
 */
export default function AssistantApp() {
    const dispatch = useChatDispatch()
    const sessionId = useChatSelector((s) => s.chat.sessionId)

    useEffect(() => {
        const fromUrl = new URLSearchParams(window.location.search).get('session')
        if (fromUrl) dispatch(chat.resume(fromUrl))
        document.title = 'ITSM AI Assistant'
    }, [dispatch])

    useEffect(() => {
        const params = new URLSearchParams(window.location.search)
        if (sessionId) params.set('session', sessionId)
        else params.delete('session')
        const qs = params.toString()
        window.history.replaceState(null, '', `${window.location.pathname}${qs ? `?${qs}` : ''}`)
    }, [sessionId])

    return (
        <div className="assistant">
            <header className="assistant-header">
                <div>
                    <Heading label="ITSM AI Assistant" level={1} variant="header-secondary" hasNoMargin />
                    <span className="caption">Incidents, problems, changes and requests - acting with your own access.</span>
                </div>
                <Button label="New conversation" icon="plus-outline" variant="tertiary" onClicked={() => dispatch(chat.reset())} />
            </header>
            <ChatWindow />
            <Composer />
        </div>
    )
}
