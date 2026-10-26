import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'VITE_');
  return {
    // Relative base: works on GitHub Pages project sites (/<repo>/) and any static host.
    base: env.VITE_BASE || './',
    plugins: [react()],
    test: { environment: 'node' },
  };
});
