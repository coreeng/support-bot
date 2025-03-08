Feature: Basic
  It's polite to greet someone

  Scenario: registry impact returns ok
    Given a rest service
    When I call the registry impact endpoint
    Then an ok response is returned
