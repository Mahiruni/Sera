export const guides = [
  {
    slug: "check-a-job-opportunity",
    title: "How to check a job opportunity before applying",
    description:
      "A practical checklist for checking the employer, vacancy source, application route and information you share.",
    section: "Careers",
    published: "2026-11-07",
    sections: [
      {
        title: "Start with the original announcement",
        body: "Save the original vacancy URL, the date you found it and the stated deadline. Look for the role on the employer’s own website or another channel the employer identifies as official. A copied poster or forwarded message is a lead to investigate, not proof that a vacancy is genuine. If details conflict, ask through a contact route independently located on the official site.",
      },
      {
        title: "Compare the details before you respond",
        body: "Check the organization name, role title, location, responsibilities and required experience. Read how the application is supposed to be submitted. An email address that resembles an organization’s name is not enough on its own: compare it with the published domain and application instructions. Keep a record of any clarification you receive. Do not assume a familiar company name means the sender represents it.",
      },
      {
        title: "Prepare a focused application",
        body: "Match your introduction to the responsibilities in the announcement. Use specific examples of work you actually completed, explain your contribution and check the requested file format. Keep an editable master CV and a clearly named copy for each application. A useful filename might include your name, the role and the date; it should not contain identity numbers or passwords.",
      },
      {
        title: "Share information in stages",
        body: "Before sending sensitive material, understand who is receiving it, why it is requested and how the submission channel works. Avoid placing identity documents, banking details or confidential work samples into a public comment or an unverified chat. If a request feels unusual, pause and verify it through an independent official contact route. This checklist is practical information, not a guarantee that a vacancy is safe.",
      },
      {
        title: "Keep a simple application record",
        body: "Track the original source, application date, documents sent and follow-up status in a private record. When a vacancy closes, update that record rather than continuing to treat the announcement as current. Sera.et’s recruitment area is presently a browser-only demonstration: its sample jobs and local application statuses are not live employer submissions.",
      },
    ],
  },
  {
    slug: "compare-university-college-options",
    title: "How to compare university, college and training options",
    description:
      "Build a clear comparison of programmes, admissions requirements, campuses and costs using current official information.",
    section: "Education",
    published: "2026-11-07",
    sections: [
      {
        title: "Define the programme you are comparing",
        body: "Write down the qualification, subject, study mode, teaching language and preferred location. Separate a university degree, a college qualification and a short training course rather than treating them as interchangeable. Identify what you want to learn and which programme outcomes matter to you. This makes it easier to compare the information institutions actually publish.",
      },
      {
        title: "Check the institution and programme separately",
        body: "Use the institution’s official website and current announcements. Where recognition or programme approval matters, verify it with the responsible official body instead of relying on a logo, social post or directory entry. Record the source and date of each check. Sera.et has not yet launched a verified institution directory and does not certify institutions or programmes.",
      },
      {
        title: "Ask clear admissions questions",
        body: "Compare entry requirements, the application route, documents requested, deadlines and any selection process. Ask whether an announcement applies to the particular campus, intake and programme you are considering. Keep copies of the official instructions and clarification messages. A requirement from an earlier intake may not describe the current one.",
      },
      {
        title: "Build a complete cost comparison",
        body: "Ask the institution for a current written fee breakdown. Keep tuition, registration, examination charges, materials, accommodation, transport and other relevant costs in separate rows. Note whether the figures apply per course, semester or year, and which costs are optional. Do not combine old screenshots and current prices without labelling their dates. This page does not publish or estimate institution fees.",
      },
      {
        title: "Compare learning support and daily practicalities",
        body: "Consider timetable, campus access, libraries, learning materials, accessibility arrangements and how students contact teaching or administrative staff. Ask how assignments, examinations, project supervision and results are handled. Use the same questions for each option so that an attractive advertisement does not replace a fair comparison.",
      },
      {
        title: "Keep the decision record up to date",
        body: "Create one row per programme and link each important fact to its source. Mark unanswered questions instead of filling them with assumptions. Recheck time-sensitive details before applying or making a commitment. Admissions, costs and official updates must come from current institutional or government information; Sera.et does not currently accept admissions applications.",
      },
    ],
  },
  {
    slug: "organize-assignments-exams-projects",
    title: "A practical plan for assignments, exams and student projects",
    description:
      "Organize deadlines, revisions, group responsibilities and study materials without exposing private academic information.",
    section: "Student resources",
    published: "2026-11-07",
    sections: [
      {
        title: "Turn the brief into a small plan",
        body: "Read the instructions and record the expected output, marking criteria, format and deadline. Break the work into research, an outline, a first draft, review and final submission. If an instruction is unclear, ask the teacher or institution through its approved channel. Keep the official brief alongside your working plan rather than relying on a summary from a group chat.",
      },
      {
        title: "Give files a clear revision history",
        body: "Use consistent filenames and keep a record of important changes. Distinguish working copies from submitted versions, and avoid overwriting your only copy during file conversion. Open the final file after exporting it to check page order, text, tables and attachments. Store the submission confirmation with the version that was actually sent.",
      },
      {
        title: "Make group responsibilities visible",
        body: "Agree who owns each task, how the group will review work and where the shared source of truth is kept. Record decisions and changes to the plan so that absent members can catch up. Share only material the group is authorized to access. A private workspace and an approved institutional submission system serve different purposes; do not assume one replaces the other.",
      },
      {
        title: "Prepare for tests and midterms",
        body: "Create a realistic review schedule around the published syllabus and timetable. Keep a list of topics you can explain and questions that still need work. Use materials your institution permits, and confirm examination arrangements through official announcements. Do not distribute restricted exam papers, classmates’ work or grades through public links.",
      },
      {
        title: "Know what Sera.et can do today",
        body: "This guide is available now. Academic accounts, institutional permissions, assignment submission, revision tracking, file conversion, exam delivery, calling and live chat are planned capabilities and are not enabled services. A future academic system will need secure identity, access controls, retention rules and institutional review before real student records are accepted.",
      },
    ],
  },
] as const;
export function findGuide(slug: string) {
  return guides.find((guide) => guide.slug === slug);
}
