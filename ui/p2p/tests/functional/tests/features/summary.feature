@summary
Feature: Support Summary window presets
  As a support engineer
  I want the window presets to mean whole business periods
  So that "last week" is the same Monday-to-Friday whichever day I open the page

  Background:
    Given the backend returns support members including "engineer@example.com"
    And the summary page is enabled

  Scenario: The default window is the two business weeks before the current one
    Given the current date is "2026-09-09T12:00:00Z"
    When user "engineer@example.com" logs in
    And user navigates directly to "/summary"
    Then the summary should be requested from "2026-08-24" to "2026-09-04"
    And the summary window should show "Last 2 weeks"
    And the summary window should show "24 Aug – 4 Sept 2026"

  Scenario: Last Week is Monday to Friday of the previous week
    Given the current date is "2026-09-09T12:00:00Z"
    When user "engineer@example.com" logs in
    And user navigates directly to "/summary"
    And user selects the "Last Week" summary window
    Then the summary should be requested from "2026-08-31" to "2026-09-04"
    And the summary window should show "31 Aug – 4 Sept 2026"

  Scenario: Last Week still means the previous week on a weekend
    Given the current date is "2026-09-13T20:00:00Z"
    When user "engineer@example.com" logs in
    And user navigates directly to "/summary?dateFilter=lastWeek"
    Then the summary should be requested from "2026-08-31" to "2026-09-04"
    And the summary window should show "Last week"

  Scenario: Last Month is the previous calendar month
    Given the current date is "2026-09-09T12:00:00Z"
    When user "engineer@example.com" logs in
    And user navigates directly to "/summary"
    And user selects the "Last Month" summary window
    Then the summary should be requested from "2026-08-01" to "2026-08-31"
    And the summary window should show "1 – 31 Aug 2026"

  Scenario: Without team groups the summary reads as it did before them
    Given the current date is "2026-09-09T12:00:00Z"
    When user "engineer@example.com" logs in
    And user navigates directly to "/summary"
    Then no team group selector should be shown
    And no summary scope should be shown

  Scenario: The summary can be narrowed to a team group
    Given the current date is "2026-09-09T12:00:00Z"
    And the summary has the team group "payments-area" labelled "Payments"
    When user "engineer@example.com" logs in
    And user navigates directly to "/summary"
    And user narrows the summary to the "Payments" team group
    Then the summary should be requested for the team group "payments-area"
    And the summary scope should show "Area Payments"

  Scenario: A shared link opens the summary narrowed to its team group
    Given the current date is "2026-09-09T12:00:00Z"
    And the summary has the team group "payments-area" labelled "Payments"
    When user "engineer@example.com" logs in
    And user navigates directly to "/summary?teamGroup=payments-area"
    Then the summary should be requested for the team group "payments-area"
    And the summary should be requested from "2026-08-24" to "2026-09-04"
    And the summary scope should show "Area Payments"

