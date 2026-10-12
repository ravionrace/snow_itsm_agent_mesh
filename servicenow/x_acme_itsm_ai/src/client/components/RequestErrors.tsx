import React from 'react'
import { Alert } from '@servicenow/react-components/Alert'
import { requests } from '../store/kit'
import { useAppDispatch, useAppSelector } from '../store'

/** Shows (dismissible) errors for the given commands; one component instead of per-call error state. */
export default function RequestErrors({ commands }: { commands: { type: string }[] }) {
    const dispatch = useAppDispatch()
    const errors = useAppSelector((s) => commands.map((c) => [c.type, s.requests.errors[c.type]] as const).filter(([, e]) => e))
    return (
        <>
            {errors.map(([type, message]) => (
                <Alert key={type} status="critical" header="Request failed" content={message as string}
                       action={{ type: 'dismiss' }} onActionClicked={() => dispatch(requests.actions.dismiss(type))} />
            ))}
        </>
    )
}
