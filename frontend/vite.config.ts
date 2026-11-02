/// <reference types="vitest/config" />
import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

// In development the API is proxied to Spring Boot (default :8080), so the browser sees one origin (no CORS needed).
// VITE_API_TARGET points the proxy elsewhere, e.g. at the throwaway backend the end-to-end tests start.
export default defineConfig(({ mode }) => {
  const api = loadEnv(mode, '.', '').VITE_API_TARGET || 'http://localhost:8080';
  return {
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    proxy: { '/api': api, '/actuator': api },
  },
  // `vite preview` serves the production build; the end-to-end tests run against it.
  preview: {
    proxy: { '/api': api, '/actuator': api },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test-setup.ts'],
    css: false,
    include: ['src/**/*.test.{ts,tsx}'],
  },
  };
});
