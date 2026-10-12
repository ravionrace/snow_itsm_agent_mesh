import React from 'react'
import { Card } from '@servicenow/react-components/Card'
import { Heading } from '@servicenow/react-components/Heading'

interface Props {
    label: string
    value: number
    unit?: string
    note?: string
    tone?: 'critical' | 'positive' | 'info'
}

export default function StatCard({ label, value, unit, note, tone }: Props) {
    return (
        <Card size="md" sidebar={tone ? { color: tone, variant: 'primary' } : undefined}>
            <div className="stat">
                <span className="stat-label">{label}</span>
                <Heading label={`${value.toLocaleString()}${unit ? ` ${unit}` : ''}`} level={2}
                         variant="header-primary" hasNoMargin />
                {note && <span className="stat-note">{note}</span>}
            </div>
        </Card>
    )
}
