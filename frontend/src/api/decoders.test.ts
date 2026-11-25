import { describe, expect, it } from 'vitest';
import { decodeSwitchState } from './killSwitch';
import { decodeSession } from './session';

describe('decodeSession', () => {
  it('accepts the four roles of RBAC_MATRIX', () => {
    for (const role of ['CUST', 'SALES', 'OPS', 'SECREV']) {
      expect(decodeSession({ role, customerLinked: false }).role).toBe(role);
    }
  });

  it.each([
    [{ role: 'ADMIN', customerLinked: false }],
    [{ role: 'ops', customerLinked: false }],
    [{ role: 'OPS' }],
    [{ role: 'OPS', customerLinked: 'true' }],
    [null],
    [['OPS']],
  ])('fails closed on %j', (value) => {
    expect(() => decodeSession(value)).toThrow();
  });
});

describe('decodeSwitchState', () => {
  it('reads the off state with its reason and time', () => {
    expect(decodeSwitchState({ aiEnabled: false, reason: 'PROVIDER_AUTH', since: '2026-11-23T08:40:00Z' }))
      .toEqual({ aiEnabled: false, reason: 'PROVIDER_AUTH', since: new Date('2026-11-23T08:40:00Z') });
  });

  it('keeps an unknown reason as UNKNOWN rather than dropping the off state', () => {
    expect(decodeSwitchState({ aiEnabled: false, reason: 'SOMETHING_NEW', since: null }).reason).toBe('UNKNOWN');
    expect(decodeSwitchState({ aiEnabled: false }).reason).toBe('UNKNOWN');
  });

  it('ignores a reason while the AI is on and an unreadable time', () => {
    expect(decodeSwitchState({ aiEnabled: true, reason: 'OPERATOR', since: 'yesterday' }))
      .toEqual({ aiEnabled: true, reason: null, since: null });
  });

  it.each([[{ aiEnabled: 'true' }], [{}], [null], ['on']])('fails closed on %j', (value) => {
    expect(() => decodeSwitchState(value)).toThrow();
  });
});
