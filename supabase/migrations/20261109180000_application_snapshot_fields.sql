-- These columns already exist on the inspected hosted database without a migration record.
alter table public.applications
  add column if not exists candidate_headline text not null default '' check (char_length(candidate_headline) <= 120),
  add column if not exists education text not null default '' check (char_length(education) <= 2000);
