import React, { useState } from 'react'
import { Select } from '@servicenow/react-components/Select'
import { Input } from '@servicenow/react-components/Input'
import { Button } from '@servicenow/react-components/Button'
import { Alert } from '@servicenow/react-components/Alert'
import { RESOURCES } from '../services/explorerResources'
import { explorer } from '../store/features/explorer'
import { selectPending } from '../store/kit'
import { useAppDispatch, useAppSelector } from '../store'

interface Props {
    resourceId: string
    onResourceChange: (id: string) => void
}

/**
 * Calls the agent REST layer as the signed-in user, to check what an agent acting for that user would get:
 * the same ACLs, GlideRecordSecure filtering and business rules apply. Every call is audited.
 */
export default function ApiExplorer({ resourceId, onResourceChange }: Props) {
    const resource = RESOURCES.find((r) => r.id === resourceId) ?? RESOURCES[0]
    const [id, setId] = useState('')
    const [params, setParams] = useState<Record<string, string>>({})
    const dispatch = useAppDispatch()
    const { url, response } = useAppSelector((s) => s.explorer)
    const busy = useAppSelector((s) => selectPending(s, explorer.call))
    const needsId = resource.path.includes('{id}')

    const send = () => {
        const path = `/${resource.api}${resource.path === '/' ? '' : resource.path.replace('{id}', encodeURIComponent(id.trim()))}`
        dispatch(explorer.call({ path, query: Object.fromEntries(resource.params.map((p) => [p, params[p] ?? ''])) }))
    }

    return (
        <div className="view">
            <div className="explorer-form">
                <Select label="Resource" search="contains" selectedItem={resource.id}
                        items={RESOURCES.map((r) => ({ id: r.id, label: r.label }))}
                        onSelectedItemSet={(e) => onResourceChange(String(e.detail.payload.value))} />
                {needsId && (
                    <Input label="Record number or sys_id" required value={id}
                           onInput={(e) => setId(e.detail.payload.fieldValue)} />
                )}
                {resource.params.map((p) => (
                    <Input key={p} label={p} optional value={params[p] ?? ''}
                           onInput={(e) => setParams((prev) => ({ ...prev, [p]: e.detail.payload.fieldValue }))} />
                ))}
                <div>
                    <Button label={busy ? 'Calling...' : 'Call as me'} variant="primary" icon="play-outline"
                            disabled={busy || (needsId && !id.trim())} onClicked={send} />
                </div>
            </div>
            <p className="caption">
                GET /api/x_1320561_itsm_ai/v1/{resource.api}{resource.path === '/' ? '' : resource.path}
            </p>
            {response && !response.ok && (
                <Alert status="critical" header={`HTTP ${response.status}`}
                       content={(response.body as { error?: { message?: string } } | null)?.error?.message ?? 'Request failed'} />
            )}
            {response && (
                <>
                    <p className="caption">GET {url} - HTTP {response.status} in {response.elapsedMs} ms</p>
                    <pre className="json" aria-label="Response body">{JSON.stringify(response.body, null, 2)}</pre>
                </>
            )}
        </div>
    )
}
