import { Badge } from "@/components/ui/badge";
import type { TroubleshootingMode } from "@/lib/types/troubleshooting";

export function ModeBadge({ mode }: { mode: TroubleshootingMode }) {
  return <Badge variant={mode === "AUTOMATIC" ? "team" : "outline"}>{mode === "AUTOMATIC" ? "Automatic" : "Manual"}</Badge>;
}
