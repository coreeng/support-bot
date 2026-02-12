# Coding Agent Guidelines

## Commit Messages

Always use [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/) for all commit messages.

### Format

```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

### Types

- `feat`: A new feature
- `fix`: A bug fix
- `docs`: Documentation only changes
- `style`: Changes that do not affect the meaning of the code (white-space, formatting)
- `refactor`: A code change that neither fixes a bug nor adds a feature
- `perf`: A code change that improves performance
- `test`: Adding missing tests or correcting existing tests
- `build`: Changes that affect the build system or external dependencies
- `ci`: Changes to CI configuration files and scripts
- `chore`: Other changes that don't modify src or test files

### Examples

```
feat(api): add user authentication endpoint
fix(ui): resolve null pointer in login form
refactor(api): extract CORS configuration to dedicated class
docs: update README with setup instructions
```

# Other AGENT.md / CLAUDE.md files

- `ui/AGENTS.md`
- `ui/CLAUDE.md`
- `api/AGENTS.md`
- `api/CLAUDE.md`
