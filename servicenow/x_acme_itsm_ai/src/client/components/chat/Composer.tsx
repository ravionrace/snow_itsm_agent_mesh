import React, { useState } from 'react'
import { Textarea } from '@servicenow/react-components/Textarea'
import { Button } from '@servicenow/react-components/Button'
import { chat } from '../../store/features/chat'
import { useChatDispatch, useChatSelector } from '../../store/assistant-store'

export default function Composer() {
    const dispatch = useChatDispatch()
    const thinking = useChatSelector((s) => s.chat.thinking)
    const [text, setText] = useState('')
    const submit = () => {
        const message = text.trim()
        if (!message || thinking) return
        dispatch(chat.send(message))
        setText('')
    }
    return (
        <div className="composer" onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); submit() }
        }}>
            <Textarea label="Message" placeholder="Describe what you need (Enter to send, Shift+Enter for a new line)"
                      rows={2} maxlength={4000} value={text} onInput={(e) => setText(e.detail.payload.fieldValue)} />
            <Button label="Send" variant="primary" icon="arrow-up-outline" disabled={thinking || !text.trim()} onClicked={submit} />
        </div>
    )
}
