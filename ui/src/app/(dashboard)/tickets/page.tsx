import { redirect } from "next/navigation";

/** The tickets table now lives on the home page; keep old links and bookmarks working. */
export default function Tickets() {
  redirect("/");
}
