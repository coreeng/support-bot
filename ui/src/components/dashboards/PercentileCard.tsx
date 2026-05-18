// src/components/dashboards/PercentileCard.tsx
import { Clock } from "lucide-react";

interface PercentileCardProps {
  title: string;
  p50: string;
  p90: string;
  p75?: string;
  icon?: React.ReactNode;
  colorScheme?: "blue" | "green" | "purple";
}

export function PercentileCard({ title, p50, p90, p75, icon }: PercentileCardProps) {
  return (
    <div className="bg-card rounded-xl border p-6">
      <div className="mb-6 flex items-center gap-2">
        {icon ? <span className="text-muted-foreground">{icon}</span> : <Clock className="text-muted-foreground h-4 w-4" />}
        <h2 className="text-foreground text-base font-semibold">{title}</h2>
      </div>

      <div className={`grid ${p75 ? "grid-cols-3" : "grid-cols-2"} gap-4`}>
        <div>
          <p className="text-muted-foreground text-sm font-medium">P50</p>
          <p className="text-foreground mt-2 font-mono text-2xl font-semibold tracking-tight tabular-nums">{p50}</p>
        </div>
        {p75 && (
          <div>
            <p className="text-muted-foreground text-sm font-medium">P75</p>
            <p className="text-foreground mt-2 font-mono text-2xl font-semibold tracking-tight tabular-nums">{p75}</p>
          </div>
        )}
        <div>
          <p className="text-muted-foreground text-sm font-medium">P90</p>
          <p className="text-foreground mt-2 font-mono text-2xl font-semibold tracking-tight tabular-nums">{p90}</p>
        </div>
      </div>
    </div>
  );
}
