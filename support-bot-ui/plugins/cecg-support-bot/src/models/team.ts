export enum TeamType {
    tenant = "tenant",
    firstLineSupport = "support",
    secondLineSupport = "secondLineSupport",
    leadership = "leadership",
}

export interface Team {
    name: string;
    type: TeamType;
}

export function cleanTeamName(name: string): string {
    if (!name) return "";
    return name
            .replace(/-([a-z])/g, (_, letter) => " " + letter.toUpperCase())
            .split(' ')
            .map(word => word.charAt(0).toUpperCase() + word.slice(1))
            .join(' ');
}
