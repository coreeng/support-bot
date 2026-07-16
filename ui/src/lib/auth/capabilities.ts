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
 * Converts untrusted session/backend role data into the string-array shape
 * consumed by the UI. Malformed values fail closed instead of reaching array
 * operations in render paths.
 */
export function normalizeBackendRoles(roles: unknown): string[] {
  if (!Array.isArray(roles)) return [];
  return roles.filter((role): role is string => typeof role === "string");
}

/**
 * Derives a UI capability from the roles issued by the backend in the session.
 * Unknown and differently-cased roles deliberately fail closed.
 */
export function hasUiCapability(roles: unknown, capability: UiCapability): boolean {
  const normalizedRoles = normalizeBackendRoles(roles);
  const permittedRoles = ROLES_BY_CAPABILITY[capability];
  return normalizedRoles.some((role) => isBackendRole(role) && permittedRoles.has(role));
}
