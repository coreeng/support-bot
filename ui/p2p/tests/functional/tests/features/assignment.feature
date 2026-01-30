Feature: Ticket Assignment
  Background:
    Given assignment feature is enabled
    And support members are available
    And user is a support engineer

  Scenario: View assignee in Ticket Workbench table
    Given there are 2 opened tickets assigned to "support-engineer-1@example.com"
    When user navigates to Analytics & Operations
    And user selects "Ticket Workbench" tab
    Then user should see "Support Engineer" column in the table
    And table should display assignee "support-engineer-1@example.com"

  Scenario: Filter tickets by assignee
    Given there are 3 opened tickets assigned to "support-engineer-1@example.com"
    And there are 2 opened tickets assigned to "support-engineer-2@example.com"
    When user navigates to Analytics & Operations
    And user selects "Ticket Workbench" tab
    And user filters by assignee "support-engineer-1@example.com"
    Then table should show 3 tickets
    And all displayed tickets should have assignee "support-engineer-1@example.com"

  Scenario: Edit assignee in ticket modal
    Given User is a support engineer
    When User navigates to the tickets page
    And User clicks on the first ticket
    Then Ticket details modal should appear
    And assignee select should be visible
    When user changes assignee to "support-engineer-2@example.com"
    And User clicks "Save Changes"
    Then success message should appear for edit

  Scenario: Bulk reassign tickets
    Given there are 3 opened tickets assigned to "support-engineer-1@example.com"
    And there are 2 opened tickets assigned to "support-engineer-2@example.com"
    When user navigates to Analytics & Operations
    And user selects "Ticket Workbench" tab
    And user expands "Bulk Reassign Tickets" section
    And user selects "support-engineer-1@example.com" from assignee filter
    And user selects "support-engineer-2@example.com" as reassign target
    Then bulk reassign count should show "3"
    When user clicks "Reassign All" button
    And user clicks "Yes, Reassign" button
    Then success message should appear

  Scenario: Assignment feature disabled
    Given assignment feature is disabled
    And support members are available
    And user is a support engineer
    And there are 2 opened tickets assigned to "support-engineer-1@example.com"
    When user navigates to Analytics & Operations
    And user selects "Ticket Workbench" tab
    Then user should not see "Support Engineer" column
    And user should not see "Bulk Reassign Tickets" section

