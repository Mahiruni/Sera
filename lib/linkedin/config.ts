import 'server-only'

export type LinkedInConfig = {
  clientId: string
  clientSecret: string
  redirectUri: string
  encryptionKey: string
  origin: string
}

export function linkedInEnabled(): boolean {
  return Boolean(
    process.env.LINKEDIN_CLIENT_ID &&
      process.env.LINKEDIN_CLIENT_SECRET &&
      process.env.LINKEDIN_REDIRECT_URI &&
      process.env.TOKEN_ENCRYPTION_KEY,
  )
}

export function linkedInConfig(): LinkedInConfig {
  const clientId = process.env.LINKEDIN_CLIENT_ID
  const clientSecret = process.env.LINKEDIN_CLIENT_SECRET
  const redirectUri = process.env.LINKEDIN_REDIRECT_URI
  const encryptionKey = process.env.TOKEN_ENCRYPTION_KEY
  if (!clientId || !clientSecret || !redirectUri || !encryptionKey) {
    throw new Error('LinkedIn sign-in is not configured')
  }
  return { clientId, clientSecret, redirectUri, encryptionKey, origin: new URL(redirectUri).origin }
}

/** Where users land. Adjust to your routes. */
export const LINKEDIN_PATHS = {
  afterAuth: '/profile?linkedin=review',
  login: '/account',
  error: '/account',
} as const

/** Only same-site relative paths are allowed as a post-login target. */
export function safeNextPath(value: string | null | undefined): string | null {
  if (!value) return null
  if (!value.startsWith('/') || value.startsWith('//') || value.includes('\\')) return null
  return value
}
