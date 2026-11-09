import { NextResponse } from 'next/server'
import { adapter } from '@/lib/linkedin/adapter'
import { linkedInConfig } from '@/lib/linkedin/config'
import { decryptToken } from '@/lib/linkedin/crypto'
import { revokeToken } from '@/lib/linkedin/oidc'

export const runtime = 'nodejs'
export const dynamic = 'force-dynamic'

export async function POST(request: Request) {
  // CSRF: same-origin requests only.
  const origin = request.headers.get('origin')
  if (!origin || origin !== new URL(request.url).origin) {
    return NextResponse.json({ error: 'forbidden' }, { status: 403 })
  }
  const userId = await adapter.getCurrentUserId(request)
  if (!userId) return NextResponse.json({ error: 'unauthenticated' }, { status: 401 })

  const identity = await adapter.findIdentityByUserId(userId)
  if (identity?.accessTokenEnc) {
    try {
      const cfg = linkedInConfig()
      await revokeToken({
        token: decryptToken(identity.accessTokenEnc, cfg.encryptionKey),
        clientId: cfg.clientId,
        clientSecret: cfg.clientSecret,
      })
    } catch {
      // Revocation is best-effort; local tokens are removed regardless.
    }
  }
  await adapter.markDisconnected(userId)
  console.info(JSON.stringify({ scope: 'linkedin', event: 'disconnected', userId }))
  return NextResponse.json({ status: 'disconnected' })
}
