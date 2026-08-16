import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// specs/frontend/00-overview.md — one proxy rule for everything under /api, since every
// backend session-plane endpoint is consistently namespaced there (specs/backend/00-overview.md).
// Keeps the browser talking only to localhost:5173, so every request is same-origin — no CORS
// config needed here or in prod (prod serves the built app from the same Spring Boot instance).
// /SCTracker.dll is a second rule for the same reason: it's a static asset served by Spring Boot
// directly from src/main/resources/static (the Account page's plugin download), not by anything
// under /api — without this, the Vite dev server would 404 on it itself instead of forwarding to
// the backend, even though the exact same link works fine against the built jar.
// /plugin-version is a third rule for the same reason again — the plugin calls it machine-key-free
// and top-level (see PluginVersionController), not under /api, and the Account page fetches it
// directly (not via the /api-prefixed api client) to show the current version next to the download link.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/SCTracker.dll': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/plugin-version': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});
