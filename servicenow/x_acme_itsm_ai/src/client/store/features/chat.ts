import { createSlice, type Action, type PayloadAction } from '@reduxjs/toolkit'
import { combineEpics } from 'redux-observable'
import { catchError, distinctUntilChanged, filter, map, merge, of, switchMap, takeUntil, takeWhile, timer } from 'rxjs'
import { get, post, send } from '../http'
import { commands, endpoint, none, payload, type AppEpic } from '../kit'

// ---------------------------------------------------------------------------------------------- model

export interface Change { field: string; from?: string; to?: string }
export interface Pending {
    id: string; agent: string; tool: string; expires_at?: string
    preview: { action?: string; table?: string; record?: string; changes?: Change[]; warnings?: string[] }
}
/** How a turn ran, as the runtime reports it: routing, the access check, the model's thinking, each tool call. */
export type Step =
    | { kind: 'route'; agent: string }
    | { kind: 'access'; api: string; read: boolean; write: boolean }
    | { kind: 'thinking'; agent: string; text: string }
    | { kind: 'tool'; agent: string; tool: string; phase: 'call' | 'preview' | 'commit' | 'blocked'; status: string; ms: number }
    | { kind: 'handoff'; from: string; to: string }
    | { kind: 'check'; agent: string; ok: boolean }
export interface TurnResult { reply: string | null; agent: string | null; pending: Pending | null; notice?: string; steps?: Step[] }
export interface Turn { turn_id: string; session_id: string; status: 'running' | 'done' | 'failed'; result?: TurnResult; error?: string; progress?: Step[] }
export interface Message { role: 'user' | 'assistant' | 'event' | 'notice' | 'error'; text: string; agent?: string | null; steps?: Step[] }
interface Transcript { transcript: { role: string; text: string; agent?: string }[]; pending: Pending | null }

// ---------------------------------------------------------------------------------------------- commands

export const chat = commands('chat', {
    send: payload<string>(),
    decide: payload<{ pendingId: string; decision: 'confirm' | 'reject' }>(),
    resume: payload<string>(),            // session id from the URL
    reset: none,
})

// ---------------------------------------------------------------------------------------------- state

interface ChatState { sessionId: string | null; messages: Message[]; pending: Pending | null; thinking: boolean; live: Step[] }

export const chatSlice = createSlice({
    name: 'chatData',
    initialState: { sessionId: null, messages: [], pending: null, thinking: false, live: [] } as ChatState,
    reducers: {
        started: (s, a: PayloadAction<Turn>) => { s.sessionId = a.payload.session_id; s.thinking = true; s.live = [] },
        progressed: (s, a: PayloadAction<Step[]>) => { s.live = a.payload },
        finished: (s, { payload: t }: PayloadAction<Turn>) => {
            s.thinking = false
            s.live = []
            if (t.status === 'failed' || !t.result) {
                s.messages.push({ role: 'error', text: t.error ?? 'The Assistant could not answer. Please try again.' })
                return
            }
            if (t.result.notice) s.messages.push({ role: 'notice', text: t.result.notice })
            if (t.result.reply) s.messages.push({ role: 'assistant', text: t.result.reply, agent: t.result.agent, steps: t.result.steps })
            s.pending = t.result.pending
        },
        failed: (s, a: PayloadAction<string>) => { s.thinking = false; s.live = []; s.messages.push({ role: 'error', text: a.payload }) },
        resumed: (s, { payload }: PayloadAction<{ sessionId: string; history: Transcript }>) => {
            s.sessionId = payload.sessionId
            s.pending = payload.history.pending
            s.messages = payload.history.transcript
                .filter((m) => m.text)
                .map((m) => ({ role: (m.role === 'user' || m.role === 'assistant' ? m.role : 'event') as Message['role'],
                               text: m.text, agent: m.agent }))
        },
    },
    extraReducers: (b) => b
        .addCase(chat.send, (s, a) => { s.messages.push({ role: 'user', text: a.payload }); s.pending = null; s.thinking = true })
        .addCase(chat.decide, (s, a) => {
            s.messages.push({ role: 'event', text: a.payload.decision === 'confirm' ? 'You confirmed the change.' : 'You rejected the change.' })
            s.pending = null
            s.thinking = true
        })
        .addCase(chat.reset, () => ({ sessionId: null, messages: [], pending: null, thinking: false, live: [] })),
})
const done = chatSlice.actions

// ---------------------------------------------------------------------------------------------- epics
//            when ...        call ...                                                                  then ...

const endpoints = combineEpics(
    endpoint(chat.send,   (text, s: { chat: ChatState }) => post('/chat/turns', { message: text, session_id: s.chat.sessionId ?? undefined }), done.started, 'exhaust'),
    endpoint(chat.decide, (d, s: { chat: ChatState })    => post(`/chat/sessions/${s.chat.sessionId}/confirmations/${d.pendingId}`, { decision: d.decision }), done.started, 'exhaust'),
    endpoint(chat.resume, (sessionId)                   => get(`/chat/sessions/${sessionId}`), (history: Transcript, sessionId) => done.resumed({ sessionId, history })),
)

const POLL_MS = 1500
const TURN_LIMIT_MS = 180_000

/**
 * After a turn starts, poll it until it is done (or failed / too slow), reporting its live steps on the way;
 * a new turn or a reset stops the old poll.
 */
const pollTurn: AppEpic = (action$) => action$.pipe(
    filter(done.started.match),
    switchMap(({ payload: t }) => merge(
        timer(0, POLL_MS).pipe(
            switchMap(() => send<Turn>(get(`/chat/turns/${t.turn_id}`))),
            distinctUntilChanged((a, b) => a.status === b.status && (a.progress?.length ?? 0) === (b.progress?.length ?? 0)),
            map((turn) => turn.status === 'running' ? done.progressed(turn.progress ?? []) : done.finished(turn)),
            catchError((e: Error) => of(done.failed(e.message))),
        ),
        timer(TURN_LIMIT_MS).pipe(map(() => done.failed('The Assistant is taking too long. Please try again.'))),
    ).pipe(
        takeWhile((a: Action) => done.progressed.match(a), true),   // stop at finished / failed, whichever comes first
        takeUntil(action$.pipe(filter(chat.reset.match))),
    )),
)

/** Failed send / decide / resume calls become a chat message, not a silent console error. */
const surfaceErrors: AppEpic = (action$) => action$.pipe(
    filter((a): a is PayloadAction<{ type: string; message: string }> => a.type === 'requests/failed'),
    filter((a) => a.payload.type.startsWith('chat/')),
    map((a) => done.failed(a.payload.message)),
)

export const chatEpics = combineEpics(endpoints, pollTurn, surfaceErrors)
