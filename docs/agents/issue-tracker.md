# Issue tracker: local Markdown

Use local Markdown tickets at `.scratch/<feature>/issues/<NN>-<slug>.md`,
with a plan in the same feature directory when needed. Do not create GitHub
issues for these tickets. Existing GitHub links are references, not the active
tracker.

Number tickets in dependency order: `01-description.md`, `02-loading.md`, and
so on. A ticket records `Blocked by`, `Status`, `Owner`, acceptance criteria,
implementation commits and actual validation evidence. Sort open, unblocked
tickets by number; mark them `in-progress` when claimed and `done` after their
acceptance checks pass. Keep completed tickets in place to retain stable links.
Use the triage vocabulary in [triage-labels.md](triage-labels.md) for readiness.

Update a ticket's evidence before claiming success; private screenshots, logs,
credentials and account records remain ignored.

Cross-repository tickets list the affected repositories and separate commits.
Commit only the ticket's work. Push shared design first, then clients and the
dashboard, then the backend pins; never force-push or include unrelated edits.
