// Table API fields come back as plain strings or, with sysparm_display_value=all, as { value, display_value }.
// The agent REST layer returns references as { value, display }. These helpers read any of the three shapes.
export type Field = string | number | boolean | null | undefined | { value?: string; display_value?: string; display?: string }

export function value(field: Field): string {
    if (field === null || field === undefined) return ''
    if (typeof field === 'object') return field.value ?? ''
    return String(field)
}

export function display(field: Field): string {
    if (field === null || field === undefined) return ''
    if (typeof field === 'object') return field.display_value ?? field.display ?? field.value ?? ''
    return String(field)
}
