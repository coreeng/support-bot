import { redirect } from "next/navigation";

type SearchParams = Record<string, string | string[] | undefined>;

/**
 * The tickets table now lives on the home page; keep old links and bookmarks working.
 * The home page reads the same URL filter keys, so the query string is forwarded as-is.
 */
export default async function Tickets({ searchParams }: { searchParams: Promise<SearchParams> }) {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(await searchParams)) {
    if (value === undefined) continue;
    for (const item of Array.isArray(value) ? value : [value]) {
      params.append(key, item);
    }
  }
  const qs = params.toString();
  redirect(qs ? `/?${qs}` : "/");
}
