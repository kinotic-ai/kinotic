import { apiUrl } from '../util/helpers'

/**
 * Reads the error message from a failed auth response body, falling back to
 * {@code fallback} when the body is not JSON or carries no error field.
 */
export async function readAuthError(res: Response, fallback: string): Promise<string> {
    try {
        const body = await res.json()
        return body?.error ?? fallback
    } catch {
        return fallback
    }
}

/**
 * Verifies credentials against a gateway login route; on success the gateway establishes
 * the browser session and sets the session cookie. Rejects with the server's error message
 * (or "Invalid credentials") when the login is refused.
 */
export async function postCredentials(path: string, email: string, password: string): Promise<void> {
    // credentials:'include' so the cross-origin Set-Cookie is stored.
    const res = await fetch(apiUrl(path), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ email, password })
    })
    if (!res.ok) {
        throw new Error(await readAuthError(res, 'Invalid credentials'))
    }
}
