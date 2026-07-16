export const BACKEND_ROLES = {
  USER: "USER",
  LEADERSHIP: "LEADERSHIP",
  SUPPORT_ENGINEER: "SUPPORT_ENGINEER",
  ESCALATION: "ESCALATION",
} as const;

export type BackendRole = (typeof BACKEND_ROLES)[keyof typeof BACKEND_ROLES];

export const UI_CAPABILITIES = {
  VIEW_RESTRICTED_DASHBOARDS: "view-restricted-dashboards",
} as const;

export type UiCapability = (typeof UI_CAPABILITIES)[keyof typeof UI_CAPABILITIES];

const KNOWN_BACKEND_ROLES: ReadonlySet<string> = new Set(Object.values(BACKEND_ROLES));

const ROLES_BY_CAPABILITY: Readonly<Record<UiCapability, ReadonlySet<BackendRole>>> = {
  [UI_CAPABILITIES.VIEW_RESTRICTED_DASHBOARDS]: new Set([BACKEND_ROLES.LEADERSHIP, BACKEND_ROLES.SUPPORT_ENGINEER]),
};

function isBackendRole(role: string): role is BackendRole {
  return KNOWN_BACKEND_ROLES.has(role);
}

/**
 * Derives a UI capability from the roles issued by the backend in the session.
 * Unknown and differently-cased roles deliberately fail closed.
 */
export function hasUiCapability(roles: readonly string[] | null | undefined, capability: UiCapability): boolean {
  if (!roles) return false;
  const permittedRoles = ROLES_BY_CAPABILITY[capability];
  return roles.some((role) => isBackendRole(role) && permittedRoles.has(role));
}
