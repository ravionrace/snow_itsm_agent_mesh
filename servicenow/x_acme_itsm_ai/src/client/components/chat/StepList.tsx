import React from 'react'
import type { Step } from '../../store/features/chat'

export const AGENT_LABEL: Record<string, string> = {
    incident: 'Incident agent', problem: 'Problem agent', change: 'Change agent', catalog: 'Catalog agent',
    access: 'Access check', coordinator: 'Assistant',
}
const agent = (name: string) => AGENT_LABEL[name] ?? name
const PHASE: Record<string, string> = { call: '', preview: 'preview', commit: 'saved', blocked: 'blocked' }

function line(step: Step): { icon: string; text: React.ReactNode; tone?: 'ok' | 'warn' } {
    switch (step.kind) {
        case 'route': return { icon: '➜', text: <>Routed to the <b>{agent(step.agent)}</b></> }
        case 'access': return {
            icon: '🔒', tone: step.read ? 'ok' : 'warn',
            text: <>Access check · {step.api}: read {step.read ? '✓' : '✗'} · write {step.write ? '✓' : '✗'}</>,
        }
        case 'thinking': return { icon: '💭', text: <span className="step-thinking">{step.text}</span> }
        case 'tool': {
            const ok = step.status === 'OK' || step.status === 'PENDING_CONFIRMATION'
            const phase = PHASE[step.phase] ?? step.phase
            return {
                icon: '🔧', tone: ok ? 'ok' : 'warn',
                text: <><code>{step.tool}</code>{phase && ` (${phase})`} · {ok ? '✓' : step.status} · {step.ms} ms</>,
            }
        }
        case 'handoff': return { icon: '⇄', text: <>Handed off from the {agent(step.from)} to the <b>{agent(step.to)}</b></> }
        case 'check': return { icon: '↺', tone: 'warn', text: <>Fact check: rewriting with data from ServiceNow</> }
    }
}

/** How the agents worked on a turn: routing, the access check, their thinking and each tool call. */
export default function StepList({ steps, live = false }: { steps: Step[]; live?: boolean }) {
    if (!steps.length) return null
    const items = (
        <ol className="steps">
            {steps.map((s, i) => {
                const l = line(s)
                return <li key={i} className={`step step-${s.kind}${l.tone ? ` step-${l.tone}` : ''}`}>
                    <span className="step-icon" aria-hidden>{l.icon}</span><span>{l.text}</span>
                </li>
            })}
        </ol>
    )
    if (live) return items
    const tools = steps.filter((s) => s.kind === 'tool').length
    return (
        <details className="steps-details">
            <summary>How I worked on this · {tools} tool call{tools === 1 ? '' : 's'}</summary>
            {items}
        </details>
    )
}
