# Agent Guidelines for api/

## Code Formatting

This project uses **Spotless** with Palantir Java Format.

### Before Committing Java Changes

Always verify formatting:

```bash
./gradlew spotlessCheck
```

### Fix Formatting Issues

To automatically fix formatting:

```bash
./gradlew spotlessApply
```

## Static Analysis

**ErrorProne** runs automatically during compilation. All warnings are treated as errors (`-Werror`).

**NullAway** is enabled for null-safety analysis on the `com.coreeng.supportbot` package.

**Checkstyle** enforces naming conventions:
- Enum constants must use `UPPER_SNAKE_CASE` (e.g., `MY_CONSTANT`, not `myConstant`)

## Quick Reference

| Task | Command |
|------|---------|
| Check formatting | `./gradlew spotlessCheck` |
| Fix formatting | `./gradlew spotlessApply` |
| Check naming conventions | `./gradlew checkstyleMain` |
| Compile with ErrorProne | `./gradlew compileJava` |
| Full build | `./gradlew build` |
