import type { ReactNode } from "react";
import { Compass, type LucideIcon } from "lucide-react";

/** The same compact introduction on community, career and information pages. */
export function PageIntro({
  title,
  description,
  icon: Icon = Compass,
  children,
}: {
  title: string;
  description?: string;
  icon?: LucideIcon;
  children?: ReactNode;
}) {
  return (
    <header className="sera-page-intro page-heading">
      <div className="sera-page-intro-copy">
        <span className="sera-page-symbol" aria-hidden="true">
          <Icon size={23} strokeWidth={1.7} />
        </span>
        <div>
          <h1>{title}</h1>
          {description && <p className="subtext">{description}</p>}
        </div>
      </div>
      {children && <div className="sera-page-intro-actions">{children}</div>}
    </header>
  );
}
