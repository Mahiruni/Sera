-- A repost is a distinct published idea with immutable attribution to an
-- original post. The server API always screens user-added commentary and the
-- original text before promoting the author's draft.
--
-- One repost of a specific original is allowed per author. Old posts and
-- existing clients remain compatible; they simply omit the nullable column.
ALTER TABLE public.posts
    ADD COLUMN IF NOT EXISTS repost_of uuid
        REFERENCES public.posts(id) ON DELETE CASCADE;

DO $repost_constraint$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'repost_not_self'
          AND conrelid = 'public.posts'::regclass
    ) THEN
        ALTER TABLE public.posts
            ADD CONSTRAINT repost_not_self
            CHECK (repost_of IS NULL OR repost_of <> id);
    END IF;
END $repost_constraint$;

CREATE UNIQUE INDEX IF NOT EXISTS posts_one_repost_per_author
    ON public.posts (author_id, repost_of) WHERE repost_of IS NOT NULL;
CREATE INDEX IF NOT EXISTS posts_repost_source_idx
    ON public.posts (repost_of) WHERE repost_of IS NOT NULL;

-- Existing posts_create ownership RLS + draft-only publication trigger stay
-- in force. No new open INSERT/UPDATE policy and no SECURITY DEFINER RPC.
