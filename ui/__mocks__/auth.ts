// Mock for auth in tests
export const auth = jest.fn(() => Promise.resolve(null));
export const signIn = jest.fn();
export const signOut = jest.fn();
