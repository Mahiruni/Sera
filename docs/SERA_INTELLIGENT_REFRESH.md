# Sera Intelligent Refresh — implementation phase 1

- Shared web foreground scheduler coalesces inbox and notification invalidations, owns one message EventSource, pauses polling while hidden/offline, and reconciles on visibility/reconnect.
- Native message socket ignores Phoenix heartbeat acknowledgements as message changes, retains REST reconciliation, coalesces bursts and applies bounded exponential reconnect backoff with jitter.
- Existing encrypted history, optimistic outbox, receipts, scroll anchoring and global voice host remain unchanged.
- Next: account-scoped feed session cache, new-post probe, Home reselect behavior, full per-page state retention, incremental reconciliation and real-device weak-network tests. No claim of complete offline support or all-screen refresh parity.

Phase 2: account-scoped in-memory Paging cache and retained Home/Saved list state, Home reselect and a foreground RLS-filtered latest-post probe (no media). The new-post chip does not insert or reorder feed rows. Probes stop during active calls. Persistent feed cache across process death, ranked-feed freshness, full page-level state retention and incremental API sync remain future work.

Phase 3: manual and real-time native message reconciliation share one per-thread mutex and coalesced manual reload; offline polling pauses and resumes on connectivity changes. Freshness probes compare actual post ordering instead of treating deletion of an old feed head as a new post. Paging cancellation is propagated. These are code-level improvements; real-device and full release verification remain required.

Phase 4: encrypted 15-minute public-original text snapshots (20 entries, bounded text), account isolation and sign-out clearing, displayed as read-only previews during cold startup or network errors. No private/reposted content, media, saved or ranked feed is persisted. Native destinations now use a per-account SaveableStateHolder for existing saveable UI state; ordinary remember-only state still needs targeted migration. Verification tests cover privacy filtering, expiry and clearing.
