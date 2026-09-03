"use client";

import LoadingSkeleton from "@/components/LoadingSkeleton";
import StatsPage from "@/components/stats/stats";
import TicketsPage from "@/components/tickets/tickets";
import { useAuth } from "@/hooks/useAuth";

export default function Home() {
  const { isLoading } = useAuth();

  if (isLoading) {
    return <LoadingSkeleton />;
  }

  return (
    <div className="space-y-6">
      <StatsPage />
      <TicketsPage embedded />
    </div>
  );
}
