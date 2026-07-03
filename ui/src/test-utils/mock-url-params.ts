/**
 * Shared jest mock for the `useUrlParams` hook.
 *
 * Mirrors the real hook's URL semantics closely enough for component tests:
 * a `useState`-backed store that re-renders on `setParams`, and treats an
 * empty-string update as "remove the param" (falls back to the key's
 * default), matching `useUrlParams.ts`'s `router.replace` stripping behavior.
 *
 * Usage in a test file (imported under a `mock`-prefixed alias so
 * babel-plugin-jest-hoist allows referencing it inside `jest.mock`, whose
 * factory runs before the module's own imports are hoisted):
 * ```ts
 * import { useMockUrlParams as mockUseUrlParams } from "@/test-utils/mock-url-params";
 *
 * jest.mock("../../../lib/hooks/useUrlParams", () => ({
 *   ...jest.requireActual("../../../lib/hooks/useUrlParams"),
 *   useUrlParams: mockUseUrlParams,
 * }));
 * ```
 */

import { useState } from "react";

type StringRecord = Record<string, string>;

export function useMockUrlParams<T extends StringRecord>(defaults: T): [T, (updates: Partial<T>) => void] {
  const [params, setParamsState] = useState<T>(defaults);
  const setParams = (updates: Partial<T>) => {
    setParamsState((prev) => {
      const next: StringRecord = { ...prev };
      for (const [key, value] of Object.entries(updates)) {
        next[key] = value === "" ? defaults[key as keyof T] : (value as string);
      }
      return next as T;
    });
  };
  return [params, setParams];
}
