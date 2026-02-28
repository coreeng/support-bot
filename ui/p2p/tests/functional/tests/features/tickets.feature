Feature: Tickets Dashboard
  As a support team member
  I want to view and filter tickets
  So that I can manage support requests effectively

  Scenario: User can view tickets list
    Given Tickets API endpoints are mocked
    When User navigates to the tickets page
    Then Tickets table should be visible
    And Tickets table should have required headers

  Scenario: Tickets display correct data
    Given Tickets API endpoints are mocked with sample data
    When User navigates to the tickets page
    Then Tickets should display status information
    And Tickets should display team information
    And Tickets should display impact information
    And Tickets should display tags information
    And Tickets should display escalated to information

  Scenario: User can filter tickets by status
    Given Tickets API endpoints are mocked with mixed statuses
    When User navigates to the tickets page
    And User selects "opened" from status filter
    Then Only opened tickets should be displayed

  Scenario: User can filter tickets by escalated to team
    Given Tickets API endpoints are mocked with escalations and varied dates
    When User navigates to the tickets page
    And User selects "Support Team" from escalated to filter
    Then Only tickets escalated to "Support Team" should be displayed

  Scenario: User can sort tickets by Opened At and Closed At
    Given Tickets API endpoints are mocked with escalations and varied dates
    When User navigates to the tickets page
    And User sorts tickets by opened at
    Then Tickets should be sorted by opened at descending
    When User toggles opened at sort
    Then Tickets should be sorted by opened at ascending
    When User sorts tickets by closed at
    Then Tickets should be sorted by closed at descending

  Scenario: User can view ticket details
    Given Tickets API endpoints are mocked with sample data
    When User navigates to the tickets page
    And User clicks on the first ticket
    Then Ticket details panel should appear
    And Ticket details should show the ticket ID

  Scenario: Empty state shows when no tickets
    Given Tickets API returns empty list
    When User navigates to the tickets page
    Then Empty state message should be visible

  Scenario: Support engineer can edit ticket
    Given User is a support engineer
    And Tickets API endpoints are mocked with sample data
    When User navigates to the tickets page
    And User clicks on the first ticket
    Then Ticket edit modal should appear
    And Modal should show editable fields
    When User changes ticket status to "closed"
    And User selects impact "medium"
    And User selects team "Support"
    And User adds tag "urgent"
    And User clicks "Save Changes"
    Then Ticket should be updated successfully
    And Modal should close

  Scenario: Non-support engineer sees read-only ticket view
    Given User is not a support engineer
    And Tickets API endpoints are mocked with sample data
    When User navigates to the tickets page
    And User clicks on the first ticket
    Then Ticket edit modal should appear
    And Modal should show read-only view
    And "Save Changes" button should not be visible
    And "Close" button should be visible
