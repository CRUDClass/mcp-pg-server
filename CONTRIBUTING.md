# Contributing

Thanks for your interest in contributing to mcp-pg-server.

## Getting Started

1. Fork the repository
2. Clone your fork: `git clone https://github.com/YOUR_USERNAME/mcp-pg-server.git`
3. Create a branch: `git checkout -b feat/your-feature`
4. Make your changes
5. Build and verify: `mvn package -DskipTests`
6. Commit using conventional commits (`feat:`, `fix:`, `chore:`, `docs:`)
7. Push and open a Pull Request

## Conventions

- **Java 21**, Spring Boot 3.5.14
- **Package**: `com.crudclass.mcpserver.pg`
- **Build**: `mvn package -DskipTests` (tests are not yet required but encouraged for new features)
- **Formatting**: Default IntelliJ IDEA Java conventions
- **Commit messages**: Follow [Conventional Commits](https://www.conventionalcommits.org/)

## Pull Request Process

1. Ensure the build passes: `mvn package -DskipTests`
2. Update the README if your change affects usage
3. Link any related issues in the PR description
4. A maintainer will review and merge

## Code of Conduct

This project follows the [Contributor Covenant Code of Conduct](CODE_OF_CONDUCT.md).
