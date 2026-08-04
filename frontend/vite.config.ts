import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// specs/frontend/00-overview.md — one proxy rule for everything under /api, since every
// backend session-plane endpoint is consistently namespaced there (specs/backend/00-overview.md).
// Keeps the browser talking only to localhost:5173, so every request is same-origin — no CORS
// config needed here or in prod (prod serves the built app from the same Spring Boot instance).
// /downloads is a second rule for the same reason: static assets (e.g. the GWToolboxdll plugin
// download on the Account page) are served by Spring Boot directly from src/main/resources/static,
// not by anything under /api — without this, the Vite dev server would 404 on them itself instead
// of forwarding to the backend, even though the exact same link works fine against the built jar.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/downloads': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});
