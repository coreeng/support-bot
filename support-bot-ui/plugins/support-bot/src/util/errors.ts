export function wrapError(value: unknown, message: string): Error {
    if (value instanceof Error) {
        return new Error(`${message}: ${value.message}`, {cause: value});
    }

    let stringified;
    try {
        stringified = JSON.stringify(value)
    } catch (e) {
        stringified = '[Unable to stringify the thrown value]'
    }
    return new Error(`${message}: error as raw value: ${stringified}`)
}