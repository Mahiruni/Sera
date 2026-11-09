import { NextResponse } from 'next/server'
import { adapter } from '@/lib/linkedin/adapter'
import { linkedInEnabled } from '@/lib/linkedin/config'

export const runtime = 'nodejs'
export const dynamic = 'force-dynamic'

/** Safe for the browser: never returns tokens. */
export async function GET(request: Request) {
  const enabled = linkedInEnabled()
  const userId = await adapter.getCurrentUserId(request)
  const identity = userId ? await adapter.findIdentityByUserId(userId) : null
  let status: 'not_connected' | 'connected' | 'expired' | 'reconnect_required' = 'not_connected'
  if (identity && identity.status !== 'disconnected') {
    status =
      identity.status === 'reconnect_required'
        ? 'reconnect_required'
        : identity.tokenExpiresAt && new Date(identity.tokenExpiresAt) < new Date()
          ? 'expired'
          : identity.status
  }
  return NextResponse.json({
    enabled,
    status,
    profileUrl: identity?.profileUrl ?? null,
    lastImportAt: identity?.lastImportAt ?? null,
  })
}
