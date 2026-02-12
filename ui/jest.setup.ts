import "@testing-library/jest-dom";

// Polyfills for Node.js environment
if (typeof TextEncoder === "undefined") {
  const { TextEncoder, TextDecoder } = require("util");
  global.TextEncoder = TextEncoder;
  global.TextDecoder = TextDecoder;
}

if (typeof ReadableStream === "undefined") {
  const { ReadableStream } = require("stream/web");
  global.ReadableStream = ReadableStream;
}

// Mock next-auth/react globally to avoid ESM import issues
jest.mock("next-auth/react", () => ({
    useSession: jest.fn(() => ({
        data: null,
        status: "unauthenticated",
    })),
    signIn: jest.fn(),
    signOut: jest.fn(),
    getSession: jest.fn(() => Promise.resolve(null)),
    SessionProvider: ({ children }: { children: React.ReactNode }) => children,
}));

// Mock next-auth to avoid ESM import issues
jest.mock("next-auth", () => {
    const mockNextAuth = jest.fn(() => ({
        handlers: { GET: jest.fn(), POST: jest.fn() },
        auth: jest.fn(() => Promise.resolve(null)),
        signIn: jest.fn(),
        signOut: jest.fn(),
    }));
    return { default: mockNextAuth, __esModule: true };
});

// Mock next-auth providers
jest.mock("next-auth/providers/credentials", () => {
    const mockCredentials = jest.fn((config) => ({
        id: config?.id || "credentials",
        name: config?.name || "Credentials",
        type: "credentials",
        credentials: config?.credentials || {},
        authorize: config?.authorize || jest.fn(),
    }));
    return { default: mockCredentials, __esModule: true };
});

// Mock next/cache to avoid Node.js Request API issues
jest.mock("next/cache", () => ({
    revalidateTag: jest.fn(),
    revalidatePath: jest.fn(),
    unstable_cache: jest.fn((fn) => fn),
}));

// Mock Request and Headers for server actions
if (typeof Request === "undefined") {
    global.Request = class Request {
        constructor(public url: string, public init?: any) {}
    } as any;
}

if (typeof Headers === "undefined") {
    global.Headers = class Headers {
        private headers: Map<string, string> = new Map();
        set(name: string, value: string) {
            this.headers.set(name, value);
        }
        get(name: string) {
            return this.headers.get(name);
        }
        has(name: string) {
            return this.headers.has(name);
        }
    } as any;
}

// Mock matchMedia for Radix UI components
Object.defineProperty(window, "matchMedia", {
    writable: true,
    value: jest.fn().mockImplementation((query) => ({
        matches: false,
        media: query,
        onchange: null,
        addListener: jest.fn(),
        removeListener: jest.fn(),
        addEventListener: jest.fn(),
        removeEventListener: jest.fn(),
        dispatchEvent: jest.fn(),
    })),
});
