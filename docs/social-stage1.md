# SERA.et Stage 1: professional community

## Repository and database reconciliation

The branch starts at `42a0417` on `origin/main`. It does not rely on the two local Termux files. That commit contained a canonical feed reader but actions and cards for an incompatible schema, no feed/detail routes, and two conflicting migrations.

Read-only inspection of Sera's Project (`okegwojigrtxadvgxpwm`) on 10 November 2026 confirmed:

- Canonical `posts.author_id`, `kind`, `status`, `body`, `published_at` and trigger-owned counters.
- `post_images`, `post_comments.author_id/body/parent_id`, private `post_likes`, `post_saves`, `post_shares`, and `post_authors`.
- Private `sera-post-media` storage with member-read/owner-write policies.
- Only the five migrations through `20261108053352` are recorded, although the LinkedIn fields, application snapshots and social schema already exist. Application CV attachment columns are not present.

The unrecorded March migration (`20260310000000_social_network.sql`) is removed because it creates the incompatible `user_id/type/content` schema before the real foundation. No hosted tables or records were dropped. The canonical social baseline now skips creation only when its expected columns, private bucket and guard functions already exist; an incompatible/partial schema fails explicitly. The two earlier unrecorded additive migrations use `ADD COLUMN IF NOT EXISTS`. The existing LinkedIn constraint migration already replaces its named constraint safely. Both fresh replay and replay over those existing canonical objects are tested.

`20261110103729_social_author_privacy.sql` makes `post_authors` a security-invoker view over an explicitly session-gated, narrowly scoped private lookup. Other members see only the author's ID, name, headline and location for published contributions; private profiles and account files retain existing owner isolation.

No hosted migration, history repair, merge or deployment was performed. After review, validate the pending migrations against a staging copy before applying them through the normal migration workflow. Do not mark a migration applied without verifying its complete definition. An installation that actually applied the incompatible March schema needs a separate data migration; this branch refuses to silently replace it.

## User flows

Verified members enter `/feed` from Home or Community navigation. The public home remains available to signed-out visitors; signed-in visitors to `/` enter the community. Jobs, applications, employer workspaces, profile/CV management and saved jobs remain available. Saved-story failures are isolated from saved jobs.

Members publish ideas, photos, quotes, talents, projects, achievements and discussions. Headlines, content, skills and HTTPS project links are validated on the server. Up to four JPEG/PNG/WebP files, totaling 3 MB, are decoded, rotated, resized within 2400 × 2400 and encoded as WebP. The pixel limit is 16 million. Originals and arbitrary external image URLs are not stored. The Server Action request ceiling is 4 MB; the file limit leaves space for multipart/text overhead.

Publication is draft → upload → attach metadata → publish. Upload, metadata and publish failures remove the draft and any newly stored files; cleanup failures are reported. Failed submissions retain form inputs. Owners can edit text/metadata and delete stories; deleting removes associated storage files. Editing preserves the immutable post kind and current image set.

Likes and saves use desired state and treat duplicate inserts as successful retries. Saves remain private. Sharing uses native sharing or clipboard copying and records at most one share per member after a successful share/copy. Shared story URLs return email/password readers to the original story after sign-in; OAuth continues to use its existing provider routing.

Stories have dedicated URLs, member author pages, type filters, and 20-item feed/save pagination. Comment pages contain ten top-level threads and all replies to those threads, fetched in batches. Reply depth is one, enforced by the database. Authors can remove their own comments; story owners can moderate comments and cascading replies. Counters remain database-owned.

## Verification and limits

- `npm run typecheck`: Next route generation and TypeScript.
- `npm test`: 30 tests, including fresh migration replay, existing-schema replay, five SQL RLS suites, recruitment/profile regression tests and real image processing through the social actions.
- SQL tests use PGlite's embedded PostgreSQL engine with minimal Supabase `auth`/`storage` platform definitions. Application migrations run unchanged. Fixtures roll back. This verifies PostgreSQL grants, triggers and RLS, not the hosted GoTrue, PostgREST or object-storage services.
- Social-action tests use a small SDK boundary adapter executing the real PostgreSQL schema and ownership policies; only Next cache/navigation and the Supabase transport are substituted. They cover media publication, replies, duplicate interactions, unauthorized edits/deletes, owner moderation, deletion cleanup, invalid image bytes, and injected upload/metadata/publish failures.
- `npm run lint`: no errors; six pre-existing unused-parameter warnings in the Google/LinkedIn auth modules.
- `npm run build`: optimized Next.js build, all existing and new routes.
- Browser checks: protected-story sign-in redirect; desktop and 390px fixture rendering of the actual composer/cards/comments; quote/photo type switching. Temporary fixture routes were removed. These are component checks, not authenticated hosted end-to-end tests.

An authenticated staging run against real Supabase Auth, PostgREST and Storage remains a release gate. Recheck image upload, signed-image reads, two-user interactions and the complete recruitment lifecycle there before merging or deploying. No live test users or community posts were created.
