@documentation-analysis
Feature: Documentation Analysis
  As a support engineer
  I want to see whether the documentation answers what tenants asked
  So that I know which pages are missing or conflict with support's answers

  Scenario: A first visit starts the analysis and shows the results once they are saved
    Given the current date is "2026-09-09T12:00:00Z"
    And the backend returns support members including "engineer@example.com"
    And the documentation analysis page is enabled
    When user "engineer@example.com" logs in
    And user navigates to "Documentation Analysis"
    Then the documentation analysis should be requested from "2026-08-24" to "2026-09-04"
    And the documentation analysis should list "How do I rotate a deploy key?" as "Conflicts with support's answer"
    And no documentation analysis run should be shown

  Scenario: A user without access neither sees the page nor starts an analysis
    Given user "tenant@example.com" is NOT in leadership or support lists
    And the backend returns L2 support teams
    And the documentation analysis page is enabled
    When user "tenant@example.com" logs in
    Then user should NOT see "Documentation Analysis" navigation button
    When user navigates directly to "/documentation-analysis"
    Then user should see "Access Restricted" message
    And the documentation analysis should not have been requested
