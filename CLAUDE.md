# Claude Code — Project Instructions

## Off-limits files

Never read, cat, or print the contents of `.env` (repo root) or any `.env` file anywhere in this repository. Hard rule — do not read `.env` even if asked to verify secrets or debug connector configuration.

## Autonomy

Claude operates in **delegated mode** for all tasks in this repository unless the user explicitly asks for guided or report-only behaviour.

In delegated mode:
- Apply all critical and important fixes automatically without asking for confirmation.
- Propose and apply changes to BPMN files, test JSON, schemas, CI config, and tooling directly.
- Commit changes when a logical unit of work is complete, following the commit conventions below.
- Run `npx bpmnlint --config .bpmnlintrc <file>` after any BPMN edit and fix all errors before opening in Camunda Modeler.
- Run `mvn test` (or flag that it should run in CI) after test file edits.

Ask before acting only for:
- Force-pushing or resetting branches.
- Deleting files that may contain unreplaced work.
- Opening or merging pull requests.
- Any action that affects systems outside this repository (email, Slack, external APIs).

## Process variable schema

There are two group-level schemas, each covering all processes in their group:
- `solutions/variables.schema.json` — vocabulary for all solution processes
- `quick-start/variables.schema.json` — vocabulary for all quick-start processes

Both point `$schema` at the absolute URL: `https://raw.githubusercontent.com/camunda/camunda-8-tutorials/main/schema/process-variables.meta-schema.json`

The schema system is described in full in GitHub issue #95. Claude maintains it autonomously:

- **Add a new process variable:** add it to the appropriate group schema with `title`, `type`, `description`, and `examples`. For object variables add `properties`. `examples` is required at every nesting level: every entry with a `type` field must carry `examples`, including nested object `properties`.
- **Check on PR:** run `--check --group solutions|quick-start` and `--dead --group solutions|quick-start`; fix unregistered variable errors; post dead-variable candidates as PR comments.
- **Naming violations:** fix in BPMN output targets, DMN outputs, form keys, and test JSON in one commit (order: form keys → BPMN → DMN → test JSON). Update the schema entry name to match.
- **Structural similarity:** run `node tools/schema/find-similar-variables.mjs`; evaluate whether cross-group consolidation into `schema/$defs/` is warranted at Jaccard ≥ 0.70.
- **Cascade:** when `schema/` changes, both group schemas are affected; run both checks before merging.
- **`enforceNaming`:** set to `true` in `.variable-schema.config.json` after issue #96 (snake_case migration) is merged.

## BPMN and process changes

- Run `npx bpmnlint --config .bpmnlintrc <file>` after every BPMN edit; fix all errors before committing.
- When resolving a merge conflict on a lint-fix PR, run `git checkout origin/main -- <file>` then `npm run lint` to confirm the main version already carries the fix. If lint passes, the conflict is resolved; don't attempt a manual 3-way merge of BPMN XML.
- Do not rename BPMN element `id` attributes — they break test references.
- Only propose `name` attribute changes and test JSON updates.
- After fixing BPMN names, open in Camunda Modeler: `open -a "Camunda Modeler" <file>`.

## Commit conventions

- Format: `<type>(<scope>): <short description>`
- Types: `feat`, `fix`, `chore`, `docs`, `test`, `ci`
- Scope: solution name or tool area (e.g. `absence-request`, `schema`, `ci`)
- Always append `Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>`

## CI

Two workflows:
- `bpmn-lint.yml` — runs on BPMN/DMN changes; must pass before merge.
- `variable-schema.yml` — runs on diagram, form, test, and schema changes; check and naming-lint jobs must pass; dead-scan and similarity are advisory.
- `test-suites.yml` — runs Maven tests for solutions with a `test/pom.xml`.

## Tools available

| Tool | Command |
|------|---------|
| Extract variables | `node tools/schema/extract-variables.mjs --extract\|--check\|--dead --group solutions\|quick-start` |
| Lint naming | `node tools/schema/lint-naming.mjs --group solutions\|quick-start` |
| Find similar variables | `node tools/schema/find-similar-variables.mjs` |
| BPMN lint | `npx bpmnlint --config .bpmnlintrc <file>` |
| Render screenshot | `node /Users/eric.lundberg/GitHub/camunda-ai-dev-kit/tools/camunda-viewer/screenshot.mjs <file>` |
