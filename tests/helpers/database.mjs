import { PGlite } from "@electric-sql/pglite";
import { readFile, readdir } from "node:fs/promises";
import { fileURLToPath } from "node:url";
const root = fileURLToPath(new URL("../../", import.meta.url));
export async function testDatabase() {
  const db = new PGlite();
  // Supabase platform schemas are supplied by the service in production. The application
  // migrations below run unchanged against a real embedded PostgreSQL engine, including RLS.
  await db.exec(`
    create role anon; create role authenticated; create role service_role bypassrls;
    create schema auth; create schema storage;
    create table auth.users(id uuid primary key, email text, email_confirmed_at timestamptz, raw_user_meta_data jsonb default '{}');
    create function auth.uid() returns uuid language sql stable as $$
      select (nullif(current_setting('request.jwt.claims',true),'')::jsonb->>'sub')::uuid
    $$;
    grant usage on schema auth,storage,public to anon,authenticated;
    grant execute on function auth.uid() to anon,authenticated;
    create table storage.buckets(id text primary key,name text,public boolean,file_size_limit bigint,allowed_mime_types text[]);
    create table storage.objects(id uuid primary key default gen_random_uuid(),bucket_id text references storage.buckets(id),name text not null,unique(bucket_id,name));
    alter table storage.objects enable row level security;
    grant select,insert,update,delete on storage.objects to anon,authenticated;
    create function storage.foldername(name text) returns text[] language sql immutable as $$ select (string_to_array(name,'/'))[1:array_length(string_to_array(name,'/'),1)-1] $$;
    create function public.rls_auto_enable() returns event_trigger language plpgsql as $$ begin return; end $$;
  `);
  const migrations = (await readdir(root + "supabase/migrations"))
    .filter((p) => p.endsWith(".sql"))
    .sort();
  try {
    for (const name of migrations)
      await db.exec(
        await readFile(root + "supabase/migrations/" + name, "utf8"),
      );
  } catch (error) {
    await db.close();
    throw error;
  }
  return db;
}
