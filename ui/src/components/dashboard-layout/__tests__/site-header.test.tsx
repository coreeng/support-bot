import { render, screen } from '@testing-library/react'
import { SiteHeader } from '../site-header'

jest.mock('next/navigation', () => ({
    usePathname: () => '/tickets',
}))

jest.mock('../../ui/sidebar', () => ({
    SidebarTrigger: () => <button data-testid="sidebar-trigger" />,
}))

jest.mock('../../TeamSelector', () => {
    const Mock = () => <div data-testid="team-selector" />
    return { __esModule: true, default: Mock }
})

jest.mock('../user-dropdown', () => ({
    UserDropdown: () => <div data-testid="user-dropdown" />,
}))

jest.mock('../../ui/mode-toggle', () => ({
    ModeToggle: () => <button data-testid="mode-toggle" aria-label="Toggle theme" />,
}))

describe('SiteHeader', () => {
    it('renders the ModeToggle inside a `hidden sm:flex` wrapper so it is hidden on mobile', () => {
        // Regression guard: previously the toggle was wrapped in `<Button asChild>`
        // which forwards props onto its child's root element. ModeToggle's root is a
        // DropdownMenu (not a DOM element), so the responsive class was discarded
        // and the toggle leaked onto mobile breakpoints.
        render(<SiteHeader />)

        const toggle = screen.getByTestId('mode-toggle')
        const wrapper = toggle.parentElement as HTMLElement
        expect(wrapper).not.toBeNull()
        expect(wrapper.className).toContain('hidden')
        expect(wrapper.className).toContain('sm:flex')
    })

    it('does not wrap ModeToggle in a `<button>` (which would discard the responsive class)', () => {
        render(<SiteHeader />)

        const toggle = screen.getByTestId('mode-toggle')
        const wrapper = toggle.parentElement as HTMLElement
        expect(wrapper.tagName).toBe('DIV')
    })
})
