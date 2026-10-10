import { defineConfig } from 'vite';

// Relative assets let the same release run at / or a project subdirectory.
export default defineConfig({ base: './' });
