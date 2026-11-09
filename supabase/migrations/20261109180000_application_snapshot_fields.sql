alter table public.applications
  add column candidate_headline text not null default '' check (char_length(candidate_headline) <= 120),
  add column education text not null default '' check (char_length(education) <= 2000);
