# Tesoriere

A personal savings and budgeting app for Android and Windows, offline first. It carries the money logic of the earlier Sci-Fin app into a new codebase and records the shopping receipts that [Chatelaine](https://github.com/haziaferi/Chatelaine) exports, splitting each payment across its categories and pairing it with its bank-statement row so nothing is counted twice.

`SPEC.md` is the authority on what the app does; `SPEC-history.md` records each change to it. The repository holds the spec only until the port begins.

## How changes land

How changes land: through a pull request whose checks (`publish-check` and CodeQL) pass, merged on the owner's go-ahead; nothing is pushed straight to `main`; no version tags. The merge method is picked per pull request and named in its description:

- squash, by default, for feature and fix pull requests;
- rebase-merge when each commit was deliberately kept clean and self-contained;
- a merge commit only to sync branches, never for feature work.

The first commits reached `main` by one direct push, because an empty repository has no branch for a pull request to target.

## Personal data

The repository is public. Real statements, receipts, backups and databases never enter it (`.gitignore`); test fixtures are synthetic. `tools/publish_check.py` runs on every push and pull request (`.github/workflows/publish-check.yml`) and as the local pre-push hook.

## Licence

GPL-3.0: see `LICENSE`.
