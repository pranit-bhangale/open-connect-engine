import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');

  return {
    envPrefix: ['VITE_', 'CHAT_', 'HOST_'],
    plugins: [react()],
    resolve: {
      alias: {
        '@': path.resolve(import.meta.dirname || process.cwd(), './src'),
      },
    },
    server: {
      port: 3000,
      open: true,
      proxy: {
        '/api/rooms': {
          target: env.BACKEND_PROXY_URL || 'http://localhost:8080',
          changeOrigin: true,
        },
        '/chat': {
          target: env.BACKEND_PROXY_URL || 'http://localhost:8080',
          ws: true,
          changeOrigin: true,
        },
        '/actuator': {
          target: env.BACKEND_PROXY_URL || 'http://localhost:8080',
          changeOrigin: true,
        },
      },
    },
    build: {
      outDir: 'dist',
      sourcemap: false,
    },
  };
});
