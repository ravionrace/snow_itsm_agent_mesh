import { defineConfig } from 'vitest/config'

// Separate from vite.config.mjs (used by the now-sdk build), which disables Vite's own TS transform.
export default defineConfig({
    test: { include: ['tests/**/*.test.ts'], environment: 'node' },
})
