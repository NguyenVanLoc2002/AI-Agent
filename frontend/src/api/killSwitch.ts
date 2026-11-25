import { ApiError, isRecord, request } from './client';

// UC-08 AI kill switch, `/api/ops/kill-switch` (EP-09a, A-44): OPS only, checked by the backend at the
// URL and in the service. Body `{aiEnabled, reason, since}`; `reason` is null while the AI is on.

export const DISABLED_REASONS = ['OPERATOR', 'PROVIDER_AUTH', 'UNREADABLE'] as const;
export type DisabledReason = (typeof DISABLED_REASONS)[number] | 'UNKNOWN';

export interface SwitchState {
  aiEnabled: boolean;
  /** Why the AI is off; null while it is on. A reason this client does not know is UNKNOWN. */
  reason: DisabledReason | null;
  since: Date | null;
}

/** Fails closed: a body this client cannot read is an invalid response (the screen then treats the AI as off). */
export function decodeSwitchState(value: unknown): SwitchState {
  if (!isRecord(value) || typeof value.aiEnabled !== 'boolean') {
    throw new ApiError('invalid-response');
  }
  let since: Date | null = null;
  if (typeof value.since === 'string') {
    const parsed = new Date(value.since);
    since = Number.isNaN(parsed.getTime()) ? null : parsed;
  }
  if (value.aiEnabled) {
    return { aiEnabled: true, reason: null, since };
  }
  const reason = typeof value.reason === 'string' && (DISABLED_REASONS as readonly string[]).includes(value.reason)
    ? (value.reason as DisabledReason)
    : 'UNKNOWN';
  return { aiEnabled: false, reason, since };
}

export async function readSwitch(): Promise<SwitchState> {
  return decodeSwitchState(await request('/api/ops/kill-switch'));
}

export async function setSwitch(enabled: boolean): Promise<SwitchState> {
  return decodeSwitchState(await request('/api/ops/kill-switch', { method: 'POST', json: { enabled } }));
}
