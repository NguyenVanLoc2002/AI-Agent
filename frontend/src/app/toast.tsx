import { createContext, useCallback, useContext, useMemo, useRef, useState, type ReactNode } from 'react';
import { Toast, ToastViewport } from '../design-system';
import { failureText, referenceText } from './texts';

// FE-UI-P2 (P2-T07, SH-08). Toasts take only fixed text: an entry of TOAST_TEXT, or a failure whose text is the
// fixed failureText() plus the "REQ-…" reference of the server trace ID. There is no way to pass free text, so an
// exception message, a response body or business data cannot reach a toast (A-80 review security L2).
// Every toast stays until closed: the artboards show a close button and no timer, and no time limit means no WCAG 2.2.1
// issue. Auto-close for success toasts is left for when the first success entry exists (with a pause on hover and
// focus). "Đã …" only after the backend confirmed (writing.md).

type Tone = 'success' | 'info' | 'warning' | 'error';

interface ToastText { tone: Tone; title: string; description?: string }

/** Copy from the approved artboards (F6-StaffStates: 409 conflicts are warning toasts). Pages add theirs here. */
export const TOAST_TEXT = {
  'claimed-by-other': { tone: 'warning', title: 'Yêu cầu đã có người nhận', description: 'Một nhân viên khác vừa nhận yêu cầu này. Danh sách đã được tải lại.' },
  'conversation-busy': { tone: 'warning', title: 'Cuộc trò chuyện đang bận', description: 'Một thao tác khác đang chạy. Vui lòng thử lại sau vài giây.' },
} as const satisfies Record<string, ToastText>;

export type ToastId = keyof typeof TOAST_TEXT;

const MAX_TOASTS = 3;

interface Item extends ToastText { key: number }

interface ToastApi {
  show: (id: ToastId) => void;
  /** An error toast for a failed action: fixed text by kind of failure, plus the reference code. */
  failure: (error: unknown) => void;
}

const ToastContext = createContext<ToastApi | null>(null);

export function useToast(): ToastApi {
  const api = useContext(ToastContext);
  if (!api) throw new Error('useToast outside ToastProvider');
  return api;
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<Item[]>([]);
  const next = useRef(1);
  const push = useCallback((text: ToastText) => {
    const key = next.current++;
    // The same text twice is shown once (review UX M4: five "busy" toasts would push an unread error out).
    setItems((list) => [...list.filter((t) => t.title !== text.title || t.description !== text.description), { ...text, key }].slice(-MAX_TOASTS));
  }, []);
  const close = useCallback((key: number) => setItems((list) => list.filter((t) => t.key !== key)), []);
  const api = useMemo<ToastApi>(() => ({
    show: (id) => push(TOAST_TEXT[id]),
    failure: (error) => {
      const reference = referenceText(error);
      push(reference ? { tone: 'error', title: failureText(error), description: reference } : { tone: 'error', title: failureText(error) });
    },
  }), [push]);

  return (
    <ToastContext.Provider value={api}>
      {children}
      <ToastViewport>
        {items.map((t) => <Toast key={t.key} tone={t.tone} title={t.title} description={t.description} onClose={() => close(t.key)} />)}
      </ToastViewport>
    </ToastContext.Provider>
  );
}
