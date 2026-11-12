import {POST as publish} from "../../media/route";
import type {NextRequest} from "next/server";
export const runtime="nodejs";
export async function POST(request:NextRequest){return publish(request);}
