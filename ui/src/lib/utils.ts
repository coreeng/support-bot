import { clsx, type ClassValue } from "clsx"
import { twMerge } from "tailwind-merge"

// Tailwind utility
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

// Re-export utility functions
export * from './utils/query-params'
export * from './utils/format'
export * from './utils/chart'
export * from './utils/distribution'
export * from './utils/running-average'