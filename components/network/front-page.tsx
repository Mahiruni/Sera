import Link from "next/link";
import {
  ArrowRight,
  ArrowUpRight,
  BriefcaseBusiness,
  Layers3,
  MoveUpRight,
  PenLine,
  Quote,
  Search,
  Sparkles,
  UsersRound,
  Check,
  ChevronRight,
} from "lucide-react";
import { NetworkBrand, NextOnSera } from "./shared";
import { ThemeToggle } from "./theme-toggle";
export function NetworkFrontPage() {
  return (
    <div className="sera-network network-public">
      <div className="network-public-top">
        <NetworkBrand />
        <nav aria-label="Explore Sera">
          <Link href="/discover">Discover</Link>
          <Link href="/jobs">Opportunities</Link>
          <Link href="#next-on-sera">What’s next</Link>
        </nav>
        <div>
          <ThemeToggle />
          <Link href="/sign-in" className="network-signin">
            Sign in <ArrowUpRight size={15} />
          </Link>
        </div>
      </div>
      <section className="network-public-hero">
        <div className="network-hero-copy">
          <p className="network-kicker">
            <span className="network-live-dot" /> FOR THE WORK. AND THE PEOPLE
            BEHIND IT.
          </p>
          <h1>
            Your work.
            <br />
            Your people.
            <br />
            <span>Your next chapter.</span>
          </h1>
          <p className="network-hero-description">
            A home for your ideas, your talent and what comes next. Share what
            you’re building. Discover people worth knowing. Find work that moves
            you forward.
          </p>
          <div className="network-hero-actions">
            <Link href="/sign-up" className="network-button">
              Find your place <ArrowUpRight size={18} />
            </Link>
            <Link href="/feed" className="network-text-link">
              Explore the community <ArrowRight size={16} />
            </Link>
          </div>
          <div className="network-hero-note">
            <span>
              <Check size={14} /> Your professional story
            </span>
            <span>
              <Check size={14} /> Work worth discovering
            </span>
          </div>
        </div>
        <div
          className="network-hero-stage"
          aria-label="Illustrative preview of sharing your work"
        >
          <div className="network-stage-grid" aria-hidden="true" />
          <div className="network-stage-label">
            <span className="network-live-dot" /> A LITTLE LOOK INSIDE{" "}
            <span>Illustrative preview</span>
          </div>
          <article className="network-feature-story">
            <header>
              <span className="network-example-avatar">
                <Layers3 size={21} />
              </span>
              <div>
                <strong>Your next great project</strong>
                <span>Created by you. Shared with your community.</span>
              </div>
              <MoveUpRight size={18} />
            </header>
            <div className="network-artwork" aria-hidden="true">
              <span className="network-artwork-caption">
                IDEAS
                <br />
                IN MOTION.
              </span>
              <div className="network-orbit one" />
              <div className="network-orbit two" />
              <div className="network-orbit three" />
              <span className="network-artwork-number">
                01 / THE POSSIBILITIES
              </span>
              <span className="network-artwork-mark">S.</span>
            </div>
            <div className="network-story-bottom">
              <span className="network-tag">PROJECT SPOTLIGHT</span>
              <h2>Make room for your best work.</h2>
              <p>
                Show the thinking, craft and purpose behind what you create.
              </p>
              <div className="network-mini-tags">
                <span>Design</span>
                <span>Technology</span>
                <span>Possibility</span>
              </div>
            </div>
          </article>
          <div className="network-floating-note">
            <Quote size={19} />
            <p>
              Great things start
              <br />
              with a conversation.
            </p>
            <span>A perspective worth sharing</span>
          </div>
          <div className="network-floating-skill">
            <Sparkles size={17} />
            <span>
              There’s more to you
              <br />
              <strong>than a job title.</strong>
            </span>
          </div>
        </div>
      </section>
      <section className="network-explore-strip">
        <div>
          <span className="network-kicker">FOLLOW YOUR CURIOSITY</span>
          <h2>What’s your next move?</h2>
        </div>
        <form action="/jobs" role="search">
          <Search size={20} aria-hidden="true" />
          <label className="sr-only" htmlFor="front-job-search">
            Search job titles
          </label>
          <input
            id="front-job-search"
            name="q"
            placeholder="Find a role that feels like you"
            maxLength={80}
          />
          <button type="submit">
            Find opportunities <ArrowRight size={16} />
          </button>
        </form>
      </section>
      <section className="network-pathways" aria-label="Your place on Sera">
        {[
          {
            n: "01",
            Icon: PenLine,
            title: "A voice. A point of view.",
            body: "Share ideas, insights and milestones. Give your perspective a place to grow.",
            href: "/feed",
            cta: "Share your story",
          },
          {
            n: "02",
            Icon: UsersRound,
            title: "Find your kind of people.",
            body: "Discover the professionals behind the projects, skills and ideas that interest you.",
            href: "/discover",
            cta: "Meet the community",
          },
          {
            n: "03",
            Icon: BriefcaseBusiness,
            title: "The next door is open.",
            body: "Explore employer-posted opportunities and introduce yourself through your professional profile.",
            href: "/jobs",
            cta: "Explore opportunities",
          },
        ].map(({ n, Icon, title, body, href, cta }) => (
          <Link key={n} href={href} className="network-pathway">
            <div>
              <span>{n}</span>
              <Icon size={23} />
            </div>
            <h2>{title}</h2>
            <p>{body}</p>
            <span className="network-pathway-link">
              {cta}
              <ArrowUpRight size={17} />
            </span>
          </Link>
        ))}
      </section>
      <section className="network-identity">
        <div className="network-identity-art">
          <span className="network-kicker">
            YOUR STORY, WITH MORE DIMENSION
          </span>
          <div className="network-profile-example">
            <span className="network-profile-example-avatar">
              You<span>✦</span>
            </span>
            <div>
              <strong>Your name belongs here.</strong>
              <p>Your craft. Your experience. Your ambition.</p>
            </div>
            <div className="network-profile-example-lines">
              <span>What you know</span>
              <span>What you’ve built</span>
              <span>Where you’re going</span>
            </div>
          </div>
          <span className="network-identity-caption">
            A résumé tells part of the story. You bring the rest.
          </span>
        </div>
        <div>
          <p className="network-kicker">LET THE WHOLE YOU SHOW UP</p>
          <h2>
            More than
            <br />a list of skills.
          </h2>
          <p>
            Bring your experience, education and creative work into one
            professional identity. Keep your profile current, manage your CV and
            let your published work speak for itself.
          </p>
          <Link href="/sign-up" className="network-text-link">
            Build your professional story <ChevronRight size={17} />
          </Link>
          <div className="network-identity-features">
            <span>
              <Check size={15} /> Profile & professional experience
            </span>
            <span>
              <Check size={15} /> Projects, talents & achievements
            </span>
            <span>
              <Check size={15} /> CV upload with a SERA.et branded copy
            </span>
          </div>
        </div>
      </section>
      <NextOnSera />
      <section className="network-final-cta">
        <span className="network-kicker">THE NEXT CHAPTER STARTS WITH YOU</span>
        <h2>Bring what makes you, you.</h2>
        <p>Your ideas deserve a home. Your work deserves to be seen.</p>
        <Link href="/sign-up" className="network-button">
          Join Sera.et <ArrowUpRight size={18} />
        </Link>
        <Link href="/employer" className="network-text-link">
          Here to hire? Meet your next team. <ArrowRight size={16} />
        </Link>
      </section>
    </div>
  );
}
