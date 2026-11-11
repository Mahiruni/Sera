-- SERA native server-gateway publishing: a verified member must create
-- the initial draft under their own JWT before privileged server moderation
-- is permitted to promote that draft to "published".
--
-- This restores only INSERT for authenticated users. Existing RLS
-- posts_create WITH CHECK and the private.guard_post / publication-gate
-- triggers continue enforcing ownership, verified email, and draft-only
-- direct creation; no client may publish directly.
GRANT INSERT ON TABLE public.posts TO authenticated;
