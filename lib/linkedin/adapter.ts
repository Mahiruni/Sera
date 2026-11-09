import 'server-only'
import type { NextResponse } from 'next/server'
import type { LinkedInUserInfo } from './oidc'

export type LinkedInStatus = 'connected' | 'expired' | 'reconnect_required' | 'disconnected'

export type IdentityRecord = {
  userId: string
  sub: string
  accessTokenEnc: string | null
  tokenExpiresAt: string | null
  status: LinkedInStatus
  profileUrl: string | null
  lastImportAt: string | null
}

/**
 * THE ONLY FILE THAT TOUCHES SERA'S USER / SESSION SYSTEM.
 * Implement these against your existing auth + database, then nothing else
 * in the LinkedIn module needs to change.
 */
export interface SeraAuthAdapter {
  /** Signed-in Sera user id from the request's session, or null. */
  getCurrentUserId(req: Request): Promise<string | null>
  findIdentityBySub(sub: string): Promise<IdentityRecord | null>
  findIdentityByUserId(userId: string): Promise<IdentityRecord | null>
  /** Insert or update the row in linkedin_identities (tokens already encrypted). */
  upsertIdentity(rec: IdentityRecord): Promise<void>
  /** Delete stored token, set status = 'disconnected'. Keep the Sera profile. */
  markDisconnected(userId: string): Promise<void>
  /** Existing Sera account with this email, if any (used to block silent merges). */
  findUserIdByEmail(email: string): Promise<string | null>
  /** Create a Sera account from LinkedIn data; return its id. */
  createUser(info: LinkedInUserInfo): Promise<string>
  /**
   * Save LinkedIn values as UNAPPROVED import candidates
   * (profile_field_sources: source='linkedin', approved=false).
   * Must not overwrite fields where edited_by_user = true.
   */
  recordImport(userId: string, info: LinkedInUserInfo): Promise<void>
  /** Attach your normal Sera session cookie(s) to the response. */
  establishSession(res: NextResponse, userId: string): Promise<void>
}

const notWired = (name: string) => () => {
  throw new Error(`Sera auth adapter not wired: ${name}`)
}

export const adapter: SeraAuthAdapter = {
  getCurrentUserId: notWired('getCurrentUserId'),
  findIdentityBySub: notWired('findIdentityBySub'),
  findIdentityByUserId: notWired('findIdentityByUserId'),
  upsertIdentity: notWired('upsertIdentity'),
  markDisconnected: notWired('markDisconnected'),
  findUserIdByEmail: notWired('findUserIdByEmail'),
  createUser: notWired('createUser'),
  recordImport: notWired('recordImport'),
  establishSession: notWired('establishSession'),
}
