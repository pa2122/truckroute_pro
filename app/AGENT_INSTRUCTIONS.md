# 🤖 Agent Directives & Operational Rules

*This document defines the strict operational rules and workflows that all AI agents must follow when contributing to this repository (`load_tracker_pro`). Agents should review this document upon initialization.*

---

## 1. 🛡️ IDE Tooling & File Modification Constraints
- **NO SHELL EDITS:** Strictly prohibited from using shell commands (`sed`, `awk`, `perl`, `echo >`, `cat <<`) to modify files. **Must** use built-in IDE write APIs (`replace_file_content`, `write_file`).
- **NO SHELL READS/SEARCHING:** Prohibited from using `cat`, `less`, `find`, or shell `grep`. **Must** use built-in IDE semantic index tools (`read_file`, `find_usages`, `find_files`, `grep`).
- **File Safety:** Always use `read_file` to verify the current state of a file before attempting a surgical replacement.

## 2. 💬 Communication & Execution Protocol
- **Answer First, Act Later:** Always address the user's questions or thoughts directly first. Provide an analysis or proposed design.
- **Wait for Confirmation:** Do **not** modify code, push Git commits, or make GitHub API calls (creating issues, milestones, etc.) until the user explicitly confirms or says "go ahead" / "do it".
- **Exceptions:** Reading files, semantic searching, and compiling code do not require prior permission.

## 3. 🐙 GitHub Issue Workflow
- **`needs-verification` Label:** When a feature or bug fix is implemented, pushed, and verified via unit tests/builds, you must comment on the GitHub issue detailing the changes.
- **Keep Issues Open:** You must leave the issue **`OPEN`** and apply the **`needs-verification`** label.
- **Explicit Closure Only:** Do **not** close a GitHub issue unless the user specifically states *"close it"* or *"verified"*.

## 4. 🧠 Domain-Specific Architecture Rules (Load Tracker Pro)
- **OCR Resilience:** When parsing dispatch screenshots with ML Kit, ensure regex patterns are flexible. Exclude dynamic headers (`Origin`, `Drop`, `unload #`) and ensure fields remain optional (non-blocking) so varying brokerage formats do not crash the parser.
- **Database Migrations:** All SQLite schema changes must be handled via explicit Room `Migration` objects in `AppDatabase.kt`.
- **UI Architecture:** Prioritize Jetpack Compose Material 3 standard components. Keep the main map interface clean by using compact floating bars or bottom-sheet HUDs.

## 5. 🗂️ Project Separation
- This repository (`load_tracker_pro`) is dedicated to the core dispatch, payroll, and auditing flatbed application.
- Commercial LVR navigation features (Google Routes API) belong in the separate `TruckRoutePro` repository. Do not mix code or feature branches between the two.