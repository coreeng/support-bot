export function getRandom<T>(arr: T[]): T;
export function getRandom<T>(arr: T[], n: number, distinct?: boolean): T[];
export function getRandom<T>(arr: T[], n: number = 1, distinct: boolean = false): T | T[] {
    if (n === 1) {
        return arr[Math.floor(Math.random() * arr.length)] as T;
    }
    let result = [];
    let items = [...arr];
    for (let i = 0; i < n; i++) {
        if (distinct && arr.length > n) {
            const randomItem = items[Math.floor(Math.random() * items.length)];
            items = items.filter(item => item !== randomItem);
            result.push(randomItem);
        } else {
            result.push(arr[Math.floor(Math.random() * arr.length)]);
        }
    }
    return result as T[];
}
