import type { Role } from '../api/session';

// FE-UI-P2 (A-80: D01 hash routes, D02 react-router-dom HashRouter, D04 no conversation or order ID in the URL).
// Which screen a role is offered is presentation only: the backend checks the role, the team and the resource on
// every call (CLAUDE.md, rule 10). Labels, groups and icons come from the approved batch F artboards
// (docs/design/prototypes/F2-Inbox, F4-Inspector, F5-KillSwitch, F1-Chat; docs/design/specs/50).

export type ScreenKey = 'inbox' | 'kill-switch' | 'inspector' | 'chat';

export interface ScreenRoute {
  key: ScreenKey;
  path: `/${string}`;
  role: Role;
  /** Sidebar section title and first breadcrumb level (artboard `sidebar[].title`, `crumbs[0]`). */
  group: string;
  /** Sidebar item, last breadcrumb level and document title. */
  label: string;
  /** Lucide name for the sidebar; null for the customer, who has no sidebar (F1). */
  icon: string | null;
}

export const SCREEN_ROUTES: readonly ScreenRoute[] = [
  { key: 'inbox', path: '/hop-cho', role: 'SALES', group: 'Kinh doanh', label: 'Hộp chờ nhân viên', icon: 'users' },
  { key: 'kill-switch', path: '/cong-tac-ai', role: 'OPS', group: 'Vận hành', label: 'Công tắc AI', icon: 'sliders-horizontal' },
  { key: 'inspector', path: '/su-kien-bao-mat', role: 'SECREV', group: 'Kiểm tra bảo mật', label: 'Sự kiện bảo mật', icon: 'shield-check' },
  { key: 'chat', path: '/chat', role: 'CUST', group: 'Khách hàng', label: 'Trò chuyện', icon: null },
];

export const APP_NAME = 'AutoParts Agent Sandbox';

export function routesFor(role: Role): ScreenRoute[] {
  return SCREEN_ROUTES.filter((r) => r.role === role);
}

/** The screen a role lands on after sign-in and on `/`. Every role has exactly one in this release. */
export function homeRoute(role: Role): ScreenRoute {
  const route = routesFor(role)[0];
  if (!route) throw new Error(`no screen for role ${role}`);
  return route;
}

export type Resolution =
  | { kind: 'home' }
  | { kind: 'screen'; route: ScreenRoute }
  /** A screen of another role: shown as "no access" without calling its API. */
  | { kind: 'forbidden' }
  | { kind: 'not-found' };

export function resolve(pathname: string, role: Role): Resolution {
  if (pathname === '/' || pathname === '') return { kind: 'home' };
  const route = SCREEN_ROUTES.find((r) => r.path === pathname);
  if (!route) return { kind: 'not-found' };
  return route.role === role ? { kind: 'screen', route } : { kind: 'forbidden' };
}

export function documentTitle(label: string | null): string {
  return label ? `${label} · ${APP_NAME}` : APP_NAME;
}
