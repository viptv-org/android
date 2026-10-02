# Domain documentation

Use the single-context layout: a root `GLOSSARY.md` and `docs/adr/` when they
exist. Read relevant entries before exploring; proceed when they are absent.
Use pinned design specifications, `SPEC.md` and shared core contracts as the
current authority. Never create a competing source or continuation policy.

For this work, Continue Watching means the profile's in-progress viewing queue;
the info page is title details and its episode selector; Choose a source means
the existing source picker. Add-ons and their configuration belong to accounts.

All subagents use `gpt-6.1-sol` with high reasoning (`high`) for implementation,
testing, investigation and independent review. Set both values explicitly;
restart retained workers using other settings before assigning further work.
