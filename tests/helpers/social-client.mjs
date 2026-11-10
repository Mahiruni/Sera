// Small SDK boundary adapter for action tests. All table and storage metadata operations
// execute PostgreSQL with the real migrations, column grants and authenticated RLS role.
export function socialClient(db, user) {
  const files = new Map();
  const failures = { upload: false, imageRow: false, publish: false };
  const quote = (name) => {
    if (!/^[a-z_]+$/.test(name)) throw new Error("Invalid test identifier");
    return '"' + name + '"';
  };
  async function query(sql, args = []) {
    await db.exec("begin");
    try {
      await db.query("select set_config('request.jwt.claims',$1,true)", [
        JSON.stringify({ sub: user.id, role: "authenticated" }),
      ]);
      await db.exec("set local role authenticated");
      const result = await db.query(sql, args);
      await db.exec("commit");
      return { data: result.rows, error: null };
    } catch (error) {
      await db.exec("rollback");
      return {
        data: null,
        error: { code: error.code, message: error.message },
      };
    }
  }
  function from(table) {
    let method = "select",
      values = {},
      columns = "*",
      singular = false;
    const filters = [];
    const builder = {
      select(value = "*") {
        columns = value;
        return builder;
      },
      insert(value) {
        method = "insert";
        values = value;
        return builder;
      },
      update(value) {
        method = "update";
        values = value;
        return builder;
      },
      delete() {
        method = "delete";
        return builder;
      },
      eq(key, value) {
        filters.push([key, value]);
        return builder;
      },
      single() {
        singular = true;
        return builder;
      },
      async then(resolve, reject) {
        try {
          if (
            (table === "post_images" &&
              method === "insert" &&
              failures.imageRow) ||
            (table === "posts" &&
              method === "update" &&
              values.status === "published" &&
              failures.publish)
          )
            return resolve({
              data: null,
              error: { code: "test", message: "Injected failure" },
            });
          const args = [];
          const param = (value) => {
            args.push(value);
            return "$" + args.length;
          };
          const projection =
            columns === "*" ? "*" : columns.split(",").map(quote).join(",");
          let sql;
          if (method === "insert")
            sql = `insert into public.${quote(table)} (${Object.keys(values).map(quote).join(",")}) values (${Object.values(values).map(param).join(",")})`;
          if (method === "update")
            sql = `update public.${quote(table)} set ${Object.entries(values)
              .map(([k, v]) => `${quote(k)}=${param(v)}`)
              .join(",")}`;
          if (method === "delete") sql = `delete from public.${quote(table)}`;
          if (method === "select")
            sql = `select ${projection} from public.${quote(table)}`;
          if (filters.length)
            sql +=
              " where " +
              filters.map(([k, v]) => `${quote(k)}=${param(v)}`).join(" and ");
          if (method !== "select") sql += " returning " + projection;
          const result = await query(sql, args);
          if (!result.error && singular) {
            if (result.data.length !== 1)
              return resolve({
                data: null,
                error: { code: "PGRST116", message: "Expected one row" },
              });
            result.data = result.data[0];
          }
          return resolve(result);
        } catch (error) {
          return reject(error);
        }
      },
    };
    return builder;
  }
  return {
    failures,
    files,
    from,
    auth: { getUser: async () => ({ data: { user } }) },
    storage: {
      from: (bucket) => ({
        async upload(path, bytes) {
          if (failures.upload)
            return { error: { message: "Injected upload failure" } };
          const result = await query(
            "insert into storage.objects(bucket_id,name) values ($1,$2) returning id",
            [bucket, path],
          );
          if (!result.error) files.set(path, bytes);
          return result;
        },
        async remove(paths) {
          for (const path of paths) {
            const result = await query(
              "delete from storage.objects where bucket_id=$1 and name=$2 returning id",
              [bucket, path],
            );
            if (result.error) return result;
            files.delete(path);
          }
          return { error: null };
        },
      }),
    },
  };
}
