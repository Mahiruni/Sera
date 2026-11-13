# Sera Intelligent Refresh — implementation phase 1

- Shared web foreground scheduler coalesces inbox and notification invalidations, owns one message EventSource, pauses polling while hidden/offline, and reconciles on visibility/reconnect.
- Native message socket ignores Phoenix heartbeat acknowledgements as message changes, retains REST reconciliation, coalesces bursts and applies bounded exponential reconnect backoff with jitter.
- Existing encrypted history, optimistic outbox, receipts, scroll anchoring and global voice host remain unchanged.
- Next: account-scoped feed session cache, new-post probe, Home reselect behavior, full per-page state retention, incremental reconciliation and real-device weak-network tests. No claim of complete offline support or all-screen refresh parity.
