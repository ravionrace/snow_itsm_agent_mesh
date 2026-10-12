// Declarations for platform script includes that are not in @servicenow/glide's bundled typings.
// Running `now-sdk dependencies` against an instance can generate richer types; these cover only the
// members this app calls. Each one is optional at runtime and wrapped in try/catch by the caller.

declare module '@servicenow/glide/global' {
    export const global: {
        ChangeRequestStateHandler: new (gr: any) => {
            isNext?(state: string): boolean
            moveTo?(state: string): boolean
        }
        RiskCalculator: new (gr: any) => {
            calculateRisk(): { risk?: string | number; impact?: string | number; conditions?: unknown } | null
        }
        ChangeCheckConflicts: new (gr: any) => { check(): unknown }
    }
}

declare module '@servicenow/glide/sn_chg_rest' {
    interface ChangeRequestInstance {
        setValues(values: Record<string, string>): void
        insert(): string | null
        getGlideRecord(): any
    }
    export const ChangeRequest: {
        newNormal(): ChangeRequestInstance
        newStandard(templateSysId: string): ChangeRequestInstance | null
    }
}
