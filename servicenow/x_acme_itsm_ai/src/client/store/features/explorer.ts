import { createSlice, type PayloadAction } from '@reduxjs/toolkit'
import { concat, filter, map, of, switchMap } from 'rxjs'
import { get, sendRaw, toUrl, type RawResponse } from '../http'
import { commands, payload, requests, type AppEpic } from '../kit'

export interface ExplorerCall { path: string; query: Record<string, string> }

export const explorer = commands('explorer', { call: payload<ExplorerCall>() })

export const explorerSlice = createSlice({
    name: 'explorerData',
    initialState: { url: '', response: null as RawResponse | null },
    reducers: {
        answered: (s, a: PayloadAction<{ url: string; response: RawResponse }>) => {
            s.url = a.payload.url
            s.response = a.payload.response
        },
    },
})

/** The explorer wants the raw answer (status, error body, timing), not just success - so no endpoint() here. */
export const explorerEpics: AppEpic = (action$) => action$.pipe(
    filter(explorer.call.match),
    switchMap(({ payload: { path, query } }) => {
        const req = get(path, query)
        return concat(
            of(requests.actions.started(explorer.call.type)),
            sendRaw(req).pipe(map((response) => explorerSlice.actions.answered({ url: toUrl(req), response }))),
            of(requests.actions.settled(explorer.call.type)),
        )
    }),
)
