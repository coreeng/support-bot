Feature: Authorization Flow
  As a user with different roles
  I want to see the appropriate dashboards and access levels
  So that I can access only the features I'm authorized for

  Background:
    Given I am on the home page

  Scenario: Leadership member sees full access
    Given the backend returns leadership members including "leader@example.com"
    And the backend returns L2 support teams
    When user "leader@example.com" logs in
    Then user should see "Home" navigation button
    And user should see "Tickets" navigation button
    And user should see "Escalations" navigation button
    And user should see "Analytics & Operations" navigation button
    And user should see "SLA Dashboard" navigation button

  Scenario: Support Engineer sees full access
    Given the backend returns support members including "engineer@example.com"
    And the backend returns L2 support teams
    When user "engineer@example.com" logs in
    Then user should see "Home" navigation button
    And user should see "Tickets" navigation button
    And user should see "Escalations" navigation button
    And user should see "Analytics & Operations" navigation button
    And user should see "SLA Dashboard" navigation button

  Scenario: Regular tenant sees restricted view
    Given user "tenant@example.com" is NOT in leadership or support lists
    And the backend returns L2 support teams
    When user "tenant@example.com" logs in
    Then user should see "Home" navigation button
    And user should see "Tickets" navigation button
    And user should see "Escalations" navigation button
    But user should NOT see "Analytics & Operations" navigation button
    And user should NOT see "SLA Dashboard" navigation button

  Scenario: Escalation team member sees escalation features when selecting their team
    Given the backend returns L2 support teams including "core-platform"
    And user "escalation@example.com" is member of "core-platform"
    When user "escalation@example.com" logs in
    And user selects "core-platform" from team dropdown
    And user navigates to "Home"
    Then user should see "Escalations We Are Handling" section
    When user navigates to "Escalations"
    Then user should see "Escalated to My Team" table

  Scenario: Escalation team member does NOT see escalation features when selecting non-escalation team
    Given the backend returns L2 support teams including "core-platform"
    And user "escalation@example.com" is member of "core-platform" and "team-a"
    When user "escalation@example.com" logs in
    And user selects "team-a" from team dropdown
    And user navigates to "Home"
    Then user should NOT see "Escalations We Are Handling" section
    When user navigates to "Escalations"
    Then user should NOT see "Escalated to My Team" table

  Scenario: User with no teams sees empty home dashboard
    Given user has no teams
    When user logs in
    And user navigates to "Home"
    Then user should NOT see "Escalations We Are Handling" section
    And total tickets count should be "0"

  Scenario: Backend authorization endpoints fail gracefully
    Given the backend authorization endpoints return 500 error
    When user "user@example.com" logs in
    Then user should see "Home" navigation button
    And user should see "Tickets" navigation button
    And user should see "Escalations" navigation button
    But user should NOT see "Analytics & Operations" navigation button
    And user should NOT see "SLA Dashboard" navigation button

