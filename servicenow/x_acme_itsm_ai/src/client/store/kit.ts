import { createAction, createSlice, type Action, type PayloadAction, type PayloadActionCreator } from '@reduxjs/toolkit'
import type { Epic } from 'redux-observable'
import { catchError, concat, concatMap, exhaustMap, filter, mergeMap, of, switchMap, type Observable, type OperatorFunction } from 'rxjs'
import { send, type Request } from './http'

/**
 * Three helpers keep features free of boilerplate:
 *   commands()  declares a feature's trigger actions in one object literal
 *   endpoint()  "when <trigger> fires, call <request>, then dispatch <done>" - with pending/error tracking
 *   react()     "when <trigger> fires, also dispatch these actions"
 * Anything more involved (debounce, polling, raw responses) is written as an ordinary epic.
 */

// ---------------------------------------------------------------------------------------------- commands

export function commands<M extends Record<string, unknown>>(domain: string, shape: M) {
    return Object.fromEntries(Object.keys(shape).map((k) => [k, createAction(`${domain}/${k}`)])) as unknown as
        { [K in keyof M]: PayloadActionCreator<M[K]> }
}

/** Payload type markers for commands(): `{ load: none, grant: payload<Grant>() }` */
export const payload = <T>() => undefined as unknown as T
export const none = undefined as void

// ---------------------------------------------------------------------------------------------- request state

interface RequestsState {
    pending: Record<string, boolean>
    errors: Record<string, string | undefined>
}

export const requests = createSlice({
    name: 'requests',
    initialState: { pending: {}, errors: {} } as RequestsState,
    reducers: {
        started: (s, { payload: type }: PayloadAction<string>) => { s.pending[type] = true; delete s.errors[type] },
        settled: (s, { payload: type }: PayloadAction<string>) => { s.pending[type] = false },
        failed: (s, { payload }: PayloadAction<{ type: string; message: string }>) => {
            s.pending[payload.type] = false
            s.errors[payload.type] = payload.message
        },
        dismiss: (s, { payload: type }: PayloadAction<string>) => { delete s.errors[type] },
    },
})

/** Pending flag / error message for a command, e.g. selectPending(state, access.grant). */
export const selectPending = (s: { requests: RequestsState }, cmd: { type: string }) => !!s.requests.pending[cmd.type]
export const selectError = (s: { requests: RequestsState }, cmd: { type: string }) => s.requests.errors[cmd.type]

// ---------------------------------------------------------------------------------------------- epics

type Strategy = 'switch' | 'merge' | 'concat' | 'exhaust'
type Flatten = <T, R>(project: (value: T) => Observable<R>) => OperatorFunction<T, R>
const FLATTEN: Record<Strategy, Flatten> = {
    switch: (p) => switchMap(p), merge: (p) => mergeMap(p), concat: (p) => concatMap(p), exhaust: (p) => exhaustMap(p),
}

type Trigger<P> = PayloadActionCreator<P>
/** Every epic in the app reads and emits plain Redux actions. */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type AppEpic<S = any> = Epic<Action, Action, S>
type AnyEpic<S> = AppEpic<S>

/**
 * switch  - latest wins, earlier in-flight calls are cancelled (loads, searches)
 * concat  - run one after another, in order (grants, membership changes)
 * exhaust - ignore repeats while one is running (create, submit buttons)
 * merge   - run concurrently (per-key loads such as members of several groups)
 */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function endpoint<P, R, S = any>(
    trigger: Trigger<P>,
    request: (payload: P, state: S) => Request,
    done: (result: R, payload: P) => Action,
    strategy: Strategy = 'switch',
): AnyEpic<S> {
    return (action$, state$) => action$.pipe(
        filter(trigger.match),
        FLATTEN[strategy]((action: PayloadAction<P>): Observable<Action> => concat(
            of(requests.actions.started(trigger.type)),
            send<R>(request(action.payload, state$.value)).pipe(
                mergeMap((result) => of(done(result, action.payload), requests.actions.settled(trigger.type))),
                catchError((e: Error) => of(requests.actions.failed({ type: trigger.type, message: e.message }))),
            ),
        )),
    )
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function react<P, S = any>(trigger: Trigger<P>, next: (payload: P) => Action[]): AnyEpic<S> {
    return (action$) => action$.pipe(filter(trigger.match), mergeMap((a: PayloadAction<P>) => next(a.payload)))
}

