import { CONVERSATION_ID, decodeMessages, MAX_MESSAGE_CHARS, type ChatMessage } from './chat';
import { ApiError, isRecord, request } from './client';
import { decodeLine, decodeStatus, QUOTE_REF, type QuoteLine, type QuoteStatus } from './quotes';

// Staff inbox and held conversation (UC-05/06, EP-08; HandoffController, StaffConversationController).
// SALES only, own team only, checked by the backend at the URL and in the services. A handoff carries no
// customer identity and no message content; the reason is an internal code for staff (A-14: never shown
// to the customer). Every list, message and quote read is audited by the backend.

const REASON_CODE = /^[A-Z][A-Z_]{0,63}$/;
const CURRENCY = /^[A-Z]{3}$/;
const AMOUNT = /^\d{1,15}(\.\d{1,4})?$/;

export type HandoffStatus = 'OPEN' | 'CLAIMED';

export interface Handoff {
  handoffId: number;
  conversationId: string;
  reasonCode: string;
  status: HandoffStatus;
  openedAt: Date;
  /** The calling staff member holds it. */
  mine: boolean;
  /** Another reason raised while this handoff was active; resolving opens a new handoff with it. */
  followUpReasonCode: string | null;
}

/** The newest quote of a held conversation, with prices and without a confirmation token (EP-08b). */
export interface StaffQuote {
  quoteRef: string;
  version: number;
  status: QuoteStatus;
  lines: QuoteLine[];
  total: string;
  currency: string;
  validUntil: Date;
  notes: string[];
}

const HANDOFF_KEYS = ['handoffId', 'conversationId', 'reasonCode', 'status', 'openedAt', 'mine', 'followUpReasonCode'];
const QUOTE_KEYS = ['quoteRef', 'version', 'status', 'lines', 'total', 'currency', 'validUntil', 'notes'];
/** Fails closed on a field this client does not know (security review L-4): a new field is reviewed first. */
const onlyKnownKeys = (value: Record<string, unknown>, keys: string[]) => Object.keys(value).every((k) => keys.includes(k));

/** RESOLVED is valid only as the answer to a resolve; the inbox lists open work only. */
export function decodeHandoff(value: unknown, allowResolved = false): Handoff & { resolved: boolean } {
  if (!isRecord(value) || !onlyKnownKeys(value, HANDOFF_KEYS) || typeof value.handoffId !== 'number' || !Number.isSafeInteger(value.handoffId) || value.handoffId < 1
      || typeof value.conversationId !== 'string' || !CONVERSATION_ID.test(value.conversationId)
      || typeof value.reasonCode !== 'string' || !REASON_CODE.test(value.reasonCode)
      || typeof value.openedAt !== 'string' || typeof value.mine !== 'boolean'
      || !(value.followUpReasonCode === null || value.followUpReasonCode === undefined
        || (typeof value.followUpReasonCode === 'string' && REASON_CODE.test(value.followUpReasonCode)))) {
    throw new ApiError('invalid-response');
  }
  const statuses = allowResolved ? ['OPEN', 'CLAIMED', 'RESOLVED'] : ['OPEN', 'CLAIMED'];
  if (typeof value.status !== 'string' || !statuses.includes(value.status)) {
    throw new ApiError('invalid-response');
  }
  const openedAt = new Date(value.openedAt);
  if (Number.isNaN(openedAt.getTime())) {
    throw new ApiError('invalid-response');
  }
  return {
    handoffId: value.handoffId,
    conversationId: value.conversationId,
    reasonCode: value.reasonCode,
    status: value.status === 'RESOLVED' ? 'CLAIMED' : value.status as HandoffStatus,
    resolved: value.status === 'RESOLVED',
    openedAt,
    mine: value.mine,
    followUpReasonCode: typeof value.followUpReasonCode === 'string' ? value.followUpReasonCode : null,
  };
}

export async function listHandoffs(): Promise<Handoff[]> {
  const value = await request('/api/internal/handoffs');
  if (!Array.isArray(value) || value.length > 500) throw new ApiError('invalid-response');
  return value.map((item) => decodeHandoff(item));
}

const checkId = (handoffId: number) => {
  if (!Number.isSafeInteger(handoffId) || handoffId < 1) throw new ApiError('invalid-response');
};

/** The answer must be this handoff, held by the caller (security review L-1); otherwise the outcome is unknown. */
export async function claimHandoff(handoffId: number): Promise<Handoff> {
  checkId(handoffId);
  const result = decodeHandoff(await request(`/api/internal/handoffs/${handoffId}/claim`, { method: 'POST' }));
  if (result.handoffId !== handoffId || result.status !== 'CLAIMED' || !result.mine) throw new ApiError('invalid-response');
  return result;
}

/** The answer must be this handoff, now resolved (security review L-1); otherwise the outcome is unknown. */
export async function resolveHandoff(handoffId: number): Promise<Handoff & { resolved: boolean }> {
  checkId(handoffId);
  const result = decodeHandoff(await request(`/api/internal/handoffs/${handoffId}/resolve`, { method: 'POST' }), true);
  if (result.handoffId !== handoffId || !result.resolved) throw new ApiError('invalid-response');
  return result;
}

const checkConversation = (conversationId: string) => {
  if (!CONVERSATION_ID.test(conversationId)) throw new ApiError('invalid-response');
};

export async function readHeldMessages(conversationId: string): Promise<ChatMessage[]> {
  checkConversation(conversationId);
  return decodeMessages(await request(`/api/internal/conversations/${conversationId}/messages`));
}

const EVENT_ID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

/**
 * STAFF-MSG-ID (A-90): the same `clientEventId` with the same text is stored once, so a resend after an unknown
 * outcome is safe; the caller keeps the ID until the send succeeded or the text changed.
 */
export async function postStaffMessage(conversationId: string, clientEventId: string, text: string): Promise<void> {
  checkConversation(conversationId);
  const trimmed = text.trim();
  if (!EVENT_ID.test(clientEventId) || trimmed === '' || trimmed.length > MAX_MESSAGE_CHARS) {
    throw new ApiError('invalid-response', null, null, null, false);
  }
  await request(`/api/internal/conversations/${conversationId}/messages`, { method: 'POST', json: { clientEventId, text: trimmed } });
}

/** Fails closed: a quote with any field this client cannot read is not shown. */
export function decodeStaffQuote(value: unknown): StaffQuote {
  if (!isRecord(value) || !onlyKnownKeys(value, QUOTE_KEYS) || typeof value.quoteRef !== 'string' || !QUOTE_REF.test(value.quoteRef)
      || typeof value.version !== 'number' || !Number.isInteger(value.version) || value.version < 1 || value.version > 999_999
      || !Array.isArray(value.lines) || value.lines.length === 0 || value.lines.length > 50
      || typeof value.total !== 'string' || !AMOUNT.test(value.total)
      || typeof value.currency !== 'string' || !CURRENCY.test(value.currency)
      || typeof value.validUntil !== 'string' || !Array.isArray(value.notes) || value.notes.length > 10
      || !value.notes.every((n) => typeof n === 'string' && n.length > 0 && n.length <= 500)) {
    throw new ApiError('invalid-response');
  }
  const validUntil = new Date(value.validUntil);
  if (Number.isNaN(validUntil.getTime())) throw new ApiError('invalid-response');
  return {
    quoteRef: value.quoteRef,
    version: value.version,
    status: decodeStatus(value.status),
    lines: value.lines.map(decodeLine),
    total: value.total,
    currency: value.currency,
    validUntil,
    notes: value.notes as string[],
  };
}

export async function readHeldQuote(conversationId: string): Promise<StaffQuote> {
  checkConversation(conversationId);
  return decodeStaffQuote(await request(`/api/internal/conversations/${conversationId}/quote`));
}
