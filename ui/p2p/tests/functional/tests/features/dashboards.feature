Feature: SLA Dashboards

  Background:
    Given Dashboard API endpoints are mocked

  Scenario: User can navigate to dashboards page
    Given User navigates to the dashboards page
    Then The page title should be "SLA Dashboards"
    And The page subtitle should contain "Performance at a glance"

  Scenario: Date filter controls are visible and functional
    Given User navigates to the dashboards page
    Then Date filter quick buttons should be visible
    And Quick filter "Last 7 Days" should be clickable
    And Quick filter "Last Month" should be clickable
    And Quick filter "Last Year" should be clickable
    And Quick filter "Custom" should be clickable
    When User clicks on "Custom" quick filter
    Then Date range inputs should be visible

  Scenario: Tab sections are available for navigation (lazy loading)
    Given User navigates to the dashboards page
    Then All tab sections should be visible
    And Response SLAs tab should be visible
    And Resolution SLAs tab should be visible
    And Escalation SLAs tab should be visible
    And Weekly Trends tab should be visible

  Scenario: Lazy loading prevents API calls until tab is selected
    Given User navigates to the dashboards page
    And Response SLAs tab is active by default
    Then API calls for Response SLAs should be triggered
    And Dashboard content should be visible or show loading state

  Scenario: User can switch between tab sections
    Given User navigates to the dashboards page
    When User clicks on "Resolution SLAs" tab
    Then "Resolution SLAs" tab should be active
    And Section metric titles should be visible
    When User clicks on "Response SLAs" tab
    Then "Response SLAs" tab should be active

  Scenario: Only one tab section is visible at a time
    Given User navigates to the dashboards page
    When User clicks on "Resolution SLAs" tab
    Then Only Resolution SLAs content should be visible
    When User clicks on "Escalation SLAs" tab
    Then Only Escalation SLAs content should be visible

  Scenario: Date filter quick buttons change state
    Given User navigates to the dashboards page
    When User clicks on "Last 7 Days" quick filter
    Then "Last 7 Days" button should be active
    And Other quick filter buttons should not be active
    When User clicks on "Last Month" quick filter
    Then "Last Month" button should be active
    And "Last 7 Days" button should not be active

  Scenario: Custom date range can be set
    Given User navigates to the dashboards page
    When User clicks on "Custom" quick filter
    And User sets start date to "2025-01-01"
    And User sets end date to "2025-01-31"
    Then Custom date inputs should display the selected dates

  Scenario: Refresh button is available in expanded sections
    Given User navigates to the dashboards page
    When User clicks on "Response SLAs" tab
    Then Refresh button should be visible in the section

  Scenario: Empty state is shown when no data available
    Given Dashboard API returns empty data
    And User navigates to the dashboards page
    When User clicks on "Response SLAs" tab
    Then Empty state or zero values should be displayed

  Scenario: Loading state is shown while fetching data
    Given Dashboard API has delayed responses
    And User navigates to the dashboards page
    When User clicks on "Response SLAs" tab
    Then Loading indicators should be visible
    And Content should appear after loading completes

  Scenario: Navigation tabs work correctly
    Given User navigates to the dashboards page
    Then "Support" navigation tab should be active
    When User clicks on "Home" navigation tab
    Then User should be redirected to home page

