export enum TeamType {
    tenant = "tenant",
    firstLineSupport = "support",
    secondLineSupport = "l2Support",
    leadership = "leadership",
}

export function isSupportTeamType(type: TeamType): boolean {
    return type === TeamType.firstLineSupport || type === TeamType.secondLineSupport;
}

export interface Team {
    name: string;
    types: TeamType[];
}

export function isSupportTeam(team: Team): boolean {
    return team.types.findIndex((t, _) => isSupportTeamType(t)) !== -1;
}

export function cleanTeamName(name: string): string {
    if (!name) return "";
    return name
            .replace(/-([a-z])/g, (_, letter) => ` ${letter.toUpperCase()}`)
            .split(' ')
            .map(word => word.charAt(0).toUpperCase() + word.slice(1))
            .join(' ');
}
