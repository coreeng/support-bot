export const normalizeTeamKey = (value?: string | null): string =>
    (value || '').trim().toLowerCase().replace(/[\s_-]+/g, '')
