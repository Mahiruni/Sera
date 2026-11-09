import { createPublicKey, verify, type JsonWebKey } from 'node:crypto'

const AUTHORIZE_URL = 'https://www.linkedin.com/oauth/v2/authorization'
const TOKEN_URL = 'https://www.linkedin.com/oauth/v2/accessToken'
const USERINFO_URL = 'https://api.linkedin.com/v2/userinfo'
const JWKS_URL = 'https://www.linkedin.com/oauth/openid/jwks'
const REVOKE_URL = 'https://www.linkedin.com/oauth/v2/revoke'
const ISSUER = 'https://www.linkedin.com'

export type LinkedInUserInfo = {
  sub: string
  name?: string
  givenName?: string
  familyName?: string
  picture?: string
  email?: string
  emailVerified: boolean
  locale?: string
}

type Fetch = typeof fetch
type Jwk = JsonWebKey & { kid?: string }

export function buildAuthorizeUrl(p: { clientId: string; redirectUri: string; state: string; nonce: string }): string {
  const u = new URL(AUTHORIZE_URL)
  u.searchParams.set('response_type', 'code')
  u.searchParams.set('client_id', p.clientId)
  u.searchParams.set('redirect_uri', p.redirectUri)
  u.searchParams.set('scope', 'openid profile email')
  u.searchParams.set('state', p.state)
  u.searchParams.set('nonce', p.nonce)
  return u.toString()
}

export async function exchangeCode(
  p: { code: string; clientId: string; clientSecret: string; redirectUri: string },
  f: Fetch = fetch,
): Promise<{ accessToken: string; expiresIn: number; idToken: string }> {
  const res = await f(TOKEN_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'authorization_code',
      code: p.code,
      client_id: p.clientId,
      client_secret: p.clientSecret,
      redirect_uri: p.redirectUri,
    }),
    cache: 'no-store',
  })
  if (!res.ok) throw new Error(`LinkedIn token exchange failed (${res.status})`)
  const j = (await res.json()) as { access_token?: string; expires_in?: number; id_token?: string }
  if (!j.access_token || !j.id_token) throw new Error('LinkedIn token response missing fields')
  return { accessToken: j.access_token, expiresIn: j.expires_in ?? 0, idToken: j.id_token }
}

let jwksCache: { keys: Jwk[]; at: number } | null = null

async function getKey(kid: string, f: Fetch) {
  const fresh = jwksCache && Date.now() - jwksCache.at < 3_600_000
  let key = fresh ? jwksCache!.keys.find((k) => k.kid === kid) : undefined
  if (!key) {
    const res = await f(JWKS_URL, { cache: 'no-store' })
    if (!res.ok) throw new Error('Could not load LinkedIn signing keys')
    const j = (await res.json()) as { keys: Jwk[] }
    jwksCache = { keys: j.keys, at: Date.now() }
    key = j.keys.find((k) => k.kid === kid)
  }
  if (!key) throw new Error('Unknown ID token signing key')
  return createPublicKey({ key, format: 'jwk' })
}

const b64json = (s: string) => JSON.parse(Buffer.from(s, 'base64url').toString('utf8'))

/** Verifies signature (RS256), iss, aud, exp and nonce. Returns the claims. */
export async function verifyIdToken(
  idToken: string,
  p: { clientId: string; nonce: string; now?: number },
  f: Fetch = fetch,
): Promise<{ sub: string; [k: string]: unknown }> {
  const parts = idToken.split('.')
  if (parts.length !== 3) throw new Error('Malformed ID token')
  const header = b64json(parts[0])
  if (header.alg !== 'RS256' || !header.kid) throw new Error('Unsupported ID token algorithm')
  const key = await getKey(header.kid, f)
  const ok = verify('RSA-SHA256', Buffer.from(`${parts[0]}.${parts[1]}`), key, Buffer.from(parts[2], 'base64url'))
  if (!ok) throw new Error('ID token signature invalid')
  const c = b64json(parts[1])
  const aud = Array.isArray(c.aud) ? c.aud : [c.aud]
  const now = Math.floor((p.now ?? Date.now()) / 1000)
  if (c.iss !== ISSUER) throw new Error('ID token issuer mismatch')
  if (!aud.includes(p.clientId)) throw new Error('ID token audience mismatch')
  if (typeof c.exp !== 'number' || c.exp < now - 60) throw new Error('ID token expired')
  if (c.nonce !== p.nonce) throw new Error('ID token nonce mismatch')
  if (typeof c.sub !== 'string') throw new Error('ID token missing sub')
  return c
}

export async function fetchUserInfo(accessToken: string, f: Fetch = fetch): Promise<LinkedInUserInfo> {
  const res = await f(USERINFO_URL, { headers: { Authorization: `Bearer ${accessToken}` }, cache: 'no-store' })
  if (!res.ok) throw new Error(`LinkedIn userinfo failed (${res.status})`)
  const j = (await res.json()) as Record<string, unknown>
  const loc = j.locale
  const locale =
    typeof loc === 'string'
      ? loc
      : loc && typeof loc === 'object'
        ? [(loc as Record<string, string>).language, (loc as Record<string, string>).country].filter(Boolean).join('_')
        : undefined
  const s = (v: unknown) => (typeof v === 'string' && v ? v : undefined)
  return {
    sub: String(j.sub),
    name: s(j.name),
    givenName: s(j.given_name),
    familyName: s(j.family_name),
    picture: s(j.picture),
    email: s(j.email),
    emailVerified: j.email_verified === true || j.email_verified === 'true',
    locale,
  }
}

export async function revokeToken(p: { token: string; clientId: string; clientSecret: string }, f: Fetch = fetch) {
  const res = await f(REVOKE_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ token: p.token, client_id: p.clientId, client_secret: p.clientSecret }),
  })
  return res.ok
}
