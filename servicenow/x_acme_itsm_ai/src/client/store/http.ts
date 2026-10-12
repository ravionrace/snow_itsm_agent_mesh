import { Observable, catchError, map, throwError } from 'rxjs'
import { ajax, AjaxError } from 'rxjs/ajax'

declare global {
    interface Window {
        g_ck: string
    }
}

/** The app's REST layer. Paths starting with /api/ (e.g. the Table API) are used as-is. */
export const APP_API = '/api/x_1320561_itsm_ai/v1'

export interface Request {
    method: 'GET' | 'POST' | 'PATCH'
    url: string
    query?: Record<string, string | number | boolean | undefined>
    body?: unknown
}

export const get = (url: string, query?: Request['query']): Request => ({ method: 'GET', url, query })
export const post = (url: string, body: unknown = {}): Request => ({ method: 'POST', url, body })

export function toUrl({ url, query }: Request): string {
    const qs = new URLSearchParams(
        Object.entries(query ?? {}).filter(([, v]) => v !== undefined && v !== '').map(([k, v]) => [k, String(v)]),
    ).toString()
    return `${url.startsWith('/api/') ? '' : APP_API}${url}${qs ? `?${qs}` : ''}`
}

const headers = () => ({ Accept: 'application/json', 'Content-Type': 'application/json', 'X-UserToken': window.g_ck })

/** Raw call: status, body and timing, success or not (used by the API explorer). */
export interface RawResponse { status: number; ok: boolean; body: unknown; elapsedMs: number }

export function sendRaw(req: Request): Observable<RawResponse> {
    const started = performance.now()
    const done = (status: number, body: unknown): RawResponse =>
        ({ status, ok: status >= 200 && status < 300, body, elapsedMs: Math.round(performance.now() - started) })
    return ajax({ url: toUrl(req), method: req.method, headers: headers(), body: req.body }).pipe(
        map((res) => done(res.status, res.response)),
        catchError((e: AjaxError) => [done(e.status ?? 0, e.response ?? String(e.message))]),
    )
}

/** Normal call: unwraps ServiceNow's {result}, and turns {error:{message, detail}} into an Error. */
export function send<T>(req: Request): Observable<T> {
    return ajax<{ result: T }>({ url: toUrl(req), method: req.method, headers: headers(), body: req.body }).pipe(
        map((res) => res.response.result),
        catchError((e: AjaxError) => {
            const err = (e.response as { error?: { message?: string; detail?: string } } | null)?.error
            const text = err?.message ? `${err.message}${err.detail ? `: ${err.detail}` : ''}` : `HTTP ${e.status || 'error'}`
            return throwError(() => new Error(text))
        }),
    )
}
