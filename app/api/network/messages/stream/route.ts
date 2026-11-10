import { currentUser, serverClient } from "@/lib/supabase/server";
export const maxDuration = 60;
export async function GET(request: Request) {
  const user = await currentUser();
  if (!user?.email_confirmed_at) return new Response(null, { status: 401 });
  const db = await serverClient();
  const { data: threads, error } = await db.from("professional_conversations")
    .select("id").or("member_a.eq." + user.id + ",member_b.eq." + user.id).limit(80);
  if (error) return new Response(null, { status: 503 });
  const session = (await db.auth.getSession()).data.session;
  if (!session) return new Response(null, { status: 401 });
  await db.realtime.setAuth(session.access_token);
  const ids = new Set((threads ?? []).map((row) => row.id));
  const channel = db.channel("professional-messages-" + user.id);
  let stop: (cancelled?: boolean) => void = () => {};
  const stream = new ReadableStream<Uint8Array>({
    start(controller) {
      const encoder = new TextEncoder();
      let ended = false;
      const notify = (payload: { new?: { conversation_id?: string } }) => {
        if (!ended && payload.new?.conversation_id && ids.has(payload.new.conversation_id))
          controller.enqueue(encoder.encode("data: refresh\n\n"));
      };
      const timer = setTimeout(() => stop(), 50000);
      stop = (cancelled = false) => {
        if (ended) return;
        ended = true;
        clearTimeout(timer);
        request.signal.removeEventListener("abort", abort);
        void db.removeChannel(channel);
        if (!cancelled) controller.close();
      };
      function abort() { stop(); }
      request.signal.addEventListener("abort", abort, { once: true });
      if (request.signal.aborted) return stop();
      controller.enqueue(encoder.encode("retry: 5000\n\n"));
      for (const event of ["INSERT", "UPDATE"] as const)
        channel.on("postgres_changes", { event, schema: "public", table: "professional_messages" }, notify);
      channel.subscribe((status) => {
        if (status === "SUBSCRIBED") controller.enqueue(encoder.encode("data: refresh\n\n"));
        if (status === "CHANNEL_ERROR" || status === "TIMED_OUT") stop();
      });
    },
    cancel() { stop(true); },
  });
  return new Response(stream, {
    headers: { "Content-Type": "text/event-stream", "Cache-Control": "private, no-store, no-transform", "X-Accel-Buffering": "no" },
  });
}
