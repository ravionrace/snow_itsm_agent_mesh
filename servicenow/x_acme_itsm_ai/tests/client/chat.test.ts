import { beforeEach, describe, expect, it, vi } from 'vitest'
import { TestScheduler } from 'rxjs/testing'
import { merge, Subject, type Observable } from 'rxjs'
import { StateObservable } from 'redux-observable'
import type { Action } from '@reduxjs/toolkit'

type Req = { url: string; method: string; body?: unknown }
const sendMock = vi.fn<(req: Req) => Observable<unknown>>()
vi.mock('../../src/client/store/http', async (orig) => ({
    ...(await orig<typeof import('../../src/client/store/http')>()),
    send: (req: Req) => sendMock(req),
}))
const { chat, chatEpics, chatSlice } = await import('../../src/client/store/features/chat')

beforeEach(() => { sendMock.mockReset() })

function run(marbles: string, actions: Record<string, Action>, state: unknown, reply: (req: Req, cold: TestScheduler['createColdObservable']) => Observable<unknown>) {
    const out: [number, string, unknown?][] = []
    const scheduler = new TestScheduler((a, b) => expect(a).toEqual(b))
    scheduler.run(({ hot, cold }) => {
        sendMock.mockImplementation((req) => reply(req, cold))
        const state$ = new StateObservable(new Subject(), state)
        // Like the epic middleware, feed every emitted action back into the action stream.
        const feedback$ = new Subject<Action>()
        chatEpics(merge(hot(marbles, actions), feedback$), state$ as never, {}).subscribe((a: Action) => {
            out.push([scheduler.now(), a.type, (a as { payload?: unknown }).payload])
            feedback$.next(a)
        })
    })
    return out
}

describe('chat epics', () => {
    it('send starts a turn, then polls until it is done', () => {
        let polls = 0
        const out = run('-a', { a: chat.send('Show my incidents') }, { chat: { sessionId: null } }, (req, cold) => {
            if (req.method === 'POST') return cold('-r|', { r: { turn_id: 't1', session_id: 's1', status: 'running' } })
            polls++
            return cold('-r|', { r: polls < 3 ? { turn_id: 't1', status: 'running' }
                : { turn_id: 't1', session_id: 's1', status: 'done', result: { reply: 'You have 2 incidents.', agent: 'incident', pending: null } } })
        })
        expect(sendMock.mock.calls[0][0]).toMatchObject({ method: 'POST', url: '/chat/turns', body: { message: 'Show my incidents' } })
        expect(sendMock.mock.calls.slice(1).every(([r]) => r.url === '/chat/turns/t1')).toBe(true)
        const finished = out.find(([, t]) => t === 'chatData/finished')
        expect(finished?.[2]).toMatchObject({ status: 'done' })
        expect(polls).toBe(3)
    })

    it('reports live steps while the turn runs, once per change', () => {
        let polls = 0
        const step = { kind: 'tool', agent: 'incident', tool: 'get_my_incidents', phase: 'call', status: 'OK', ms: 90 }
        const out = run('-a', { a: chat.send('my incidents') }, { chat: { sessionId: null } }, (req, cold) => {
            if (req.method === 'POST') return cold('-r|', { r: { turn_id: 't1', session_id: 's1', status: 'running' } })
            polls++
            return cold('-r|', { r: polls === 1 ? { status: 'running', progress: [] }
                : polls < 4 ? { status: 'running', progress: [step] }
                : { turn_id: 't1', session_id: 's1', status: 'done', result: { reply: 'Two.', agent: 'incident', pending: null, steps: [step] } } })
        })
        const types = out.map(([, t]) => t).filter((t) => t.startsWith('chatData/'))
        expect(types).toEqual(['chatData/started', 'chatData/progressed', 'chatData/progressed', 'chatData/finished'])
        expect(polls).toBe(4)
    })

    it('a decision posts to the session and polls the follow-up turn', () => {
        run('-a', { a: chat.decide({ pendingId: 'p1abcdef', decision: 'confirm' }) }, { chat: { sessionId: 's1abcdefgh' } }, (req, cold) =>
            req.method === 'POST' ? cold('-r|', { r: { turn_id: 't2', session_id: 's1abcdefgh', status: 'running' } })
                : cold('-r|', { r: { turn_id: 't2', status: 'done', result: { reply: 'Done.', agent: 'incident', pending: null } } }))
        expect(sendMock.mock.calls[0][0]).toMatchObject({ url: '/chat/sessions/s1abcdefgh/confirmations/p1abcdef', body: { decision: 'confirm' } })
    })

    it('a failed call becomes an error message in the chat', () => {
        const out = run('-a', { a: chat.send('hi') }, { chat: { sessionId: null } }, (_, cold) => cold('-#', {}, new Error('The Assistant is not configured yet.')))
        expect(out.find(([, t]) => t === 'chatData/failed')?.[2]).toBe('The Assistant is not configured yet.')
    })

    it('reducer keeps the transcript, pending change and thinking flag in step', () => {
        let s = chatSlice.reducer(undefined, chat.send('order a laptop'))
        expect(s.thinking).toBe(true)
        s = chatSlice.reducer(s, chatSlice.actions.finished({ turn_id: 't', session_id: 's', status: 'done',
            result: { reply: 'Confirm the order?', agent: 'catalog', pending: { id: 'p', agent: 'catalog', tool: 'order_item', preview: {} } } }))
        expect(s.messages.map((m) => m.role)).toEqual(['user', 'assistant'])
        expect(s.pending?.tool).toBe('order_item')
        expect(s.live).toEqual([])
        s = chatSlice.reducer(s, chat.decide({ pendingId: 'p', decision: 'confirm' }))
        expect(s.pending).toBeNull()
    })
})
