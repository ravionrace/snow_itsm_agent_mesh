import React from 'react'
import { createRoot } from 'react-dom/client'
import { Provider } from 'react-redux'
import AssistantApp from './AssistantApp'
import { assistantStore } from '../store/assistant-store'

const rootElement = document.getElementById('root')
if (!rootElement) {
    throw new Error('Root element not found')
}

createRoot(rootElement).render(
    <React.StrictMode>
        <Provider store={assistantStore}>
            <AssistantApp />
        </Provider>
    </React.StrictMode>,
)
