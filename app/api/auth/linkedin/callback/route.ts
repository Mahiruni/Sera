import { NextResponse } from 'next/server'
import { adapter } from '@/lib/linkedin/adapter'
import { linkedInConfig, LINKEDIN_PATHS, safeNextPath } from '@/lib/linkedin/config'
import { encryptToken, safeEqual } from '@/lib/linkedin/crypto'
import { exchangeCode, fetchUserInfo, verifyIdToken } from '@/lib/linkedin/oidc'

export const runtime = 'nodejs'
export const dynamic = 'force-dynamic'

const audit = (event: string, data: Record<string, unknown> = {}) =>
  console.info(JSON.stringify({ scope: 'linkedin', event, ...data })) // never log tokens or codes

function cookie(request: Request, name: string): string | undefined {
  const m = request.headers.get('cookie')?.match(new RegExp(`(?:^|;\\s*)${name}=([^;]+)`))
  return m?.[1]
}

export async function GET(request: Request) {
  let cfg
  try {
    cfg = linkedInConfig()
  } catch {
    return NextResponse.redirect(new URL(`${LINKEDIN_PATHS.error}?linkedin_error=not_configured`, request.url))
  }

  const fail = (reason: string) => {
    const res = NextResponse.redirect(new URL(`${LINKEDIN_PATHS.error}?linkedin_error=${reason}`, cfg.origin))
    clear(res)
    return res
  }
  const clear = (res: NextResponse) => {
    for (const n of ['li_state', 'li_nonce', 'li_next']) res.cookies.set(n, '', { path: '/api/auth/linkedin', maxAge: 0 })
  }

  const q = new URL(request.url).searchParams
  if (q.get('error')) {
    audit('provider_error', { error: q.get('error') })
    return fail(q.get('error') === 'user_cancelled_login' || q.get('error') === 'user_cancelled_authorize' ? 'cancelled' : 'denied')
  }

  const state = q.get('state')
  const code = q.get('code')
  const savedState = cookie(request, 'li_state')
  const nonce = cookie(request, 'li_nonce')
  if (!state || !code || !savedState || !nonce || !safeEqual(state, savedState)) {
    audit('state_mismatch')
    return fail('invalid_state')
  }

  try {
    const tokens = await exchangeCode({ code, ...cfg })
    const claims = await verifyIdToken(tokens.idToken, { clientId: cfg.clientId, nonce })
    const info = await fetchUserInfo(tokens.accessToken)
    if (info.sub !== claims.sub) throw new Error('sub mismatch between ID token and userinfo')

    const enc = encryptToken(tokens.accessToken, cfg.encryptionKey)
    const expiresAt = new Date(Date.now() + tokens.expiresIn * 1000).toISOString()
    const currentUserId = await adapter.getCurrentUserId(request)
    const existing = await adapter.findIdentityBySub(info.sub)

    let userId: string
    let outcome: 'signed_in' | 'linked' | 'created'

    if (existing) {
      if (currentUserId && currentUserId !== existing.userId) return fail('linked_to_other_account')
      userId = existing.userId
      outcome = 'signed_in'
    } else if (currentUserId) {
      userId = currentUserId
      outcome = 'linked'
    } else {
      // Never silently merge on email match: require login with the existing method first.
      if (info.email && (await adapter.findUserIdByEmail(info.email))) {
        audit('email_conflict', { sub: info.sub })
        return fail('email_exists_login_first')
      }
      userId = await adapter.createUser(info)
      outcome = 'created'
    }

    await adapter.upsertIdentity({
      userId,
      sub: info.sub,
      accessTokenEnc: enc,
      tokenExpiresAt: expiresAt,
      status: 'connected',
      profileUrl: existing?.profileUrl ?? null,
      lastImportAt: existing?.lastImportAt ?? null,
    })
    await adapter.recordImport(userId, info)
    audit(outcome, { userId })

    const next = safeNextPath(decodeURIComponent(cookie(request, 'li_next') ?? '')) ?? LINKEDIN_PATHS.afterAuth
    const res = NextResponse.redirect(new URL(next, cfg.origin))
    if (outcome !== 'linked') await adapter.establishSession(res, userId)
    clear(res)
    return res
  } catch (e) {
    audit('callback_error', { message: e instanceof Error ? e.message : 'unknown' })
    return fail('server_error')
  }
}
