import {NextResponse} from "next/server";
export const dynamic="force-dynamic";
/** Retired channel: old bridge clients fall back to the single stable updater. */
export async function GET(){return NextResponse.json({available:false},{headers:{"Cache-Control":"private, no-store"}});}
