import Link from "next/link";
export default function NotFound() {
  return (
    <section className="container page">
      <p className="eyebrow">404 · Sera</p>
      <h1>This path is still unwritten.</h1>
      <p className="subtext">The page or sample role could not be found.</p>
      <Link className="button" href="/jobs">
        Explore opportunities
      </Link>
    </section>
  );
}
