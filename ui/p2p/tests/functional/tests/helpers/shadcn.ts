import { Locator, Page, expect } from '@playwright/test';

/**
 * Helpers for driving shadcn / Radix UI controls from Playwright steps.
 *
 * The Support Bot UI no longer uses native <select> elements; controls are
 * Radix-based (Select renders as a button with role="combobox", DropdownMenu
 * as role="menu", Tabs as role="tablist"). These helpers wrap the
 * "click trigger → click option/menuitem" pattern so step files don't have
 * to repeat it.
 */

/** Open a Radix Select trigger and click the option whose accessible name matches `optionName`. */
export async function selectShadcnOption(page: Page, trigger: Locator, optionName: string | RegExp) {
    await trigger.waitFor({ state: 'visible', timeout: 5000 });
    await trigger.click();
    const option = page.getByRole('option', { name: optionName });
    await option.first().waitFor({ state: 'visible', timeout: 5000 });
    // cmdk re-renders option rows as keyboard-focus shifts (data-selected toggles),
    // which makes Playwright's stability check repeatedly retry until the element
    // detaches. force:true skips the stability check; the option is still visible
    // and clickable, just animating its selected state.
    await option.first().click({ force: true });
}

/** Read the visible text shown in a Radix Select trigger button. */
export async function readShadcnSelectValue(trigger: Locator): Promise<string> {
    await trigger.waitFor({ state: 'visible', timeout: 5000 });
    return ((await trigger.textContent()) || '').trim();
}

/** Open a Radix DropdownMenu trigger and click the menu item whose accessible name matches. */
export async function selectDropdownMenuItem(page: Page, trigger: Locator, itemName: string | RegExp) {
    await trigger.waitFor({ state: 'visible', timeout: 5000 });
    await trigger.click();
    const item = page.getByRole('menuitem', { name: itemName });
    await item.first().waitFor({ state: 'visible', timeout: 5000 });
    await item.first().click();
}

/**
 * Detect the active sort direction of a column whose <th> contains an
 * ArrowUp / ArrowDown / ArrowUpDown lucide icon. Returns:
 *   - 'asc' if the header has the ArrowUp icon (`.lucide-arrow-up`)
 *   - 'desc' if the header has the ArrowDown icon (`.lucide-arrow-down`)
 *   - 'none' otherwise (ArrowUpDown idle state)
 */
export async function readSortDirection(header: Locator): Promise<'asc' | 'desc' | 'none'> {
    if ((await header.locator('.lucide-arrow-up').count()) > 0) return 'asc';
    if ((await header.locator('.lucide-arrow-down').count()) > 0) return 'desc';
    return 'none';
}

/** Click a tab trigger by its accessible name. */
export async function clickTab(page: Page, tabName: string | RegExp) {
    const tab = page.getByRole('tab', { name: tabName });
    await tab.waitFor({ state: 'visible', timeout: 5000 });
    await tab.click();
}

/** Assert a shadcn Select trigger contains the given text. */
export async function expectShadcnSelectValue(trigger: Locator, expected: string | RegExp) {
    await expect(trigger).toContainText(expected);
}
