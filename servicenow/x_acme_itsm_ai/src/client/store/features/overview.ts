import { createSlice, type PayloadAction } from '@reduxjs/toolkit'
import { combineEpics } from 'redux-observable'
import { filter, map, switchMap, takeUntil, timer } from 'rxjs'
import { get } from '../http'
import { commands, endpoint, payload, type AppEpic } from '../kit'

export interface Summary {
    window_hours: number; since: string; calls: number; errors: number; error_rate: number
    writes_committed: number; idempotent_replays: number; avg_duration_ms: number
    by_api: { key: string; count: number }[]; top_resources: { key: string; count: number }[]
}

export const overview = commands('overview', {
    load: payload<number>(),            // hours
    watch: payload<number>(),           // start auto-refresh for this window
    unwatch: payload<void>(),
})

export const overviewSlice = createSlice({
    name: 'overviewData',
    initialState: { summary: null as Summary | null },
    reducers: { loaded: (s, a: PayloadAction<Summary>) => { s.summary = a.payload } },
})

const REFRESH_MS = 60_000

/** Refresh every minute while the overview is on screen; a new window restarts the timer. */
const autoRefresh: AppEpic = (action$) => action$.pipe(
    filter(overview.watch.match),
    switchMap(({ payload: hours }) => timer(0, REFRESH_MS).pipe(
        map(() => overview.load(hours)),
        takeUntil(action$.pipe(filter(overview.unwatch.match))),
    )),
)

export const overviewEpics = combineEpics(
    endpoint(overview.load, (hours) => get('/console/summary', { hours }), overviewSlice.actions.loaded),
    autoRefresh,
)
