# LinkedIn module: what's included and what's left

Included (new files only, nothing existing is modified):
- lib/linkedin/{config,crypto,oidc,adapter}.ts
- app/api/auth/linkedin/{start,callback,disconnect}/route.ts, app/api/linkedin/status/route.ts
- components/LinkedInButton.tsx
- supabase/migrations/20261109000000_linkedin_identity.sql
- ENV_ADDITIONS.txt (copy into .env.example)

To finish:
1. Implement the 9 functions in lib/linkedin/adapter.ts against Sera's auth/database.
2. Add <LinkedInButton /> to the login and register screens.
3. Run the migration; set the 4 env vars in Vercel; add the redirect URL in the LinkedIn app.
4. Build the review screen at LINKEDIN_PATHS.afterAuth.

LinkedIn (self-serve OIDC) returns only: name, email, photo, locale, member id.
Headline, position, history and education are NOT available; use manual entry or CV upload.
