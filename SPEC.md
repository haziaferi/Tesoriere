# Tesoriere spec

Scope: Tesoriere, the owner's personal savings and budgeting app for Android and Windows: a selective port of Sci-Fin's logic that also records the receipts Chatelaine exports.

History: `SPEC-history.md`, one line per change. It is never a source of current rules.

Kinds: O owner rule · D decided · I inherited · A assumed · Q open.

## Rule index

| ID | Name | Section |
|---|---|---|
| D-R01 | Jobs and how often | 1. Purpose and jobs |
| D-R02 | Who uses it | 1. Purpose and jobs |
| O-R03 | A standalone app | 2. The project |
| O-R04 | A selective port of Sci-Fin | 2. The project |
| D-R05 | Repository and licence | 2. The project |
| D-R06 | Platforms | 2. The project |
| D-R07 | Languages | 2. The project |
| D-R08 | Offline first | 2. The project |
| D-R09 | Minimum Android version | 2. The project |
| O-R10 | No valence colour | 3. Owner rules and visible design |
| O-R11 | No grading, no streaks | 3. Owner rules and visible design |
| O-R12 | Inform, never block | 3. Owner rules and visible design |
| O-R13 | Visible decisions are the owner's | 3. Owner rules and visible design |
| O-R14 | No stock look | 3. Owner rules and visible design |
| Q-R16 | Visible design | 3. Owner rules and visible design |
| D-R17 | Port source | 4. The port from Sci-Fin |
| D-R18 | Ported pieces | 4. The port from Sci-Fin |
| D-R19 | Sci-Fin backup import | 4. The port from Sci-Fin |
| D-R20 | Money and time | 5. Money model |
| D-R49 | Savings | 5. Money model |
| D-R50 | Goals hold money out of Savings | 5. Money model |
| D-R51 | The ledger | 5. Money model |
| D-R52 | Direct edits log the change | 5. Money model |
| D-R53 | Undoing a mistaken entry | 5. Money model |
| D-R21 | Payment | 5. Money model |
| D-R22 | Source and parts are separate | 5. Money model |
| D-R54 | What a payment's source moves | 5. Money model |
| D-R23 | Parts add up | 5. Money model |
| D-R24 | Any payment can be split | 5. Money model |
| D-R25 | Spent is derived | 5. Money model |
| D-R55 | Goal calculator | 6. Goals and the monthly plan |
| D-R56 | Where a goal fits | 6. Goals and the monthly plan |
| D-R57 | Gentler monthly amounts | 6. Goals and the monthly plan |
| D-R58 | Deleting a goal | 6. Goals and the monthly plan |
| D-R59 | Plan against spent | 6. Goals and the monthly plan |
| D-R26 | Statement import | 7. Statement import |
| D-R27 | The receipt contract | 8. Chatelaine receipts |
| D-R28 | Receipt folder | 8. Chatelaine receipts |
| D-R29 | Picking up receipts | 8. Chatelaine receipts |
| D-R30 | Receipt files Tesoriere cannot take | 8. Chatelaine receipts |
| D-R31 | Receipt review | 8. Chatelaine receipts |
| D-R32 | Category links | 8. Chatelaine receipts |
| D-R33 | Closing a review | 8. Chatelaine receipts |
| D-R34 | Changing a link | 8. Chatelaine receipts |
| D-R35 | A receipt's source | 8. Chatelaine receipts |
| D-R36 | Store links | 9. One payment, two sources |
| D-R37 | Pairing rule | 9. One payment, two sources |
| D-R38 | Whichever arrives first | 9. One payment, two sources |
| D-R39 | Difference part | 9. One payment, two sources |
| D-R40 | Pairing by hand | 9. One payment, two sources |
| D-R41 | Deleting a receipt payment | 10. Edge cases |
| D-R42 | Deleting a linked category | 10. Edge cases |
| D-R43 | Test plan | 11. Test plan |
| D-R44 | Rollout phases | 12. Rollout |
| D-R45 | Price comparison | Out of scope |
| D-R46 | Households in v1 | Out of scope |
| D-R47 | Currency conversion in v1 | Out of scope |
| D-R48 | Splitting a receipt by payment | Out of scope |

## Glossary

| Term | Meaning |
|---|---|
| Payment | One movement of money out, with one date, one total, one source and one or more parts. Every expense in Tesoriere is a payment. |
| Part | One category's share of a payment, in cents. A payment's parts add up to its total. |
| Split | A payment with more than one part. |
| Source | Where a payment's money came from: Savings, a Goal, or a credit or loan Account. |
| Receipt | One payment made at one store, as Chatelaine closes it (Chatelaine's spec, rule 124). |
| Receipt file | The file Chatelaine writes for one receipt, in the format of Chatelaine's spec, rule 123. |
| Receipt folder | The folder the user chooses, where Chatelaine writes receipt files and Tesoriere reads them. |
| Bank row | One line of an imported bank statement (CSV or OFX). |
| Ledger | The log of every money movement that is not a payment, one row per movement (R51). |
| Adjustment | A logged change to a category's spent figure for one month, written as a `cat:` ledger row (R25, R51). |
| Pair | A receipt and a bank row recorded as the same payment. |
| Category link | A remembered match from a Chatelaine category id to a Tesoriere category. |
| Store link | A remembered match from a Chatelaine store name to a bank row's payee text. |
| Profile | A separate money pool with its own Savings, Goals, Categories and ledger, as in Sci-Fin. Not a person. |
| Sci-Fin | The owner's earlier budgeting app, `haziaferi/sci-fin`, whose spec is `scifinspec.md`. |

## 1. Purpose and jobs

Sci-Fin is a budgeting app built against the failure modes of typical budgeting apps: punitive red styling, grading, streak pressure, and the assumption that money starts from a paycheck. Its base is Savings, the one real pool, with goals and categories as labelled portions of it. It cannot tell what a supermarket trip was spent on: a bank statement shows one card payment, and Sci-Fin files each payment under one category. Chatelaine, the owner's home inventory, knows what was bought and closes each shopping trip as one receipt with a category on every line. Tesoriere carries Sci-Fin's money logic into a new app that reads those receipts, splits each payment across its categories, and recognises a receipt and a bank row as one payment, so nothing is counted twice.

### D-R01 · Jobs and how often

**Rule:** Tesoriere serves the jobs below, as often as each row says.

**Values:**

| Job | How often | Done when |
|---|---|---|
| Review the Chatelaine receipts that arrived | after each shopping trip | each receipt is recorded as a payment split across categories |
| Log a payment or income by hand | a few times a week | the payment or income is in the ledger |
| Check categories, plan against spent | weekly | the user has seen each category's spent figure for the month |
| Import a bank statement and confirm pairs | monthly | every bank row is recorded or paired with a receipt |
| Start the new month, set plans and goals | monthly | the month's plans are set |
| Read reports | monthly | the user has seen the month's reports |
| Back up | automatic, weekly; restore rarely | a backup file exists in the chosen folder |

**Acceptance:** none: the jobs set what the design pass optimises for (R16) and are checked there, not in code.

**Since:** 2026-11-24 · owner · agreed rewording

### D-R02 · Who uses it

**Rule:** One person uses Tesoriere, on their own Android phone and Windows PC, with data kept on those devices. Profiles separate money pools, not people.

**Evidence:** Follows from the owner's answers of 2026-11-24: one chosen Profile receives receipts until households are introduced (R46).

**Acceptance:** none: no feature identifies a person; the app has no account, sign-in or roles.

**Since:** 2026-11-24 · builder · own words

## 2. The project

### O-R03 · A standalone app

**Rule:** Tesoriere is a standalone app on par with Chatelaine, not a module of it. Its name is Tesoriere, Italian for treasurer, and its package and application id are `com.hazia.tesoriere`.

**Why:** Money is a different job from a home inventory, with its own rhythm, tone and security bar (Chatelaine's spec, rule 98). The package follows the owner's `com.hazia.<app>` convention.

**Acceptance:** The Android `applicationId`, the namespace and every Kotlin package start with `com.hazia.tesoriere`.

**Not:** A savings module inside Chatelaine: Chatelaine's spec, rule 98 rules it out.

**Since:** 2026-11-24 · owner · agreed rewording

### O-R04 · A selective port of Sci-Fin

**Rule:** Tesoriere is a new repository into which Sci-Fin's logic is carried piece by piece (R17, R18). None of Sci-Fin's user interface is inherited.

**Why:** Sci-Fin's money logic is specified and tested; its screens are not what the owner wants for Tesoriere, which is designed afresh and closer to Chatelaine's.

**Acceptance:** No file under Sci-Fin's `ui/` package is copied into Tesoriere; every ported engine file names its Sci-Fin source in its commit message.

**Not:** A fork of Sci-Fin: it would carry Sci-Fin's UI, history and identifiers (`app.scifin`) into a new app.

**Since:** 2026-11-24 · owner · agreed rewording

### D-R05 · Repository and licence

**Rule:** The code lives in the public GitHub repository `haziaferi/Tesoriere` under GPL-3.0, with `SPEC.md`, `LICENSE` and `README.md` at the root beside `settings.gradle.kts`, `shared/`, `androidApp/` and `desktopApp/`. The repository is created and pushed only on the owner's word.

**Acceptance:** `LICENSE` holds the GPL-3.0 text; `settings.gradle.kts` sits at the root; `publish_check` runs as the pre-push hook before the first push.

**Not:** The code under `native/`, as in Sci-Fin: one folder is one repository, so no prefix is needed. A private repository, as Sci-Fin's: the owner's other apps are public.

**Since:** 2026-11-24 · owner · agreed rewording

### D-R06 · Platforms

**Rule:** Tesoriere runs on Android and on Windows 10 and 11, from one Kotlin codebase with Compose Multiplatform, as Sci-Fin does. No iOS.

**Acceptance:** One Gradle build produces the Android app and the Windows desktop app from the shared module.

**Since:** 2026-11-24 · owner · agreed rewording

### D-R07 · Languages

**Rule:** The interface speaks English and Italian, as Sci-Fin does: text the app writes into the data is stored in English and shown in the active language, and what the user typed stays as typed.

**Acceptance:** Switching the language changes every interface string and the app's own ledger labels, and leaves user-typed names unchanged.

**Since:** 2026-11-24 · owner · agreed rewording

### D-R08 · Offline first

**Rule:** Offline first by default, any possible Internet connection will always be optional and default OFF. Every feature that can touch the network is listed below, with its trigger, before it is designed.

**Values:**

| Feature | Trigger | Runs in the background | Default |
|---|---|---|---|
| none | none | no | none |

**Acceptance:** While the table lists no feature, the Android manifest omits `android.permission.INTERNET` and the Windows build makes no network call; a feature added to the table has a setting that is off on a fresh install.

**Since:** 2026-11-24 · owner · own words

### D-R09 · Minimum Android version

**Rule:** Tesoriere runs on Android 11 (API 30) and later, Sci-Fin's `minSdk`, so ported code that relies on it keeps working, Sci-Fin's API 30 workarounds included (no `DROP COLUMN` in SQLite).

**Acceptance:** `minSdk = 30` in the Android and shared build files.

**Since:** 2026-11-24 · owner · agreed rewording

## 3. Owner rules and visible design

### O-R10 · No valence colour

**Rule:** Being over or under a plan or a goal is never shown by colour. Each theme has one accent, used the same way whatever the state; going past a reference is shown by a pattern on the same bar, as Sci-Fin's spec, section 2.1, describes.

**Why:** Red and green for over and under is one of the punitive styles the research audit behind Sci-Fin found in typical budgeting apps.

**Acceptance:** No theme defines a positive or warning colour pair; a category over its plan and one under it draw their bars in the same colour.

**Since:** 2026-11-24 · owner · agreed rewording

### O-R11 · No grading, no streaks

**Rule:** Nothing scores a month, a category or a goal as passed or failed, and nothing counts streaks. Reports show trends and plain figures.

**Why:** Grading and streak pressure are failure modes the research audit behind Sci-Fin found in typical budgeting apps.

**Acceptance:** No screen or notification shows a score, badge, streak count, or a pass or fail word.

**Since:** 2026-11-24 · owner · agreed rewording

### O-R12 · Inform, never block

**Rule:** Feasibility and sustainability checks inform and never block a choice the user makes about their money, as Sci-Fin's spec, section 2.3, describes.

**Why:** The user may know something the app does not, such as a raise coming or a cut planned elsewhere.

**Acceptance:** Every feasibility note sits beside an action that stays available; no such check disables a save.

**Not:** Reading this rule as forbidding completeness checks: a split that does not add up (R23) or a receipt line with no category is incomplete data, not a choice, and its Confirm stays off until it is complete.

**Since:** 2026-11-24 · owner · agreed rewording

### O-R13 · Visible decisions are the owner's

**Rule:** Every visible decision (layout, wording, navigation, palette, type, icons, first run) is put to the owner and written into this spec before it is built. Placeholder screens count.

**Why:** In earlier projects screens were decided silently under a minimal-scaffold plan, and the owner had to undo them.

**Acceptance:** none: a working practice, checked by the rule that cites it (R16) having no open row before its screens are built.

**Since:** 2026-11-24 · owner · agreed rewording

### O-R14 · No stock look

**Rule:** Tesoriere does not look like a framework's defaults. A calm register still has a bold, energising presentation, given its own identity in type, scale contrast, shape, rhythm and motion, while staying easy to use.

**Why:** The owner's apps tended to end up looking like stock Material designs; scoring on layout alone produced competent but plain screens.

**Acceptance:** The design pass (R16) scores each candidate on a boldness axis of its own, beside the layout axes.

**Not:** Material 3 defaults as the design: the framework is plumbing.

**Since:** 2026-11-24 · owner · agreed rewording

### Q-R16 · Visible design

**Rule:** How do Tesoriere's screens look and work? Each row below is decided with the owner in a design pass before any screen is built (R13).

**Default:** none proposed: the design pass decides every row with the owner and the dedicated design skills, from the jobs and their frequency (R01), with no rule set beforehand beyond the owner rules R10 to R14. Sci-Fin's values, accessibility included, are not carried over as a starting point.

**Why:** No Sci-Fin UI is inherited (R04), so every screen is open.

**Values:**

| Decision | Bears on |
|---|---|
| Navigation and what opens first | R01 |
| Screen for each job | R01 |
| Receipt review layout and its closing warning | R31, R32, R33 |
| Pairing in the statement review | R37, R38 |
| Split editor | R24 |
| Palette and themes | R10, R14 |
| Type and spacing | R14 |
| Accessibility values: touch targets, text size, motion, low distraction | R10, R14 |
| First run, including the Sci-Fin import offer | R19 |
| App icon | R03 |
| Wording of every visible string | R07 |

**Since:** 2026-11-24 · owner · agreed rewording

## 4. The port from Sci-Fin

### D-R17 · Port source

**Rule:** A ported piece behaves as Sci-Fin's spec `scifinspec.md` describes it at Sci-Fin commit `2892110`, unless a rule here says otherwise. When a piece is ported, its rules are written into this spec, and from then on this spec, not Sci-Fin's, decides it.

**Evidence:** `haziaferi/sci-fin` main at `2892110` with `scifinspec.md` unmodified in the working tree; checked 2026-11-24.

**Acceptance:** Each ported piece has its own rules in this spec before its code is merged, and its tests cite them as `SPEC:Rnn`.

**Not:** Copying Sci-Fin's spec into this repository: it is a private repository's document, and its stacked amendments would carry stale wording.

**Since:** 2026-11-24 · builder · own words

### D-R18 · Ported pieces

**Rule:** The Sci-Fin pieces below are ported, each starting on or off on a fresh install as its row says. A piece that starts off is switched on in the settings.

**Values:**

| Piece | Sci-Fin source | Starts |
|---|---|---|
| Savings pool and Goals, with the goal calculator | sections 3.1, 3.2, 6 | on |
| Categories, plan against spent | section 4.3 | on |
| New Month cycle and catch-up | section 3.9 | on |
| Recurring expenses, sweep and reminders | sections 3.5, 3.11 | on |
| Income, salaried and hourly | sections 3.3, 3.10 | off |
| Credit and Loan accounts, payoff calculators | sections 7, 9.1, 9.2 | off |
| Statement import, CSV and OFX | section 4.3 | on |
| Report: Buffer and Age of Money | section 5.1 | on |
| Report: Spending | section 5.2 | on |
| Report: Income vs Expenses | section 5.4 | with Income |
| Report: Savings vs Owed | section 5.3 | off |
| Encrypted backup and restore | section 4.7 | on |
| Automatic copy to a folder | section 4.7 | off |
| Trash, 30 days | section 3.6 | on |
| App lock | section 4.7 | off |
| Damaged-file guard and migration snapshots | section 12 | on |
| Profiles | section 8 | off |
| Science topics | sections 4.5, 9.3 | off |
| Overtime calculator | section 10 | off |

**Evidence:** Automatic copy and App lock start off because they start off in Sci-Fin (automatic copy needs a folder first); the owner did not rule on them separately.

**Acceptance:** On a fresh install the pieces marked off show no screen, row or notification until switched on; Profiles off means one Profile and no switcher.

**Since:** 2026-11-24 · owner · agreed rewording

### D-R19 · Sci-Fin backup import

**Rule:** Tesoriere reads a Sci-Fin backup file, encrypted or not, of Sci-Fin's backup format up to schema 10, on first run and at any later time from the data settings. Each Sci-Fin Profile in the file arrives as a new Tesoriere Profile after a review of its counts; nothing is merged into an existing Profile. A file of a schema above 10 is refused whole.

**Why:** Tesoriere replaces Sci-Fin on the owner's devices, and the owner's data is in Sci-Fin.

**Acceptance:** Importing a Sci-Fin schema-10 backup yields one new Tesoriere Profile per Sci-Fin Profile, with the same Savings, Goals, Categories, Recurring expenses, Accounts and ledger counts the review showed; a schema-11 file is refused and nothing is written.

**Not:** First run only: the owner wants it any time.

**Since:** 2026-11-24 · owner · agreed rewording

## 5. Money model

### D-R20 · Money and time

**Rule:** Money is a whole number of minor units (cents) everywhere in the engine; ids are UUID strings; dates are calendar days stored as `YYYY-MM-DD`; the moment an entry is logged is an instant in epoch milliseconds. As Sci-Fin's spec, section 12.

**Acceptance:** No floating-point type holds money in `shared/`; every date field is a calendar day and every logged time an instant.

**Since:** 2026-11-24 · builder · own words

### D-R49 · Savings

**Rule:** Each Profile has one Savings figure: the money not set aside in a Goal. It may go below zero. Until it is first given a value it is unset, which is not zero: checks that compare against Savings are skipped while it is unset, and any movement that touches Savings ends the unset state, counting from zero.

**Evidence:** Sci-Fin's spec, section 3.1, and `engine/Ledger.kt` at `2892110`.

**Acceptance:** A fresh Profile's Savings is unset; a 60.00 movement out of an unset Savings leaves it at -60.00; a direct edit to 0.00 leaves it at 0.00, no longer unset.

**Not:** Reading unset as zero: an honest zero must stay distinguishable from no answer.

**Since:** 2026-11-24 · builder · own words

### D-R50 · Goals hold money out of Savings

**Rule:** Money put into a Goal leaves Savings and is held by the Goal, so no figure read from Savings counts it. Taking it back out of the Goal returns it to Savings.

**Evidence:** Sci-Fin's spec, section 3.2.

**Acceptance:** Adding 150.00 to a Goal raises its saved money by 150.00 and lowers Savings by 150.00.

**Not:** Counting Goal money in Savings as well: the same euro would count twice.

**Since:** 2026-11-24 · builder · own words

### D-R51 · The ledger

**Rule:** Every movement of money other than a payment is a ledger row: a key naming what it changes, the signed change to it, a date, the instant it was logged and the target's name at that instant. How Savings moves follows from the key, as below. The date places the row in its month and in reports; the figure it changes moves when the row is written, whatever its date.

**Values:**

| `key` | `amount` is the change to | Savings moves by |
|---|---|---|
| `savings` | Savings | `+amount` |
| `goal:<id>` | the Goal's saved money | `-amount` |
| `cat:<id>` | the category's spent figure, as an adjustment (R25) | `-amount` |
| `rec:<id>` | the money swept for the recurring expense, positive when swept out | `-amount` |
| `acct:<id>` | the Account's balance | 0 |
| `spend:<goalId>` | the Goal's saved money, negative; only in rows read from a Sci-Fin backup (R19) | 0 |

**Evidence:** Sci-Fin's spec, section 3.6, and `engine/Ledger.kt` at `2892110`.

**Acceptance:** A 20.00 adjustment on a category is stored as `cat:<id>` with amount 2000 and lowers Savings by 20.00; a 100.00 sweep is `rec:<id>` with amount 10000 and lowers Savings by 100.00; an `acct:` row leaves Savings as it was.

**Not:** Storing the Savings effect instead of the change to the target: every goal and adjustment row would read inverted.

**Since:** 2026-11-24 · builder · own words

### D-R52 · Direct edits log the change

**Rule:** Savings and a Goal's saved money can be set directly to a new figure. The edit logs one ledger row holding the change, never the new figure, with the note `Correction` unless the user writes one; a change to a Goal's saved money moves Savings by its opposite. An edit to the same figure logs nothing.

**Evidence:** Sci-Fin's spec, section 3.6, and `engine/Writes.kt` at `2892110`.

**Acceptance:** Setting Savings from 150.00 to 1,000.00 logs a `savings` row of +850.00; setting a Goal's saved money from 400.00 to 300.00 logs a `goal:<id>` row of -100.00 and raises Savings by 100.00.

**Not:** Logging the new figure itself: a second statement of the truth that the ledger's sums could no longer match.

**Since:** 2026-11-24 · builder · own words

### D-R53 · Undoing a mistaken entry

**Rule:** The ledger's rows are never edited or deleted one by one. The one exception is a pair: two rows deleted together when the second cancels the first under every condition below. Deleting a pair changes no balance; the date of the last logged income is recomputed from the rows left.

**Values:**

| Condition | Holds when |
|---|---|
| Same target | both rows have the same `key` |
| Opposite | the amounts are equal, opposite and not zero |
| Next | the second is the next row on that key in logged order, with none between |
| Window | they were logged at most 48 hours apart |
| Once | each row belongs to at most one pair, pairing forward only |
| Fixed | which rows pair is found once, from the ledger before any delete |
| Not a sweep | neither row has a `rec:` key |

**Evidence:** Sci-Fin's spec, section 7.3, and `engine/Ledger.kt` at `2892110`.

**Acceptance:** Rows of +40.00 and -40.00 on one Goal logged 47 hours apart pair; 49 hours apart they do not; four rows +40.00, -40.00, +40.00, -40.00 logged in that order make two pairs, the first two and the last two.

**Not:** Deleting single rows: the ledger would stop adding up to the figures it moved.

**Since:** 2026-11-24 · builder · own words

### D-R21 · Payment

**Rule:** Every expense is a payment with the fields below. A payment from a receipt keeps the receipt's lines, so each part can be traced to the lines it came from.

**Values:**

| Field | Holds |
|---|---|
| `id` | UUID |
| `date` | the day of the payment: the receipt's date when there is a receipt, else the bank row's or the entered date |
| `loggedAt` | the instant it was recorded |
| `total` | cents, positive |
| `source` | Savings, a Goal, or an Account (R22) |
| `payee` | optional text: the store or the bank row's payee |
| `parts` | one or more: category id and cents (R23); only a part read from a Sci-Fin backup may have no category (R19) |
| `receiptId` | the receipt file's id, when the payment came from or was paired with a receipt |
| `receiptLines` | per line: name, pack, quantity, price paid, Chatelaine category id and name, Tesoriere category |
| `bankKeys` | the bank row's FITID and its date, amount and description key, when it came from or was paired with a bank row (R26) |

**Acceptance:** A recorded receipt payment holds every receipt line; a paired payment holds both its `receiptId` and its `bankKeys`.

**Since:** 2026-11-24 · owner · agreed rewording

### D-R22 · Source and parts are separate

**Rule:** A payment has one source, where the money came from, and its parts, what it was spent on. A payment charged to a credit card counts in its categories and raises the card's balance.

**Why:** A card-paid shopping trip is still groceries and household spending; in Sci-Fin a card charge replaced the category, so it counted in none.

**Acceptance:** A EUR 40 receipt charged to a card adds its parts to their categories' spent figures and EUR 40 to the card's balance, and moves nothing in Savings.

**Not:** Sci-Fin's model, where a charge to a card has no category.

**Since:** 2026-11-24 · owner · agreed rewording

### D-R54 · What a payment's source moves

**Rule:** A payment's source decides what its total moves, as below; its parts move only the categories' spent figures (R25). A payment is its own record and writes no ledger row. Editing a payment undoes its old source's effect and applies the new one; removing it undoes its effect.

**Values:**

| Source | Its total | Savings |
|---|---|---|
| Savings | lowers Savings | lowered by the total |
| Goal | lowers the Goal's saved money; a total above what the Goal holds cannot be saved | unchanged |
| Account | raises the Account's balance | unchanged |

**Evidence:** Sci-Fin's spec, sections 3.6 and 7.2, at `2892110`.

**Acceptance:** A 42.30 payment from Savings lowers Savings by 42.30; a 30.00 payment from a Goal holding 50.00 leaves it 20.00 and Savings unchanged, and one of 60.00 cannot be saved; a 40.00 payment charged to a card with a 0.00 balance leaves the balance at 40.00.

**Not:** Spending past what a Goal holds: the Goal would hold less than nothing.

**Since:** 2026-11-24 · owner · agreed rewording

### D-R23 · Parts add up

**Rule:** A payment's parts add up to its total to the cent. Until they do, the editor shows the remainder and its save stays off.

**Acceptance:** A EUR 42.30 payment with parts of EUR 30.00 and EUR 12.00 shows EUR 0.30 left and cannot be saved; with a third part of EUR 0.30 it can.

**Not:** Filing the remainder to a default category on its own: it hides a mistake.

**Since:** 2026-11-24 · owner · agreed rewording

### D-R24 · Any payment can be split

**Rule:** Any payment can be split by hand into parts, whether it came from a bank row or was logged by hand. A payment from a receipt arrives already split by its lines' categories (R31).

**Acceptance:** A bank row and a hand-logged payment can each be saved with two parts in two categories.

**Not:** Splits for receipts only: a bank row with no receipt behind it, such as a pharmacy bill, could not be divided.

**Since:** 2026-11-24 · owner · agreed rewording

### D-R25 · Spent is derived

**Rule:** A category's spent figure for a month is computed when read, from the parts of the payments dated in that month and the category's logged adjustments. It is never stored.

**Why:** A late receipt can re-file a payment that is already recorded (R38); with a derived figure that is one change to the payment, not correction rows.

**Acceptance:** Re-filing a payment's parts changes the spent figures of the categories involved, for the payment's month, and writes no correction row.

**Not:** Sci-Fin's stored spent figure, edited directly and incremented by each entry: every re-filing would need correction rows. Sci-Fin's direct edit of spent becomes a logged adjustment.

**Since:** 2026-11-24 · owner · agreed rewording

## 6. Goals and the monthly plan

### D-R55 · Goal calculator

**Rule:** A Goal with a target is planned either by a date, which gives a monthly amount, or by a monthly amount, which gives a number of months. Both are worked out from the money still needed, the target minus the saved money, whenever they are read, as below. A Goal with no target, or a target of zero, is a running tally with no monthly amount.

**Values:**

| Figure | Worked out as |
|---|---|
| Months, by date | calendar months from the current month to the target's month, at least 1 |
| Monthly amount, by date | money still needed divided by the months, rounded up to the cent |
| Months, by amount | money still needed divided by the monthly amount, rounded up to a whole month |

**Evidence:** Sci-Fin's spec, sections 6.1 and 6.3, and `engine/Goals.kt` at `2892110`.

**Acceptance:** A 1,200.00 goal with 400.00 saved and a target date six calendar months ahead needs 133.34 a month; the same goal at 150.00 a month takes 6 months.

**Not:** A deadline with no target: there is nothing to divide.

**Since:** 2026-11-24 · builder · own words

### D-R56 · Where a goal fits

**Rule:** A goal with a monthly amount is compared with the month's room as below, which informs and never blocks (R12). Another goal stops counting once its saved money reaches its target; one whose date has passed short of its target keeps counting.

**Values:**

| Income | Compared | It fits when |
|---|---|---|
| on | the goal's monthly amount with the income received this month, minus this month's recurring expenses, minus the other goals' monthly amounts | the monthly amount is at most that room |
| off | the recurring expenses plus the other goals' monthly amounts plus this goal's, with Savings | that sum is at most Savings |

**Evidence:** Sci-Fin's spec, sections 6.2 and 6.4, and `engine/Goals.kt` at `2892110`.

**Acceptance:** With Income off, Savings 1,000.00, recurring expenses of 300.00 and another goal at 200.00 a month, a goal at 133.34 a month adds up to 633.34 against 1,000.00 and fits; if the other goal has reached its target, it adds nothing.

**Since:** 2026-11-24 · builder · own words

### D-R57 · Gentler monthly amounts

**Rule:** When a goal's monthly amount is above the threshold below, three gentler monthly amounts are offered, each rounded to a whole currency unit, which the user may take or ignore. When the base is zero or less, no amounts are offered.

**Values:**

| Income | Base | Threshold | Amounts offered |
|---|---|---|---|
| on | income received this month | 1/6 of the base | 1/14, 1/12 and 1/6 of the base |
| off | Savings | 1/12 of the base | 1/20, 1/16 and 1/12 of the base |

**Evidence:** Sci-Fin's spec, section 6.4, and `engine/Goals.kt` at `2892110`.

**Acceptance:** With Income off and Savings 2,400.00, a goal at 250.00 a month is offered 120.00, 150.00 and 200.00; one at 200.00 a month is offered nothing.

**Since:** 2026-11-24 · builder · own words

### D-R58 · Deleting a goal

**Rule:** Deleting a Goal that holds money asks whether to return it to Savings. Returned, it moves back with a logged `goal:<id>` row; kept, Savings stays as it was. Either way the Goal's past rows stay in the ledger.

**Evidence:** Sci-Fin's spec, section 3.6, and `engine/GoalEditing.kt` at `2892110`.

**Acceptance:** Deleting a Goal holding 300.00 with the money returned logs a `goal:<id>` row of -300.00 and raises Savings by 300.00; without, Savings is unchanged; both remove the Goal.

**Since:** 2026-11-24 · builder · own words

### D-R59 · Plan against spent

**Rule:** Each category has one planned amount, the same every month, read beside its spent figure for the month (R25). The category editor holds the name, the icon and the planned amount; spent changes only through payments and logged adjustments.

**Evidence:** Sci-Fin's spec, section 4.3, and `engine/CategoryEditing.kt` at `2892110`.

**Acceptance:** A category planned at 300.00 with payments of 320.00 in March reads planned 300.00 and spent 320.00 for March.

**Not:** Sci-Fin's reset of spent at each new month: a figure worked out per month needs none.

**Since:** 2026-11-24 · builder · own words

## 7. Statement import

### D-R26 · Statement import

**Rule:** A bank statement in CSV or OFX is read on the device, and every bank row lands in a review as a suggestion; nothing is recorded until one Confirm, which names the total. Each recorded bank row keeps two keys, the OFX FITID where there is one and a key over date, amount and description, and both are checked, so a row already recorded arrives unticked with the reason shown. A payee once filed by the user pre-fills its category, as in Sci-Fin's spec, section 4.3.

**Acceptance:** Importing the same OFX statement twice records nothing the second time; importing a CSV export of the same period also records nothing; a payee filed under a category once is pre-filled on the next statement.

**Since:** 2026-11-24 · owner · agreed rewording

## 8. Chatelaine receipts

### D-R27 · The receipt contract

**Rule:** Receipts arrive one way, from Chatelaine to Tesoriere, as files in the format of Chatelaine's spec, rule 123. Tesoriere follows Chatelaine's spec, rules 98, 113, 122 to 126 and 139, never copies the format into this spec, and never writes to the receipt folder or to Chatelaine. A change to the file format is made through a Chatelaine pull request on rule 123, never only in Tesoriere.

**Why:** Chatelaine stays free of money logic and the flow stays one way (Chatelaine's spec, rule 98).

**Evidence:** Chatelaine's rules read in `haziaferi/Chatelaine` at `9e2859c`, as merged in Chatelaine pull requests 8 and 9; checked 2026-11-24.

**Acceptance:** The receipt parser's tests cite Chatelaine's rule 123 field by field; no Tesoriere code opens the receipt folder for writing.

**Not:** Telling Chatelaine a receipt was recorded, by a file it would read: Chatelaine's spec, rules 98 and 122, keep the flow one way, and the owner kept it so.

**Since:** 2026-11-24 · owner · agreed rewording

### D-R28 · Receipt folder

**Rule:** The user picks the receipt folder once, inside one Profile, and that Profile receives every receipt from it. Only one Profile has a receipt folder.

**Acceptance:** With the folder set in Profile A, receipts are reviewed and recorded only in Profile A; Profile B offers no receipt folder while A has one.

**Not:** Asking for the Profile on each receipt: one user, one pool for shopping, until households are introduced (R46).

**Since:** 2026-11-24 · owner · agreed rewording

### D-R29 · Picking up receipts

**Rule:** The user chooses how new receipt files are found, from the rows below; the first is the default. Nothing is recorded without the user's Confirm (R31).

**Values:**

| Choice | Finds new files | Default |
|---|---|---|
| On open | each time Tesoriere opens, it counts the new files in the receipt folder and offers to review them | yes |
| Daily check | as on open, plus a daily background check that sends one notification naming how many receipts wait | no, opt-in |

**Acceptance:** With the default, opening the app with three new files offers three receipts and runs no background work; with the daily check on, three new files give one notification that day; no receipt is recorded before its Confirm.

**Not:** Picking the folder at every import: the folder is remembered.

**Since:** 2026-11-24 · owner · agreed rewording

### D-R30 · Receipt files Tesoriere cannot take

**Rule:** A receipt file that cannot be recorded is listed with the reason its row gives, left in place, and records nothing.

**Values:**

| Case | Shown as | Recorded |
|---|---|---|
| Its receipt id was recorded before | not shown again | nothing |
| Format version above the highest Tesoriere reads | needs a newer Tesoriere, naming the version | nothing |
| Currency other than the Profile's | names both currencies | nothing |
| Not a receipt file, or unreadable | cannot be read | nothing |

**Evidence:** The currency row is the owner's for v1 (R47); the version and unreadable rows follow Sci-Fin's rule that a file of a higher version is refused whole, not half read.

**Acceptance:** One file per row, put in the receipt folder, is listed with its reason (or, for a recorded id, not at all) and changes no figure.

**Since:** 2026-11-24 · owner · agreed rewording

### D-R31 · Receipt review

**Rule:** A receipt is reviewed as one payment: its store, date and total, and its lines each in the Tesoriere category its category link gives (R32). A line can be moved to another category for this receipt only; no line can be left out. One Confirm, naming the total, records the payment; closing the review records nothing.

**Why:** Chatelaine sends no line without a category (Chatelaine's spec, rule 126), so every line has a place.

**Acceptance:** A receipt of five lines in two categories records one payment of the receipt's total with two parts; moving one line to a third category gives three parts and changes no category link; closing the review leaves no payment.

**Not:** Excluding a line from the payment: the payment would no longer equal the bank row.

**Since:** 2026-11-24 · owner · agreed rewording

### D-R32 · Category links

**Rule:** The user gives categories equal names in both apps, and Tesoriere links them by name, ignoring capitals, the first time a Chatelaine category id appears; from then on the link is by id, so renaming the category in Chatelaine keeps it. A Chatelaine category with no equal name asks, in the review, to add it as a new Tesoriere category or to link it to an existing one. The review cannot be confirmed until every line's category is linked.

**Values:**

| Case | Result |
|---|---|
| Known id | the linked category, whatever the name |
| New id, a Tesoriere category of equal name ignoring capitals | linked to it |
| New id, no equal name, user adds it | a new Tesoriere category with the Chatelaine name, linked |
| New id, no equal name, user links an existing one | linked; the review reminds the user to rename the Chatelaine category to the Tesoriere name |
| Known id whose name differs from the linked category's | linked as before, with a note that the names differ |

**Acceptance:** A receipt with category Pulizia links to Tesoriere's pulizia; a later receipt with the same id named Pulizie stays linked and shows the names-differ note; a new category with no equal name keeps Confirm off until it is added or linked.

**Not:** Linking by name on every receipt: a rename in Chatelaine would read as a new category. Renaming the Tesoriere category to the Chatelaine name: the owner keeps Tesoriere's names.

**Since:** 2026-11-24 · owner · agreed rewording

### D-R33 · Closing a review

**Rule:** Category links, and categories added in a review, are kept only when the review is confirmed. Closing a review that holds changes first shows a prompt warning that they will be lost; the user can dismiss it and stay in the review, or close and lose them.

**Acceptance:** Adding a category in a review and closing it shows the warning; dismissing it returns to the review with the change intact; closing anyway leaves no new category and no new link.

**Not:** Keeping links from a closed review: a link would exist with no recorded receipt behind it.

**Since:** 2026-11-24 · owner · agreed rewording

### D-R34 · Changing a link

**Rule:** A category link can be changed later. The change applies to receipts recorded after it; lines already recorded stay in the category they were filed under.

**Acceptance:** After relinking Snack from Eating out to Groceries, last month's Eating out spent figure is unchanged and the next receipt's Snack lines go to Groceries.

**Not:** Moving past lines too: past months' figures would shift on their own.

**Since:** 2026-11-24 · owner · agreed rewording

### D-R35 · A receipt's source

**Rule:** A receipt's source is Savings unless the user changes it in the review, and the source last confirmed for a store is pre-filled on that store's next receipt. Pairing with a bank row keeps the receipt's source.

**Why:** The receipt file does not say how it was paid (Chatelaine's spec, rule 123), and a store is usually paid the same way.

**Acceptance:** The first receipt from a store shows Savings; after it is confirmed with a card as source, the next receipt from that store shows the card.

**Not:** Asking for the source on every receipt: one more choice per receipt.

**Since:** 2026-11-24 · owner · agreed rewording

## 9. One payment, two sources

### D-R36 · Store links

**Rule:** The first time a receipt from a store is paired with a bank row, Tesoriere remembers that store's name with the bank row's payee text. Later pairs for that store are suggested on the store link as well as the total and date.

**Why:** A bank row's text rarely equals the store's name, so the store is linked once rather than compared.

**Acceptance:** After pairing an Esselunga receipt with POS ESSELUNGA MI 0423, the next Esselunga receipt is suggested only with bank rows whose payee matches that link, when one exists.

**Since:** 2026-11-24 · owner · agreed rewording

### D-R37 · Pairing rule

**Rule:** A receipt and a bank row are suggested as one payment when their totals and dates meet a row below; the user confirms every pair. Whichever arrives first is recorded, and the second becomes a pair, not new spending (R38).

**Values:**

| Totals | Bank date | Suggested |
|---|---|---|
| equal to the cent | same day as the receipt, or up to 3 days after | yes |
| differ by up to EUR 0.50 | same day as the receipt, or up to 3 days after | yes, with a difference part (R39) |
| differ by more than EUR 0.50 | any | no |
| any | before the receipt, or more than 3 days after | no |

**Acceptance:** Each row of the table, as a receipt and a bank row, gives the suggestion its row says; no pair is recorded without the user's confirm.

**Not:** Pairing without asking: a wrong pair is harder to see after the fact. Same-day dates only: bank rows are often booked a day or more after the payment.

**Since:** 2026-11-24 · owner · agreed rewording

### D-R38 · Whichever arrives first

**Rule:** The payment is recorded from whichever arrives first, and the pair completes it.

**Values:**

| First | Then | Result |
|---|---|---|
| Receipt | bank row in a later statement | the bank row arrives unticked as already recorded, suggested as the receipt's pair; confirming adds its `bankKeys` to the payment and records no new spending |
| Bank row, filed under one category | receipt | the receipt's split replaces the bank row's parts, dated the receipt's day; the payment keeps its `bankKeys` |

**Acceptance:** Recording a EUR 42.30 receipt and then importing its bank row leaves one payment of EUR 42.30; importing the bank row first under Groceries and then its receipt with Groceries 30.00 and Household 12.30 leaves one payment with those two parts.

**Not:** Asking at each pair whether to keep the bank row's category: the receipt knows what was bought.

**Since:** 2026-11-24 · owner · agreed rewording

### D-R39 · Difference part

**Rule:** When a pair's totals differ within the tolerance (R37), the bank row's amount is the payment's total, and the difference becomes one more part that the user files under a category; the pair cannot be confirmed until it is filed.

**Why:** The bank amount is what left the account.

**Acceptance:** A EUR 42.10 receipt paired with a EUR 42.30 bank row records a EUR 42.30 payment with a EUR 0.20 part in the category the user chose, and the pair's confirm is off until it is chosen.

**Not:** Filing the difference with the largest line, or spreading it over the lines: both guess where the money went.

**Since:** 2026-11-24 · owner · agreed rewording

### D-R40 · Pairing by hand

**Rule:** The user can pair a receipt and a bank row by hand, from either side, also when the pairing rule (R37) does not suggest them. The bank row's amount is the total, and any difference is a part filed as in R39.

**Why:** A pair the rule misses would otherwise count the payment twice.

**Acceptance:** A EUR 40.00 receipt and a EUR 41.20 bank row, not suggested, can be paired by hand into one EUR 41.20 payment with a EUR 1.20 part the user files.

**Since:** 2026-11-24 · owner · agreed rewording

## 10. Edge cases

### D-R41 · Deleting a receipt payment

**Rule:** A payment recorded from a receipt is deleted through the Trash like any other. Its receipt id stays recorded, so the file is not offered again; restoring it from the Trash restores the payment with its parts and pairs.

**Evidence:** Not checked: the owner has not ruled on whether a deleted receipt should be offered again.

**Acceptance:** Deleting a receipt payment and reopening the app does not offer the receipt file again; restoring it brings back its parts.

**Since:** 2026-11-24 · builder · own words

### D-R42 · Deleting a linked category

**Rule:** Deleting a Tesoriere category removes the category links to it; the next receipt with that Chatelaine category asks again, as for a new one (R32).

**Acceptance:** After deleting Pulizia, the next receipt with Chatelaine's Pulizia keeps Confirm off until its line is linked or added.

**Since:** 2026-11-24 · builder · own words

## 11. Test plan

### D-R43 · Test plan

**Rule:** Each level below runs as its row says before a change is merged.

**Values:**

| Level | Covers | Rules | Runs |
|---|---|---|---|
| Engine unit tests on the JVM | parts, spent, pairing, links, review outcomes | R21 to R25, R31 to R42 | every commit |
| Receipt file fixtures | one file per Chatelaine rule 123 field and per case of R30 | R27, R30 | every commit |
| Statement import fixtures | OFX and CSV, duplicates, payee rules | R26 | every commit |
| Sci-Fin backup fixtures | schema 1 to 10, encrypted and not, schema 11 refused | R19 | every commit |
| Migration test | every schema version has a migration path | R18 | every commit |
| Device walk | receipt pick-up, review, pairing, on the phone and on Windows | R29, R31, R37 | each release |

**Acceptance:** CI runs the first five levels on every pull request; a release records its device walk.

**Since:** 2026-11-24 · builder · own words

## 12. Rollout

### D-R44 · Rollout phases

**Rule:** Tesoriere replaces Sci-Fin on the owner's devices in the phases below. A phase moves on as soon as its gate holds, with no minimum time, and goes back when its rollback trigger fires.

**Values:**

| Phase | What | Gate to the next phase | Rollback trigger |
|---|---|---|---|
| 1 | the engine port with receipts and pairs, tested on the JVM only | every level of R43 green | none |
| 2 | the app on the owner's phone and PC beside Sci-Fin, with Sci-Fin's data imported (R19) | a few receipts recorded and one bank statement covering them imported, every pair right and nothing counted twice | a wrong pair or a double count: Sci-Fin stays the record until it is fixed and phase 2's gate holds |
| 3 | Sci-Fin retired | none | none |

**Acceptance:** Phase 2's gate is recorded in `SPEC-history.md` or the release notes before Sci-Fin is retired.

**Not:** A month side by side: longer than the owner wants the switch to take. Retiring Sci-Fin at once: a wrong pair would go unnoticed with no record to compare against.

**Since:** 2026-11-24 · owner · agreed rewording

## Acceptance list

- R03 · A standalone app: The Android `applicationId`, the namespace and every Kotlin package start with `com.hazia.tesoriere`.
- R04 · A selective port of Sci-Fin: No file under Sci-Fin's `ui/` package is copied into Tesoriere; every ported engine file names its Sci-Fin source in its commit message.
- R05 · Repository and licence: `LICENSE` holds the GPL-3.0 text; `settings.gradle.kts` sits at the root; `publish_check` runs as the pre-push hook before the first push.
- R06 · Platforms: One Gradle build produces the Android app and the Windows desktop app from the shared module.
- R07 · Languages: Switching the language changes every interface string and the app's own ledger labels, and leaves user-typed names unchanged.
- R08 · Offline first: While the table lists no feature, the Android manifest omits `android.permission.INTERNET` and the Windows build makes no network call; a feature added to the table has a setting that is off on a fresh install.
- R09 · Minimum Android version: `minSdk = 30` in the Android and shared build files.
- R10 · No valence colour: No theme defines a positive or warning colour pair; a category over its plan and one under it draw their bars in the same colour.
- R11 · No grading, no streaks: No screen or notification shows a score, badge, streak count, or a pass or fail word.
- R12 · Inform, never block: Every feasibility note sits beside an action that stays available; no such check disables a save.
- R14 · No stock look: The design pass (R16) scores each candidate on a boldness axis of its own, beside the layout axes.
- R17 · Port source: Each ported piece has its own rules in this spec before its code is merged, and its tests cite them as `SPEC:Rnn`.
- R18 · Ported pieces: On a fresh install the pieces marked off show no screen, row or notification until switched on; Profiles off means one Profile and no switcher.
- R19 · Sci-Fin backup import: Importing a Sci-Fin schema-10 backup yields one new Tesoriere Profile per Sci-Fin Profile, with the same Savings, Goals, Categories, Recurring expenses, Accounts and ledger counts the review showed; a schema-11 file is refused and nothing is written.
- R20 · Money and time: No floating-point type holds money in `shared/`; every date field is a calendar day and every logged time an instant.
- R49 · Savings: A fresh Profile's Savings is unset; a 60.00 movement out of an unset Savings leaves it at -60.00; a direct edit to 0.00 leaves it at 0.00, no longer unset.
- R50 · Goals hold money out of Savings: Adding 150.00 to a Goal raises its saved money by 150.00 and lowers Savings by 150.00.
- R51 · The ledger: A 20.00 adjustment on a category is stored as `cat:<id>` with amount 2000 and lowers Savings by 20.00; a 100.00 sweep is `rec:<id>` with amount 10000 and lowers Savings by 100.00; an `acct:` row leaves Savings as it was.
- R52 · Direct edits log the change: Setting Savings from 150.00 to 1,000.00 logs a `savings` row of +850.00; setting a Goal's saved money from 400.00 to 300.00 logs a `goal:<id>` row of -100.00 and raises Savings by 100.00.
- R53 · Undoing a mistaken entry: Rows of +40.00 and -40.00 on one Goal logged 47 hours apart pair; 49 hours apart they do not; four rows +40.00, -40.00, +40.00, -40.00 logged in that order make two pairs, the first two and the last two.
- R21 · Payment: A recorded receipt payment holds every receipt line; a paired payment holds both its `receiptId` and its `bankKeys`.
- R22 · Source and parts are separate: A EUR 40 receipt charged to a card adds its parts to their categories' spent figures and EUR 40 to the card's balance, and moves nothing in Savings.
- R54 · What a payment's source moves: A 42.30 payment from Savings lowers Savings by 42.30; a 30.00 payment from a Goal holding 50.00 leaves it 20.00 and Savings unchanged, and one of 60.00 cannot be saved; a 40.00 payment charged to a card with a 0.00 balance leaves the balance at 40.00.
- R23 · Parts add up: A EUR 42.30 payment with parts of EUR 30.00 and EUR 12.00 shows EUR 0.30 left and cannot be saved; with a third part of EUR 0.30 it can.
- R24 · Any payment can be split: A bank row and a hand-logged payment can each be saved with two parts in two categories.
- R25 · Spent is derived: Re-filing a payment's parts changes the spent figures of the categories involved, for the payment's month, and writes no correction row.
- R55 · Goal calculator: A 1,200.00 goal with 400.00 saved and a target date six calendar months ahead needs 133.34 a month; the same goal at 150.00 a month takes 6 months.
- R56 · Where a goal fits: With Income off, Savings 1,000.00, recurring expenses of 300.00 and another goal at 200.00 a month, a goal at 133.34 a month adds up to 633.34 against 1,000.00 and fits; if the other goal has reached its target, it adds nothing.
- R57 · Gentler monthly amounts: With Income off and Savings 2,400.00, a goal at 250.00 a month is offered 120.00, 150.00 and 200.00; one at 200.00 a month is offered nothing.
- R58 · Deleting a goal: Deleting a Goal holding 300.00 with the money returned logs a `goal:<id>` row of -300.00 and raises Savings by 300.00; without, Savings is unchanged; both remove the Goal.
- R59 · Plan against spent: A category planned at 300.00 with payments of 320.00 in March reads planned 300.00 and spent 320.00 for March.
- R26 · Statement import: Importing the same OFX statement twice records nothing the second time; importing a CSV export of the same period also records nothing; a payee filed under a category once is pre-filled on the next statement.
- R27 · The receipt contract: The receipt parser's tests cite Chatelaine's rule 123 field by field; no Tesoriere code opens the receipt folder for writing.
- R28 · Receipt folder: With the folder set in Profile A, receipts are reviewed and recorded only in Profile A; Profile B offers no receipt folder while A has one.
- R29 · Picking up receipts: With the default, opening the app with three new files offers three receipts and runs no background work; with the daily check on, three new files give one notification that day; no receipt is recorded before its Confirm.
- R30 · Receipt files Tesoriere cannot take: One file per row, put in the receipt folder, is listed with its reason (or, for a recorded id, not at all) and changes no figure.
- R31 · Receipt review: A receipt of five lines in two categories records one payment of the receipt's total with two parts; moving one line to a third category gives three parts and changes no category link; closing the review leaves no payment.
- R32 · Category links: A receipt with category Pulizia links to Tesoriere's pulizia; a later receipt with the same id named Pulizie stays linked and shows the names-differ note; a new category with no equal name keeps Confirm off until it is added or linked.
- R33 · Closing a review: Adding a category in a review and closing it shows the warning; dismissing it returns to the review with the change intact; closing anyway leaves no new category and no new link.
- R34 · Changing a link: After relinking Snack from Eating out to Groceries, last month's Eating out spent figure is unchanged and the next receipt's Snack lines go to Groceries.
- R35 · A receipt's source: The first receipt from a store shows Savings; after it is confirmed with a card as source, the next receipt from that store shows the card.
- R36 · Store links: After pairing an Esselunga receipt with POS ESSELUNGA MI 0423, the next Esselunga receipt is suggested only with bank rows whose payee matches that link, when one exists.
- R37 · Pairing rule: Each row of the table, as a receipt and a bank row, gives the suggestion its row says; no pair is recorded without the user's confirm.
- R38 · Whichever arrives first: Recording a EUR 42.30 receipt and then importing its bank row leaves one payment of EUR 42.30; importing the bank row first under Groceries and then its receipt with Groceries 30.00 and Household 12.30 leaves one payment with those two parts.
- R39 · Difference part: A EUR 42.10 receipt paired with a EUR 42.30 bank row records a EUR 42.30 payment with a EUR 0.20 part in the category the user chose, and the pair's confirm is off until it is chosen.
- R40 · Pairing by hand: A EUR 40.00 receipt and a EUR 41.20 bank row, not suggested, can be paired by hand into one EUR 41.20 payment with a EUR 1.20 part the user files.
- R41 · Deleting a receipt payment: Deleting a receipt payment and reopening the app does not offer the receipt file again; restoring it brings back its parts.
- R42 · Deleting a linked category: After deleting Pulizia, the next receipt with Chatelaine's Pulizia keeps Confirm off until its line is linked or added.
- R43 · Test plan: CI runs the first five levels on every pull request; a release records its device walk.
- R44 · Rollout phases: Phase 2's gate is recorded in `SPEC-history.md` or the release notes before Sci-Fin is retired.

## Out of scope

### D-R45 · Price comparison

**Rule:** Tesoriere compares no shop prices and does not use the Price-compare library.

**Why:** Chatelaine keeps the price book and applies every discount before writing the receipt file, so the receipt already holds the prices paid.

**Acceptance:** none: a scope boundary; no dependency on the Price-compare library appears in the build.

**Since:** 2026-11-24 · owner · agreed rewording

### D-R46 · Households in v1

**Rule:** Version 1 has no households: no shared data, no second user, and one Profile receives receipts (R28).

**Why:** The owner will reassess the receipt folder's Profile when households are introduced.

**Acceptance:** none: a scope boundary for version 1.

**Since:** 2026-11-24 · owner · agreed rewording

### D-R47 · Currency conversion in v1

**Rule:** Version 1 converts no currency: a receipt in a currency other than its Profile's is held with a note (R30).

**Acceptance:** none: a scope boundary for version 1.

**Since:** 2026-11-24 · owner · agreed rewording

### D-R48 · Splitting a receipt by payment

**Rule:** Tesoriere never divides one receipt into several payments: Chatelaine closes one receipt per payment (Chatelaine's spec, rule 124).

**Why:** Tesoriere sees payments only days later, in a statement, when no one remembers which items went with which payment.

**Acceptance:** none: a scope boundary; the receipt review offers no way to divide a receipt into payments.

**Since:** 2026-11-24 · owner · agreed rewording
