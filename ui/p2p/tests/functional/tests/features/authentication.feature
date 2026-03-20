Feature: Authentication and Session Expiry
  As a user
  I want to be redirected to login when my session expires
  So that I can re-authenticate and return to what I was doing

  Scenario: User is redirected to login when accessing protected page without session
    Given I have no active session
    When I try to access the tickets page
    Then I should be redirected to the login page
    And the callback URL should be "/tickets"

  Scenario: Authenticated user can access protected pages
    Given I am logged in as a user
    When I navigate to the home page
    Then I should see the home page
    And I should see "Support" navigation button

  Scenario: Authenticated user is redirected away from login page
    Given I am logged in as a user
    When I try to visit the login page
    Then I should be redirected to the home page

  Scenario: Login page with callback URL parameter is accessible
    When I visit the login page with callback URL "/knowledge-gaps"
    Then the page should load without errors

