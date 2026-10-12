import { gs, GlideRecordSecure } from '@servicenow/glide'
import { global } from '@servicenow/glide/global'
import { ChangeRequest } from '@servicenow/glide/sn_chg_rest'
import { AgentApi, fail } from '../lib/agent-api.ts'
import { STATE } from './change-states.ts'

/**
 * Adapters over release-dependent change APIs. Each needs a Cross Scope Privilege (see
 * src/fluent/security/cross-scope.now.ts) and falls back to GlideRecordSecure, so the change model's business
 * rules still decide what is allowed. Check these against your instance's release before go-live.
 */
export const ChangeApis = {
    /** sn_chg_rest.ChangeRequest.newNormal() applies the normal change model's defaults. */
    newNormal(values: Record<string, string>): any {
        try {
            const chg = ChangeRequest.newNormal()
            chg.setValues(values)
            return chg.insert() ? chg.getGlideRecord() : null
        } catch (e) {
            gs.info(`[x_1320561_itsm_ai] ChangeRequest API unavailable, using GlideRecordSecure: ${e}`)
            const gr: any = new GlideRecordSecure('change_request')
            gr.initialize()
            gr.setValue('type', 'normal')
            for (const k of Object.keys(values)) gr.setValue(k, values[k])
            return gr.insert() ? gr : null
        }
    },

    /** sn_chg_rest.ChangeRequest.newStandard(templateId) copies the template's pre-approved values. */
    newStandard(templateId: string, values: Record<string, string>): any {
        let chg
        try {
            chg = ChangeRequest.newStandard(templateId)
        } catch (e) {
            fail(501, 'Standard changes need the Change Management REST plugin (sn_chg_rest).', String(e))
        }
        if (!chg) fail(404, 'Standard change template not found or not available to you.')
        // Template fields stay as published; only planning fields may be supplied.
        const filtered: Record<string, string> = {}
        for (const k of ['short_description', 'description', 'cmdb_ci', 'start_date', 'end_date', 'requested_by']) {
            if (values[k]) filtered[k] = values[k]
        }
        chg.setValues(filtered)
        return chg.insert() ? chg.getGlideRecord() : null
    },

    /** Moves the change through the state model; the handler validates the transition, then we update. */
    moveTo(gr: any, stateName: string, api: AgentApi): void {
        let handler: { isNext?(s: string): boolean; moveTo?(s: string): boolean } | null = null
        try {
            handler = new global.ChangeRequestStateHandler(gr)
        } catch (e) {
            handler = null
        }
        if (handler?.isNext && !handler.isNext(stateName)) fail(409, `The change model does not allow moving to ${stateName} now.`)
        if (handler?.moveTo) handler.moveTo(stateName)
        else gr.setValue('state', STATE[stateName])
        api.secureUpdate(gr)
    },

    /** Risk calculator (com.snc.change.risk). */
    calculateRisk(gr: any): Record<string, unknown> | null {
        try {
            const r = new global.RiskCalculator(gr).calculateRisk()
            if (!r) return null
            return { risk: r.risk === undefined ? null : String(r.risk), impact: r.impact === undefined ? null : String(r.impact),
                     conditions: r.conditions ?? null }
        } catch (e) {
            return { unavailable: true, note: 'Risk calculator is not available on this instance.' }
        }
    },

    /** Conflict detection writes conflict records for the change. */
    runConflictCheck(gr: any): void {
        try {
            new global.ChangeCheckConflicts(gr).check()
        } catch (e) {
            gs.info(`[x_1320561_itsm_ai] conflict check unavailable: ${e}`)
        }
    },
}
