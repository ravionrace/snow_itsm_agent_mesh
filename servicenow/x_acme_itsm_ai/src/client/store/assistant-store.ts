import { combineReducers, configureStore, type Action } from '@reduxjs/toolkit'
import { createEpicMiddleware } from 'redux-observable'
import { useDispatch, useSelector } from 'react-redux'
import { requests } from './kit'
import { chatEpics, chatSlice } from './features/chat'

/** The Assistant page has its own small store (chat only); the console keeps its own. */
const rootReducer = combineReducers({ requests: requests.reducer, chat: chatSlice.reducer })
export type AssistantState = ReturnType<typeof rootReducer>

const epicMiddleware = createEpicMiddleware<Action, Action, AssistantState>()
export const assistantStore = configureStore({ reducer: rootReducer, middleware: (d) => d().concat(epicMiddleware) })
epicMiddleware.run(chatEpics)

export const useChatDispatch = useDispatch.withTypes<typeof assistantStore.dispatch>()
export const useChatSelector = useSelector.withTypes<AssistantState>()
