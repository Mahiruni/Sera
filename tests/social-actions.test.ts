import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { createRequire } from "node:module";
import { runInNewContext } from "node:vm";
import ts from "typescript";
import sharp from "sharp";
import { testDatabase } from "./helpers/database.mjs";
import { socialClient } from "./helpers/social-client.mjs";
import type { SocialState } from "../lib/social/actions";
const require = createRequire(import.meta.url);
test("server actions publish actual media, handle replies and idempotent interactions, enforce ownership, and clean failed publication", async () => {
  const db = await testDatabase();
  try {
    const a = {
      id: "36c7a54e-88d8-4fc0-aa91-11582cae249f",
      email_confirmed_at: new Date().toISOString(),
    };
    const b = {
      id: "46c7a54e-88d8-4fc0-aa91-11582cae249f",
      email_confirmed_at: new Date().toISOString(),
    };
    await db.query(
      "insert into auth.users(id,email_confirmed_at) values ($1,now()),($2,now())",
      [a.id, b.id],
    );
    const owner = socialClient(db, a),
      other = socialClient(db, b);
    let client = owner;
    const invalidated: string[] = [];
    const exports: Record<
      string,
      (state: SocialState, form: FormData) => Promise<SocialState>
    > = {};
    const source = ts.transpileModule(
      await readFile("lib/social/actions.ts", "utf8"),
      {
        compilerOptions: {
          module: ts.ModuleKind.CommonJS,
          target: ts.ScriptTarget.ES2022,
          esModuleInterop: true,
        },
      },
    ).outputText;
    runInNewContext(source, {
      exports,
      FormData,
      File,
      Buffer,
      Uint8Array,
      console,
      require: (name: string) => {
        // Keep this database/RLS suite isolated from optional external screening.
        if (name === "@/lib/moderation/screen")
          return {
            moderationEnabled: () => false,
            screenSubmission: () => { throw new Error("Unexpected external screening"); },
          };
        if (name === "@/lib/moderation/writer")
          return {
            securePublishingConfigured: () => false,
            moderationWriter: () => { throw new Error("Unexpected privileged writer"); },
          };
        if (name === "@/lib/supabase/server")
          return { serverClient: async () => client };
        if (name === "next/cache")
          return { revalidatePath: (path: string) => invalidated.push(path) };
        if (name === "next/navigation")
          return {
            redirect: () => {
              throw new Error("Unexpected redirect");
            },
          };
        if (name === "./validation")
          return require("../lib/social/validation.ts");
        return require(name);
      },
    });
    const form = (fields: Record<string, string>) => {
      const f = new FormData();
      Object.entries(fields).forEach(([k, v]) => f.set(k, v));
      return f;
    };
    const text = () =>
      form({
        kind: "project",
        title: "A real engineering project",
        body: "Testing the complete publishing boundary.",
        skills: "Design, React",
        project_url: "https://example.com",
      });
    const image = await sharp({
      create: { width: 40, height: 30, channels: 3, background: "#00aabb" },
    })
      .jpeg()
      .toBuffer();
    const post = text();
    post.set("kind", "photos");
    post.append(
      "media",
      new File([new Uint8Array(image)], "photo.jpg", { type: "image/jpeg" }),
    );
    const result = await exports.createPost({}, post);
    assert.ok(result.success, result.error);
    const id = result.postId!;
    assert.equal(owner.files.size, 1);
    const stored = [...owner.files.values()][0];
    assert.equal((await sharp(stored).metadata()).format, "webp");
    const rows = await db.query<{ status: string; author_id: string }>(
      "select status,author_id from posts where id=$1",
      [id],
    );
    assert.deepEqual(rows.rows[0], { status: "published", author_id: a.id });
    client = other;
    const like = form({ post_id: id });
    assert.ok((await exports.toggleLikePost({}, like)).success);
    assert.ok(
      (await exports.toggleLikePost({}, like)).success,
      "duplicate request is idempotent",
    );
    assert.ok((await exports.toggleSavePost({}, like)).success);
    assert.ok((await exports.sharePost({}, like)).success);
    assert.ok(
      (
        await exports.addComment(
          {},
          form({ post_id: id, body: "A useful project" }),
        )
      ).success,
    );
    const parent = await db.query<{ id: string }>(
      "select id from post_comments where post_id=$1",
      [id],
    );
    assert.ok(
      (
        await exports.addComment(
          {},
          form({ post_id: id, parent_id: parent.rows[0].id, body: "A reply" }),
        )
      ).success,
    );
    const counters = await db.query<{
      like_count: number;
      comment_count: number;
      share_count: number;
    }>("select like_count,comment_count,share_count from posts where id=$1", [
      id,
    ]);
    assert.deepEqual(counters.rows[0], {
      like_count: 1,
      comment_count: 2,
      share_count: 1,
    });
    const edit = text();
    edit.set("post_id", id);
    edit.set("kind", "photos");
    assert.ok(
      (await exports.updatePost({}, edit)).error,
      "other member cannot edit",
    );
    assert.ok(
      (await exports.deletePost({}, form({ post_id: id }))).error,
      "other member cannot delete",
    );
    client = owner;
    assert.ok((await exports.updatePost({}, edit)).success);
    assert.ok(
      (
        await exports.deleteComment(
          {},
          form({ post_id: id, comment_id: parent.rows[0].id }),
        )
      ).success,
      "post owner can moderate",
    );
    assert.ok((await exports.deletePost({}, form({ post_id: id }))).success);
    assert.equal(owner.files.size, 0);
    for (const failure of ["upload", "imageRow", "publish"] as const) {
      owner.failures[failure] = true;
      assert.ok((await exports.createPost({}, post)).error, failure);
      owner.failures[failure] = false;
      const count = await db.query<{ n: number }>(
        "select count(*)::int as n from posts",
      );
      assert.equal(
        count.rows[0].n,
        0,
        failure + " leaves no drafts or published posts",
      );
      assert.equal(owner.files.size, 0, failure + " leaves no storage objects");
    }
    const malformed = text();
    malformed.append(
      "media",
      new File(["not an image"], "fake.jpg", { type: "image/jpeg" }),
    );
    assert.ok(
      (await exports.createPost({}, malformed)).error,
      "invalid bytes are decoded and rejected",
    );
    client = socialClient(db, { id: a.id, email_confirmed_at: "" });
    assert.ok(
      (await exports.createPost({}, text())).error,
      "unverified user blocked",
    );
    assert.ok(
      invalidated.includes("/feed") &&
        invalidated.includes("/saved") &&
        invalidated.includes("/posts/" + id),
    );
  } finally {
    await db.close();
  }
});
