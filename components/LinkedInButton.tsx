type Props = { label?: string; next?: string; className?: string }

/** "Continue with LinkedIn". Place on login and register screens. */
export default function LinkedInButton({ label = 'Continue with LinkedIn', next, className }: Props) {
  const href = `/api/auth/linkedin/start${next ? `?next=${encodeURIComponent(next)}` : ''}`
  return (
    <a
      href={href}
      className={className}
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 10,
        minHeight: 48,
        padding: '0 20px',
        borderRadius: 999,
        background: '#0A66C2',
        color: '#fff',
        font: '600 15px/1 inherit',
        textDecoration: 'none',
      }}
    >
      <svg width="20" height="20" viewBox="0 0 24 24" aria-hidden="true" fill="currentColor">
        <path d="M20.45 20.45h-3.56v-5.57c0-1.33-.03-3.04-1.85-3.04-1.86 0-2.14 1.45-2.14 2.94v5.67H9.35V9h3.41v1.56h.05c.48-.9 1.64-1.85 3.37-1.85 3.6 0 4.27 2.37 4.27 5.46v6.28zM5.34 7.43a2.06 2.06 0 1 1 0-4.12 2.06 2.06 0 0 1 0 4.12zM7.12 20.45H3.56V9h3.56v11.45z" />
      </svg>
      {label}
    </a>
  )
}
