import { ApiError, isRecord, request } from './client';

// UC-07 Security Inspector (EP-09b, A-45; SecurityInspectorController, profile demo). SECREV only, checked
// by the backend at the URL and in the service; every read and every refusal is audited (INSPECTOR_READ,
// ACCESS_DENIED). The backend sends metadata only: codes, field names, tool names, roles, staff pseudonyms
// and trace IDs, never a message, a price, a value or a customer, conversation or quote identifier
// (decision 6A, RBAC_MATRIX EP-09b). This client fails closed on anything else: a field it does not know,
// a value outside the backend's own formats, or a customer pseudonym (A-70).

/** Audit actions (AuditEvent.Action) and the kill switch pseudo-action, in the backend's order. */
export const EVENT_TYPES = [
  'ACCESS_DENIED', 'HANDOFF_CLAIMED', 'HANDOFF_RESOLVED', 'STAFF_MESSAGE_POSTED', 'STAFF_QUOTE_READ',
  'STAFF_ORDER_DECIDED', 'ORDER_RECONCILED', 'QUOTE_CONFIRMATION', 'INSPECTOR_READ', 'STAFF_CONVERSATION_READ',
  'STAFF_LIST_READ', 'KILL_SWITCH_CHANGED',
] as const;
export type EventType = (typeof EVENT_TYPES)[number];

export const ACTOR_ROLES = ['CUST', 'SALES', 'OPS', 'SECREV'] as const;
export type ActorRole = (typeof ACTOR_ROLES)[number];

/** Window of one events query, at most 7 days (decision 7A of EP-09b). */
export const MAX_WINDOW_MS = 7 * 24 * 60 * 60 * 1000;
/** The backend's page size bounds (SecurityInspector.DEFAULT_EVENTS / MAX_EVENTS). */
export const PAGE_SIZE = 50;
const MAX_EVENTS = 100;
/** Rows per section of a trace (SecurityInspector.MAX_TRACE_ROWS). */
export const MAX_TRACE_ROWS = 200;

export const TRACE_ID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const CODE = /^[A-Z][A-Z_]{1,39}$/;
const PSEUDONYM = /^usr_[A-Za-z0-9_-]{22}$/;
const CURSOR = /^\d{1,12}\.\d{9}[AK]\d{1,18}$/;
const HONEYTOKEN_ID = /^HT-[A-Z0-9-]{2,24}$/;
const FIELD_NAME = /^[A-Za-z][A-Za-z0-9_]{0,63}$/;
const FIELD_PATH = /^\$(?:\.[A-Za-z][A-Za-z0-9_]{0,63}|\[\d{1,4}\]){0,16}$/;
const TOOL_NAME = /^[A-Za-z][A-Za-z0-9_]{0,39}$/;
/** An `Instant` as Jackson writes it (UTC, ISO-8601); any other date text is refused (security review L-5). */
const INSTANT = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d{1,9})?Z$/;

export interface SecurityEvent {
  action: string;
  resource: string;
  outcome: string;
  /** Null for the system (the provider-auth breaker). */
  actorRole: ActorRole | null;
  /** Pseudonym of a staff actor; always null for a customer and for the system. */
  actor: string | null;
  traceId: string | null;
  occurredAt: Date;
  /** Identical events of the same minute shown as one row (AUDIT-FLOOD); 1 otherwise. */
  occurrences: number;
  lastOccurredAt: Date | null;
  lastTraceId: string | null;
}

export interface EventPage {
  events: SecurityEvent[];
  /** Cursor of the next page; null when no further event exists. */
  next: string | null;
}

export interface EgressCheck {
  decision: 'PASS' | 'BLOCK';
  attempt: number;
  policyCode: string | null;
  honeytokenId: string | null;
  blockedFieldPath: string | null;
  fieldNames: string[];
  byteLength: number;
  occurredAt: Date;
  /** REQUEST, or RESPONSE for a model answer the gateway withheld (A-59). */
  direction: 'REQUEST' | 'RESPONSE';
}

export interface ToolCallRecord {
  toolName: string;
  decision: 'ADMITTED' | 'INVOKED' | 'REJECTED' | 'FILTERED';
  reasonCode: string | null;
  occurredAt: Date;
}

export interface Trace {
  traceId: string;
  egress: EgressCheck[];
  tools: ToolCallRecord[];
  events: SecurityEvent[];
  /** A section had more than MAX_TRACE_ROWS rows and was cut by the backend. */
  truncated: boolean;
}

export interface EventQuery {
  type: EventType | null;
  role: ActorRole | null;
  from: Date;
  to: Date;
}

const EVENT_KEYS = ['action', 'resource', 'outcome', 'actorRole', 'actor', 'traceId', 'occurredAt', 'occurrences', 'lastOccurredAt', 'lastTraceId'];
const PAGE_KEYS = ['events', 'next'];
const EGRESS_KEYS = ['decision', 'attempt', 'policyCode', 'honeytokenId', 'blockedFieldPath', 'fieldNames', 'byteLength', 'occurredAt', 'direction'];
const TOOL_KEYS = ['toolName', 'decision', 'reasonCode', 'occurredAt'];
const TRACE_KEYS = ['traceId', 'egress', 'tools', 'events', 'truncated'];

const invalid = (): never => { throw new ApiError('invalid-response'); };
const onlyKnownKeys = (value: Record<string, unknown>, keys: string[]) => Object.keys(value).every((k) => keys.includes(k));
const absent = (value: unknown) => value === null || value === undefined;

function record(value: unknown, keys: string[]): Record<string, unknown> {
  return isRecord(value) && onlyKnownKeys(value, keys) ? value : invalid();
}

/** A string matching the pattern, or null when the field is null or missing. */
function optional(value: unknown, pattern: RegExp): string | null {
  if (absent(value)) return null;
  return typeof value === 'string' && pattern.test(value) ? value : invalid();
}

function required(value: unknown, pattern: RegExp): string {
  return typeof value === 'string' && pattern.test(value) ? value : invalid();
}

function instant(value: unknown): Date {
  if (typeof value !== 'string' || !INSTANT.test(value)) return invalid();
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? invalid() : parsed;
}

function integer(value: unknown, min: number, max: number): number {
  return typeof value === 'number' && Number.isSafeInteger(value) && value >= min && value <= max ? value : invalid();
}

function oneOf<T extends string>(value: unknown, allowed: readonly T[]): T {
  return typeof value === 'string' && (allowed as readonly string[]).includes(value) ? value as T : invalid();
}

function list(value: unknown, max: number): unknown[] {
  return Array.isArray(value) && value.length <= max ? value : invalid();
}

export function decodeEvent(value: unknown): SecurityEvent {
  const v = record(value, EVENT_KEYS);
  const actorRole = absent(v.actorRole) ? null : oneOf(v.actorRole, ACTOR_ROLES);
  const actor = optional(v.actor, PSEUDONYM);
  // A customer is shown by role only (decision 6A), and a pseudonym without a role is not a staff member.
  if (actor !== null && (actorRole === null || actorRole === 'CUST')) invalid();
  const occurrences = integer(v.occurrences, 1, 1_000_000_000);
  const lastOccurredAt = absent(v.lastOccurredAt) ? null : instant(v.lastOccurredAt);
  return {
    action: required(v.action, CODE),
    resource: required(v.resource, CODE),
    outcome: required(v.outcome, CODE),
    actorRole,
    actor,
    traceId: optional(v.traceId, TRACE_ID),
    occurredAt: instant(v.occurredAt),
    occurrences,
    lastOccurredAt,
    lastTraceId: optional(v.lastTraceId, TRACE_ID),
  };
}

export function decodeEventPage(value: unknown): EventPage {
  const v = record(value, PAGE_KEYS);
  return { events: list(v.events, MAX_EVENTS).map(decodeEvent), next: optional(v.next, CURSOR) };
}

function decodeEgress(value: unknown): EgressCheck {
  const v = record(value, EGRESS_KEYS);
  const decision = oneOf(v.decision, ['PASS', 'BLOCK'] as const);
  const check: EgressCheck = {
    decision,
    attempt: integer(v.attempt, 0, 9),
    policyCode: optional(v.policyCode, CODE),
    honeytokenId: optional(v.honeytokenId, HONEYTOKEN_ID),
    blockedFieldPath: optional(v.blockedFieldPath, FIELD_PATH),
    fieldNames: list(v.fieldNames, 64).map((name) => required(name, FIELD_NAME)),
    byteLength: integer(v.byteLength, 0, 2_147_483_647),
    occurredAt: instant(v.occurredAt),
    direction: oneOf(v.direction, ['REQUEST', 'RESPONSE'] as const),
  };
  // The same rules as EgressDecisionEvent and the egress_record check constraints (V2, V3, V19; security
  // review L-3): a pass has no finding and attempt 1..9; a blocked request has a finding and attempt 0; a
  // withheld answer (RESPONSE) is always a block, with a finding and the attempt 1..9 it answered.
  const finding = check.policyCode !== null && check.blockedFieldPath !== null;
  const noFinding = check.policyCode === null && check.honeytokenId === null && check.blockedFieldPath === null;
  const valid = check.direction === 'RESPONSE'
    ? decision === 'BLOCK' && finding && check.attempt >= 1
    : decision === 'PASS' ? noFinding && check.attempt >= 1 : finding && check.attempt === 0;
  return valid ? check : invalid();
}

function decodeTool(value: unknown): ToolCallRecord {
  const v = record(value, TOOL_KEYS);
  return {
    toolName: required(v.toolName, TOOL_NAME),
    decision: oneOf(v.decision, ['ADMITTED', 'INVOKED', 'REJECTED', 'FILTERED'] as const),
    reasonCode: optional(v.reasonCode, CODE),
    occurredAt: instant(v.occurredAt),
  };
}

export function decodeTrace(value: unknown, traceId: string): Trace {
  const v = record(value, TRACE_KEYS);
  if (v.traceId !== traceId || typeof v.truncated !== 'boolean') invalid();
  return {
    traceId,
    egress: list(v.egress, MAX_TRACE_ROWS).map(decodeEgress),
    tools: list(v.tools, MAX_TRACE_ROWS).map(decodeTool),
    events: list(v.events, MAX_TRACE_ROWS).map(decodeEvent),
    truncated: v.truncated as boolean,
  };
}

/** Reads one page of events, newest first. `before` is the `next` cursor of the previous page. */
export async function readEvents(query: EventQuery, before: string | null = null): Promise<EventPage> {
  const params = new URLSearchParams();
  if (query.type !== null) params.set('type', oneOf(query.type, EVENT_TYPES));
  if (query.role !== null) params.set('role', oneOf(query.role, ACTOR_ROLES));
  params.set('from', query.from.toISOString());
  params.set('to', query.to.toISOString());
  params.set('limit', String(PAGE_SIZE));
  if (before !== null) params.set('before', required(before, CURSOR));
  return decodeEventPage(await request(`/api/demo/security/events?${params.toString()}`));
}

/** Reads one trace; the answer must be that trace. */
export async function readTrace(traceId: string): Promise<Trace> {
  const id = required(traceId, TRACE_ID);
  return decodeTrace(await request(`/api/demo/security/traces/${id}`), id);
}
