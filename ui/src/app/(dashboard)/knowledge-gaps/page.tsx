import { redirect } from "next/navigation";

/** Support Area Summary is retired in favour of the Support Summary page; keep old links working. */
export default function KnowledgeGaps() {
  redirect("/summary");
}
