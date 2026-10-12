import { beforeEach, describe, expect, it, vi } from 'vitest'
import { TestScheduler } from 'rxjs/testing'
import { Subject, type Observable } from 'rxjs'
import { StateObservable } from 'redux-observable'
import type { Action } from '@reduxjs/toolkit'

// send() is the only I/O; each test decides what it returns.
type Req = { url: string; method: string; body?: unknown; query?: Record<string, unknown> }
const sendMock = vi.fn<(req: Req) => Observable<unknown>>()
vi.mock('../../src/client/store/http', async (orig) => ({
    ...(await orig<typeof import('../../src/client/store/http')>()),
    send: (req: Req) => sendMock(req),
}))

const { commands, endpoint, none, payload } = await import('../../src/client/store/kit')
const { access, accessEpics } = await import('../../src/client/store/features/access')

const state$ = new StateObservable<unknown>(new Subject(), {})
beforeEach(() => { sendMock.mockReset() }) // block body: a returned function would run as teardown

/** Runs an epic on virtual time and returns what it emitted as [frame, type, payload?]. */
function run(epic: (a$: Observable<Action>, s$: typeof state$, d: object) => Observable<Action>,
             marbles: string, actions: Record<string, Action>, reply: (req: Req, cold: TestScheduler['createColdObservable']) => Observable<unknown>) {
    const out: [number, string, unknown?][] = []
    const scheduler = new TestScheduler((a, b) => expect(a).toEqual(b))
    scheduler.run(({ hot, cold }) => {
        sendMock.mockImplementation((req) => reply(req, cold))
        epic(hot(marbles, actions), state$, {}).subscribe((a) =>
            out.push([scheduler.now(), a.type, (a as { payload?: unknown }).payload]))
    })
    return out
}

describe('endpoint()', () => {
    const demo = commands('demo', { load: none, save: payload<number>() })
    const done = (r: unknown) => ({ type: 'demo/done', payload: r })

    it('marks the request pending, emits the result, then settles', () => {
        const out = run(endpoint(demo.load, () => ({ method: 'GET', url: '/x' }), done), '-a', { a: demo.load() },
            (_, cold) => cold('--r|', { r: [1, 2] }))
        expect(out).toEqual([[1, 'requests/started', 'demo/load'], [3, 'demo/done', [1, 2]], [3, 'requests/settled', 'demo/load']])
    })

    it('turns a failed call into requests/failed with the message', () => {
        const out = run(endpoint(demo.load, () => ({ method: 'GET', url: '/x' }), done), '-a', { a: demo.load() },
            (_, cold) => cold('-#', {}, new Error('Forbidden: no access')))
        expect(out).toEqual([[1, 'requests/started', 'demo/load'],
            [2, 'requests/failed', { type: 'demo/load', message: 'Forbidden: no access' }]])
    })

    it('switch keeps only the latest call', () => {
        const out = run(endpoint(demo.save, (n) => ({ method: 'POST', url: `/x/${n}` }), done, 'switch'),
            '-ab', { a: demo.save(1), b: demo.save(2) }, (req, cold) => cold('---r|', { r: req.url }))
        expect(out.filter(([, t]) => t === 'demo/done')).toEqual([[5, 'demo/done', '/x/2']])
    })

    it('concat runs every call, one after another, in order', () => {
        const out = run(endpoint(demo.save, (n) => ({ method: 'POST', url: `/x/${n}` }), done, 'concat'),
            '-ab', { a: demo.save(1), b: demo.save(2) }, (req, cold) => cold('---r|', { r: req.url }))
        expect(out.filter(([, t]) => t === 'demo/done')).toEqual([[4, 'demo/done', '/x/1'], [8, 'demo/done', '/x/2']])
    })

    it('exhaust ignores repeats while a call is in flight', () => {
        const out = run(endpoint(demo.save, (n) => ({ method: 'POST', url: `/x/${n}` }), done, 'exhaust'),
            '-ab', { a: demo.save(1), b: demo.save(2) }, (req, cold) => cold('---r|', { r: req.url }))
        expect(out.filter(([, t]) => t === 'demo/done')).toEqual([[4, 'demo/done', '/x/1']])
    })
})

describe('access epics', () => {
    it('grant posts the grant, then signals a change', () => {
        const grant = { group: 'g1', api: 'incident', level: 'write' as const }
        const out = run(accessEpics, '-a', { a: access.grant(grant) }, (_, cold) => cold('-r|', { r: { granted: true } }))
        expect(sendMock).toHaveBeenCalledWith({ method: 'POST', url: '/access/grants', body: grant })
        expect(out.map(([, t]) => t)).toEqual(['requests/started', 'access/changed', 'requests/settled'])
    })

    it('a change refreshes the API table, the groups and my own access', () => {
        const out = run(accessEpics, '-a', { a: access.changed() }, (_, cold) => cold('-r|', { r: [] }))
        expect(out.filter(([, t]) => t.startsWith('access/load')).map(([, t]) => t))
            .toEqual(['access/loadApis', 'access/loadGroups', 'access/loadMe'])
    })

    it('adding a member reloads that group\'s members', () => {
        const out = run(accessEpics, '-a', { a: access.addMember({ groupId: 'g1', userId: 'u1' }) },
            (_, cold) => cold('-r|', { r: { member: true } }))
        expect(sendMock).toHaveBeenCalledWith({ method: 'POST', url: '/access/groups/g1/members', body: { user: 'u1' } })
        expect(out.some(([, t, p]) => t === 'access/loadMembers' && p === 'g1')).toBe(true)
    })

    it('user search debounces keystrokes, drops repeats and skips short queries', () => {
        const out = run(accessEpics, '-a-b 300ms c-d', {
            a: access.searchUsers('an'), b: access.searchUsers('ann'), c: access.searchUsers('a'), d: access.searchUsers('a'),
        }, (_, cold) => cold('-r|', { r: [{ sys_id: 'u1', name: 'Ann' }] }))
        expect(sendMock).toHaveBeenCalledTimes(1)
        expect(sendMock.mock.calls[0][0].query).toEqual({ q: 'ann', limit: 10 })
        expect(out.map(([, t, p]) => [t, p])).toEqual([
            ['accessData/usersFound', [{ sys_id: 'u1', name: 'Ann' }]],
            ['accessData/usersFound', []],
        ])
    })
})
