import { ApiError, isRecord, request } from './client';

// The customer's own quote (UC-03/04, EP-06/07; QuoteController). The card is rendered by the backend
// (AC-03.2): amounts are exact decimal strings, never turned into floating point here. The confirmation
// token belongs to the confirm control of the latest rendered card only: reading the quote again renders
// a new card with a new token (the old one stops working).

export const QUOTE_REF = /^BG-[A-HJ-NP-Z2-9]{8}$/;
const TOKEN = /^qc_[A-Za-z0-9_-]{22}$/;
const AMOUNT = /^\d{1,15}(\.\d{1,4})?$/;
const CURRENCY = /^[A-Z]{3}$/;

export const QUOTE_STATUSES = [
  'DRAFT', 'ISSUED', 'CONFIRMED', 'CONFIRMATION_BLOCKED', 'ORDER_SUBMITTING', 'ORDER_OUTCOME_UNKNOWN',
  'ORDER_FAILED', 'DRAFT_ORDER_CREATED', 'STAFF_APPROVED', 'REJECTED', 'EXPIRED', 'SUPERSEDED', 'CANCELLED',
] as const;
export type QuoteStatus = (typeof QUOTE_STATUSES)[number];

export interface QuoteLine {
  sku: string;
  quantity: number;
  unitPrice: string;
  lineTotal: string;
}

export interface QuoteCard {
  quoteRef: string;
  version: number;
  lines: QuoteLine[];
  total: string;
  currency: string;
  validUntil: Date;
  notes: string[];
  confirmationToken: string;
}

export interface QuoteOverview {
  quoteRef: string;
  version: number;
  status: QuoteStatus;
  /** Present only while the quote can still be confirmed (with a fresh token). */
  card: QuoteCard | null;
}

export interface Confirmation {
  quoteRef: string;
  version: number;
  status: QuoteStatus;
  handedOff: boolean;
}

const isVersion = (v: unknown): v is number => typeof v === 'number' && Number.isInteger(v) && v >= 1 && v <= 999_999;
const isText = (v: unknown, max: number): v is string => typeof v === 'string' && v.length > 0 && v.length <= max;

export function decodeLine(value: unknown): QuoteLine {
  if (isRecord(value) && isText(value.sku, 64) && typeof value.quantity === 'number' && Number.isInteger(value.quantity)
      && value.quantity >= 1 && value.quantity <= 9999 && typeof value.unitPrice === 'string' && AMOUNT.test(value.unitPrice)
      && typeof value.lineTotal === 'string' && AMOUNT.test(value.lineTotal)) {
    return { sku: value.sku, quantity: value.quantity, unitPrice: value.unitPrice, lineTotal: value.lineTotal };
  }
  throw new ApiError('invalid-response');
}

/** Fails closed: a card with any field this client cannot read is not shown at all. */
export function decodeCard(value: unknown): QuoteCard {
  if (!isRecord(value) || typeof value.quoteRef !== 'string' || !QUOTE_REF.test(value.quoteRef) || !isVersion(value.version)
      || !Array.isArray(value.lines) || value.lines.length === 0 || value.lines.length > 50
      || typeof value.total !== 'string' || !AMOUNT.test(value.total)
      || typeof value.currency !== 'string' || !CURRENCY.test(value.currency)
      || typeof value.validUntil !== 'string' || !Array.isArray(value.notes)
      || typeof value.confirmationToken !== 'string' || !TOKEN.test(value.confirmationToken)) {
    throw new ApiError('invalid-response');
  }
  const validUntil = new Date(value.validUntil);
  if (Number.isNaN(validUntil.getTime()) || !value.notes.every((n) => isText(n, 500)) || value.notes.length > 10) {
    throw new ApiError('invalid-response');
  }
  return {
    quoteRef: value.quoteRef,
    version: value.version,
    lines: value.lines.map(decodeLine),
    total: value.total,
    currency: value.currency,
    validUntil,
    notes: value.notes as string[],
    confirmationToken: value.confirmationToken,
  };
}

export function decodeStatus(value: unknown): QuoteStatus {
  if (typeof value === 'string' && (QUOTE_STATUSES as readonly string[]).includes(value)) {
    return value as QuoteStatus;
  }
  throw new ApiError('invalid-response');
}

export function decodeOverview(value: unknown): QuoteOverview {
  if (!isRecord(value) || typeof value.quoteRef !== 'string' || !QUOTE_REF.test(value.quoteRef) || !isVersion(value.version)) {
    throw new ApiError('invalid-response');
  }
  const card = value.quoteCard === null || value.quoteCard === undefined ? null : decodeCard(value.quoteCard);
  if (card && (card.quoteRef !== value.quoteRef || card.version !== value.version)) {
    throw new ApiError('invalid-response');
  }
  return { quoteRef: value.quoteRef, version: value.version, status: decodeStatus(value.status), card };
}

export function decodeConfirmation(value: unknown): Confirmation {
  if (isRecord(value) && typeof value.quoteRef === 'string' && QUOTE_REF.test(value.quoteRef) && isVersion(value.version)
      && typeof value.handedOff === 'boolean') {
    return { quoteRef: value.quoteRef, version: value.version, status: decodeStatus(value.status), handedOff: value.handedOff };
  }
  throw new ApiError('invalid-response');
}

export async function readQuote(quoteRef: string): Promise<QuoteOverview> {
  if (!QUOTE_REF.test(quoteRef)) throw new ApiError('invalid-response');
  return decodeOverview(await request(`/api/demo/quotes/${quoteRef}`));
}

/** The authenticated confirmation of one card (UX-05: never from chat text). */
export async function confirmQuote(card: QuoteCard): Promise<Confirmation> {
  const result = decodeConfirmation(await request(`/api/demo/quotes/${card.quoteRef}/confirm`, {
    method: 'POST',
    json: { version: card.version, confirmationToken: card.confirmationToken },
    timeoutMs: 60_000,
  }));
  // An answer about another quote or version is not an answer to this request (review M-2): the outcome
  // is unknown, never applied to the card on screen.
  if (result.quoteRef !== card.quoteRef || result.version !== card.version) {
    throw new ApiError('invalid-response');
  }
  return result;
}

/** ORDER_SUBMITTING / ORDER_OUTCOME_UNKNOWN: the order request was sent and its result is not known yet. */
export const isPendingOutcome = (status: QuoteStatus) => status === 'ORDER_SUBMITTING' || status === 'ORDER_OUTCOME_UNKNOWN';
