import { NextResponse } from 'next/server'
import { linkedInConfig, LINKEDIN_PATHS, safeNextPath } from '@/lib/linkedin/config'
import { randomToken } from '@/lib/linkedin/crypto'
import { buildAuthorizeUrl } from '@/lib/linkedin/oidc'

export const runtime = 'nodejs'
export const dynamic = 'force-dynamic'

export async function GET(request: Request) {
  let cfg
  try {
    cfg = linkedInConfig()
  } catch {
    const url = new URL(LINKEDIN_PATHS.error, request.url)
    url.searchParams.set('linkedin_error', 'not_configured')
    return NextResponse.redirect(url)
  }

  const state = randomToken()
  const nonce = randomToken()
  const next = safeNextPath(new URL(request.url).searchParams.get('next'))

  const res = NextResponse.redirect(buildAuthorizeUrl({ ...cfg, state, nonce }))
  const opts = {
    httpOnly: true,
    secure: process.env.NODE_ENV === 'production',
    sameSite: 'lax' as const,
    path: '/api/auth/linkedin',
    maxAge: 600,
  }
  res.cookies.set('li_state', state, opts)
  res.cookies.set('li_nonce', nonce, opts)
  if (next) res.cookies.set('li_next', next, opts)
  return res
}
