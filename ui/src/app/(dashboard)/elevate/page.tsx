"use client";

import { RequireDashboardAccess } from "@/components/AccessDenied";
import ElevatePage from "@/components/elevate/elevate";

export default function Elevate() {
  return (
    <RequireDashboardAccess>
      <ElevatePage />
    </RequireDashboardAccess>
  );
}
