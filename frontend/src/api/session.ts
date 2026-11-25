import { ApiError, isRecord, request } from './client';

// Session endpoints of the backend (ADR-010, SessionController and SecurityConfiguration).
// The role decides which screens the shell offers; that is presentation only, the backend checks the
// role, the customer link and the resource on every call.

export const ROLES = ['CUST', 'SALES', 'OPS', 'SECREV'] as const;
export type Role = (typeof ROLES)[number];

export interface Session {
  role: Role;
  customerLinked: boolean;
}

/** Fails closed: an unknown role or a malformed body is treated as an invalid response. */
export function decodeSession(value: unknown): Session {
  if (isRecord(value) && typeof value.role === 'string' && (ROLES as readonly string[]).includes(value.role)
      && typeof value.customerLinked === 'boolean') {
    return { role: value.role as Role, customerLinked: value.customerLinked };
  }
  throw new ApiError('invalid-response');
}

/** The current session, or null when there is none (401). Other errors are thrown. */
export async function currentSession(): Promise<Session | null> {
  try {
    return decodeSession(await request('/api/auth/session'));
  } catch (error) {
    if (error instanceof ApiError && error.unauthenticated) {
      return null;
    }
    throw error;
  }
}

export async function login(username: string, password: string): Promise<Session> {
  return decodeSession(await request('/api/auth/login', { method: 'POST', form: { username, password } }));
}

export async function logout(): Promise<void> {
  await request('/api/auth/logout', { method: 'POST' });
}
