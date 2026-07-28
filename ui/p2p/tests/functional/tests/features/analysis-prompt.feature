Feature: Analysis Prompt Visibility
  As a support engineer
  I want to view the prompt the backend uses for analysis
  So that I can understand how support threads are classified

  Scenario: Support engineer can view the analysis prompt
    Given the backend returns support members including "engineer@example.com"
    And the analysis feature is enabled with prompt "You are a support analyst. Classify each thread."
    When user "engineer@example.com" logs in
    And user navigates directly to "/knowledge-gaps"
    And user clicks "View Prompt" button
    Then the analysis prompt dialog should show "You are a support analyst. Classify each thread."

  Scenario: Regular tenant cannot access the Support Area Summary page
    Given user "tenant@example.com" is NOT in leadership or support lists
    And the analysis feature is enabled with prompt "You are a support analyst. Classify each thread."
    When user "tenant@example.com" logs in
    And user navigates directly to "/knowledge-gaps"
    Then user should see "Access Restricted" message
