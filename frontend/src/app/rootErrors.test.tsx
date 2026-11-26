import { act } from 'react';
import { createRoot, type RootOptions } from 'react-dom/client';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ROOT_ERROR_OPTIONS, WITHHELD_NOTICE } from './rootErrors';
import { RenderErrorBoundary } from './ShellPages';

// FE-REACT19 (owner decision B): with the root options, a render error caught by the boundary, or not caught at all,
// never reaches the console with its message or stack. A control run without the options shows React would log it.

const HONEYTOKEN = 'HT-SECRET-7f3a price 1.250.000 ₫ customer cv_AAAAAAAAAAAAAAAAAAAAAA';

function Broken(): never {
  throw new Error(HONEYTOKEN);
}

function consoleText(spies: Array<ReturnType<typeof vi.spyOn>>): string {
  return spies.flatMap((s) => s.mock.calls.flat()).map((a) => (a instanceof Error ? `${a.message} ${a.stack ?? ''}` : String(a))).join('\n');
}

function renderInto(ui: React.ReactNode, options?: RootOptions) {
  const container = document.createElement('div');
  document.body.appendChild(container);
  const root = createRoot(container, options);
  act(() => { root.render(ui); });
  return { container, unmount: () => act(() => root.unmount()) };
}

afterEach(() => { vi.restoreAllMocks(); document.body.innerHTML = ''; });

describe('root error options', () => {
  it('control: without them React writes the caught error, with its message, to the console', () => {
    const spies = [vi.spyOn(console, 'error').mockImplementation(() => {}), vi.spyOn(console, 'warn').mockImplementation(() => {})];
    const { unmount } = renderInto(<RenderErrorBoundary><Broken /></RenderErrorBoundary>);

    expect(consoleText(spies)).toContain(HONEYTOKEN);
    unmount();
  });

  it('a caught render error shows the fixed screen text and logs only the fixed notice', () => {
    const spies = [vi.spyOn(console, 'error').mockImplementation(() => {}), vi.spyOn(console, 'warn').mockImplementation(() => {}),
      vi.spyOn(console, 'log').mockImplementation(() => {}), vi.spyOn(console, 'info').mockImplementation(() => {})];
    const { container, unmount } = renderInto(<RenderErrorBoundary><Broken /></RenderErrorBoundary>, ROOT_ERROR_OPTIONS);

    expect(container.textContent).toContain('Không hiển thị được trang này');
    expect(container.textContent).not.toContain(HONEYTOKEN);
    expect(consoleText(spies)).not.toContain(HONEYTOKEN);
    expect(consoleText(spies)).not.toContain('Broken');
    expect(spies[1]).toHaveBeenCalledWith(WITHHELD_NOTICE);
    unmount();
  });

  it('an uncaught render error is not logged with its message either', () => {
    const spies = [vi.spyOn(console, 'error').mockImplementation(() => {}), vi.spyOn(console, 'warn').mockImplementation(() => {})];
    // In tests React's act() rethrows an uncaught render error to the caller (in the browser onUncaughtError takes it);
    // what matters here is that nothing reached the console.
    const container = document.createElement('div');
    const root = createRoot(container, ROOT_ERROR_OPTIONS);
    let thrown: unknown = null;
    try { act(() => { root.render(<Broken />); }); } catch (error) { thrown = error; }

    // act() takes the error instead of onUncaughtError, so the fixed notice cannot be checked here (browser path not
    // tested; the app's top-level boundary catches render errors before they are uncaught).
    expect(thrown).not.toBeNull();
    expect(consoleText(spies)).not.toContain(HONEYTOKEN);
  });
});
