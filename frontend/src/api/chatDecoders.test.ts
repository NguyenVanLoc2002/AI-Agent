import { describe, expect, it } from 'vitest';
import { formatMoney } from '../app/money';
import { decodeMessages, decodeTurn } from './chat';
import { decodeCard, decodeConfirmation, decodeOverview } from './quotes';

// Synthetic values only.
const CARD = {
  quoteRef: 'BG-ABCDEFGH', version: 1, currency: 'VND', validUntil: '2026-11-26T09:41:00Z',
  confirmationToken: 'qc_1111111111111111111111', total: '2500000', notes: ['Giá chưa gồm VAT'],
  lines: [{ sku: 'SKU-BRK-PAD-A', quantity: 2, unitPrice: '1250000', lineTotal: '2500000' }],
};

describe('decodeCard', () => {
  it('reads a card the backend renders', () => {
    expect(decodeCard(CARD)).toMatchObject({ quoteRef: 'BG-ABCDEFGH', version: 1, total: '2500000', confirmationToken: 'qc_1111111111111111111111' });
  });

  it.each<[string, Record<string, unknown>]>([
    ['a token of another shape', { confirmationToken: 'qh_1111111111111111111111' }],
    ['a missing token', { confirmationToken: undefined }],
    ['a reference of another shape', { quoteRef: 'BG-0000O000' }],
    ['an amount in exponent form', { total: '2.5e6' }],
    ['a negative amount', { total: '-1' }],
    ['a currency of another shape', { currency: 'vnd' }],
    ['no line', { lines: [] }],
    ['a line with a fractional quantity', { lines: [{ ...CARD.lines[0], quantity: 1.5 }] }],
    ['a line with a numeric price', { lines: [{ ...CARD.lines[0], unitPrice: 1250000 }] }],
    ['an unreadable validity', { validUntil: 'tomorrow' }],
    ['a note that is not text', { notes: [42] }],
    ['version 0', { version: 0 }],
  ])('refuses %s', (_label, change) => {
    expect(() => decodeCard({ ...CARD, ...change })).toThrow();
  });
});

describe('decodeTurn', () => {
  it('keeps the answer and marks a card it cannot read instead of failing the turn', () => {
    const turn = decodeTurn({ turnId: 't', reply: 'Xin chào', handedOff: false, duplicate: false, quoteCard: { ...CARD, total: 'x' } });
    expect(turn).toMatchObject({ reply: 'Xin chào', card: null, cardUnreadable: true });
  });

  it('fails closed on a turn without its flags', () => {
    expect(() => decodeTurn({ reply: 'Xin chào' })).toThrow();
  });
});

describe('decodeOverview and decodeConfirmation', () => {
  it('refuses a card that belongs to another reference or version', () => {
    expect(() => decodeOverview({ quoteRef: 'BG-ABCDEFGH', version: 2, status: 'ISSUED', quoteCard: CARD })).toThrow();
  });

  it.each(['ISSUED_SOON', 'issued', null])('refuses the status %j', (status) => {
    expect(() => decodeOverview({ quoteRef: 'BG-ABCDEFGH', version: 1, status, quoteCard: null })).toThrow();
    expect(() => decodeConfirmation({ quoteRef: 'BG-ABCDEFGH', version: 1, status, handedOff: false })).toThrow();
  });
});

describe('decodeMessages', () => {
  it('refuses an unknown author and a body that is not a list', () => {
    expect(() => decodeMessages([{ direction: 'ADMIN', text: 'x' }])).toThrow();
    expect(() => decodeMessages({ messages: [] })).toThrow();
  });
});

describe('formatMoney', () => {
  it.each([['2500000', 'VND', '2.500.000 ₫'], ['0', 'VND', '0 ₫'], ['999', 'VND', '999 ₫'], ['1234.50', 'USD', '1.234,50 USD']])(
    'formats %s %s exactly', (amount, currency, expected) => {
      expect(formatMoney(amount, currency)).toBe(expected);
    });

  it('keeps every digit of a large amount (no floating point)', () => {
    expect(formatMoney('123456789012345', 'VND')).toBe('123.456.789.012.345 ₫');
  });
});
