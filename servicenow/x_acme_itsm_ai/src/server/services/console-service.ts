import { GlideAggregate, GlideDateTime } from '@servicenow/glide'
import { AgentApi, AUDIT_TABLE, Json } from '../lib/agent-api.ts'

/**
 * /api/x_1320561_itsm_ai/v1/console - aggregates over the tool-call audit table for the operations console.
 * Restricted to x_1320561_itsm_ai.auditor by the route ACL.
 */
export class ConsoleService {
    constructor(private readonly api: AgentApi) {}

    /** GET /summary?hours= */
    summary(): Json {
        const hours = this.api.queryInt('hours', 24, 1, 24 * 30)
        const since = new GlideDateTime()
        since.addSeconds(-hours * 3600)
        const sinceValue = since.getValue()

        const totals = this.count(sinceValue, null)
        const byApi = this.groupCount(sinceValue, 'api')
        const byOutcome = this.groupCount(sinceValue, 'outcome')
        const byResource = this.groupCount(sinceValue, 'resource', 10)
        const writes = this.count(sinceValue, 'http_method!=GET^outcome=success')
        const replays = this.count(sinceValue, 'outcome=replay')
        const errors = byOutcome.find((r) => r.key === 'error')?.count ?? 0

        return {
            window_hours: hours,
            since: `${sinceValue} UTC`,
            calls: totals,
            errors,
            error_rate: totals ? Math.round((Number(errors) / totals) * 1000) / 10 : 0,
            writes_committed: writes,
            idempotent_replays: replays,
            by_api: byApi,
            top_resources: byResource,
            avg_duration_ms: this.average(sinceValue, 'duration_ms'),
        }
    }

    private count(since: string, extra: string | null): number {
        const ga: any = new GlideAggregate(AUDIT_TABLE)
        ga.addQuery('sys_created_on', '>=', since)
        if (extra) ga.addEncodedQuery(extra)
        ga.addAggregate('COUNT')
        ga.query()
        return ga.next() ? parseInt(ga.getAggregate('COUNT'), 10) : 0
    }

    private groupCount(since: string, field: string, limit = 50): { key: string; count: number }[] {
        const ga: any = new GlideAggregate(AUDIT_TABLE)
        ga.addQuery('sys_created_on', '>=', since)
        ga.addAggregate('COUNT', field)
        ga.groupBy(field)
        ga.orderByAggregate('COUNT', field)
        ga.query()
        const out: { key: string; count: number }[] = []
        while (ga.next() && out.length < limit) {
            out.push({ key: String(ga.getValue(field) ?? ''), count: parseInt(ga.getAggregate('COUNT', field), 10) })
        }
        return out.sort((a, b) => b.count - a.count)
    }

    private average(since: string, field: string): number {
        const ga: any = new GlideAggregate(AUDIT_TABLE)
        ga.addQuery('sys_created_on', '>=', since)
        ga.addAggregate('AVG', field)
        ga.query()
        return ga.next() ? Math.round(parseFloat(ga.getAggregate('AVG', field) || '0')) : 0
    }
}
