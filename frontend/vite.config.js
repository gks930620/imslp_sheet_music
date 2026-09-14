import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  // 개발 서버는 화면만 띄우고 데이터·파일은 전부 백엔드(8104)로 넘긴다.
  // strictPort: 포트가 이미 쓰이면 조용히 다른 번호로 옮겨 가지 않고 실패한다 —
  // QA 중에 "5104 인 줄 알았는데 5105 였다" 를 만들지 않기 위한 것.
  server: {
    port: 5104,
    strictPort: true,
    proxy: {
      "/api": "http://localhost:8104",
      // 악보 미리보기 PNG (EditionDtoAssembler.previewUrl = FileEntity.filePath).
      // 이게 빠지면 개발 서버에서 썸네일이 전부 깨진다.
      "/uploads": "http://localhost:8104",
      "/custom-oauth2": "http://localhost:8104",
      "/login/oauth2": "http://localhost:8104",
      "/ws-chat": {
        target: "http://localhost:8104",
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
