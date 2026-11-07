import "server-only";
import { createClient } from "@supabase/supabase-js";
import nodemailer from "nodemailer";
import { renderEmail } from "./render";
import { siteOrigin } from "@/lib/seo";
export function welcomeConfigured() {
  return Boolean(
    process.env.SERA_SMTP_PASSWORD &&
    process.env.SUPABASE_SERVICE_ROLE_KEY &&
    process.env.NEXT_PUBLIC_SUPABASE_URL,
  );
}
export async function dispatchWelcome(userId?: string) {
  if (!welcomeConfigured()) return { sent: 0, failed: 0, deferred: true };
  const admin = createClient(
    process.env.NEXT_PUBLIC_SUPABASE_URL!,
    process.env.SUPABASE_SERVICE_ROLE_KEY!,
    { auth: { persistSession: false, autoRefreshToken: false } },
  );
  if (userId) {
    const u = await admin.auth.admin.getUserById(userId);
    if (u.error || !u.data.user?.email_confirmed_at)
      return { sent: 0, failed: 0, deferred: false };
    const queued = await admin
      .from("email_outbox")
      .upsert(
        { user_id: userId },
        { onConflict: "user_id", ignoreDuplicates: true },
      );
    if (queued.error) return { sent: 0, failed: 1, deferred: false };
  }
  const claimed = await admin.rpc("claim_welcome", {
    p_user_id: userId || null,
  });
  if (claimed.error) return { sent: 0, failed: 1, deferred: false };
  const transport = nodemailer.createTransport({
    host: "smtp.hostinger.com",
    port: 465,
    secure: true,
    auth: { user: "mahir@hisabtech.com", pass: process.env.SERA_SMTP_PASSWORD },
    connectionTimeout: 10000,
    socketTimeout: 20000,
  });
  let sent = 0,
    failed = 0;
  for (const row of claimed.data || []) {
    try {
      const r = await admin.auth.admin.getUserById(row.user_id);
      const u = r.data.user;
      if (r.error || !u?.email_confirmed_at || !u.email)
        throw Error("recipient_unavailable");
      const p = await admin
        .from("profiles")
        .select("full_name,locale")
        .eq("id", u.id)
        .maybeSingle();
      const email = renderEmail({
        kind: "welcome",
        locale: p.data?.locale || u.user_metadata.locale || "en",
        name: p.data?.full_name || "there",
        actionUrl: siteOrigin + "/onboarding",
        origin: siteOrigin,
      });
      await transport.sendMail({
        from: { name: "Sera.et", address: "mahir@hisabtech.com" },
        to: u.email,
        subject: email.subject,
        text: email.text,
        html: email.html,
        messageId: `<sera-welcome-${row.id}@hisabtech.com>`,
      });
      const marked = await admin
        .from("email_outbox")
        .update({
          status: "sent",
          sent_at: new Date().toISOString(),
          last_error: null,
        })
        .eq("id", row.id)
        .eq("attempts", row.attempts);
      if (marked.error) throw Error("queue_acknowledgement_failed");
      sent++;
    } catch {
      failed++;
      await admin
        .from("email_outbox")
        .update({
          status: "pending",
          last_error: "delivery_failed",
          next_attempt_at: new Date(
            Date.now() + Math.min(24 * 60, 10 * 2 ** row.attempts) * 60000,
          ).toISOString(),
        })
        .eq("id", row.id)
        .eq("attempts", row.attempts);
    }
  }
  transport.close();
  return { sent, failed, deferred: false };
}
