import { NextRequest } from 'next/server';
import { getToken } from 'next-auth/jwt';

export interface ProxyAuthToken {
    email: string | null;
    isLeadership?: boolean;
    isSupportEngineer?: boolean;
}

/**
 * Authenticate request and extract user token
 * Handles both production JWT validation and test mode bypass
 */
export async function authenticateProxyRequest(
    request: NextRequest
): Promise<ProxyAuthToken | null> {
    // TEST MODE: Bypass JWT validation for functional tests
    // SECURITY: Requires explicit opt-in via ENABLE_TEST_AUTH_BYPASS
    if (isTestMode()) {
        return authenticateTestMode(request);
    }

    // PRODUCTION/DEV: Validate real JWT token
    return authenticateProduction(request);
}

/**
 * Check if running in test mode with auth bypass enabled
 */
function isTestMode(): boolean {
    return (
        process.env.NODE_ENV === 'test' &&
        process.env.ENABLE_TEST_AUTH_BYPASS === 'true'
    );
}

/**
 * Test mode authentication - requires session cookie, returns mock token
 */
function authenticateTestMode(request: NextRequest): ProxyAuthToken | null {
    const sessionCookie = request.cookies.get('next-auth.session-token');

    if (!sessionCookie) {
        return null;
    }

    // Mock token with full permissions for testing
    return {
        email: 'test@example.com',
        isLeadership: true,
        isSupportEngineer: true,
    };
}

/**
 * Production authentication - validates JWT token
 */
async function authenticateProduction(
    request: NextRequest
): Promise<ProxyAuthToken | null> {
    try {
        const token = await getToken({
            req: request,
            secret: process.env.NEXTAUTH_SECRET,
        });

        if (!token || !token.email) {
            return null;
        }

        return {
            email: token.email as string,
            isLeadership: (token.isLeadership as boolean) || false,
            isSupportEngineer: (token.isSupportEngineer as boolean) || false,
        };
    } catch (error) {
        console.error('[API Proxy Auth] Error validating token:', error);
        return null;
    }
}
