# TruckRoutePro - Agent Development Rules

## 1. File Modification Policy
- **NO CHANGES WITHOUT APPROVAL**: Never modify, create, or delete any source files without explicit, prior user approval.

## 2. Git & GitHub Workflow Rules
- **Needs-Verification**: All git commits and pushes must be marked with `[needs-verification]` in the commit message.
- **GitHub Issues**: 
  - If a task or commit does not have an active GitHub issue, create one.
  - Leave GitHub issues **open** after committing/pushing until the user explicitly instructs you to close them upon verification.

## 3. Engineering Standards
- Prioritize idiomatic Kotlin and Jetpack Compose best practices.
- Ensure all builds pass successfully (`app:assembleDebug`) after any approved changes.
