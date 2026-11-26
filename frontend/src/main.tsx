import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './app/App';
import { ROOT_ERROR_OPTIONS } from './app/rootErrors';
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
    // FE-REACT19: React's own console reports of render errors are replaced (no message or stack is logged).
    createRoot(root, ROOT_ERROR_OPTIONS).render(<StrictMode><App /></StrictMode>);
  }
}

void start();
