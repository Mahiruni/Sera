"use client";
export default function ErrorPage({ reset }: { reset: () => void }) {
  return (
    <div className="container page">
      <h1>Something interrupted this page.</h1>
      <p>Your browser data has not been deleted.</p>
      <button className="button" onClick={reset}>
        Try again
      </button>
    </div>
  );
}
