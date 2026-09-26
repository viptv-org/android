# Design-first cross-platform synchronization

This process maintains the product contract as TV implementations evolve. The current authority is the versioned specification and asset source in this repository. It separates three facts: an app faithfully contains its pinned contract, its pin is current with a chosen design revision, and its rendered behavior meets that contract. Each requires different evidence.

## Change and delivery sequence

1. Read the affected visual screen, shared components, behavior inventory and platform rebuild contract together. Search existing design and implementation issues; [TRACKING.md](TRACKING.md) links their owners. Specify the before/after experience, states, remote actions, cancellation/return focus, assets and affected platforms in design first. Correct extraction mistakes in their owning section with immutable source evidence. Mark new behavior proposed until implementation and validation support promotion.
2. Commit the reviewed design change and use its full immutable commit SHA in implementation issues and each adopting app's DESIGN_REF. Each issue lists affected screen/state IDs, adapters and exceptions. An app may intentionally lag while its implementation catches up; that lag remains explicit. A design commit does not automatically claim adoption by every platform.
3. Import that exact revision into the app using its synchronization tooling. Review the pin, specification diff and asset additions/removals together. Regenerate derived assets from design-owned sources; retain packaged assets for offline/reproducible builds. Avoid manual edits to generated/vendored contract files. If implementation exposes a missing design rule, amend design first and import a new immutable revision.
4. Implement all affected surfaces, then run the agreed final validation batch. Record functional, visual and device results independently per affected screen/state. An exception records the reason, discoverable equivalent, acceptance scenario and approval/reference in design; decoder limitations do not excuse unrelated presentation drift.
5. Publish the implementation revision and evidence with its design pin. Update platform status and linked tickets only to the level demonstrated. Before a release, compare against the selected current design revision and either adopt the outstanding relevant changes or record deliberate lag and its impact. Never silently move a pin to latest.

## Shared TV-web snapshot contract

The shared Tizen/Vizio app records a full design commit in DESIGN_REF. Its design-contract/ directory contains a vendored specification snapshot and assets/FILES.json provenance, plus snapshot-lock.json with file hashes tied to that pin. Packaged Roku assets in public/assets are imported from the pinned design assets/roku/roku/images tree. The snapshot is an implementation input; authoritative edits remain here.

From the tv-web repository:

```sh
node scripts/design-sync.mjs sync ../design <full-design-commit-sha>
node scripts/design-sync.mjs check
node scripts/design-sync.mjs freshness ../design
```

The sync command imports the explicitly supplied git revision, not uncommitted local files. The offline check verifies pin/snapshot/packaged-asset integrity and runs through `npm run design:check` during build. CI can check a clean app checkout without credentials for another private repository. A modified vendored document or asset must fail integrity validation until an intentional new import is reviewed.

Freshness is a separate comparison against the supplied design checkout's HEAD. That checkout must first be brought to the intended reviewed revision; an old checkout cannot establish that the pin is current on GitHub. Offline CI success establishes consistency with the pin, not freshness, visual similarity or complete UX coverage. Hashes are a reproducibility/drift check, not a substitute for code review or semantic acceptance. An intentional design update includes implementation/parity review even when the mechanical import succeeds.

Android, Roku and future clients use the same design-first pin and evidence process. Shared TV-web automation does not imply those repositories already have equivalent snapshot tooling. Each client keeps ownership of its platform renderer, build and acceptance evidence; shared design does not require a shared UI framework.

## Per-platform parity record

Each implementation's parity matrix records one row per affected screen/state, referencing the normative section and stable acceptance ID. Shared TV-web and Android use the design system reference screen names (viptv-design-system/reference/screens/index.json) as state IDs, plus their TESTING.md. Record at least:

| Field | Required content |
| --- | --- |
| Contract | Design SHA, screen/state ID, normative section and intended inputs/content |
| Implementation | Full app SHA, platform configuration and linked execution issue |
| Functional evidence | Scenario, result, test/run link and cancellation/return-focus coverage |
| Visual evidence | Matched reference/app state, reference revision, resolution, reproducible metric and inspected differences; unmeasured when absent |
| Device evidence | Exact platform/model/OS and run outcome, or explicitly unverified |
| Disposition | Pending, implemented/unverified, verified for the stated scope, failed, or approved exception with reference |

Use separate Tizen and Vizio columns for device evidence even when browser UI/controller evidence is shared. A skipped test names its platform reason. A failed or unmeasured state remains visible after successful build or publication. The aggregate may say which groups passed; it cannot convert partial evidence into universal parity. Keep private captures out of this repository and release assets; store durable textual measurements and run references instead.

## Future Figma-first authoring

The owner raised Figma-first design as a future workflow. It is a proposed authoring option, not an additional dependency for this rebuild and not the present source of truth. No Figma file is currently declared authoritative by this contract.

If adopted, first approve its ownership and a concrete mapping: canonical TV frame, components/variants, token names, screen/state IDs, focus states, platform exceptions and export/provenance rules. Record the exact Figma file/node/version references alongside each design change. Export approved specs/tokens/assets into this repository and commit them before implementations advance DESIGN_REF. Behavior, timing, cancellation and recovery still need explicit versioned contracts where a static frame is insufficient.

A mutable Figma link alone cannot replace an immutable implementation pin. If Figma and the committed contract disagree, resolve that difference in a reviewed design update before implementation. Adoption must define which fields Figma owns and which remain text-owned, plus a reproducible export/check path, so teams do not manually maintain two competing sets of geometry. Until that decision, improve the existing versioned contract and snapshot workflow directly.
