 import { NextResponse } from "next/server";
import { publicFetch } from "../_lib/public-fetch";

export const dynamic = "force-dynamic";
export const revalidate = 0;

export async function GET() {
  try {
    const response = await publicFetch("/auth/providers", { cache: "no-store" });
    if (!response.ok) {
      return NextResponse.json(
        { providers: [] },
        { headers: { "Cache-Control": "no-store" } }
      );
    }

    const body = await response.json();
    return NextResponse.json(body, { headers: { "Cache-Control": "no-store" } });
  } catch {
    return NextResponse.json(
      { providers: [] },
      { headers: { "Cache-Control": "no-store" } }
    );
  }
}
