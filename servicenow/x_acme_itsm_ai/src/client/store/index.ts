import { combineReducers, configureStore, type Action } from '@reduxjs/toolkit'
import { combineEpics, createEpicMiddleware } from 'redux-observable'
import { useDispatch, useSelector } from 'react-redux'
import { requests } from './kit'
import { accessEpics, accessSlice } from './features/access'
import { overviewEpics, overviewSlice } from './features/overview'
import { explorerEpics, explorerSlice } from './features/explorer'
import { auditEpics, auditSlice } from './features/audit'

const rootReducer = combineReducers({
    requests: requests.reducer,
    access: accessSlice.reducer,
    overview: overviewSlice.reducer,
    explorer: explorerSlice.reducer,
    audit: auditSlice.reducer,
})

export type RootState = ReturnType<typeof rootReducer>

const epicMiddleware = createEpicMiddleware<Action, Action, RootState>()

export const store = configureStore({
    reducer: rootReducer,
    middleware: (getDefault) => getDefault().concat(epicMiddleware),
})

epicMiddleware.run(combineEpics(accessEpics, overviewEpics, explorerEpics, auditEpics))

export type AppDispatch = typeof store.dispatch
export const useAppDispatch = useDispatch.withTypes<AppDispatch>()
export const useAppSelector = useSelector.withTypes<RootState>()
