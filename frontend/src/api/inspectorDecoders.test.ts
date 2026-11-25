import { describe, expect, it } from 'vitest';
import { ApiError } from './client';
import { actorParts } from '../app/inspectorLabels';
import { decodeEvent, decodeEventPage, decodeTrace } from './inspector';

// Fail-closed decoding of the Security Inspector answers (A-70). Synthetic codes and pseudonyms only.
const TRACE = '7c1e2f00-0000-4000-8000-00000000a904';
const STAFF = 'usr_AAAAAAAAAAAAAAAAAAAAAA';

const event = (extra: Record<string, unknown> = {}) => ({
  action: 'HANDOFF_CLAIMED', resource: 'HANDOFF', outcome: 'CLAIMED', actorRole: 'SALES', actor: STAFF, traceId: TRACE,
  occurredAt: '2026-11-23T08:05:00Z', occurrences: 1, lastOccurredAt: null, lastTraceId: null, ...extra,
});
const egress = (extra: Record<string, unknown> = {}) => ({
  decision: 'BLOCK', attempt: 0, policyCode: 'HONEYTOKEN', honeytokenId: 'HT-03', blockedFieldPath: '$.messages[2].content',
  fieldNames: ['messages', 'tools'], byteLength: 1284, occurredAt: '2026-11-23T08:58:00Z', direction: 'REQUEST', ...extra,
});
const trace = (extra: Record<string, unknown> = {}) => ({ traceId: TRACE, egress: [egress()], tools: [], events: [], truncated: false, ...extra });

const refused = (fn: () => unknown) => expect(fn).toThrow(ApiError);

describe('security events', () => {
  it('reads a staff event, and nullable fields that are missing', () => {
    expect(decodeEvent(event())).toMatchObject({ actorRole: 'SALES', actor: STAFF, traceId: TRACE, occurrences: 1 });
    const { lastOccurredAt: _a, lastTraceId: _b, actor: _c, ...short } = event({ actorRole: 'CUST' });
    expect(decodeEvent(short)).toMatchObject({ actorRole: 'CUST', actor: null, lastTraceId: null });
  });

  it('refuses a customer pseudonym and a pseudonym without a role (decision 6A)', () => {
    refused(() => decodeEvent(event({ actorRole: 'CUST' })));
    refused(() => decodeEvent(event({ actorRole: null })));
  });

  it('refuses a field it does not know and values outside the backend formats', () => {
    refused(() => decodeEvent(event({ conversationId: 'cv_AAAAAAAAAAAAAAAAAAAAAA' })));
    refused(() => decodeEvent(event({ outcome: 'Không tìm thấy' })));
    refused(() => decodeEvent(event({ action: '<img src=x>' })));
    refused(() => decodeEvent(event({ actorRole: 'ADMIN' })));
    refused(() => decodeEvent(event({ actor: 'someone@example.test' })));
    refused(() => decodeEvent(event({ traceId: 'not-a-trace' })));
    refused(() => decodeEvent(event({ occurrences: 0 })));
    refused(() => decodeEvent(event({ occurredAt: 'yesterday' })));
    refused(() => decodeEvent(event({ occurredAt: 'Nov 23 2026' })));
    refused(() => decodeEvent(event({ occurredAt: '2026' })));
    refused(() => decodeEvent(event({ lastOccurredAt: '2026-11-23 08:05:00' })));
  });

  it('never labels a customer with a pseudonym, even past the decoder (defence in depth)', () => {
    const decoded = decodeEvent(event());
    expect(actorParts({ ...decoded, actorRole: 'CUST' })).toEqual({ role: 'Khách hàng', pseudonym: null });
    expect(actorParts(decoded)).toEqual({ role: 'Nhân viên kinh doanh', pseudonym: STAFF });
    expect(actorParts({ ...decoded, actorRole: null, actor: null })).toEqual({ role: 'Hệ thống', pseudonym: null });
  });

  it('reads a page and refuses an oversized page or a malformed cursor', () => {
    expect(decodeEventPage({ events: [event()], next: '1795580419.000000000A42' }).next).toBe('1795580419.000000000A42');
    refused(() => decodeEventPage({ events: Array.from({ length: 101 }, () => event()), next: null }));
    refused(() => decodeEventPage({ events: [], next: '../../admin' }));
    refused(() => decodeEventPage({ events: [], next: null, total: 3 }));
  });
});

describe('security traces', () => {
  it('reads a trace with a blocked egress check', () => {
    expect(decodeTrace(trace(), TRACE).egress[0]).toMatchObject({ decision: 'BLOCK', honeytokenId: 'HT-03' });
    expect(decodeTrace(trace({ egress: [egress({ direction: 'RESPONSE', attempt: 2 })] }), TRACE).egress[0]).toMatchObject({ direction: 'RESPONSE', attempt: 2 });
    expect(decodeTrace(trace({ egress: [egress({ decision: 'PASS', attempt: 1, policyCode: null, honeytokenId: null, blockedFieldPath: null, occurredAt: '2026-11-23T08:58:00.123456Z' })] }), TRACE).egress).toHaveLength(1);
  });

  it('refuses a trace other than the one asked for', () => {
    refused(() => decodeTrace(trace({ traceId: '00000000-0000-4000-8000-000000000000' }), TRACE));
  });

  it('refuses egress rows that break the egress_record rules or carry a value', () => {
    refused(() => decodeTrace(trace({ egress: [egress({ decision: 'PASS', attempt: 1 })] }), TRACE));
    refused(() => decodeTrace(trace({ egress: [egress({ decision: 'PASS', attempt: 0, policyCode: null, honeytokenId: null, blockedFieldPath: null })] }), TRACE));
    refused(() => decodeTrace(trace({ egress: [egress({ attempt: 1 })] }), TRACE));
    refused(() => decodeTrace(trace({ egress: [egress({ direction: 'RESPONSE', attempt: 0 })] }), TRACE));
    refused(() => decodeTrace(trace({ egress: [egress({ direction: 'RESPONSE', decision: 'PASS', attempt: 1, policyCode: null, honeytokenId: null, blockedFieldPath: null })] }), TRACE));
    refused(() => decodeTrace(trace({ egress: [egress({ direction: 'RESPONSE', decision: 'PASS', attempt: 1 })] }), TRACE));
    refused(() => decodeTrace(trace({ egress: [egress({ blockedFieldPath: null })] }), TRACE));
    refused(() => decodeTrace(trace({ egress: [egress({ fieldNames: ['0900000000'] })] }), TRACE));
    refused(() => decodeTrace(trace({ egress: [egress({ blockedFieldPath: 'messages[2] = "giá 1.290.000"' })] }), TRACE));
    refused(() => decodeTrace(trace({ egress: [egress({ value: 'secret' })] }), TRACE));
  });

  it('refuses unknown tool decisions and a section longer than 200 rows', () => {
    refused(() => decodeTrace(trace({ tools: [{ toolName: 'searchParts', decision: 'APPROVED', reasonCode: null, occurredAt: '2026-11-23T08:58:00Z' }] }), TRACE));
    refused(() => decodeTrace(trace({ events: Array.from({ length: 201 }, () => event()) }), TRACE));
  });
});
