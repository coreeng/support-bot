# Agent Guidelines for `ui/`

## Package manager

Use **yarn** for all package operations. Do not use npm or pnpm.

## Formatting

**Always run `yarn format` after every UI change.** No exceptions — every edit to anything under `ui/` ends with `yarn format`, even one-line tweaks.

`yarn build` runs `yarn format:check` first and fails fast if any file is not formatted, so unformatted code will not pass CI. Run `yarn format` locally to fix.

## Validation commands

Run these before committing:

| Command             | Purpose                                                     |
| ------------------- | ----------------------------------------------------------- |
| `yarn format`       | Prettier (must be run after every change)                   |
| `yarn format:check` | Prettier check (also runs automatically as part of `build`) |
| `yarn lint`         | ESLint                                                      |
| `yarn test`         | Jest                                                        |
| `yarn build`        | Next.js production build (runs `format:check` + type check) |

---

# UI Design System — STRICT

This UI is a single, fully-tokenized design system. **Every new page, table, modal, button, card, badge, or filter MUST follow these rules.** Visual consistency across pages is non-negotiable.

If you find yourself wanting to deviate, the answer is no — open a discussion first. "It looks better this way" is not a reason; consistency is the feature.

## 1. Tokens only — no raw color classes

**FORBIDDEN** anywhere in `src/`:

```
bg-gray-*  text-gray-*  border-gray-*  ring-gray-*
bg-slate-*  bg-zinc-*  bg-neutral-*  bg-stone-*
bg-red-*  bg-orange-*  bg-amber-*  bg-yellow-*  bg-lime-*  bg-green-*
bg-emerald-*  bg-teal-*  bg-cyan-*  bg-sky-*  bg-blue-*  bg-indigo-*
bg-violet-*  bg-purple-*  bg-fuchsia-*  bg-pink-*  bg-rose-*
(same for text-, border-, ring- variants)
```

Same rule for raw hex (`#3b82f6`, `#22c55e`, etc.) — these break in dark mode.

**Use these tokens instead:**

| Purpose                 | Token                                                                                 |
| ----------------------- | ------------------------------------------------------------------------------------- |
| Page background         | `bg-background`                                                                       |
| Card surface            | `bg-card` (paired with `border` and `text-card-foreground`)                           |
| Subtle surface          | `bg-muted` (text on it: `text-muted-foreground`)                                      |
| Primary text            | `text-foreground`                                                                     |
| Secondary / helper text | `text-muted-foreground`                                                               |
| Brand color             | `bg-primary` / `text-primary` / `text-primary-foreground`                             |
| Success                 | `bg-success` / `text-success` (use `bg-success/10` or `/15` for tints)                |
| Warning                 | `bg-warning` / `text-warning`                                                         |
| Destructive / error     | `bg-destructive` / `text-destructive`                                                 |
| Info                    | `bg-info` / `text-info`                                                               |
| Border                  | `border` (uses `--border`) — do NOT add a second `border` class with no side modifier |
| Input border            | `border border-input`                                                                 |
| Focus ring              | `ring-ring`                                                                           |
| Recharts series         | `var(--chart-1)` … `var(--chart-11)` (NOT raw hex)                                    |

For tints use slash opacity: `bg-success/10`, `bg-destructive/15`, etc.

**Forbidden double-border**: `border-t border` is a bug — `border` alone means 1px on all four sides, so `border-t border` doubles up. Use only `border-t`, `border-b`, etc. when you want a single edge.

## 2. Use shadcn primitives — never native form controls

Native HTML elements styled by hand drift visually. Always reach for the shadcn equivalent:

| Don't use                          | Use                                                                            |
| ---------------------------------- | ------------------------------------------------------------------------------ |
| `<select>`                         | `Select` (`@/components/ui/select`)                                            |
| `<input type="text">`              | `Input` (`@/components/ui/input`)                                              |
| `<button>`                         | `Button` (`@/components/ui/button`)                                            |
| Custom flex tabs                   | `Tabs` / `TabsList` / `TabsTrigger` / `TabsContent`                            |
| `<table>` chrome                   | `Table` / `TableHeader` / `TableBody` / `TableHead` / `TableRow` / `TableCell` |
| Hand-rolled checklist filter       | `MultiSelect`                                                                  |
| Hand-rolled single picklist filter | `SingleSelectFilter`                                                           |

`Button` always carries one of: `variant="default" | "outline" | "ghost" | "secondary" | "destructive"` and `size="sm" | "default" | "lg" | "icon"`. Add `cursor-pointer` to anything clickable that's not already a `Button` (e.g. table-header `th` cells used for sort).

## 3. Page anatomy

Every top-level page renders inside `SidebarInset` (already wired). Inside the page body:

```tsx
<div className="space-y-6">
  <div className="flex items-start justify-between gap-4">
    <div>
      <h1 className="text-2xl font-bold text-foreground">{Page Title}</h1>
      <p className="text-muted-foreground text-sm">{One-line description}</p>
    </div>
    {/* right-aligned page-level controls (date filter Select, etc.) */}
  </div>

  {/* Tabs, if the page has them */}
  <Tabs defaultValue={...} className="space-y-4">
    <TabsList>
      <TabsTrigger value="..." className="cursor-pointer">
        <Icon className="h-4 w-4" /> Label
      </TabsTrigger>
    </TabsList>
    <TabsContent value="..." className="space-y-6">
      {/* tab body */}
    </TabsContent>
  </Tabs>
</div>
```

- **NO** `min-h-screen`, `max-w-[1600px]`, `sticky top-0`, `shadow-md`, or hand-rolled chrome on the page root. The shell handles all of that.
- **NO** card-style wrapper around the entire page body.
- Outer rhythm is `space-y-6`; inside `Tabs`, the wrapper uses `space-y-4` and `TabsContent` uses `space-y-6`.

## 4. Section anatomy (inside a page or tab)

```tsx
<div>
  <h2 className="text-base font-semibold text-foreground">{Section Title}</h2>
  <p className="text-sm text-muted-foreground">{Optional description}</p>
</div>
```

- Section title: `text-base font-semibold text-foreground`. Do NOT use muted color, smaller sizes, or `font-medium` for section titles.
- Description: `text-sm text-muted-foreground`. No `mt-0.5`, no `text-xs`.

## 5. Cards

Every card is a flat tokenized panel — no gradients, no shadows, no `ring-1`, no hand-picked colors:

```tsx
<div className="bg-card rounded-xl border p-6">
  <h2 className="text-foreground mb-4 text-base font-semibold">{Title}</h2>
  {/* body */}
</div>
```

- Padding is `p-6` (or `p-5` only when explicitly tight). Don't mix `p-4` and `p-6` in the same row of cards.
- Heading rhythm: `text-base font-semibold text-foreground mb-4`.
- For chart cards keep the same heading; the chart sits below, not above, the title.

### StatCard (KPI tile)

For numeric KPI tiles, the established pattern is:

```tsx
<div className="bg-card relative overflow-hidden rounded-xl border p-6">
  <div className="bg-{accent}/15 absolute -top-4 -right-4 h-24 w-24 rounded-full" />
  <div className="bg-{accent}/15 absolute -right-6 -bottom-6 h-20 w-20 rounded-full" />
  <div className="relative">
    <p className="text-muted-foreground mb-2 text-sm font-medium">{Label}</p>
    <p className="text-{semanticOrForeground} font-mono text-3xl font-semibold tracking-tight tabular-nums">{value}</p>
  </div>
</div>
```

- **Every KPI in the same row MUST have a unique accent.** Don't repeat colors in a single row of cards.
- Accent palette: `primary`, `info`, `success`, `warning`, `destructive`, `purple` (`bg-chart-4/15`), `indigo` (`bg-chart-9/15`).
- Accent is a stable identity — do NOT flip the accent based on value state. Flip the value text color (`text-warning`, `text-destructive`) instead.

## 6. Numbers

Anywhere you display a metric, count, duration, percentage, or any quantity:

```
font-mono tabular-nums
```

For large display values: `font-mono text-3xl font-semibold tracking-tight tabular-nums`. For inline cell numbers: `font-mono tabular-nums text-sm`. Never display a metric in a proportional font.

## 7. Tables

Use the shadcn `Table` family (`@/components/ui/table`). The base `TableHead` already renders `text-xs font-bold uppercase tracking-wider` — **do NOT re-apply** these classes; just pass column-specific classes (alignment, width).

If you can't use `Table` for a specific reason and must drop to a raw `<table>`, the headers MUST still match:

```tsx
<th className="text-foreground px-4 py-2 text-left text-xs font-bold uppercase">{Label}</th>
```

- Header background: `bg-muted` (on `<thead>` or `TableHeader`).
- Body row hover: `hover:bg-accent` (or `hover:bg-muted/50` for the shadcn TableRow default).
- Dividers: `divide-y` (no `divide-slate-100` etc.).
- Empty / loading / error states use `p-16 text-center text-muted-foreground text-sm`.

### Sortable headers

Use the established pattern: `ArrowUp` / `ArrowDown` when active, `ArrowUpDown` (muted) when inactive. The whole `<th>` is the click target with `cursor-pointer select-none`.

### Pagination

Right-aligned, single rhythm — used everywhere:

```tsx
<div className="flex items-center justify-end gap-4 px-6 py-3 border-t">
  <span className="text-sm text-muted-foreground">Page {page} of {total}</span>
  <Button variant="outline" size="sm" disabled={page === 1} onClick={...}>Previous</Button>
  <Button variant="outline" size="sm" disabled={page === total} onClick={...}>Next</Button>
</div>
```

Do NOT introduce a numbered-page button strip. Do NOT use `aria-label="Previous page"` icon-only buttons; the words "Previous" / "Next" are the labels.

## 8. Filters

For a single picklist filter that lives next to a table or page header (e.g., "Status", "Team", "Impact"), use **`SingleSelectFilter`** (`@/components/ui/single-select-filter`). For multi-value filters use **`MultiSelect`**.

Date range pickers: shadcn `Select` for the preset (`Last Week / Last Month / Custom`), with `Input type="date"` siblings shown only when `custom` is selected. Match widths: `w-[160px]` for the preset, `w-[150px]` for each date input.

## 9. Charts (recharts)

Every chart MUST share these conventions so dark mode works:

- `<CartesianGrid stroke="var(--border)" strokeDasharray="3 3" />`
- `<XAxis stroke="var(--border)" tick={{ fill: 'var(--muted-foreground)', fontSize: 11 }} />` (same on `<YAxis>`)
- `<Tooltip` with:
  ```tsx
  contentStyle={{ background: 'var(--popover)', color: 'var(--popover-foreground)', border: '1px solid var(--border)', borderRadius: '8px', boxShadow: '0 4px 12px rgba(0,0,0,0.15)' }}
  labelStyle={{ color: 'var(--popover-foreground)' }}
  itemStyle={{ color: 'var(--popover-foreground)' }}
  cursor={{ stroke: 'var(--border)', fill: 'var(--accent)' }}
  ```
- Series colors: `var(--chart-1)` through `var(--chart-11)`. Pick a stable mapping per chart and stick to it. No raw hex.

## 10. Modals (Dialog)

Use `Dialog` (`@/components/ui/dialog`). Title is `DialogTitle` rendered at `text-xl` (avoid `text-2xl`/`text-3xl` — modals should not feel bigger than the page).

Section labels inside the modal: `text-sm font-medium text-foreground` (NOT muted — labels stay readable). Inputs use `Input` / `Select` / `MultiSelect` consistent with the page; widths line up.

Footer buttons: `<DialogFooter>` with `Button` variants (`outline` for cancel, `default` for save). Don't add wrapper widths that force extra horizontal space.

## 11. Spacing rhythm

| Context                  | Spacing                                                                |
| ------------------------ | ---------------------------------------------------------------------- |
| Outer page body          | `space-y-6`                                                            |
| Tabs wrapper             | `space-y-4`                                                            |
| TabsContent              | `space-y-6`                                                            |
| Card padding             | `p-6` (chart cards), `p-5` rare exception                              |
| Card heading → body      | `mb-4` (between `<h2>` and the chart/table/value)                      |
| Stack of cards in a grid | `gap-4` for `grid-cols-N gap-4`; `gap-6` for prominent two-up sections |

## 12. Animations

Use Tailwind's `animate-in` utilities for expand/collapse, fade, and zoom. Default duration for expand/collapse panels is `duration-[400ms]` — fast feels janky, slow feels broken. Add `transition-all` for smooth state changes (hover, active).

## 13. Theme

`next-themes` is wired with `attribute="class"` and `defaultTheme="system"`. Never hard-code `dark:` overrides for color — let tokens flip automatically. The only valid use of `dark:` modifiers is for asset swaps (e.g., a logo that has a separate dark variant), not for color tweaks.

## 14. Cursor

Anything clickable that isn't a `Button` MUST have `cursor-pointer`. This includes sortable `<th>`s, table rows that open a modal on click, custom collapse triggers, etc.

---

# Pre-PR checklist

Before opening a PR that touches the UI:

- [ ] No raw color classes (`bg-gray-500`, `text-blue-600`, `#3b82f6`, etc.) — `grep -rE 'bg-(gray|slate|red|blue|green|...)-[0-9]'` returns nothing in `src/`
- [ ] Every `<select>` / `<input>` / `<button>` is a shadcn component
- [ ] Every numeric value uses `font-mono tabular-nums`
- [ ] Every section heading uses `text-base font-semibold text-foreground`
- [ ] Every card uses `rounded-xl border bg-card p-6` (or `p-5` if explicitly justified)
- [ ] No `shadow-*`, no `ring-1 ring-slate-*`, no gradients on cards
- [ ] Pagination is the right-aligned `Page X of Y · Previous · Next` pattern
- [ ] All KPI cards in a row have unique accent colors
- [ ] Charts pass tokens to `Tooltip`, `XAxis`, `YAxis`, `CartesianGrid`
- [ ] `yarn lint && yarn test && yarn build` all pass

If your change diverges from any of the above, it's wrong — fix the change, not the rules.
