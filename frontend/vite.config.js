import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// In development the Vite server proxies REST and WebSocket traffic to Spring Boot,
// so the browser talks to a single origin and no CORS setup is needed.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/ws': { target: 'ws://localhost:8080', ws: true },
    },
  },
});
