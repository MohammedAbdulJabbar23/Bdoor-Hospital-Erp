import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';
export default defineConfig({
    plugins: [react()],
    resolve: {
        alias: {
            '@': path.resolve(__dirname, './src'),
        },
    },
    server: {
        host: '0.0.0.0', // IPv4 wildcard — browsers resolve localhost → 127.0.0.1
        port: 5173,
        strictPort: true,
        proxy: {
            // Use 127.0.0.1 explicitly — avoids Node's IPv6-first 'localhost' resolution.
            '/api': 'http://127.0.0.1:8080',
        },
    },
});
