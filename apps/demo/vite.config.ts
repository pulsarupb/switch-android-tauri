import { defineConfig } from "vite";
import { svelte } from "@sveltejs/vite-plugin-svelte";
import process from "node:process";

const host = process.env.TAURI_DEV_HOST;

// https://vite.dev/config/
export default defineConfig({
  plugins: [svelte()],

  resolve: {
    dedupe: ["svelte"],
  },

  // The workspace library is consumed as source (it ships `.svelte.ts` runes modules),
  // so it must not be pre-bundled by esbuild.
  optimizeDeps: {
    exclude: ["@pulsarupb/svelte-switch-input"],
  },

  // Prevent Vite from obscuring Rust errors.
  clearScreen: false,

  server: {
    port: 1420,
    strictPort: true,
    host: host || false,
    hmr: host
      ? {
          protocol: "ws",
          host,
          port: 1421,
        }
      : undefined,
    watch: {
      ignored: ["**/src-tauri/**"],
    },
  },

  build: {
    target: "esnext",
    ...(process.env.TAURI_ENV_DEBUG
      ? { minify: false as const, sourcemap: true }
      : {}),
  },
});
