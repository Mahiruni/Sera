import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { testDatabase } from "./helpers/database.mjs";

test("all migrations replay, canonical baseline is repeatable, and SQL authorization suites pass", async () => {
  const db = await testDatabase();
  try {
    // Mirrors hosted changes that exist without corresponding migration-history entries.
    for (const name of [
      "20261109120000_linkedin_profile_import",
      "20261109150000_linkedin_fields_limit",
      "20261109180000_application_snapshot_fields",
      "20261109230000_social_feed_foundation",
    ])
      await db.exec(await readFile(`supabase/migrations/${name}.sql`, "utf8"));
    for (const suite of [
      "profiles",
      "recruitment",
      "account-assets",
      "social",
      "social-storage",
      "activity",
      "professional-network",
      "identity-verification",
      "identity-intake",
    ]) {
      const results = await db.exec(
        await readFile(`tests/${suite}.rls.sql`, "utf8"),
      );
      assert.ok(
        results.some((r: { rows: { result?: string }[] }) =>
          r.rows.some((row) => row.result?.startsWith("PASS:")),
        ),
        suite,
      );
    }
    const empty = await db.query<{ n: number }>(
      "select count(*)::int as n from auth.users",
    );
    assert.equal(empty.rows[0].n, 0, "all fixtures rolled back");
    const view = await db.query<{ reloptions: string[] }>(
      "select reloptions from pg_class where oid='public.post_authors'::regclass",
    );
    assert.ok(view.rows[0].reloptions.includes("security_invoker=true"));
  } finally {
    await db.close();
  }
});
