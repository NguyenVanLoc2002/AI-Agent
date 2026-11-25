import { CONVERSATION_ID } from '../api/chat';
import { QUOTE_REF } from '../api/quotes';

// Decision A-68 (2A, 3A): the backend has no "list my conversations" endpoint, so this tab remembers
// the customer's current conversation and the last quote reference in sessionStorage. Neither is a
// credential (the backend checks the session owner on every read and answers 404 otherwise). Cleared on
// sign-out and when the backend no longer knows the conversation. Storage can be unavailable (private
// mode, blocked site data): every access is guarded and the page works without it.

const CONVERSATION_KEY = 'apas.chat.conversation';
const QUOTE_KEY = 'apas.chat.quote';

function read(key: string, format: RegExp): string | null {
  try {
    const value = window.sessionStorage.getItem(key);
    return value !== null && format.test(value) ? value : null;
  } catch {
    return null;
  }
}

function write(key: string, value: string | null): void {
  try {
    if (value === null) window.sessionStorage.removeItem(key);
    else window.sessionStorage.setItem(key, value);
  } catch {
    // Not remembered: the next page load starts a new conversation.
  }
}

// Every sign-in, sign-out and session expiry starts a new epoch and clears the storage. A page captures the
// epoch when it opens; a write from an older epoch (an answer that arrived after sign-out) is dropped, so
// nothing of one customer reaches the next person who uses this tab (EP-10b review M-1).
let epoch = 0;

export const conversationMemory = {
  epoch: () => epoch,
  /** Sign-in, sign-out, expiry: forget everything and refuse writes of earlier pages. */
  reset: () => {
    epoch += 1;
    write(CONVERSATION_KEY, null);
    write(QUOTE_KEY, null);
  },
  conversationId: () => read(CONVERSATION_KEY, CONVERSATION_ID),
  quoteRef: () => read(QUOTE_KEY, QUOTE_REF),
  rememberConversation: (id: string, from: number) => {
    if (from !== epoch) return;
    write(CONVERSATION_KEY, CONVERSATION_ID.test(id) ? id : null);
    write(QUOTE_KEY, null);
  },
  rememberQuote: (ref: string, from: number) => {
    if (from !== epoch) return;
    write(QUOTE_KEY, QUOTE_REF.test(ref) ? ref : null);
  },
  forgetQuote: () => write(QUOTE_KEY, null),
  forgetConversation: () => { write(CONVERSATION_KEY, null); write(QUOTE_KEY, null); },
};
