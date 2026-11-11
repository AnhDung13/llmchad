import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import path from 'node:path'
import { defineConfig, loadEnv } from 'vite'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  // Đọc .env, .env.local... trong apps/web
  const env = loadEnv(mode, import.meta.dirname)
  const apiUrl = env.VITE_API_URL || 'http://localhost:8080'

  return {
    plugins: [react(), tailwindcss()],
    resolve: {
      // import '@/...' = import từ src/ (shadcn dùng alias này)
      alias: { '@': path.resolve(import.meta.dirname, './src') },
    },
    server: {
      port: 5173,
      proxy: {
        // Trình duyệt gọi /api/... trên cùng origin với Vite, Vite chuyển tiếp sang Spring → không cần CORS khi dev.
        // xfwd: gửi kèm X-Forwarded-Host/Proto để Spring (server.forward-headers-strategy=framework) biết
        // trình duyệt đang ở localhost:5173 → redirect_uri của OAuth trỏ về đúng Vite, không phải :8080.
        '/api': { target: apiUrl, changeOrigin: true, xfwd: true },
      },
    },
  }
})
