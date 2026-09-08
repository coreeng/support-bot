Feature: Prompt Visibility
  As a support engineer
  I want to view the prompts the backend uses
  So that I can understand how tickets are classified and how the summary is generated

  Scenario: The prompt dialog defaults to the ticket classification prompt
    Given the backend returns support members including "engineer@example.com"
    And the summary page is enabled with classification prompt "You are a support analyst. Classify each thread." and summary prompt "You are a support analyst. Summarise the window."
    When user "engineer@example.com" logs in
    And user navigates directly to "/summary"
    And user clicks "View Prompts" button
    Then the prompt dialog should show "You are a support analyst. Classify each thread."

  Scenario: The dropdown switches to the summary generation prompt
    Given the backend returns support members including "engineer@example.com"
    And the summary page is enabled with classification prompt "You are a support analyst. Classify each thread." and summary prompt "You are a support analyst. Summarise the window."
    When user "engineer@example.com" logs in
    And user navigates directly to "/summary"
    And user clicks "View Prompts" button
    And user selects the "Summary generation" prompt
    Then the prompt dialog should show "You are a support analyst. Summarise the window."
