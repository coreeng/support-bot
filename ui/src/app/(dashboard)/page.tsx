"use client";

import LoadingSkeleton from "@/components/LoadingSkeleton";
import StatsPage from "@/components/stats/stats";
import { useAuth } from "@/hooks/useAuth";

export default function Home() {
  const { isLoading } = useAuth();

  if (isLoading) {
    return <LoadingSkeleton />;
  }

  return <StatsPage />;
}
