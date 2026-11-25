import { ApiError, isRecord, request } from './client';
import { decodeCard, type QuoteCard } from './quotes';

// Customer chat over the local channel simulator (UC-01, D-06; ConversationController). The customer
// comes from the session; the conversation ID is only a handle, checked against the session by the
// backend (another customer's or a malformed one is 404).

export const CONVERSATION_ID = /^cv_[A-Za-z0-9_-]{22}$/;
export const MAX_MESSAGE_CHARS = 2000;
/** The backend counts an unanswered turn as running for 2 minutes; the client waits 90 s, then offers a resend. */
export const TURN_TIMEOUT_MS = 90_000;

export type Direction = 'CUSTOMER' | 'BOT' | 'STAFF';
export type CustomerState = 'BOT' | 'WAITING_STAFF' | 'STAFF';

export interface ChatMessage {
  direction: Direction;
  text: string;
  sentAt: Date | null;
}

export interface Turn {
  reply: string;
  handedOff: boolean;
  duplicate: boolean;
  card: QuoteCard | null;
  /** The turn carried a quote card this client could not read (it is not shown). */
  cardUnreadable: boolean;
}

const DIRECTIONS: readonly string[] = ['CUSTOMER', 'BOT', 'STAFF'];
const STATES: readonly string[] = ['BOT', 'WAITING_STAFF', 'STAFF'];

export async function createConversation(): Promise<string> {
  const value = await request('/api/demo/conversations', { method: 'POST' });
  if (isRecord(value) && typeof value.conversationId === 'string' && CONVERSATION_ID.test(value.conversationId)) {
    return value.conversationId;
  }
  throw new ApiError('invalid-response');
}

export function decodeTurn(value: unknown): Turn {
  if (!isRecord(value) || typeof value.reply !== 'string' || typeof value.handedOff !== 'boolean'
      || typeof value.duplicate !== 'boolean') {
    throw new ApiError('invalid-response');
  }
  let card: QuoteCard | null = null;
  let cardUnreadable = false;
  if (value.quoteCard !== null && value.quoteCard !== undefined) {
    try {
      card = decodeCard(value.quoteCard);
    } catch {
      cardUnreadable = true;
    }
  }
  return { reply: value.reply, handedOff: value.handedOff, duplicate: value.duplicate, card, cardUnreadable };
}

export async function postMessage(conversationId: string, clientEventId: string, text: string): Promise<Turn> {
  if (!CONVERSATION_ID.test(conversationId)) throw new ApiError('invalid-response');
  return decodeTurn(await request(`/api/demo/conversations/${conversationId}/messages`, {
    method: 'POST', json: { clientEventId, text }, timeoutMs: TURN_TIMEOUT_MS,
  }));
}

/** Fails closed on an unknown direction: no message is shown under a guessed author. */
export function decodeMessages(value: unknown): ChatMessage[] {
  if (!Array.isArray(value) || value.length > 1000) throw new ApiError('invalid-response');
  return value.map((item) => {
    if (!isRecord(item) || typeof item.direction !== 'string' || !DIRECTIONS.includes(item.direction) || typeof item.text !== 'string') {
      throw new ApiError('invalid-response');
    }
    const sentAt = typeof item.sentAt === 'string' ? new Date(item.sentAt) : null;
    return { direction: item.direction as Direction, text: item.text, sentAt: sentAt && !Number.isNaN(sentAt.getTime()) ? sentAt : null };
  });
}

export async function readMessages(conversationId: string): Promise<ChatMessage[]> {
  if (!CONVERSATION_ID.test(conversationId)) throw new ApiError('invalid-response');
  return decodeMessages(await request(`/api/demo/conversations/${conversationId}/messages`));
}

export async function readState(conversationId: string): Promise<CustomerState> {
  if (!CONVERSATION_ID.test(conversationId)) throw new ApiError('invalid-response');
  const value = await request(`/api/demo/conversations/${conversationId}`);
  if (isRecord(value) && typeof value.state === 'string' && STATES.includes(value.state)) {
    return value.state as CustomerState;
  }
  throw new ApiError('invalid-response');
}

export function newClientEventId(): string {
  return crypto.randomUUID();
}
