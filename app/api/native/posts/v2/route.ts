import {POST as publish} from "../route";
import type {NextRequest} from "next/server";
export const runtime="nodejs";
export async function POST(request:NextRequest){return publish(request);}
