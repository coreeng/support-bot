"use client";

import { useEffect, useState } from "react";

export function useDebouncedValue<T>(value: T, delay = 250): T {
  const [debouncedValue, setDebouncedValue] = useState(value);

  useEffect(() => {
    if (typeof value === "string" && value.length === 0) {
      // Clearing is an immediate state transition; retaining the previous value
      // would let it reappear if the user starts a new search before the delay.
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setDebouncedValue(value);
      return;
    }
    const timeout = window.setTimeout(() => setDebouncedValue(value), delay);
    return () => window.clearTimeout(timeout);
  }, [delay, value]);

  return typeof value === "string" && value.length === 0 ? value : debouncedValue;
}
