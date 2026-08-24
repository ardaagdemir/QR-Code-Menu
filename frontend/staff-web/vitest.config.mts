import path from "node:path";
import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@": path.resolve(import.meta.dirname, "."),
    },
  },
  test: {
    environment: "jsdom",
    setupFiles: ["./vitest.setup.ts"],
    // Plain-logic tests (.test.ts) run on node:test via `npm run test:unit` instead -
    // without this, vitest also picks them up and fails each with "No test suite found"
    // since they register through node:test's own `test()`, not vitest's.
    include: ["**/*.test.tsx"],
  },
});
