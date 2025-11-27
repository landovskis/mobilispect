# Spec-Kit Setup Guide

## Overview

This project uses [GitHub Spec-Kit](https://github.com/github/spec-kit), an open-source toolkit for spec-driven development. Spec-Kit enables structured feature planning, implementation tracking, and quality assurance through slash commands integrated with Claude Code.

## Installation

The `specify-cli` tool is installed globally using the `uv` package manager:

```bash
uv tool install specify-cli --from git+https://github.com/github/spec-kit.git
```

### Current Installation

- **CLI Version**: 0.0.22
- **Template Version**: 0.0.86
- **Released**: 2025-11-26
- **Location**: `~/.local/bin/specify`

### Verify Installation

```bash
specify version
specify check
```

## Project Structure

Spec-Kit creates and manages the following directories:

- **`.specify/`** - Spec-Kit configuration and artifacts
  - `memory/constitution.md` - Project governance and principles
  - `scripts/bash/` - Automation scripts for workflows
  - `templates/` - Markdown templates for specs, plans, tasks, and checklists

- **`.claude/commands/`** - Claude Code slash commands
  - `speckit.constitution.md` - Update project constitution
  - `speckit.specify.md` - Create feature specifications
  - `speckit.plan.md` - Generate implementation plans
  - `speckit.tasks.md` - Break down features into tasks
  - `speckit.checklist.md` - Generate quality checklists
  - `speckit.analyze.md` - Cross-artifact consistency analysis
  - `speckit.clarify.md` - Identify spec ambiguities
  - `speckit.implement.md` - Execute implementation tasks
  - `speckit.taskstoissues.md` - Convert tasks to GitHub issues

## Available Commands

Use these slash commands in Claude Code:

- `/speckit.constitution` - Update project constitution
- `/speckit.specify <feature>` - Create feature specification
- `/speckit.plan` - Generate technical implementation plan
- `/speckit.tasks` - Break down plan into actionable tasks
- `/speckit.checklist` - Generate feature-specific quality checklist
- `/speckit.analyze` - Analyze cross-artifact consistency
- `/speckit.clarify` - Identify underspecified areas
- `/speckit.implement` - Execute all tasks from tasks.md
- `/speckit.taskstoissues` - Convert tasks to GitHub issues

## Development Workflow

1. **Feature Planning**
   ```bash
   /speckit.specify <feature description>
   /speckit.plan
   /speckit.tasks
   ```

2. **Quality Assurance**
   ```bash
   /speckit.checklist
   /speckit.analyze
   ```

3. **Implementation**
   ```bash
   /speckit.implement
   ```

4. **Issue Tracking**
   ```bash
   /speckit.taskstoissues
   ```

## Constitution Enforcement

The project constitution (`.specify/memory/constitution.md`) defines non-negotiable principles including:

- Test-Driven Development (80%+ coverage)
- Architecture Decision Records (ADRs)
- Cross-platform UX consistency
- Performance standards (200ms API, 60fps mobile)
- Observability & monitoring (Grafana Cloud)
- Modular monolith architecture (Spring Modulith)
- Accessibility (WCAG 2.1 AA)

All features must comply with constitutional requirements. See `CLAUDE.md` for complete guidance.

## Updating Spec-Kit

To update to the latest version of Spec-Kit:

```bash
uv tool upgrade specify-cli --from git+https://github.com/github/spec-kit.git
```

To update project templates and commands:

```bash
specify init . --force --ai claude
```

## Resources

- [GitHub Spec-Kit Repository](https://github.com/github/spec-kit)
- [Spec-Driven Development Blog Post](https://github.blog/ai-and-ml/generative-ai/spec-driven-development-with-ai-get-started-with-a-new-open-source-toolkit/)
- [Project Constitution](../.specify/memory/constitution.md)
- [Claude Code Configuration](../CLAUDE.md)
