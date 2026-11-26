import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './app/App';
import './design-system/tokens.css';
import './design-system/components/bundle.css';
import './app/foundation.css';

async function start() {
  // FE-UI-P2 (A-80 D05): the mock backend exists only in the dev server and only on request (`npm run dev:mock`, Vite mode "mock").
  // `import.meta.env.DEV` is false in a build, so the bundler drops this branch and the mock module entirely;
  // scripts/verify-no-mocks.mjs checks that after every build.
  if (import.meta.env.DEV && import.meta.env.MODE === 'mock') {
    const { installMocks } = await import('./mocks/install');
    installMocks();
  }
  const root = document.getElementById('root');
  if (root) {
    createRoot(root).render(<StrictMode><App /></StrictMode>);
  }
}

void start();
