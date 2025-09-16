# Contributing to the support bot

We welcome contributions of all forms — from bug reports and documentation improvements to new features.  
This guide outlines the process and expectations for contributing to ensure consistency and maintainability.

## Requirements

To contribute to this project, you will need:

- **Java 21**: Required to build and run the project.
- **Gradle**: The project uses the Gradle wrapper (`gradlew`) for builds, tests and code generation.
- **Docker Desktop or alternative**: Used for running the local database and optionally building Docker images.

## Code Guidelines

- **Unit tests**: Every module and function must be covered with unit tests.
- **Functional tests**: Required for all new features and bug fixes.
- **Integration tests**: Add integration tests when introducing or modifying any third-party service or external dependency.
 
> Pull requests without appropriate test coverage will not be accepted.

## Contributing

### Update the README

Make sure to update the `README.md` if necessary.

### How to Raise a PR

* Fork the repository on GitHub and clone your fork locally.
* Create a feature branch, e.g: `git checkout -b feature_xyz`.
* Create your changes, run tests and linting (see the service [README.md](api/service/README.md)) and commit locally.
  * Use clear, descriptive [commit messages](https://www.conventionalcommits.org/en/v1.0.0/). Example:
    * `feat: add support for X`
    * `fix: correct bug in Y`
    * `docs: update local run guide in readme`
    * `test: add functional test for Z`
* Push them to your GitHub fork via `git push -u origin feature_xyz`. This will create the `feature_xyz` branch within your GitHub fork.
* Once your branch is pushed, open a Pull Request from your fork’s branch to the main repository’s `main` branch.

### Fill in the PR form

* Title: Short and descriptive (e.g: `fix: handle null values in function myFunc()`)
* Description: Explain what the change does and why it is needed.
* Checklist: Confirm the tests (unit/functional/integration) are included as applicable and passing.

### Submit the PR

* Maintainers will review your submission, provide feedback if needed, and merge it once it meets the project’s requirements. We commit
to review the PR within 3 working days.

## Coding standards/style

* We use [PMD](https://pmd.github.io/) for linting 
* Use meaningful variable and function names
* Keep functions small and focused

### Reporting Bugs/Issues

When reporting Bugs or Issues, please raise a new GitHub issue in the repository, adding a `Bug` label.
Please include whether the issue is consistently reproducible, steps to reproduce, expected behaviour and environment details.

# Contributor Code of Conduct

We are committed to fostering a welcoming and harassment-free community for everyone, regardless of age, body size, disability, ethnicity, gender identity and expression, level of experience, education, socio-economic status, nationality, personal appearance, race, religion, or sexual orientation.

## Our Standards

**Examples of positive behavior include:**
- Using welcoming and inclusive language
- Respecting different viewpoints and experiences
- Accepting constructive feedback gracefully
- Showing empathy and kindness toward others

**Examples of unacceptable behavior include:**
- The use of sexualized language or imagery
- Trolling, personal attacks, or derogatory comments
- Public or private harassment
- Publishing others’ private information without consent
- Other conduct that could reasonably be considered unprofessional

## Enforcement

Project maintainers are responsible for clarifying and enforcing this Code of Conduct. In cases of unacceptable behavior, maintainers may take appropriate action, including warnings, temporary bans, or permanent bans.

## Attribution

This Code of Conduct is adapted from the [Contributor Covenant](https://www.contributor-covenant.org), version 2.1.
