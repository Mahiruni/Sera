import { pageMetadata } from "@/lib/seo";
import { notFound } from "next/navigation";
import { JobDetail } from "@/components/discovery";
export async function generateMetadata({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return pageMetadata(
    `/jobs/${encodeURIComponent(id)}`,
    "Sample Role Details — Recruitment Demo",
    "View a local demonstration role. This is not a verified live vacancy.",
  );
}
export default async function Page({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  if (!/^\d+$/.test(id)) notFound();
  return <JobDetail key={id} id={Number(id)} />;
}
