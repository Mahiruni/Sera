# Sera × LinkedIn Integration — Spec & Setup

Status: planning document. Code is added after the repo's stack is confirmed.

## 1. What LinkedIn actually provides

With the self-serve product **"Sign In with LinkedIn using OpenID Connect"**
(scopes `openid profile email`), the `userinfo` endpoint returns:

| Field | Available |
|---|---|
| `sub` (stable LinkedIn member id) | Yes |
| `name`, `given_name`, `family_name` | Yes |
| `picture` (photo URL) | Yes |
| `email`, `email_verified` | Yes |
| `locale` | Yes (language/country, not a city) |
| Headline, current position, company, bio | **No** — not in self-serve |
| Employment history, education, skills | **No** — needs approved LinkedIn partner programs |

So the import screen must: auto-fill name, email, photo; offer **manual entry**
(or CV upload) for headline, position, company, bio, location, history, education.
Never claim or fake fields LinkedIn does not return.

## 2. LinkedIn developer setup

1. Create an app at https://www.linkedin.com/developers/apps (needs a LinkedIn Company Page).
2. Products tab → request **Sign In with LinkedIn using OpenID Connect**.
3. Auth tab → add redirect URLs:
   - `https://sera-dusky-seven.vercel.app/api/auth/linkedin/callback`
   - `http://localhost:3000/api/auth/linkedin/callback`
   (Adjust the path to the repo's auth routing once known.)
4. Copy Client ID and Client Secret.

## 3. Environment variables

```
LINKEDIN_CLIENT_ID=
LINKEDIN_CLIENT_SECRET=
LINKEDIN_REDIRECT_URI=https://sera-dusky-seven.vercel.app/api/auth/linkedin/callback
TOKEN_ENCRYPTION_KEY=   # 32-byte random, base64 (openssl rand -base64 32)
```

Set them in Vercel → Project → Settings → Environment Variables (Production + Preview).
Never commit real values; commit only a `.env.example`.

## 4. Endpoints

- Authorize: `https://www.linkedin.com/oauth/v2/authorization`
- Token: `https://www.linkedin.com/oauth/v2/accessToken`
- Userinfo: `https://api.linkedin.com/v2/userinfo`
- JWKS (verify ID token): `https://www.linkedin.com/oauth/openid/jwks`
- Revoke: `https://www.linkedin.com/oauth/v2/revoke`

## 5. Flow

1. `GET /api/auth/linkedin/start`
   - generate random `state` and `nonce`, store in short-lived httpOnly, Secure, SameSite=Lax cookies
   - redirect to authorize URL with `response_type=code`, `scope=openid profile email`, `state`, `nonce`
2. `GET /api/auth/linkedin/callback`
   - reject if `error` param present, or `state` ≠ cookie
   - exchange `code` server-side for tokens
   - verify ID token signature (JWKS), `iss`, `aud`, `exp`, and `nonce`
   - call `userinfo`
   - account resolution:
     1. identity with this `sub` exists → sign in that user
     2. else if signed in → link to current user
     3. else if `email_verified` and a Sera user has that email → **do not auto-link**; require login with the existing method, then confirm link
     4. else create new user
3. Redirect new/linked users to the onboarding review screen.
4. Disconnect: revoke token, delete stored tokens, mark status `disconnected`. Keep the user's Sera profile.

## 6. Database (SQL — adapt to the repo's ORM/migration tool)

```sql
CREATE TABLE linkedin_identities (
  id              UUID PRIMARY KEY,
  user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  linkedin_sub    TEXT NOT NULL UNIQUE,
  access_token_enc TEXT,            -- encrypted, server-side only
  token_expires_at TIMESTAMPTZ,
  status          TEXT NOT NULL DEFAULT 'connected'
                  CHECK (status IN ('connected','expired','reconnect_required','disconnected')),
  profile_url     TEXT,             -- only if user provides it
  last_import_at  TIMESTAMPTZ,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (user_id)
);

CREATE TABLE profile_field_sources (
  user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  field      TEXT NOT NULL,         -- e.g. 'full_name', 'photo', 'email'
  source     TEXT NOT NULL CHECK (source IN ('linkedin','manual')),
  approved   BOOLEAN NOT NULL DEFAULT false,
  edited_by_user BOOLEAN NOT NULL DEFAULT false,
  imported_at TIMESTAMPTZ,
  PRIMARY KEY (user_id, field)
);
```

Rule: a re-import never overwrites a field where `edited_by_user = true`
unless the user explicitly confirms.

## 7. UI checklist

- "Continue with LinkedIn" button on login **and** register (LinkedIn blue `#0A66C2`, official logo rules).
- Onboarding: "Build your Sera profile with LinkedIn" → preview → select fields → edit → approve → save.
- "Imported from LinkedIn" badge on imported fields; empty fields get manual inputs.
- Candidate dashboard: status (Connected / Not connected / Expired / Reconnect required), profile URL, last import time, Import/Update, Disconnect.
- Apply flow: prefill from approved profile, CV upload, review step before submit.
- Loading, error, empty, and success states; keyboard accessible; mobile first.

## 8. Security checklist

- [ ] state + nonce validated; cookies httpOnly/Secure/SameSite
- [ ] client secret and tokens server-side only; tokens encrypted at rest
- [ ] no tokens in logs, URLs, or frontend responses
- [ ] ID token signature and claims verified
- [ ] no silent account merge on email match
- [ ] existing email/password login untouched
- [ ] `npm run build`, type-check, and lint pass before pushing

## 9. Not possible without LinkedIn approval

Headline, positions, education, skills, and history import require LinkedIn
partner API programs. Until approved, these stay manual / CV-upload only.
