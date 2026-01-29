Feature: Escalation Team Workflows
  As an escalation team member
  I want to see escalation-specific dashboards and metrics
  So that I can efficiently handle escalations to my team

  Background:
    Given the backend has escalation data

  Scenario: Escalation team member sees split dashboard on Home
    Given user is an escalation team member for "core-platform"
    When user logs in and selects "core-platform" from dropdown
    And user navigates to the "Home" tab
    Then user should see "Escalations We Are Handling" section
    And user should see "Tickets We Own" section
    And user should see escalation metrics in the handling section

  Scenario: Escalation team member sees escalation dashboard on Escalations tab
    Given user is an escalation team member for "core-platform"
    When user logs in and selects "core-platform" from dropdown
    And user navigates to the "Escalations" tab
    Then user should see "Escalated to My Team" dashboard
    And user should see status filter for escalations
    And user should see impact filter for escalations

  Scenario: Regular tenant does NOT see escalation dashboards
    Given user is a regular tenant in "team-a"
    When user logs in
    And user navigates to the "Home" tab
    Then user should NOT see "Escalations We Are Handling" section
    And user should only see regular home dashboard

  Scenario: Escalation team switching to non-escalation team
    Given user is an escalation team member for "core-platform" and also in "team-a"
    When user logs in and selects "core-platform" from dropdown
    And user navigates to the "Home" tab
    Then user should see "Escalations We Are Handling" section
    When user switches to "team-a" from dropdown
    And user navigates to the "Home" tab
    Then user should NOT see "Escalations We Are Handling" section
    And user should only see regular home dashboard

  Scenario: Escalation dashboard shows only team-specific escalations
    Given user is an escalation team member for "core-platform"
    And there are escalations for "core-platform" and "other-team"
    When user logs in and selects "core-platform" from dropdown
    And user navigates to the "Escalations" tab
    And user views the "Escalated to My Team" dashboard
    Then user should only see escalations for "core-platform"
    And user should NOT see escalations for "other-team"

  Scenario: Escalation filtering by status works
    Given user is an escalation team member for "core-platform"
    And there are ongoing and resolved escalations for "core-platform"
    When user logs in and selects "core-platform" from dropdown
    And user navigates to the "Escalations" tab
    When user filters escalations by status "ongoing"
    Then user should only see ongoing escalations
    When user filters escalations by status "resolved"
    Then user should only see resolved escalations

  Scenario: Escalation filtering by impact works
    Given user is an escalation team member for "core-platform"
    And there are high and medium impact escalations for "core-platform"
    When user logs in and selects "core-platform" from dropdown
    And user navigates to the "Escalations" tab
    When user filters escalations by impact "high"
    Then user should only see high impact escalations

  Scenario: Leadership member does NOT see escalation features when viewing as Leadership
    Given user is both Leadership and escalation team member for "core-platform"
    When user logs in and selects "leadership" from dropdown
    And user navigates to the "Home" tab
    Then user should NOT see "Escalations We Are Handling" section
    And user should have full access to all teams
    When user switches to "core-platform" from dropdown
    And user navigates to the "Home" tab
    Then user should see "Escalations We Are Handling" section



