export function WorkspaceLoading() {
  return (
    <div
      className="container page workspace-page ws-loading"
      role="status"
      aria-label="Loading your workspace"
      aria-busy="true"
    >
      <div className="ws-loading-heading" />
      <div className="ws-loading-nav" />
      <div className="ws-metrics">
        {[1, 2, 3, 4].map((i) => (
          <div className="ws-loading-metric" key={i} />
        ))}
      </div>
      <div className="ws-loading-body" />
      <span className="sr-only">Loading your private activity…</span>
    </div>
  );
}
