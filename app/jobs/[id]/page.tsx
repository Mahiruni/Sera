import { notFound } from "next/navigation";
import { JobDetail } from "@/components/discovery";
export const metadata = { title: "Role details" };
export default async function Page({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  if (!/^\d+$/.test(id)) notFound();
  return <JobDetail id={Number(id)} />;
}
