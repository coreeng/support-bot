import "@testing-library/jest-dom";

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

// Mock NextAuth
jest.mock('next-auth/react', () => ({
    useSession: jest.fn(() => ({
        data: {
            user: {
                email: 'test@company.com',
                name: 'Test User',
                team: 'test-team',
                role: 'member',
            },
        },
        status: 'authenticated',
    })),
    SessionProvider: ({ children }: { children: React.ReactNode }) => children,
    signIn: jest.fn(),
    signOut: jest.fn(),
}));

// Mock Next.js server-side utilities for client-side tests
jest.mock('next/server', () => ({
    NextResponse: {
        json: jest.fn((data) => ({
            status: 200,
            json: async () => data,
        })),
    },
}));

// Mock global Request if not available (jsdom doesn't provide it)
if (typeof global.Request === 'undefined') {
    (global as any).Request = class MockRequest {
        url: string;
        method: string;
        headers: Map<string, string>;
        
        constructor(input: string | { url: string }, init?: { method?: string; headers?: Record<string, string> }) {
            this.url = typeof input === 'string' ? input : input.url;
            this.method = init?.method || 'GET';
            this.headers = new Map(Object.entries(init?.headers || {}));
        }
    };
}

// Mock next-auth/jwt to avoid ESM issues in Jest
jest.mock('next-auth/jwt', () => ({
  getToken: jest.fn(),
}))
