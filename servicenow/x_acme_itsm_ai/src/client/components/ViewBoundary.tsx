import React from 'react'
import { Alert } from '@servicenow/react-components/Alert'

interface Props {
    title: string
    fallbackHref: string
    fallbackLabel: string
    children: React.ReactNode
}

interface State {
    error: string | null
}

/**
 * Isolates a view whose platform components fail to load on this instance (for example a record engine
 * version mismatch), so the rest of the console keeps working and the user gets a classic-UI link instead.
 */
export default class ViewBoundary extends React.Component<Props, State> {
    state: State = { error: null }

    static getDerivedStateFromError(error: unknown): State {
        return { error: error instanceof Error ? error.message : String(error) }
    }

    componentDidCatch(error: unknown) {
        console.error(`[x_1320561_itsm_ai] ${this.props.title} failed to load`, error)
    }

    render() {
        if (this.state.error) {
            return (
                <Alert status="warning" header={`${this.props.title} could not load on this instance`}
                       content={this.state.error}
                       textLinkProps={{ label: this.props.fallbackLabel, href: this.props.fallbackHref, opensWindow: true }} />
            )
        }
        return this.props.children
    }
}
