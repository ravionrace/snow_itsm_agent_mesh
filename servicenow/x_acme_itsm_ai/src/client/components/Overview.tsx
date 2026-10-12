import React, { useEffect } from 'react'
import { Card } from '@servicenow/react-components/Card'
import { CardHeader } from '@servicenow/react-components/CardHeader'
import { LabelValueStacked } from '@servicenow/react-components/LabelValueStacked'
import { Select } from '@servicenow/react-components/Select'
import { Loader } from '@servicenow/react-components/Loader'
import StatCard from './StatCard'
import RequestErrors from './RequestErrors'
import { overview } from '../store/features/overview'
import { useAppDispatch, useAppSelector } from '../store'

const WINDOWS = [
    { id: '24', label: 'Last 24 hours' },
    { id: '168', label: 'Last 7 days' },
    { id: '720', label: 'Last 30 days' },
]

interface Props {
    hours: string
    onHoursChange: (hours: string) => void
}

/** Aggregates from GET /console/summary, refreshed every minute while on screen (overview.watch epic). */
export default function Overview({ hours, onHoursChange }: Props) {
    const dispatch = useAppDispatch()
    const summary = useAppSelector((s) => s.overview.summary)

    useEffect(() => {
        dispatch(overview.watch(Number(hours)))
        return () => { dispatch(overview.unwatch()) }
    }, [dispatch, hours])

    return (
        <div className="view">
            <div className="toolbar">
                <Select label="Window" items={WINDOWS} selectedItem={hours}
                        onSelectedItemSet={(e) => onHoursChange(String(e.detail.payload.value))} />
            </div>
            <RequestErrors commands={[overview.load]} />
            {!summary && <div className="loading"><Loader label="Loading statistics" /></div>}
            {summary && (
                <>
                    <div className="stat-grid">
                        <StatCard label="Tool calls" value={summary.calls} />
                        <StatCard label="Errors" value={summary.errors} tone={summary.errors ? 'critical' : 'positive'}
                                  note={`${summary.error_rate}% of calls`} />
                        <StatCard label="Writes committed" value={summary.writes_committed} />
                        <StatCard label="Idempotent replays" value={summary.idempotent_replays}
                                  note="Retries answered without writing again" />
                        <StatCard label="Average latency" value={summary.avg_duration_ms} unit="ms" />
                    </div>
                    <div className="split">
                        <Card size="md">
                            <CardHeader heading={{ label: 'Calls by API', level: 3, size: 'md' }} />
                            <LabelValueStacked align="horizontal-equal"
                                items={summary.by_api.map((r) => ({ label: r.key || '(none)', value: String(r.count) }))} />
                        </Card>
                        <Card size="md">
                            <CardHeader heading={{ label: 'Most used resources', level: 3, size: 'md' }} />
                            <LabelValueStacked align="horizontal-equal"
                                items={summary.top_resources.map((r) => ({ label: r.key || '(none)', value: String(r.count) }))} />
                        </Card>
                    </div>
                    <p className="caption">Since {summary.since}. All times UTC.</p>
                </>
            )}
        </div>
    )
}
