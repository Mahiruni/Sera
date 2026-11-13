# Message notification recovery
- Native and web message dispatch share the service-only event claim. The queue creates one job per enabled device.
- Lease recovery, exponential backoff, six attempts and 30-minute expiry. Provider acceptance is not a user delivery or read receipt.
- The worker rechecks device ownership, preferences, conversation membership, blocks and private message read/unsent state. An ambiguous network timeout can cause a duplicate provider send; Android message IDs and web notification tags mitigate it.
- Supabase Cron invokes the protected worker once a minute only if jobs are due. A dedicated random secret must be provisioned in Vault as sera_message_recovery_secret and in Vercel production as SERA_MESSAGE_RECOVERY_SECRET. Never put the value in Git.
- Existing daily job-alert cron is a fallback. Terminal job metadata is pruned after seven days.
- Rollout: apply migrations, deploy backend, verify cron HTTP 200 and queue states using test accounts. No Android version bump required for this server-only change.
- Current enqueue uses existing post-send dispatch paths; a future transactional message-insert outbox can close the gap if dispatch is never invoked.
