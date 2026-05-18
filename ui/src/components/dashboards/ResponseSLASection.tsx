// src/components/dashboards/ResponseSLASection.tsx
import { createTimeBuckets, formatHoursToDHMS, TimeBucket } from "@/lib/utils";
import { AlertCircle } from "lucide-react";
import { MetricCard } from "./MetricCard";
import { PercentileCard } from "./PercentileCard";
import { RefreshButton } from "./RefreshButton";
import { TimeBucketChart } from "./TimeBucketChart";

interface ResponseSLASectionProps {
  firstResponsePercentiles: { p50: number; p90: number } | undefined;
  durationDistribution: number[] | undefined;
  unattendedQueries: { count: number } | undefined;
  isDistributionLoading: boolean;
  isUnattendedLoading: boolean;
  isRefreshing: boolean;
  onRefresh: () => void;
}

export function ResponseSLASection({
  firstResponsePercentiles,
  durationDistribution,
  unattendedQueries,
  isDistributionLoading,
  isUnattendedLoading,
  isRefreshing,
  onRefresh,
}: ResponseSLASectionProps) {
  // Create time-based buckets for better visualization
  const timeBuckets: TimeBucket[] = durationDistribution ? createTimeBuckets(durationDistribution) : [];
  return (
    <div>
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h2 className="text-foreground text-base font-semibold">Response Performance</h2>
          <p className="text-muted-foreground text-sm">Track first response times and unattended queries</p>
        </div>
        <RefreshButton onRefresh={onRefresh} isRefreshing={isRefreshing} />
      </div>

      {/* Cards - 50/50 Layout on Large Screens */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <PercentileCard
          title="Time to First Response"
          p50={formatHoursToDHMS((firstResponsePercentiles?.p50 || 0) / 3600)}
          p90={formatHoursToDHMS((firstResponsePercentiles?.p90 || 0) / 3600)}
          colorScheme="blue"
        />

        <MetricCard
          title="Total Unattended Queries"
          value={unattendedQueries?.count ?? 0}
          description="Queries without a corresponding ticket"
          isLoading={isUnattendedLoading}
          colorScheme="orange"
          icon={<AlertCircle className="h-4 w-4" />}
        />
      </div>

      <div className="mt-6">
        <TimeBucketChart title="Time to First Response Duration Distribution" data={timeBuckets} isLoading={isDistributionLoading} />
      </div>
    </div>
  );
}
