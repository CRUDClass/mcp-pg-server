# Contributing

Thanks for your interest in contributing to mcp-pg-server.

## Getting Started

1. Fork the repository
2. Clone your fork: `git clone https://github.com/YOUR_USERNAME/mcp-pg-server.git`
3. 从 `dev` 创建分支：`git checkout -b feat/your-feature`（详见下方分支策略）
4. Make your changes
5. Build and verify: `mvn package -DskipTests`
6. Commit using conventional commits (`feat:`, `fix:`, `chore:`, `docs:`)
7. Push and open a Pull Request（功能/修复类 PR 目标选 `dev`，发布类 PR 目标选 `main`）

## Conventions

- **Java 21**, Spring Boot 3.5.14
- **Package**: `com.crudclass.mcpserver.pg`
- **Build**: `mvn package -DskipTests` (tests are not yet required but encouraged for new features)
- **Formatting**: Default IntelliJ IDEA Java conventions
- **Commit messages**: Follow [Conventional Commits](https://www.conventionalcommits.org/)

## Branch Strategy

本仓库采用双分支策略：

### 分支结构

| 分支 | 用途 | 保护 |
|------|------|------|
| `main` | 稳定发布线，仅通过 PR 合入 | ☑ 禁止直接推送 |
| `dev` | 日常开发集成线 | ☑ 禁止直接推送（建议） |

### 工作流程

```
main (稳定发布)     ← 打 tag 发布版本
dev  (开发集成)     ← 所有功能合入这里
 ├── feat/xxx       ← 新功能
 ├── fix/yyy        ← 修复
 └── docs/zzz       ← 文档
```

1. 从 `dev` 创建分支：`git checkout -b feat/特性名`
2. 在该分支上开发并提交
3. 推送分支并创建 PR，**目标分支选 `dev`**
4. 代码审查通过后合入 `dev`
5. 发布时：创建 PR 从 `dev` 合入 `main`，在 `main` 上打版本 tag

### 分支命名规则

| 前缀 | 用途 | 示例 |
|------|------|------|
| `feat/` | 新功能 | `feat/add-mysql-support` |
| `fix/` | Bug 修复 | `fix/null-pointer-on-empty-result` |
| `docs/` | 文档变更 | `docs/add-api-examples` |
| `refactor/` | 重构 | `refactor/extract-sql-parser` |
| `chore/` | 杂项（构建、依赖等） | `chore/update-spring-boot` |

### 提交信息规范

遵循 [Conventional Commits](https://www.conventionalcommits.org/)：

```
<type>(<scope>): <description>

feat(pg): add connection pool support
fix(pg): handle null result in listTables
docs(pg): 添加完整的代码文档注释
```

### 当前基线

当前 `main` 和 `dev` 同时处于 commit `91900ec`（文档注释提交），以此为 v1.0 基线。后续新功能从 `dev` 切分支，`main` 保持发布状态不变，直到下一个里程碑。

## Pull Request Process

1. 确认 PR 目标分支正确：功能/修复/文档类 PR 选 `dev`，发布类 PR 选 `main`
2. Ensure the build passes: `mvn package -DskipTests`
3. Update the README if your change affects usage
4. Link any related issues in the PR description
5. A maintainer will review and merge

## Code of Conduct

This project follows the [Contributor Covenant Code of Conduct](CODE_OF_CONDUCT.md).
