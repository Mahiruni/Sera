create type post_type as enum ('text', 'image', 'quote', 'talent', 'project', 'achievement', 'discussion');

create table if not exists public.posts (
  id uuid default gen_random_uuid() primary key,
  user_id uuid references auth.users(id) on delete cascade not null,
  type post_type default 'text' not null,
  title text,
  content text not null,
  image_url text,
  skills text[] default '{}',
  category text,
  location text,
  created_at timestamp with time zone default timezone('utc'::text, now()) not null,
  updated_at timestamp with time zone default timezone('utc'::text, now()) not null
);

create table if not exists public.post_likes (
  post_id uuid references public.posts(id) on delete cascade not null,
  user_id uuid references auth.users(id) on delete cascade not null,
  created_at timestamp with time zone default timezone('utc'::text, now()) not null,
  primary key (post_id, user_id)
);

create table if not exists public.post_comments (
  id uuid default gen_random_uuid() primary key,
  post_id uuid references public.posts(id) on delete cascade not null,
  user_id uuid references auth.users(id) on delete cascade not null,
  content text not null,
  created_at timestamp with time zone default timezone('utc'::text, now()) not null
);

create table if not exists public.post_saves (
  post_id uuid references public.posts(id) on delete cascade not null,
  user_id uuid references auth.users(id) on delete cascade not null,
  created_at timestamp with time zone default timezone('utc'::text, now()) not null,
  primary key (post_id, user_id)
);

alter table public.posts enable row level security;
alter table public.post_likes enable row level security;
alter table public.post_comments enable row level security;
alter table public.post_saves enable row level security;

create policy "Public posts are viewable by everyone" on public.posts for select using (true);
create policy "Users can insert their own posts" on public.posts for insert with check (auth.uid() = user_id);
create policy "Users can update their own posts" on public.posts for update using (auth.uid() = user_id);
create policy "Users can delete their own posts" on public.posts for delete using (auth.uid() = user_id);

create policy "Likes are viewable by everyone" on public.post_likes for select using (true);
create policy "Users can insert/delete their own likes" on public.post_likes for all using (auth.uid() = user_id);

create policy "Comments are viewable by everyone" on public.post_comments for select using (true);
create policy "Users can insert their own comments" on public.post_comments for insert with check (auth.uid() = user_id);
create policy "Users can delete their own comments" on public.post_comments for delete using (auth.uid() = user_id);

create policy "Saves are viewable by owner" on public.post_saves for select using (auth.uid() = user_id);
create policy "Users can manage their own saves" on public.post_saves for all using (auth.uid() = user_id);
