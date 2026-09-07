import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/api": "http://localhost:8080",
      "/custom-oauth2": "http://localhost:8080",
      "/login/oauth2": "http://localhost:8080",
      "/ws-chat": {
        target: "http://localhost:8080",
        ws: true,
      },
    },
  },
  // Vitest (docs/설계/03_기술결정.md §6)
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: "./src/test/setup.js",
    css: false,
    include: ["src/**/*.test.{js,jsx}"],
  },
});
