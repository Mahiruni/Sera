-- Align hosted SERA social author view with invoker-based RLS.
-- Existing hosted view was created without the security_invoker option.
alter view public.post_authors set (security_invoker = true);
