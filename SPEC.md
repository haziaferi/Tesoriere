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
| D-R75 | Income switch | 7. Income |
| D-R76 | Paydays | 7. Income |
| D-R77 | Income received this month | 7. Income |
| D-R78 | No income logged | 7. Income |
| D-R79 | Salaried or hourly | 7. Income |
| D-R80 | Logged hours | 7. Income |
| D-R81 | Hourly payday | 7. Income |
| D-R82 | Hourly estimate | 7. Income |
| D-R83 | Leaving hourly | 7. Income |
| D-R84 | A salaried payday logged by hand | 7. Income |
| D-R60 | Recurring expenses | 8. Recurring expenses |
| D-R61 | The chain of due dates | 8. Recurring expenses |
| D-R62 | Apportioned share | 8. Recurring expenses |
| D-R63 | Sweeps | 8. Recurring expenses |
| D-R64 | Creating a recurring expense | 8. Recurring expenses |
| D-R65 | Editing a recurring expense | 8. Recurring expenses |
| D-R66 | Deleting a recurring expense | 8. Recurring expenses |
| O-R67 | Every recurring expense has a category | 8. Recurring expenses |
| D-R68 | Recurring threshold note | 8. Recurring expenses |
| D-R69 | Reminder dates | 8. Recurring expenses |
| D-R70 | The month cycle | 9. The month cycle |
| D-R71 | Automatic deposits on the same open | 9. The month cycle |
| D-R72 | End of a term | 9. The month cycle |
| D-R73 | A Loan that follows its payment | 9. The month cycle |
| D-R74 | Catch-up summary | 9. The month cycle |
| D-R85 | Accounts | 10. Credit and Loan accounts |
| D-R86 | Creating and editing an Account | 10. Credit and Loan accounts |
| D-R87 | An Account and its linked payment | 10. Credit and Loan accounts |
| D-R88 | Deleting an Account with payments charged to it | 10. Credit and Loan accounts |
| D-R89 | Quick-add on an Account | 10. Credit and Loan accounts |
| D-R111 | Undoing an Account quick-add | 10. Credit and Loan accounts |
| D-R90 | Loan calculator | 10. Credit and Loan accounts |
| D-R91 | Taking the loan or saving for it | 10. Credit and Loan accounts |
| D-R92 | Card payoff outlook | 10. Credit and Loan accounts |
| D-R93 | Committing a card's fixed payment | 10. Credit and Loan accounts |
| D-R94 | Changing an Account's type | 10. Credit and Loan accounts |
| D-R109 | A card whose charges are not logged | 10. Credit and Loan accounts |
| D-R110 | Card interest and fees | 10. Credit and Loan accounts |
| D-R95 | Savings movements in a report | 11. Reports |
| D-R96 | Age of Money | 11. Reports |
| D-R97 | Age of Money over the months | 11. Reports |
| D-R98 | Coverage | 11. Reports |
| D-R99 | Spending | 11. Reports |
| D-R100 | Savings vs Owed | 11. Reports |
| D-R101 | Income vs Expenses | 11. Reports |
| D-R102 | Overtime calculator | 12. Overtime calculator |
| D-R26 | Statement import | 13. Statement import |
| D-R103 | Statement formats | 13. Statement import |
| D-R104 | Statement amounts | 13. Statement import |
| D-R105 | CSV columns by header | 13. Statement import |
| D-R106 | Statement dates | 13. Statement import |
| D-R107 | The import review | 13. Statement import |
| D-R108 | Payee rules | 13. Statement import |
| D-R27 | The receipt contract | 14. Chatelaine receipts |
| D-R28 | Receipt folder | 14. Chatelaine receipts |
| D-R112 | The receipt Profile | 14. Chatelaine receipts |
| D-R29 | Picking up receipts | 14. Chatelaine receipts |
| D-R30 | Receipt files Tesoriere cannot take | 14. Chatelaine receipts |
| D-R116 | Scanning the receipt folder | 14. Chatelaine receipts |
| D-R117 | Reading a receipt file | 14. Chatelaine receipts |
| D-R31 | Receipt review | 14. Chatelaine receipts |
| D-R32 | Category links | 14. Chatelaine receipts |
| D-R33 | Closing a review | 14. Chatelaine receipts |
| D-R34 | Changing a link | 14. Chatelaine receipts |
| D-R35 | A receipt's source | 14. Chatelaine receipts |
| D-R113 | Opening a receipt review | 14. Chatelaine receipts |
| D-R114 | Answering a receipt review | 14. Chatelaine receipts |
| D-R115 | Confirming a receipt | 14. Chatelaine receipts |
| D-R36 | Store links | 15. One payment, two sources |
| D-R120 | The text a store link keeps | 15. One payment, two sources |
| D-R37 | Pairing rule | 15. One payment, two sources |
| D-R38 | Whichever arrives first | 15. One payment, two sources |
| D-R39 | Difference part | 15. One payment, two sources |
| D-R40 | Pairing by hand | 15. One payment, two sources |
| D-R118 | Suggesting a pair | 15. One payment, two sources |
| D-R119 | Recording a pair | 15. One payment, two sources |
| D-R128 | Unpairing | 15. One payment, two sources |
| D-R121 | The Trash | 16. The Trash |
| D-R122 | Restoring from the Trash | 16. The Trash |
| D-R123 | Deleting a category | 16. The Trash |
| D-R124 | Spending under a deleted category | 16. The Trash |
| D-R125 | Deleting and restoring a payment | 16. The Trash |
| D-R127 | Restoring a payment as a pair | 16. The Trash |
| D-R126 | A Profile in the Trash | 16. The Trash |
| D-R41 | Deleting a receipt payment | 17. Edge cases |
| D-R42 | Deleting a linked category | 17. Edge cases |
| D-R43 | Test plan | 18. Test plan |
| D-R44 | Rollout phases | 19. Rollout |
| D-R45 | Price comparison | Out of scope |
| D-R46 | Households in v1 | Out of scope |
| D-R47 | Currency conversion in v1 | Out of scope |
| D-R48 | Splitting a receipt by payment | Out of scope |

## Glossary

| Term | Meaning |
|---|---|
| Payment | One movement of money out, with one date, one total, one source and one or more parts. Every expense in Tesoriere is a payment, except a recurring expense's sweeps. |
| Part | One category's share of a payment, in cents. A payment's parts add up to its total. |
| Split | A payment with more than one part. |
| Source | Where a payment's money came from: Savings, a Goal, or a credit or loan Account. |
| Receipt | One payment made at one store, as Chatelaine closes it (Chatelaine's spec, rule 124). |
| Receipt file | The file Chatelaine writes for one receipt, in the format of Chatelaine's spec, rule 123. |
| Receipt folder | The folder the user chooses, where Chatelaine writes receipt files and Tesoriere reads them. |
| Bank row | One line of an imported bank statement (CSV or OFX). |
| Ledger | The log of every money movement that is not a payment, one row per movement (R51). |
| Adjustment | A logged change to a category's spent figure for one month, written as a `cat:` ledger row (R25, R51). |
| Recurring expense | A cost known in advance and the same each time, such as rent, set aside from Savings by sweeps (R60). |
| Sweep | A `rec:` ledger row that sets a recurring expense's money aside, filed under its category (R63, R67). |
| Payday | A day income is paid, following its cadence from the payday the user chose (R76). |
| Account | A credit card or a Loan the user tracks as money owed, by its balance (R85). |
| Shift | Hours worked on one day at a rate per hour, logged for hourly income; it moves no money until a payday pays it (R80, R81). |
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

**Acceptance:** A fresh Profile's Savings is unset; a 60.00 movement out of an unset Savings leaves it at -60.00; a direct edit to 0.00 leaves it at 0.00, no longer unset; with Income off and Savings unset, R56's comparison and R57's amounts are skipped.

**Not:** Reading unset as zero: an honest zero must stay distinguishable from no answer.

**Since:** 2026-11-24 · builder · own words

### D-R50 · Goals hold money out of Savings

**Rule:** Money put into a Goal leaves Savings and is held by the Goal, so no figure read from Savings counts it. Taking it back out of the Goal returns it to Savings.

**Evidence:** Sci-Fin's spec, section 3.2.

**Acceptance:** Adding 150.00 to a Goal raises its saved money by 150.00 and lowers Savings by 150.00.

**Not:** Counting Goal money in Savings as well: the same euro would count twice.

**Since:** 2026-11-24 · builder · own words

### D-R51 · The ledger

**Rule:** Every movement of money other than a payment is a ledger row: a key naming what it changes, the signed change to it, a date, the instant it was logged, the target's name at that instant, for a row that counts as spending the category it is filed under (R25), and for the rows of one Account quick-add the group they share (R111). How Savings moves follows from the key, as below. The date places the row in its month and in reports; the figure it changes moves when the row is written, whatever its date.

**Values:**

| `key` | `amount` is the change to | Savings moves by |
|---|---|---|
| `savings` | Savings | `+amount` |
| `goal:<id>` | the Goal's saved money | `-amount` |
| `cat:<id>` | the category's spent figure, as an adjustment (R25) | `-amount` |
| `rec:<id>` | the money swept for the recurring expense, positive when swept out | `-amount` |
| `acct:<id>` | the Account's balance | 0 |
| `spend:<goalId>` | the Goal's saved money, negative: a payment off an Account from the Goal (R89), or a row read from a Sci-Fin backup (R19) | 0 |

**Evidence:** Sci-Fin's spec, section 3.6, and `engine/Ledger.kt` at `2892110`; `spend:` for a payoff from a Goal: owner, 2026-11-25.

**Acceptance:** A 20.00 adjustment on a category is stored as `cat:<id>` with amount 2000 and lowers Savings by 20.00; a 100.00 sweep is `rec:<id>` with amount 10000 and lowers Savings by 100.00; an `acct:` row leaves Savings as it was; a 60.00 payoff of a card from a Goal is `spend:<goalId>` with amount -6000 and leaves Savings as it was.

**Not:** Storing the Savings effect instead of the change to the target: every goal and adjustment row would read inverted. A key of its own for a payoff from a Goal: `spend:` already moves a Goal and not Savings, and the reports read it so (R95, R99).

**Since:** 2026-11-25 · builder · own words

### D-R52 · Direct edits log the change

**Rule:** Savings and a Goal's saved money can be set directly to a new figure. The edit logs one ledger row holding the change, never the new figure, with the note `Correction` unless the user writes one; a change to a Goal's saved money moves Savings by its opposite. An edit to the same figure logs nothing.

**Evidence:** Sci-Fin's spec, section 3.6, and `engine/Writes.kt` at `2892110`.

**Acceptance:** Setting Savings from 150.00 to 1,000.00 logs a `savings` row of +850.00; setting a Goal's saved money from 400.00 to 300.00 logs a `goal:<id>` row of -100.00 and raises Savings by 100.00.

**Not:** Logging the new figure itself: a second statement of the truth that the ledger's sums could no longer match.

**Since:** 2026-11-24 · builder · own words

### D-R53 · Undoing a mistaken entry

**Rule:** The ledger's rows are never edited or deleted one by one. The exceptions are a pair, two rows deleted together when the second cancels the first under every condition below, and the rows of one Account quick-add, undone together (R111). Deleting a pair changes no balance; the date of the last logged income is recomputed from the rows left.

**Values:**

| Condition | Holds when |
|---|---|
| Same target | both rows have the same `key` and are filed under the same category or none; two `cat:` adjustments, or two rows filed under a category, are also dated in the same month (R25) |
| Opposite | the amounts are equal, opposite and not zero |
| Next | the second is the next row on that key in logged order, with none between |
| Window | they were logged at most 48 hours apart |
| Once | each row belongs to at most one pair, pairing forward only |
| Fixed | which rows pair is found once, from the ledger before any delete |
| Not a sweep | neither row has a `rec:` key |

**Evidence:** Sci-Fin's spec, section 7.3, and `engine/Ledger.kt` at `2892110`.

**Acceptance:** Rows of +40.00 and -40.00 on one Goal logged 47 hours apart pair; 49 hours apart they do not; four rows +40.00, -40.00, +40.00, -40.00 logged in that order make two pairs, the first two and the last two. A -60.00 `savings` row filed under Shopping and a +60.00 `savings` correction logged an hour later do not pair; two such rows both filed under Shopping in one month do.

**Not:** Deleting single rows: the ledger would stop adding up to the figures it moved. Pairing a row filed under a category with one that is not: Savings would come out even and the category's spent would lose the first.

**Since:** 2026-11-25 · builder · own words

### D-R21 · Payment

**Rule:** Every expense is a payment with the fields below, except a recurring expense's sweeps, which are ledger rows filed under its category (R63, R67). A refund is a payment with a negative total and parts that add up to it (R23): it lowers its categories' spent in its month, gives the money back to its source (R54), and is not income. A payment from a receipt keeps the receipt's lines, so each part can be traced to the lines it came from.

**Values:**

| Field | Holds |
|---|---|
| `id` | UUID |
| `date` | the day of the payment: the receipt's date when there is a receipt, else the bank row's or the entered date |
| `loggedAt` | the instant it was recorded |
| `total` | cents, not zero: positive for a purchase, negative for a refund |
| `source` | Savings, a Goal, or an Account (R22) |
| `payee` | optional text: the store or the bank row's payee |
| `parts` | one or more: category id and cents (R23), and whether the part is a pair's difference part (R39); only a part read from a Sci-Fin backup may have no category (R19) |
| `receiptId` | the receipt file's id, when the payment came from or was paired with a receipt |
| `receiptLines` | per line: name, pack, quantity, price paid, Chatelaine category id and name, Tesoriere category |
| `bankKeys` | the bank row's FITID and its date, amount and description key, when it came from or was paired with a bank row (R26) |

**Acceptance:** A recorded receipt payment holds every receipt line; a paired payment holds both its `receiptId` and its `bankKeys`; a recurring expense's sweep records no payment. With Savings at 1,000.00 and a 40.00 payment under Fun, a 15.00 refund from Savings under Fun leaves Savings at 975.00 and Fun's spent for the month at 25.00, and logs no ledger row and no income.

**Not:** A sweep as a payment: an apportioned share is a monthly set-aside, not money paid to anyone, and a return on deletion undoes sweeps (R66) rather than refunding a purchase.

**Since:** 2026-11-25 · owner · agreed rewording

### D-R22 · Source and parts are separate

**Rule:** A payment has one source, where the money came from, and its parts, what it was spent on. A payment charged to a credit card counts in its categories and raises the card's balance.

**Why:** A card-paid shopping trip is still groceries and household spending; in Sci-Fin a card charge replaced the category, so it counted in none.

**Acceptance:** A EUR 40 receipt charged to a card adds its parts to their categories' spent figures and EUR 40 to the card's balance, and moves nothing in Savings.

**Not:** Sci-Fin's model, where a charge to a card has no category.

**Since:** 2026-11-24 · owner · agreed rewording

### D-R54 · What a payment's source moves

**Rule:** A payment's source decides what its total moves, as below, and a refund's negative total moves it the other way (R21): Savings and a Goal go up, an Account's balance down; its parts move only the categories' spent figures (R25). A payment is its own record and writes no ledger row, except the `savings` row of a deleted Goal's payment and the `acct:` row of a deleted Account's payment below. Editing a payment undoes its old source's effect and applies the new one; removing it undoes its effect.

**Values:**

| Source | Its total | Savings |
|---|---|---|
| Savings | lowers Savings | lowered by the total |
| Goal | lowers the Goal's saved money; a total above what the Goal holds takes all it holds and the rest from Savings, after a warning the user can dismiss, and the payment keeps the share it took from Savings, so undoing, editing or removing it restores the Goal and Savings exactly; an edit works the share out again against what the Goal holds once the payment's own share is returned to it | lowered by the share taken from Savings, else unchanged |
| Account | raises the Account's balance | unchanged |
| a Goal deleted since (R58) | keeps the Goal as its recorded source; undoing, editing or removing it moves Savings in the Goal's place, after a warning the user can dismiss, and logs that movement as one `savings` row dated the day it is made, noted `Payment from a deleted goal`, so the movements of Savings still add up to it (R95) | moves as for a Savings source |
| an Account deleted since (R88) | keeps the Account as its recorded source; it can be edited, re-filed or removed after a warning the user can dismiss, and moves no balance; a change to the total charged to it, by a removal, a new total or a move to another source, is logged as one `acct:` row of the opposite sign, dated the day it is made, noted `Payment to a deleted account`, so the Account's rows and the payments charged to it still add up to zero (R88, R100) | unchanged |

**Evidence:** Sci-Fin's spec, sections 3.6 and 7.2, at `2892110`. The deleted-Goal row: owner, 2026-11-24, after review of PR 2. The deleted-Account row: owner, 2026-11-25; the deleted-Goal payment's `savings` row and the deleted-Account payment's `acct:` row: builder, 2026-11-25. A Goal short of a payment's total, Savings paying the rest: owner, 2026-11-25, in place of owner answer 6 of 2026-11-24.

**Acceptance:** A 42.30 payment from Savings lowers Savings by 42.30; a 30.00 payment from a Goal holding 50.00 leaves it 20.00 and Savings unchanged, and one of 60.00, after a warning, leaves it 0.00 and Savings 10.00 lower; removed, the Goal holds 50.00 and Savings is as it was; edited to 55.00 it takes 5.00 from Savings, and to 40.00 none, leaving the Goal 10.00; a 40.00 payment charged to a card with a 0.00 balance leaves the balance at 40.00, and a 15.00 refund to it then leaves 25.00; a 15.00 refund into a Goal holding 50.00 leaves it 65.00. With Savings at 1,000.00: 100.00 into a Goal, a 30.00 payment from it, the Goal deleted with its 70.00 returned leaves 970.00; removing the payment then gives 1,000.00 with a `savings` row of +30.00, re-filing its parts leaves 970.00 and logs nothing, and lowering its total to 25.00 gives 975.00 with a row of +5.00; after each, the movements of Savings add up to Savings. A 40.00 payment charged to a card that is deleted since carries a warning, can be re-filed into two parts or removed with Savings unchanged, and a new payment charged to that card cannot be saved. A 40.00 payment charged to a card at 0.00 that is deleted since, removed, logs an `acct:` row of +40.00 for that card; moved to a second card instead, it raises that card to 40.00 and logs the same row; after each, Savings vs Owed's point for the month equals its figure of the day (R100).

**Not:** Refusing a payment a Goal is short of: the money was spent all the same, and Savings holds the rest. Taking a Goal below zero: it would hold less than nothing. Working a short payment's share out again on undo or removal: what the Goal holds may have changed since, and the money would not return to where it came from. Leaving a deleted Goal's payment with nowhere to return its money: Savings would stay short by the payment. Locking a deleted Account's payments: their parts could not be filed again when a receipt arrives late (R38).

**Since:** 2026-11-25 · owner · agreed rewording

### D-R23 · Parts add up

**Rule:** A payment's total is not zero, its parts add up to it to the cent, and every part has the sign of the total, so a refund's parts are all negative (R21); the one exception is a pair's difference part (R39), at most one per payment, which may have either sign and which only pairing writes, an edit keeping one the payment already holds. Until they do, the editor shows the remainder and its save stays off.

**Acceptance:** A EUR 42.30 payment with parts of EUR 30.00 and EUR 12.00 shows EUR 0.30 left and cannot be saved; with a third part of EUR 0.30 it can. A EUR -15.00 refund with parts of EUR -20.00 and EUR 5.00 cannot be saved, and with one part of EUR -15.00 it can; a total of EUR 0.00 cannot be saved. A EUR 42.10 pair with item parts of EUR 30.00 and EUR 12.30 and a difference part of EUR -0.20 can be saved, and so can an edit of it that moves its item parts; the same EUR -0.20 as an item part, two difference parts, or a difference part entered by hand, cannot.

**Not:** Filing the remainder to a default category on its own: it hides a mistake. A part of the other sign: a refund that raises one category's spent is a purchase filed in the wrong place. Holding the difference part to the total's sign: a bank row below the receipt's total would leave the pair with no way to add up.

**Since:** 2026-11-25 · builder · own words

### D-R24 · Any payment can be split

**Rule:** Any payment can be split by hand into parts, whether it came from a bank row or was logged by hand. A payment from a receipt arrives already split by its lines' categories (R31).

**Acceptance:** A bank row and a hand-logged payment can each be saved with two parts in two categories.

**Not:** Splits for receipts only: a bank row with no receipt behind it, such as a pharmacy bill, could not be divided.

**Since:** 2026-11-24 · owner · agreed rewording

### D-R25 · Spent is derived

**Rule:** A category's spent figure for a month is computed when read, from the parts of the payments dated in that month, the category's logged adjustments dated in it, and the other ledger rows filed under the category and dated in it: recurring expense rows other than an Account payoff, a card's payment's or a Loan's with a payment charged to it (R87), the payoff rows of a card whose charges are not logged (R109), a card's interest and fees (R110), and a Loan's payments by hand while nothing is charged to it (R89). It is never stored.

**Why:** A late receipt can re-file a payment that is already recorded (R38); with a derived figure that is one change to the payment, not correction rows.

**Acceptance:** Re-filing a payment's parts changes the spent figures of the categories involved, for the payment's month, and writes no correction row. A 60.00 sweep filed under Phone and dated in March adds 60.00 to Phone's spent for March, and no payment exists for it.

**Not:** Sci-Fin's stored spent figure, edited directly and incremented by each entry: every re-filing would need correction rows. Sci-Fin's direct edit of spent becomes a logged adjustment.

**Since:** 2026-11-25 · owner · agreed rewording

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

## 7. Income

### D-R75 · Income switch

**Rule:** Income is off on a fresh Profile (R18) and the user switches it on in the settings. Switching it on keeps the day it was switched on and takes that day as the payday until the user chooses another. Switching it off clears every income setting and the hours not yet paid, as below, so switching it on again starts blank; money already received stays in Savings.

**Why:** A pay figure kept from a harder period could resurface later, and a stale deposit marker would deposit paydays that never arrived (R71).

**Values:**

| Setting | After switching off |
|---|---|
| amount per paycheck | 0 |
| automatic deposit | off |
| both paydays | none |
| the day Income was switched on | none |
| the last day income was logged, the last automatic deposit | none |
| how income is paid (R79) | salaried, a rate of 0, no payday answered |
| hours logged and not yet paid (R80) | removed |

**Evidence:** Sci-Fin's spec, sections 3.3 and 3.10, and `engine/Income.kt` and `engine/Hourly.kt` at `2892110`.

**Acceptance:** A fresh Profile has Income off, salaried, with automatic deposit off. Switched on on 14 September 2026, Income has that day as its switch-on day and its payday. A semimonthly income of 1,450.00 on the 15th and the 30th with automatic deposit on, switched off, has an amount of 0.00, automatic deposit off, and no payday, switch-on day, last logged day or last deposit. An hourly income with two shifts logged, switched off, has no shift and is salaried at a rate of 0.00.

**Not:** Taking back money already deposited: the switch controls only what comes after it.

**Since:** 2026-11-25 · builder · own words

### D-R76 · Paydays

**Rule:** Income has an amount per paycheck and a cadence, and its paydays follow the cadence from the payday the user chose, as below; a day a month lacks falls on that month's last day, and the next month returns to the chosen day. With no payday chosen, or a semimonthly income with one of its two days not chosen, there is no payday, so nothing is deposited or asked. Changing the cadence clears the chosen payday, the second one unless the new cadence is semimonthly, and the last automatic deposit, so no payday is counted across the change.

**Values:**

| Cadence | Paydays |
|---|---|
| weekly | every 7 days from the chosen payday |
| biweekly | every 14 days from the chosen payday |
| semimonthly | two days of each month: the chosen payday's and a second chosen day's |
| monthly | the chosen payday's day of each month |

**Evidence:** Sci-Fin's spec, section 3.3, and `engine/Income.kt` at `2892110`.

**Acceptance:** A biweekly income first paid 11 September 2026 is next paid 11 September read that day, 25 September read on 12 September and 9 October read on 26 September; a weekly one is next paid 18 September read on 12 September. A monthly income paid on 31 January 2026 is next paid 28 February read on 1 February, then 31 March. A semimonthly income on the 15th and the 30th is next paid 15 September read on 2 September, 30 September read on 16 September and 15 October read on 1 October; one on the 15th and the 31st is paid 28 February 2026. A semimonthly income changed to monthly has no payday, no second payday and no last deposit; set to the cadence it has, it is unchanged.

**Not:** A flat 30 days for monthly pay: the payday would drift across the calendar.

**Since:** 2026-11-25 · builder · own words

### D-R77 · Income received this month

**Rule:** The income received this month is worked out when read, never stored: the sum of the income rows dated in the month and on or after the day Income was switched on. An income row is one the user logs as income, an automatic deposit (R71) or a confirmed hourly payday (R81); it counts in the month of its date, whatever day it was logged. Nothing is counted before it arrives.

**Why:** Worked out from the rows, the figure needs no monthly reset and gives the same answer whichever runs first, the month cycle or the deposits (R71).

**Evidence:** Sci-Fin's spec, section 3.3, and `engine/Income.kt` at `2892110`.

**Acceptance:** With Income switched on on 5 September 2026 and read on 14 September: 1,450.00 of income dated 11 September counts; 1,450.00 of income dated 28 August, 300.00 of income dated 2 September and 50.00 into Savings not logged as income do not; the figure is 1,450.00.

**Not:** A monthly figure worked out from the cadence: a month with a third biweekly payday, or a payday not yet arrived, would misstate the money there is.

**Since:** 2026-11-25 · builder · own words

### D-R78 · No income logged

**Rule:** For salaried income with automatic deposit off, the engine gives the number of days with no income logged once a payday has passed with none, as below; it is shown as a note that informs and never blocks (R12), and how it reads is the design pass's (R16). The count is today minus the reference day: the last day income was logged or, with none logged since Income was switched on, the switch-on day. Hourly income has none: its payday asks instead (R81).

**Values:**

| Cadence | Given when |
|---|---|
| weekly | more than 7 days have passed since the reference day |
| biweekly | more than 14 days have passed since the reference day |
| monthly | the first payday after the reference day is today or earlier |
| semimonthly | the latest payday on or before today is after the reference day |

**Evidence:** Sci-Fin's spec, section 3.3, and `engine/Income.kt` at `2892110`.

**Acceptance:** A biweekly income last logged 20 August 2026 gives 25 on 14 September, none on 3 September and 15 on 4 September. A weekly income switched on on 10 September 2026 with nothing logged gives none on 14 September and 8 on 18 September. A monthly income on the 10th last logged 10 August 2026 gives none on 9 September and 31 on 10 September. A semimonthly income on the 15th and the 30th last logged 1 September 2026 gives none on 14 September and 15 on 16 September. With automatic deposit on, or hourly, it gives none.

**Not:** Counting from the missed payday: the count would read lower than the days that have passed with nothing logged.

**Since:** 2026-11-25 · builder · own words

### D-R79 · Salaried or hourly

**Rule:** Income is paid either salaried, a set amount per paycheck, or hourly, by a rate per hour on the same paydays (R76); a Profile has one type. Hourly income has no automatic deposit (R71) and no count of days with no income (R78): what arrives is confirmed on its payday (R81). Changing the type needs no hours left logged (R83), turns automatic deposit off and clears the last automatic deposit; a change to hourly counts paydays after the day of the change only.

**Evidence:** Sci-Fin's spec, section 3.10, and `engine/Hourly.kt` at `2892110`.

**Acceptance:** A salaried income with automatic deposit on, changed to hourly on 2 November 2026, has automatic deposit off and asks about no payday on or before 2 November. Changed to hourly on its payday of 13 November 2026, it does not ask about 13 November and asks about 27 November. An hourly income with automatic deposit set and 1,000.00 per paycheck deposits nothing by 31 December 2026 and gives no count.

**Not:** Automatic deposit for hourly pay: what arrives varies with the hours.

**Since:** 2026-11-25 · builder · own words

### D-R80 · Logged hours

**Rule:** Hourly income logs hours as shifts: a day, the hours worked and a rate per hour, which starts from the income's rate and can be changed per shift. A shift moves no money and adds nothing to the income received (R77); it can be edited or removed until a payday pays it. A shift's pay before deductions is its hours times its rate, rounded to the cent with a half cent rounded up, and a set of shifts' is the sum of each shift's.

**Evidence:** Sci-Fin's spec, section 3.10, and `engine/Hourly.kt` at `2892110`.

**Acceptance:** A 6-hour shift at 20.00 logged on 3 November 2026 leaves Savings, the ledger and the income received as they were. Shifts of 6 and 8.5 hours at 20.00 come to 290.00 before deductions. A shift of 1.5 hours at 13.33 comes to 20.00.

**Not:** Hours worked times the rate stored as money with fractions of a cent (R20).

**Since:** 2026-11-25 · builder · own words

### D-R81 · Hourly payday

**Rule:** Each payday of hourly income, from the first on or after the day Income was switched on, is asked about once its day has come, the oldest first, until answered, whether or not hours are logged, and covers every shift dated on or before it. Confirmed with what arrived, above zero, it writes one income row of that amount dated to the payday, carrying the hours and their rate (the pay before deductions divided by the hours, rounded to the cent), and the shifts it paid are removed. Answered with nothing this time, it writes nothing and its shifts wait for the next payday.

**Evidence:** Sci-Fin's spec, section 3.10, and `engine/Hourly.kt` at `2892110`.

**Acceptance:** A biweekly hourly income at 20.00, switched on on 1 November 2026 and paid from 13 November, with shifts of 6, 8.5 and 4 hours dated 3, 10 and 14 November, asks nothing on 12 November and on 13 November asks about 13 November with 14.5 hours and 290.00. Confirmed with 232.00, Savings goes from 1,000.00 to 1,232.00 by one income row dated 13 November with 14.5 hours at 20.00, the 4-hour shift stays, nothing is asked on 20 November and 27 November is asked on that day. Answered with nothing this time, a 6-hour shift waits for 27 November; with no hours logged, 13 November is still asked.

**Not:** Writing each shift as income: it would count money that has not arrived.

**Since:** 2026-11-25 · builder · own words

### D-R82 · Hourly estimate

**Rule:** The estimate for logged hours is their pay before deductions (R80), marked as such, while no payday has been confirmed. With a confirmed payday it is the pay before deductions times the average, over the last three confirmed hourly paydays, of what arrived divided by the hours times their rate, rounded to the cent, and unmarked. The user is never asked for a tax rate.

**Why:** With varying hours the share taken from pay is not one steady figure the user could enter; what arrived tells it.

**Evidence:** Sci-Fin's spec, section 3.10, and `engine/Hourly.kt` at `2892110`.

**Acceptance:** 10 hours at 20.00 are estimated at 200.00 before deductions. After four paydays of 10 hours at 20.00 at which 160.00, 160.00, 100.00 and 200.00 arrived, 10 more hours at 20.00 are estimated at 153.33, from the last three.

**Since:** 2026-11-25 · builder · own words

### D-R83 · Leaving hourly

**Rule:** Hours still logged when hourly income is left, for salaried or by switching Income off, are settled first: what arrived for them is confirmed as one income row dated today, carrying their hours and rate as a payday's does (R81), or they are discarded. Either way no shift is left.

**Evidence:** Sci-Fin's spec, section 3.10, and `engine/Hourly.kt` at `2892110`.

**Acceptance:** Shifts of 6 and 4 hours settled on 2 November 2026 with 170.00 leave no shift and one income row of 170.00 dated 2 November with 10 hours; discarded, they leave no shift and no row, and the income can then be made salaried. Changing the type with a shift still logged is refused.

**Since:** 2026-11-25 · builder · own words

### D-R84 · A salaried payday logged by hand

**Rule:** Salaried income with automatic deposit off offers one payday for logging by hand: the latest payday since the last one offered, or since the day Income was switched on, on that payday or the day after, while no income has been logged on or after it. Earlier missed paydays are left to the count of days with no income (R78). The engine gives the payday's date; how the offer reads is the design pass's (R16).

**Evidence:** Sci-Fin's spec, section 3.10, and `engine/Hourly.kt` at `2892110`.

**Acceptance:** A 1,450.00 monthly income on the 10th, switched on on 1 November 2026, offers nothing on 9 November, 10 November on 10 November, nothing on 12 November, and 10 December on 11 December. With 1,450.00 logged on 10 November, with 10 November already offered, or with automatic deposit on, nothing is offered on 11 November.

**Since:** 2026-11-25 · builder · own words

## 8. Recurring expenses

### D-R60 · Recurring expenses

**Rule:** A recurring expense is a cost known in advance and the same each time until the user changes it, such as rent, a subscription or a loan payment. It has a name, an amount, a cadence, a first due date, an optional end date, a category (R67) and the day it was created (R64), which an expense read from a Sci-Fin backup, keeping none, takes as its first due date; its money leaves Savings by sweeps (R63), never by payments. Its due dates follow its cadence as below; a date mode exists only where the table gives one.

**Values:**

| Cadence | Due dates | Date mode |
|---|---|---|
| weekly | every 7 days from the first due date | none |
| biweekly | every 14 days from the first due date | none |
| semimonthly | two days of each month: the first due date's and a second due date's | none |
| monthly | fixed date: the same day each month; interval: every 30 days | fixed date or interval |
| bimonthly | fixed date: the same day every 2 months; interval: every 60 days | fixed date or interval |
| yearly | fixed date: the same day every 12 months; interval: every 365 days | fixed date or interval |

**Evidence:** Sci-Fin's spec, section 3.11, and `engine/Recurring.kt` at `2892110`. The stored creation day: owner, 2026-11-25; schema 1 is still edited in place until the first install, so the field is part of it, and a Sci-Fin backup has none and reads it as the first due date.

**Acceptance:** A weekly expense first due 2 January 2026 falls due 5 times in January and 4 times in February; a biweekly one falls due 3 times in January (2, 16 and 30) and twice in February. A monthly interval expense first due 1 January 2026 falls due on 1 and 31 January, none in February, then 2 March. A weekly expense saved with fixed-date mode keeps no date mode. An expense with no creation day, as from a Sci-Fin backup, first due 5 March 2026, reads as created on 5 March.

**Not:** A cost that changes every time, such as groceries: it belongs in a category's plan (R59). Reading a missing creation day as the day of the backup's import: the bank rows of months Sci-Fin swept would no longer be found as the expense's, and would be recorded a second time (R107).

**Since:** 2026-11-25 · builder · own words

### D-R61 · The chain of due dates

**Rule:** Each expense keeps its first due date, before which no due date falls, and its next due date, which moves on to the following due date once its day has passed, from the start of the next day, never when a sweep runs. A day a month lacks falls on that month's last day, and a fixed-date expense returns to its own day in the months after. A first due date entered in the past moves the next due date forward to today or later; the chain stops at the last due date on or before the end date.

**Evidence:** Sci-Fin's spec, section 3.11, and `engine/Recurring.kt` at `2892110`.

**Acceptance:** A monthly expense due 10 September 2026 is still due 10 September on that day, due 10 October on 11 September, and due 10 January 2027 when read on 25 December 2026. A fixed-date rent on the 31st falls due on 28 February 2026 and then 31 March 2026; a monthly interval expense due 31 January falls due next on 2 March. An expense entered on 14 September 2026 with a first due date of 5 March 2026 keeps 5 March as its first due date and is next due 5 October. A monthly expense on the 20th with an end date of 20 September 2026, read on 2 October, stays due 20 September.

**Not:** Taking the day a short month fell on as the expense's new day: a rent on the 31st would fall on the 28th for good.

**Since:** 2026-11-24 · builder · own words

### D-R62 · Apportioned share

**Rule:** A bimonthly or yearly expense sets aside a share each month instead of its whole amount once, and that share is what counts in its category's spent and in Spending each month (R67, R99), never the whole amount in the month the bank pays the bill, whose bank row arrives as already swept (R107). The share is the amount divided by the number of 1sts of a month after the cycle's start and on or before the next due date, at least 1, rounded up to the cent, so the whole amount is set aside by the time it falls due. The share is held for the cycle and worked out again only as below; after the last due date the end date allows, nothing more is set aside.

**Values:**

| When | The share is worked out from |
|---|---|
| the expense is created | today |
| the next due date moves on (R61) | the due date just passed |
| an edit changes the amount, the cadence, a due date, the date mode or the end date | today |
| any other edit | nothing: the held share is kept |

**Evidence:** Sci-Fin's spec, section 3.11, and `engine/Recurring.kt` at `2892110`.

**Acceptance:** A 1,200.00 yearly expense due 15 July 2027 has a share of 100.00 from a cycle that started 15 July 2026, and of 120.00 when created on 14 September 2026. A 1,000.00 yearly expense due 1 April 2026 has a share of 333.34 from 1 January 2026; a 1,200.00 one due 25 June 2026 has a share of 1,200.00 from 20 June. An expense holding 300.00 whose due date of 15 July 2026 has passed holds 100.00 for its next cycle. Renaming an expense keeps its share of 100.00; changing its amount to 2,400.00 on 14 September 2026 gives 240.00. A 600.00 yearly expense created on 15 July 2026 and due 15 July 2027 counts 50.00 in each month from August 2026 to July 2027; the bank's -600.00 on 15 July 2027 arrives `RECURRING`, and July 2027 still counts 50.00.

**Not:** Dividing by a count that shrinks month by month: the share would grow through the cycle. Counting the bill in the month the bank pays it: one month would carry a year's cost and the others none.

**Since:** 2026-11-25 · builder · own words

### D-R63 · Sweeps

**Rule:** A recurring expense's money leaves Savings by sweeps, ledger rows with its `rec:` key (R51), on the 1st of each month and on creation (R64); a finished expense sweeps nothing. For a weekly, biweekly, semimonthly or monthly expense, each due date in the month, on or after the first due date and on or before the end date, writes its own row of the whole amount, dated to that due date; a bimonthly or yearly expense writes one row of its share (R62), dated to the 1st and marked as a share, set aside toward a bill still to come, as is an accepted difference of such an expense (R65). The month's scheduled total is what every expense sweeps for that month, whether or not its sweep has run.

**Evidence:** Sci-Fin's spec, sections 3.5 and 3.11, and `engine/Recurring.kt` at `2892110`. The share mark: builder, 2026-11-25, stored on the row, part of schema 1, which is still edited in place until the first install; a Sci-Fin backup's rows have none and read as no share.

**Acceptance:** A semimonthly 300.00 expense on the 15th and the 31st falls due on 15 and 28 February 2026. A monthly 100.00 expense on the 20th with an end date of 20 September 2026 sweeps in September and not in October; with an end date of 19 September it sweeps nothing in September. A yearly expense holding a 50.00 share sweeps 50.00 for September 2026; a bimonthly 90.00 expense with no share held sweeps 45.00. Two sweeps on 1 August and 1 September 2026 leave two rows dated to those days.

**Not:** One row per month for the combined amount: a return on deletion (R66) could not tell the due dates passed from those ahead. Telling a share from a due date's row by its day: a monthly expense due the 1st writes rows on the 1st too.

**Since:** 2026-11-25 · builder · own words

### D-R64 · Creating a recurring expense

**Rule:** Creating an expense sweeps at once only as the table says; otherwise its first sweep is a later 1st. Every case marks the current month as swept, so the month is never swept twice, and keeps today as the day the expense was created (R60), whatever its first due date; so does an expense created with a Loan or a card's fixed payment (R91, R93).

**Values:**

| Cadence | First due date | On creation |
|---|---|---|
| weekly to monthly | today or later this month | sweeps this month's due dates (R63) |
| weekly to monthly | earlier this month or in an earlier month | sweeps nothing, even with a later due date this month |
| weekly to monthly | a later month | sweeps nothing |
| bimonthly or yearly | any | sweeps nothing; its share is worked out from today (R62) |

**Evidence:** Sci-Fin's spec, section 3.11, and `engine/Recurring.kt` at `2892110`.

**Acceptance:** On 14 September 2026 with Savings at 1,000.00: a 100.00 monthly expense first due 20 September leaves 900.00 and one row dated 20 September; first due 10 September, it leaves 1,000.00; a biweekly one first due 10 September leaves 1,000.00 though 24 September is ahead; a yearly 1,200.00 one due 15 July 2027 leaves 1,000.00 and a share of 120.00. On 25 September a monthly one first due 5 October leaves 1,000.00. Each is marked swept for September 2026 and keeps the day it was created; one entered on 14 September with a first due date of 5 March keeps 14 September.

**Not:** Sweeping a due date already passed at creation: the expense did not exist when it fell due.

**Since:** 2026-11-25 · owner · agreed rewording

### D-R65 · Editing a recurring expense

**Rule:** An edit that moves the cadence, a due date or the date mode starts the chain again from the date entered (R61); an edit to only the second semimonthly date keeps the first. When the current month has swept and the edit changes what the month sweeps, the difference is offered, never moved on its own. Accepted, it writes one `rec:` row dated to the month's earliest due date not yet passed, else its last, or the 1st for a bimonthly or yearly expense; on an expense linked to a Loan, the Loan's balance also falls by the difference, with an `acct:` row on the same date.

**Evidence:** Sci-Fin's spec, section 3.11, and `engine/Recurring.kt` at `2892110`.

**Acceptance:** On 10 September 2026, a 1,000.00 monthly rent due the 20th, swept from Savings of 2,000.00 and edited to 1,200.00, offers 200.00 dated 20 September; accepted, Savings is 800.00. The same edit before the month has swept offers nothing. A swept 260.00 Loan payment due the 20th edited to 300.00 and accepted leaves the Loan at 3,960.00 from 4,000.00, with a -40.00 `acct:` row dated 20 September. A fixed-date rent on the 1st moved to the 15th on 10 September falls due on 15 November.

**Not:** Moving the difference without asking: a mistyped digit would move money at once.

**Since:** 2026-11-24 · builder · own words

### D-R66 · Deleting a recurring expense

**Rule:** Deleting an expense offers back what its rows hold for due dates not yet passed, when that is above zero: the sum of its `rec:` rows dated today or later, or, for a bimonthly or yearly expense, of its rows dated after its previous due date; a finished expense offers nothing. Accepted, the sum returns to Savings as a `rec:` row of the opposite sign, dated today, filed under the category the reversed rows were filed under, one row per category when they were filed under more than one, so it lowers that category's spent for the month of the delete, which may then fall below zero (R67); the earlier rows stay. An Account linked to the expense is deleted with it, with a closing `acct:` row for its balance.

**Evidence:** Sci-Fin's spec, sections 3.11 and 7.2, and `engine/Recurring.kt` at `2892110`.

**Acceptance:** A semimonthly 300.00 expense due the 15th and 30th, created on 1 September 2026 from Savings of 2,000.00, offers 300.00 when deleted on 20 September; accepted, Savings is 1,700.00 and three rows remain. A rent swept at 1,000.00 and edited to 1,200.00 with the difference declined offers 1,000.00. An expense due 1 September offers its 100.00 on 1 September and nothing on 2 September. A yearly expense that swept 100.00 on 1 August and on 1 September 2026 offers 200.00. A yearly expense whose 100.00 share swept on 1 August under Phone and, after a move to Internet, on 1 September under Internet, deleted on 14 September with the return accepted, writes -100.00 under Phone and -100.00 under Internet. A yearly insurance of 1,200.00 under Insurance whose 100.00 share swept on 1 August and 1 September 2026, deleted on 14 September with the return accepted, leaves Insurance's September spent at -100.00 and its August spent at 100.00. Deleting an expense linked to a Loan of 5,000.00 removes both and logs a -5,000.00 `acct:` row.

**Not:** Offering the expense's amount: after an edit it differs from what the sweeps took. Filing the return under the expense's category of the day: after a move it would lower a category that the sweeps never raised. Dating the return to the months its rows were swept in: past months' figures would shift on their own.

**Since:** 2026-11-25 · builder · own words

### O-R67 · Every recurring expense has a category

**Rule:** Every recurring expense has a category, picked from the existing ones or created on the spot when the expense is created or edited, and it cannot be saved without one. Each row the expense writes, a sweep, a share, an accepted difference or a return on deletion, keeps the category it is written under and counts in that category's spent for the row's month (R25): a change of category applies only to rows written after it, a return carries the category of the rows it reverses (R66), and a return can leave a month's spent below zero, as intended. The rows of an expense linked to an Account count as R87 says.

**Why:** Rent and subscriptions are spending; left out of every category, a month's spent figures would miss them.

**Acceptance:** An expense with no category, or naming a category that does not exist, cannot be saved. A 60.00 monthly expense under Phone adds 60.00 to Phone's spent in each month it sweeps; a yearly 600.00 expense sweeps a 50.00 share into its category each month; a return of 300.00 on 20 September 2026 lowers its category's spent for September by 300.00, and a yearly insurance's 200.00 of unused shares returned on 14 September leaves Insurance's September spent at -100.00 after its 100.00 share. After an expense moves from Phone to Internet, Phone's spent for the past months is unchanged and the next sweep counts under Internet; a 300.00 return of sweeps filed under Phone, written after the move, lowers Phone by 300.00 and leaves Internet as it was.

**Not:** Moving past rows to the new category: past months' figures would shift on their own.

**Since:** 2026-11-25 · owner · agreed rewording

### D-R68 · Recurring threshold note

**Rule:** A note, which informs and never blocks (R12), is shown when the month's scheduled total (R63) is above the share of the base the table gives. At a base of zero or below it is still shown, marked as having no base, and takes no ratio. The engine gives a code and the two figures; how the note reads is the design pass's (R16).

**Values:**

| Income | Base | Shown when the scheduled total is above |
|---|---|---|
| off | Savings; no note while Savings is unset (R49) | 1/12 of the base |
| on | the income received this month | 1/3 of the base |

**Evidence:** Sci-Fin's spec, section 3.5, and `engine/Threshold.kt` at `2892110`.

**Acceptance:** With Income off and Savings at 12,000.00, a scheduled 900.00 shows no note and 1,100.00 shows one holding 1,100.00 and 12,000.00; with Savings at -5.00 the note is marked as having no base; with Savings unset, or nothing scheduled, there is none. With Income on and nothing received, 900.00 shows a note with no base; after 1,450.00 received it holds a base of 1,450.00; after 2,900.00 there is none.

**Not:** Counting Savings when Income is on: a large balance would hide a monthly rate that cannot last.

**Since:** 2026-11-24 · builder · own words

### D-R69 · Reminder dates

**Rule:** The engine works out the dates of the reminders below; delivering them is the platform's, and how they read is the design pass's (R16). An expense's reminder is off until the user turns it on, with a lead of 1, 3, 7 or 14 days counted back from the next due date (R61), so it follows the chain; a finished expense has none. Each reminder fires at 09:00 on its day, device time.

**Values:**

| Reminder | Falls on |
|---|---|
| a recurring expense's | its next due date minus the lead |
| statement import, when turned on | its day of the month, 1 to 28, today or later, else next month |
| New Month | this month's 1st while its 09:00 is ahead, else next month's 1st |
| payday, while income is hourly or salaried with automatic deposit off, and the payday reminder is on | the next payday (R76) whose 09:00 is ahead |

**Evidence:** Sci-Fin's spec, sections 3.9, 3.10, 3.11 and 4.3, and `engine/Reminders.kt` at `2892110`.

**Acceptance:** A rent on the 31st with a 3-day lead reminds on 25 February 2026 and then on 28 March 2026. With the import reminder on day 1, read on 14 September 2026, it falls on 1 October. The New Month reminder read at 08:30 on 1 October 2026 is 1 October at 09:00; read at 09:00 that day, 1 November at 09:00; read at 23:00 on 31 December 2026, 1 January 2027 at 09:00. An hourly income paid every 14 days from 13 November 2026, read at 12:00 on 2 November, reminds on 13 November; read at 09:00 on 13 November, on 27 November. A salaried income with automatic deposit on, an income with the payday reminder off, and income switched off have no payday reminder.

**Since:** 2026-11-24 · builder · own words

## 9. The month cycle

### D-R70 · The month cycle

**Rule:** Each time a Profile is opened, each month after the last month processed, up to the current month, is processed in order with its real dates: the due dates move on to that month's 1st (R61), then each expense not yet swept for that month sweeps it (R63), a bimonthly or yearly one at the share in force on that 1st (R62), and is marked swept, so no month sweeps twice; after the last month the due dates move on to the day of the open. The Profile keeps the last month processed, starting at the month it was created and set to the current month by each open, and an expense swept for an older month starts the run there instead. A new month writes nothing for categories: spent is worked out per month (R25), so it reads 0 with no row and no change to any category.

**Why:** A month skipped in silence would leave Savings showing money already spoken for.

**Evidence:** Sci-Fin's spec, section 3.9, and `engine/NewMonth.kt` at `2892110`. Sci-Fin moved the due dates on to the day of the open before processing the past months, so a cycle that ended while the app was closed swept its last months at the next cycle's share; this corrects it to what Sci-Fin's spec, section 3.11, intends, a late catch-up giving the share a live rollover would: builder, 2026-11-25, after review of PR 3.

**Acceptance:** Opened on 1 September 2026 with Savings at 5,000.00, a 950.00 rent due the 1st and a 45.00 phone bill due the 10th, both swept for August, leave 4,005.00 and two rows, dated 1 and 10 September; opened again that day, nothing changes. Opened on 14 November 2026 instead, the rent writes rows dated 1 September, 1 October and 1 November and is next due 1 December. A category with 120.00 spent in August reads 0.00 for September after the open, and the categories are unchanged. A Profile with no recurring expense, last processed in June 2026 and opened on 14 September 2026, processes 3 months and is marked processed for September. A Profile created on 14 September 2026 is marked processed for September and processes no month that day. A 1,000.00 yearly expense due 1 April 2026 with a share of 333.34, swept for February and opened on 10 May 2026, sweeps 333.34 for March and for April, then 83.34 for May, and is next due 1 April 2027.

**Not:** Sci-Fin's reset of each category's stored spent, which also marked the month processed: Tesoriere stores no spent figure (R25), so the mark is the Profile's. Finding the months from the recurring expenses alone: a Profile with none would get no summary after an absence (R74). Moving the due dates on to the day of the open first: March and April would sweep 83.34, and the 1,000.00 due in April would find 500.00 less set aside.

**Since:** 2026-11-25 · builder · own words

### D-R71 · Automatic deposits on the same open

**Rule:** On the same open, salaried income with automatic deposit on deposits each payday passed since the last automatic deposit, or since the chosen payday when there has been none, as its own income row dated to that payday; switched back on after an earlier period on, it counts from the day it is switched on, so no payday passed while it was off is deposited. Hourly income (R79), and income switched off, deposits nothing by itself. The deposits and the month cycle (R70) do not depend on each other, so their order does not change the result.

**Why:** Paydays replayed across a period with automatic deposit off would put money that never arrived into Savings.

**Evidence:** Sci-Fin's spec, sections 3.3 and 3.9, and `engine/Income.kt` and `engine/NewMonth.kt` at `2892110`.

**Acceptance:** Opened on 2 October 2026 with Savings at 5,000.00, a 950.00 rent swept for September, and a 1,450.00 biweekly income first paid 11 September 2026 with automatic deposit on: one month is processed, two deposits are dated 11 and 25 September, and Savings is 6,950.00. A 1,450.00 monthly income paid the 5th, last deposited 5 June 2026, with automatic deposit switched back on on 14 September 2026, deposits nothing that day and one row dated 5 October when opened on 5 October; switched on for the first time on 14 September, a 100.00 monthly income paid from 5 August deposits rows dated 5 August and 5 September.

**Since:** 2026-11-24 · builder · own words

### D-R72 · End of a term

**Rule:** An expense whose end date is on or before the 1st of a month being processed sweeps that month's due dates up to its end date, then finishes on that 1st. A Loan linked to it with a balance left is paid off by a closing `acct:` row dated that 1st, unless its rate is interest-only or variable, which keep their balance. Each expense that finishes gives a notice holding its id, its name as typed and a code from the table; how it reads is the design pass's (R16).

**Values:**

| The finished expense | Code |
|---|---|
| linked to no Loan | `ended` |
| linked to a Loan whose update from payments (R73) took the balance to 0 | `loanPaidOff` |
| linked to an interest-only Loan otherwise | `interestOnlyEnded`: the principal is still owed |
| linked to a variable-rate Loan otherwise | `variableTermEnded`: the balance is for the user to check with the lender |
| linked to any other Loan | `loanPaidOff` |

**Evidence:** Sci-Fin's spec, sections 3.11 and 9.1, and `engine/NewMonth.kt` at `2892110`.

**Acceptance:** A 260.00 payment due the 15th, ending 15 August 2026 and linked to a fixed-rate Loan of 120.00, finishes on 1 September 2026 when opened that day: the Loan is 0.00 after one -120.00 `acct:` row, and the notice holds its id, its name and the code `loanPaidOff`. On an interest-only Loan of 5,000.00 the same payment leaves 5,000.00 with no closing row and the code `interestOnlyEnded`. A 40.00 gym fee due the 15th, swept for August and ending 15 October 2026, opened on 20 November, writes rows dated 15 September and 15 October, leaves Savings 80.00 lower and ends with `ended`. A payment ending 15 October 2026 is not finished when opened on 20 October; opened on 2 November, it finishes on 1 November.

**Not:** Stating every Loan paid off at the end of its term: interest-only and variable-rate Loans can still be owed.

**Since:** 2026-11-25 · builder · own words

### D-R73 · A Loan that follows its payment

**Rule:** A Loan set to update from its payment falls, in each month its linked expense sweeps during the cycle (R70), by the payment less one month's interest, the balance times the yearly rate divided by 12 and rounded to the cent, and never below 0. The fall is an `acct:` row dated to the payment's first due date in the month, or the 1st for a share, with the expense's name as payee; it does not move Savings (R51). When the balance reaches 0 the expense finishes on that 1st, whatever its end date, and gives the notice of R72 with the code `loanPaidOff`.

**Evidence:** Sci-Fin's spec, sections 3.9 and 7.2, and `engine/NewMonth.kt` at `2892110`.

**Acceptance:** A Loan of 6,000.00 at 6 % with a 260.00 payment due the 15th, opened on 1 September 2026, falls to 5,770.00 after 30.00 of interest, with a -230.00 row dated 15 September; Savings falls by the 260.00 sweep only. A Loan of 200.00 at 0 % falls to 0.00 and its payment finishes though it ends in 2030, with a notice holding its id, its name and `loanPaidOff`. A variable-rate Loan of 100.00 cleared by its final 260.00 payment ends with `loanPaidOff`.

**Since:** 2026-11-25 · builder · own words

### D-R74 · Catch-up summary

**Rule:** After an open that processed two or more months, or finished an expense, the engine keeps a summary until the user dismisses it: the months processed, the total swept, the count and total of automatic deposits (R71), and each finished expense's notice (R72), its id, code and name as typed, so an expense deleted since can still be named. A later open of that kind adds to a summary not yet dismissed, an open that crosses one month and finishes nothing leaves it as it is, and dismissing clears every figure; the kept notices are codes and names, never sentences.

**Why:** The summary is where a long absence's movements are told; lost on a restart, the user would not learn what left Savings while they were away.

**Evidence:** Sci-Fin's spec, section 3.9, and `engine/NewMonth.kt` at `2892110`.

**Acceptance:** A 950.00 rent swept for August, opened on 14 November 2026, keeps a summary of 3 months and 2,850.00 swept; opened again that day it still reads 3 months; dismissed, there is none. Opened on 14 October 2026 it reads 2 months; opened again on 3 November, still 2; then on 3 January 2027, 4. Opened on 1 September 2026 alone, it keeps none. A finished Car loan payment deleted before the summary is read is still named Car loan payment on it; a notice kept with no name, as summaries kept before names were, or with an empty name, reads with its id and code and no name.

**Not:** A summary after every ordinary month crossing: each user would meet one on every 1st. A notice holding the id alone: an expense deleted since could not be named.

**Since:** 2026-11-25 · builder · own words

## 10. Credit and Loan accounts

### D-R85 · Accounts

**Rule:** An Account is money owed, of one of the two types below, tracked by its balance. Accounts are off on a fresh Profile (R18) and switched on in the settings; switching them off hides them and changes nothing stored. A balance moves only by the Account's own `acct:` rows (R51) and the payments charged to it (R54); an Account moves Savings only through a payment off it from Savings (R89) and its linked payment's sweeps (R87).

**Values:**

| Type | Holds besides its name and balance |
|---|---|
| credit | a yearly rate, at most one linked recurring expense: a fixed payment (R93), whether its charges are not logged, with the payoff category its payoffs then count in (R109), and a fees category for its interest and fees (R110) |
| loan | the principal, a yearly rate, a term in months, a rate type (fixed, variable or interest-only), the day it started, whether it follows its payment (R73), at most one linked recurring expense: its payment, and a category of its own for its payments by hand (R89) |

**Evidence:** Sci-Fin's spec, sections 7.1 and 7.2, and `engine/Accounts.kt` at `2892110`. A Loan's own category: owner, 2026-11-25; schema 1 is still edited in place until the first install, so the field is part of it, and a Sci-Fin backup has none and reads it as no category of its own.

**Acceptance:** A fresh Profile has Accounts off. A card at 100.00 with a 40.00 payment charged to it reads 140.00, and Savings is unchanged.

**Not:** A checking or cash Account: a second pool of money held would count the same euro as Savings does.

**Since:** 2026-11-25 · builder · own words

### D-R86 · Creating and editing an Account

**Rule:** An Account needs a name, and a new one starts as a card. Created with a balance other than zero, it logs an opening `acct:` row of that balance with the note `Opening balance`; a later change of the balance in the editor logs the change with the note `Correction` (R52), and on a card a rise is, as the user chooses for the edit, interest or fees, filed under a category, or a correction, a card whose charges are not logged asking no choice (R110). A Loan's start day is the day it is saved unless the user gives one, it follows its payment (R73) only with its yearly rate filled in, saved without one the setting being saved off, and it is saved with or without a category of its own (R89), but not with one that does not exist.

**Evidence:** Sci-Fin's spec, section 7.2, and `engine/AccountEditing.kt` at `2892110`.

**Acceptance:** A card created at 640.00 on 14 September 2026 logs one `acct:` row of +640.00 noted `Opening balance` and leaves Savings at 1,000.00; edited to 600.00, it logs a -40.00 row noted `Correction`. A Loan saved to follow its payment with no rate is saved not following, with 14 September 2026 as its start day. A Loan with no category of its own is saved; one naming a category that does not exist is not. An Account with a blank name is not saved.

**Since:** 2026-11-25 · builder · own words

### D-R87 · An Account and its linked payment

**Rule:** An Account and a recurring expense can be linked, both ways and one link at a time: linking an Account to an expense releases the expense it linked before and any other Account linked to that expense; a Loan is finished when its linked payment is (R72). Deleting either side deletes the other after a prompt that cannot be skipped, with no way to keep one half, and deleting from the Account's side offers the expense's return on deletion (R66) in the same step; an Account leaves with a closing `acct:` row of minus its balance, noted `Account closed`. Each sweep and return a linked expense writes counts as below, decided by the link when the row is written and read as written, so a purchase charged to a Loan later, a card's switch turned or a link released leaves past months as they were.

**Why:** One half left alone would keep sweeping a payment for a debt that is gone, or track a debt with nothing paying it. A card's payment is not spending: what it pays was counted when charged (R22); nor is a Loan's once the purchase it pays for is charged to it, while a Loan with nothing charged to it has its payment as the only trace of what it bought.

**Values:**

| When the row is written, the expense is linked to | Its sweeps and returns |
|---|---|
| nothing | count in the expense's category (R67) |
| a card whose charges are logged | an Account payoff: a transfer that moves Savings and counts in no spent (R93) |
| a card whose charges are not logged | count in the card's payoff category (R109) |
| a Loan with a payment charged to it | an Account payoff: a transfer that moves Savings and counts in no spent |
| a Loan with nothing charged to it | count in the expense's category (R67) |

**Evidence:** Sci-Fin's spec, sections 7.2, 9.1 and 9.2, and `engine/Accounts.kt` and `engine/AccountEditing.kt` at `2892110`; how the rows count: owner, 2026-11-25.

**Acceptance:** A Loan linked to a 200.00 payment and linked again to a 250.00 one leaves the 200.00 payment linked to nothing, and deleting it keeps the Loan. A Loan of 4,000.00 whose 260.00 payment, due the 20th, swept on creation on 10 September 2026 from Savings of 5,000.00, deleted with the return accepted, leaves no Account, no expense and Savings at 5,000.00. A Loan of 9,800.00 deleted removes its payment too and logs a -9,800.00 row noted `Account closed`. With 40.00 charged to a card under Food in September 2026 and the card's 40.00 payment, filed under Food, sweeping that month, Food's spent is 40.00; a Loan's 260.00 payment under Car adds 260.00 to Car in September 2026, and after a 9,800.00 car is charged to that Loan in October, Car's October spent is 9,800.00 with the payment's October sweep a transfer, and its September stays 260.00.

**Not:** Counting a card's payment, or a Loan's whose purchase is charged to it, as spent: a charge and its payoff would count the same money twice. Reading the link, or whether a payment is charged to a Loan, when the figures are read: a purchase logged late would take the earlier months' sweeps out of their spent.

**Since:** 2026-11-25 · owner · agreed rewording

### D-R88 · Deleting an Account with payments charged to it

**Rule:** Payments charged to an Account stay when it is deleted, with the Account as their recorded source: their parts keep counting in their categories' spent (R25) and Savings does not move. The closing row is minus the balance, which includes those payments, so the Account's rows and the payments charged to it add up to zero, and a later change to those payments logs a row that keeps them so (R54).

**Why:** The money was spent on what the parts say whether or not the card is still tracked; removing the payments would take that spending out of past months.

**Acceptance:** A card opened at 100.00 with a 40.00 payment charged to it under Groceries in September 2026, deleted, logs a -140.00 closing row; the payment stays, Groceries' spent for September is 40.00, and Savings is unchanged; the payment removed afterwards logs a +40.00 `acct:` row, and the card's rows add up to zero.

**Not:** Sci-Fin's model: a charge there was an `acct:` row with no category, so deleting an Account had no payment to keep.

**Since:** 2026-11-25 · builder · own words

### D-R89 · Quick-add on an Account

**Rule:** A quick-add on an Account is a payment off it or an increase, on a Loan, or on a card as its interest or fees (R110), as below; a charge to a card is a payment from the card (R22), never a quick-add. Each logs one `acct:` row, noted with the user's note, else `Paid off` or `Increased`; a payment off a Loan from Savings or a Goal counts as spending while no payment is charged to the Loan, filed under the category picked for it, else the Loan's own, else its linked payment's, and refused with none of them or with a picked one that does not exist, and is a transfer once one is, each decided when its row is written, as for the Loan's sweeps (R87). A payment off an Account takes its money from Savings, from a Goal, at most what the Goal holds, or from nowhere, logging in the same step a `savings` or `spend:<goalId>` row of minus the amount noted with the user's note, else `Payment to` and the Account's name, and filed under the card's payoff category while its charges are not logged (R109); Savings is the default unless the Account is linked to a recurring expense, whose sweep already moved Savings, where nowhere is, and the user can change it for each payment.

**Values:**

| Direction | From | Accounts | Balance | Savings | Goal |
|---|---|---|---|---|---|
| payment | Savings | credit and loan | lowered by the amount | lowered by the amount | unchanged |
| payment | a Goal | credit and loan | lowered by the amount | unchanged | lowered by the amount, which it must hold |
| payment | nowhere | credit and loan | lowered by the amount | unchanged | unchanged |
| increase | nowhere | loan, and credit as interest or fees filed under a category (R110) | raised by the amount | unchanged | unchanged |

**Evidence:** Sci-Fin's spec, sections 3.6 and 7.2, and `engine/Accounts.kt` at `2892110`; the payment from a Goal: owner, 2026-11-25.

**Acceptance:** A card at 100.00 paid 60.00 from Savings of 1,000.00 reads 40.00 with Savings at 940.00 and a `savings` row of -60.00 noted `Payment to Card`; from nowhere, Savings stays 1,000.00. Paid 60.00 from a Goal holding 80.00 it reads 40.00, the Goal 20.00 and Savings 1,000.00, with a `spend:` row of -60.00 noted `Payment to Card`; 90.00 from that Goal is refused. A Loan of 4,000.00 increased by 500.00 reads 4,500.00 with Savings unchanged. An increase on a card with no fees category and none picked is refused. A Loan linked to its 260.00 payment under Car, with nothing charged to it, paid 200.00 by hand from Savings adds 200.00 to Car; after a 9,800.00 car is charged to it, 100.00 paid from a Goal adds nothing; with the car removed again, Car keeps the 200.00 and the 100.00 still counts nowhere. A Loan linked to nothing, paid from Savings with no category picked and none of its own, is refused; with Car as its own category, 200.00 counts in Car, and with Groceries picked for the payment, in Groceries; with a picked category that does not exist it is refused, not counted in Car. A Loan with Car as its own category and a linked payment under Bills counts a payment by hand in Car, and the payment's sweeps in Bills. Savings is the default for an Account linked to nothing and nowhere for a linked one.

**Not:** Requiring a Loan's own category to save the Loan: a Loan whose purchase is charged to it, or that is paid only from nowhere, never needs one, so the category is asked for only by the payment that would count under it, which is refused rather than counted under no category. A purchase as a quick-add: it would raise the card with no parts, which R22 rules out; interest and fees, filed under one category, are the one rise a card takes (R110). A payoff that takes a Goal below zero: unlike a payment (R54), nothing here is filed in a category, so the rest would leave Savings with nowhere to show it.

**Since:** 2026-11-25 · owner · agreed rewording

### D-R111 · Undoing an Account quick-add

**Rule:** An Account quick-add (R89), a payment off it from Savings, a Goal or nowhere, an increase, or a card's interest or fees (R110), can be undone as a whole: every ledger row it wrote shares one group and is deleted with the others, and Savings, the Goal, the Account's balance and any category's spent go back to what they were without it. A row with no group, as one written before groups were kept or read from a Sci-Fin backup, is not undone this way, nor a group holding an income row or a sweep (`rec:`) row. When the Account or the Goal it moved has been deleted since, the undo is refused with a code and nothing changes.

**Why:** A mistyped payoff moves an Account and Savings or a Goal under two keys, so no pair (R53) can cancel it, and a correction row would leave the mistake in the month's figures.

**Values:**

| Row of the quick-add | Undoing it |
|---|---|
| its `acct:` row | moves the Account's balance back by its amount |
| its `savings` row | moves Savings back by its amount; Savings unset before the quick-add stays set, at 0.00, since a movement ends the unset state and undoing it does not restore it (R49) |
| its `spend:` row | gives the Goal back its amount |
| any of them filed under a category (R89, R109, R110) | takes it out of that category's spent for its month, the row being gone (R25) |
| a quick-add a statement row recorded (R107) | leaves that row's keys kept (R26): the row arrives on a later import unticked as already recorded, and the user can tick it to record it again |

**Evidence:** Owner, 2026-11-25; the refusal for a deleted Account or Goal, and the kept keys: builder, 2026-11-25. The group is stored on each row, part of schema 1, which is still edited in place until the first install; a Sci-Fin backup's rows have none.

**Acceptance:** A card at 100.00 paid 60.00 from Savings of 1,000.00 and undone reads 100.00 with Savings at 1,000.00 and neither row left; paid 60.00 from a Goal holding 80.00 and undone, the Goal holds 80.00; a card whose charges are not logged, paid 60.00 under Shopping and undone, leaves Shopping's spent as it was; a 12.50 fee filed under Fees, undone, leaves the card at 300.00 and Fees as it was. A quick-add on a card deleted since, or from a Goal deleted since, is refused with a code and changes nothing; a row with no group and a sweep are not undone. A statement payoff of -60.00, undone, arrives on the next import of the same statement `DUPLICATE` and unticked, and ticked and filed again it is recorded. On a Profile whose Savings is unset, a card paid 60.00 from Savings and undone leaves Savings at 0.00, not unset.

**Not:** Following R54's deleted-source pattern: the closing row of a deleted Account (R87) or the return of a deleted Goal (R58) already settled its figure with the quick-add in it, so an undo would need a compensating row on a target that is gone, and would not remove what the quick-add did. Removing a statement row's keys on undo: the quick-add's rows do not hold them, and the OFX FITID cannot be rebuilt from a row's day, amount and payee, so it would need the keys stored on every quick-add for this one case.

**Since:** 2026-11-25 · owner · agreed rewording

### D-R90 · Loan calculator

**Rule:** The loan calculator takes a principal, a yearly rate, a term in months and a rate type, and gives the monthly payment, the total repaid, the total interest and the principal left at the end of the term, as below, with i the yearly rate divided by 12 and n the term. The monthly payment is rounded to the cent once; the other figures follow from it in whole cents (R20). A term below 1 counts as 1, and a negative principal or rate as 0.

**Values:**

| Rate type | Monthly payment | Total repaid | Total interest | Principal left |
|---|---|---|---|---|
| fixed or variable | principal × i ÷ (1 − (1 + i) to the power −n); at a rate of 0, principal ÷ n rounded up to the cent | monthly × n | total repaid − principal | 0 |
| interest-only | principal × i | monthly × n | total repaid | the principal |

**Evidence:** Sci-Fin's spec, section 9.1, and `engine/Accounts.kt` at `2892110`.

**Acceptance:** 12,000.00 at 6.5 % over 48 months, fixed: 284.58 a month, 13,659.84 repaid, 1,659.84 of interest, nothing left. Interest-only: 65.00 a month, 3,120.00 repaid, 12,000.00 left. At 0 %: 250.00 a month.

**Since:** 2026-11-25 · builder · own words

### D-R91 · Taking the loan or saving for it

**Rule:** From the calculator the user either takes the loan or saves for it, creating what the table gives in one step; Savings does not move. Taking it, the user names the Loan and its payment, picks the payment's category (R67) and chooses whether the Loan follows its payment (R73). The payment is linked both ways (R87) and, as its first due date is next month, nothing sweeps this month (R64).

**Values:**

| Choice | Creates |
|---|---|
| take the loan | a Loan named `Loan` by default, holding the calculator's inputs, today as its start day and its payment's category as its own (R89), at a balance of the principal with its opening row (R86) |
| take the loan | its payment, named `Loan payment` by default: a monthly fixed-date recurring expense of the calculator's monthly payment, first due one month after today on today's day of the month, ending on the term's last payment |
| save for it | only a Goal planned by date (R55) for the principal, due a term from today, named `Savings goal` by default |

**Evidence:** Sci-Fin's spec, section 9.1, and `engine/Accounts.kt` at `2892110`.

**Acceptance:** 9,800.00 at 5.9 % over 48 months, taken on 14 September 2026 from Savings of 1,000.00, creates a Loan of 9,800.00 with one +9,800.00 `Opening balance` row and a payment of 229.70 first due 14 October 2026, ending 14 September 2030, marked swept for September 2026, the Loan taking the payment's category as its own; Savings stays 1,000.00. With no category, or one that does not exist, nothing is created. Saving for 12,000.00 over 48 months on 14 September 2026 creates a goal of 12,000.00 by 14 September 2030 named `Savings goal`.

**Not:** Taking the loan without its payment: a decision this size is never left without a trace in the plan.

**Since:** 2026-11-25 · builder · own words

### D-R92 · Card payoff outlook

**Rule:** For a card, the engine works out the months to clear the balance and the interest paid on the way, at the Account's yearly rate divided by 12, for a fixed monthly payment and for the minimum payment, 2 % of the balance each month rounded to the cent and at least 25.00; paying in full clears it at once with no interest. Each month adds that month's interest on the balance, rounded to the cent, then takes the payment, the last one only what is left. The cases below give their own figures, none where the balance is never cleared; how the months read is the design pass's (R16).

**Values:**

| Case | Months | Interest |
|---|---|---|
| a balance of 0 or less | 0 | 0 |
| a fixed payment of 0 or less | none | none |
| a rate of 0 | the balance divided by the payment, rounded up | 0 |
| a fixed payment at or below the first month's interest | none | none |
| a minimum payment at or below a month's interest | none | none |
| still owed after 1,200 months | none | none |
| otherwise | counted month by month | the sum of the months' interest |

**Evidence:** Sci-Fin's spec, section 9.2, and `engine/Accounts.kt` at `2892110`.

**Acceptance:** 640.00 at 21.9 % paid 50.00 a month clears in 15 months with 95.63 of interest; at 11.00 a month it is never cleared; at 0 % it clears in 13 months; a balance of 0.00 clears in 0 months. The minimum on 640.00 at 21.9 % starts at 25.00 and clears in 35 months with 230.40 of interest; on 3,000.00 at 0 % it starts at 60.00; 1,000.00 at 5 % a month is never cleared.

**Not:** A running balance held with fractions of a cent (R20).

**Since:** 2026-11-25 · builder · own words

### D-R93 · Committing a card's fixed payment

**Rule:** A fixed monthly payment chosen for a card can be committed as a monthly fixed-date recurring expense of that amount, named after the card followed by `payment` unless the user names it, in a category the user picks (R67), linked both ways to the card (R87), first due one month after today on today's day of the month, with no end date, so nothing sweeps this month (R64); its sweeps pay the card off and count in no category's spent (R87), unless the card's charges are not logged (R109). A card never follows its payment: only a Loan does (R73). The minimum payment is never committed: it moves with the balance, which a recurring expense cannot (R60).

**Evidence:** Sci-Fin's spec, section 9.2, and `engine/Accounts.kt` at `2892110`; its sweeps as a card payoff: owner, 2026-11-25.

**Acceptance:** Committing 50.00 on 14 September 2026 for a card named Credit Card creates `Credit Card payment` of 50.00, first due 14 October 2026, with no end date, linked both ways; the card does not follow it, and its 50.00 sweeps add nothing to the chosen category's spent. A card that linked an earlier expense releases it.

**Since:** 2026-11-25 · owner · agreed rewording

### D-R94 · Changing an Account's type

**Rule:** Changing a Loan to a card clears what belongs to a Loan: following its payment, the principal, the yearly rate, the term, the rate type, the start day and its own category; its linked payment is deleted, after a prompt that cannot be skipped and offers its return on deletion (R66), and the card stays, linked to nothing, unless the card as edited cannot be saved (R86, R109, R110), when nothing changes: the payment, its return and the Loan stay as they were. Changing a card to a Loan clears its yearly rate, so it cannot follow a payment until a rate is filled in (R86), its switch for charges not logged with its payoff category (R109), and its fees category (R110).

**Why:** A card cannot keep a Loan's tracked balance honest, which is what following a payment is for.

**Evidence:** Sci-Fin's spec, section 7.2, and `engine/Accounts.kt` at `2892110`.

**Acceptance:** A Loan at 5 % following its payment, changed to a card, has no rate, principal, term, rate type, start day or category of its own and does not follow. A card at 20 % changed to a Loan has no rate and cannot follow, and one whose charges were not logged under Shopping has the switch off, no payoff category and no fees category. A Loan of 4,000.00 whose 260.00 payment, due the 20th, swept on creation on 10 September 2026 from Savings of 5,000.00, changed to a card with the return accepted, stays as a card linked to nothing, its payment is gone, and Savings is 5,000.00; changed to a card whose balance is raised to 4,100.00 with no choice of interest or fees or a correction, nothing changes, and with the correction chosen the change goes through at 4,100.00.

**Since:** 2026-11-25 · builder · own words

### D-R109 · A card whose charges are not logged

**Rule:** A card has a switch, off by default, for charges the user does not log in Tesoriere; switched on, it needs a payoff category, one that exists. While it is on, the card's payoffs count as spending in that category, each dated its row: a payment off the card from Savings or from a Goal (R89), a statement payoff (R107) and its fixed payment's sweeps (R93); while it is off, the charges count through their parts (R22) and a payoff is a transfer. Each row records when it is written whether it counts and in which category, and is read as written, so turning the switch, changing the category or deleting the card later leaves every past month as it was.

**Why:** A card whose spending the user does not log one by one, such as one paid off in full each month, would otherwise leave that spending in no month; with both its charges and its payoff counted, the same money would count twice.

**Values:**

| Row | Switch on when it is written | Switch off when it is written |
|---|---|---|
| the `savings` or `spend:` row of a payment off the card (R89) | filed under the payoff category, counting minus its amount | filed under none: a transfer |
| a sweep of the card's fixed payment (R93), and a return of such sweeps (R66) | filed under the payoff category, counting its amount | a card payoff, in no spent (R87) |
| a payment off the card from nowhere | counts nowhere: the fixed payment's sweep counted it | counts nowhere |
| a payment charged to the card (R22) | counts through its parts, after a warning the user can dismiss that the card's charges are not logged | counts through its parts |

**Evidence:** Owner, 2026-11-25, after the real-world check of R21, R25, R26, R54 and R67; the reading as written: builder, 2026-11-25.

**Acceptance:** With the switch on and Shopping as the payoff category, 60.00 paid off the card from Savings in September 2026 adds 60.00 to Shopping's spent for September and to its Spending, and 60.00 paid from a Goal holding 80.00 adds 60.00 more; its 50.00 fixed payment's sweep adds 50.00 and its return takes it off again, and a statement payoff of 40.00 adds 40.00. With the switch turned off in October after the 60.00 from Savings, Shopping's September stays 60.00 and a 30.00 payoff in October adds nothing; a card with the switch off whose September payoff counted nothing, turned on in October, leaves September as it was. Deleting the card leaves September as it was. The switch on with no payoff category, or one that does not exist, is not saved. A payment charged to the card carries the warning, and so does a statement row of money in filed as a refund to it (R107).

**Not:** Reading the switch as it stands when the figures are read: turned on, it would count past payoffs as spending in months whose charges were counted already, and turned off, or the card deleted, it would take payoffs out of months whose charges were never logged.

**Since:** 2026-11-25 · owner · agreed rewording

### D-R110 · Card interest and fees

**Rule:** A rise in a card's balance that is not a payment charged to it is interest or fees, counted as spending in its row's month as below; a direct edit upward (R86) asks the user whether it is interest or fees or a correction, which counts nowhere, unless the card's charges are not logged (R109). Either raises the balance and moves no Savings (R51), and the payoff that later clears it stays a transfer, so nothing counts twice.

**Why:** Interest and fees are money spent that no purchase shows; left out, what a card costs would be in no month.

**Values:**

| Change of a card's balance | Filed under | Counts as spending |
|---|---|---|
| a quick-add increase | the category picked for it, else the card's fees category | its amount, in its row's month |
| a direct edit upward chosen as interest or fees | the category picked for it, else the card's fees category | the rise, in its row's month |
| a direct edit upward chosen as a correction | none | nothing |
| a quick-add increase or a direct edit upward, while the card's charges are not logged (R109) | none, with no choice asked | nothing: the payoff counts it |
| a direct edit upward with no choice made, or interest or fees with no category that exists, on a card whose charges are logged | not saved | nothing |
| any rise with a picked category that does not exist | not saved, never passed over for the fees category | nothing |
| a direct edit downward, an opening balance (R86), a payment charged to it (R22) | none | nothing as a rise; a payment counts through its parts |

**Evidence:** Owner, 2026-11-25: the part of a card payment above the charges it clears counts as spending; read through the card's balance: builder, 2026-11-25.

**Acceptance:** A card at 300.00 with Fees as its fees category, increased by 12.50 on 30 September 2026, reads 312.50, adds 12.50 to Fees' September spent and to Spending, and leaves Savings as it was; with Bank charges picked for it, it counts there. Edited from 312.50 to 320.00 as interest or fees it adds 7.50 to Fees; as a correction it adds nothing, also on a card with no fees category; with no choice made it is not saved, except on a card whose charges are not logged, which saves it under no category with no choice; edited down to 310.00 it adds nothing; a card created at 640.00 adds nothing. A card with no fees category refuses an increase and an edit as interest or fees unless a category is picked; one whose charges are not logged takes them under none. A card with Fees as its fees category refuses an increase or an edit as interest or fees with a picked category that does not exist. Paying 320.00 off the card from Savings afterwards adds nothing to Fees or to Spending.

**Not:** Counting the part of a payoff above the charges it clears, worked out when the payoff is logged: which charges a payoff clears is known per card, not per payment, and a later charge or refund would change what an old payoff counted. Asking the choice on a card whose charges are not logged: either answer files the rise under no category, so the question would change nothing.

**Since:** 2026-11-25 · owner · agreed rewording

## 11. Reports

### D-R95 · Savings movements in a report

**Rule:** Every report reads the movements of Savings the same way: each ledger row by how it moves Savings (R51), and each payment with a Savings source as money out of Savings by its total, on its date, a refund as money in (R54). A payment from a Goal moves Savings only by the share it took from Savings when the Goal was short of it (R54), money out on its date; one charged to an Account moves no Savings and is no movement of it. Rows and payments of the same date are taken in the order they were logged.

**Evidence:** Sci-Fin's spec, section 5.1, and `engine/Reports.kt` at `2892110`, read with the payment model of R21 and R54.

**Acceptance:** In a month with a 100.00 sweep, a 42.30 payment from Savings, a 30.00 payment from a Goal and a 40.00 payment charged to a card, 142.30 went out of Savings; a 60.00 payment from a Goal holding 50.00 takes 10.00 more out. A 30.00 refund to Savings is 30.00 in: read on 14 September 2026 after 100.00 in on 1 June and a 100.00 payment from Savings on 10 August, a 30.00 refund to Savings on 1 September gives an Age of Money of 13 days (R96), and a 460.00 refund on 1 August leaves Coverage's outflow as it was (R98).

**Not:** Reading the ledger alone: a payment writes no ledger row (R54), so every expense would be missing.

**Since:** 2026-11-25 · builder · own words

### D-R96 · Age of Money

**Rule:** Age of Money is the number of days since the oldest money still in Savings came in, found by replaying the movements of Savings (R95) up to the day asked, oldest first: each movement in opens a parcel dated to its day, and each movement out empties the oldest parcels first. Money out beyond every parcel is a shortfall that the next movements in fill before they open a parcel, so the parcels always add up to the replayed Savings. While no parcel holds money, or a shortfall is left, the figure is 0.

**Why:** The figure says how long money sits before it is spent, and works the same with Income on or off.

**Evidence:** Sci-Fin's spec, section 5.1, and `engine/Reports.kt` at `2892110`.

**Acceptance:** Read on 14 September 2026: 100.00 in on 1 June, 100.00 in on 1 August and 50.00 out on 10 August reads 105 days; with 60.00 more out on 1 September it reads 44. 100.00 in on 1 June and 350.00 out on 10 June reads 0; with 1,000.00 in on 1 July and 400.00 out on 1 August it reads 75, the July parcel holding 350.00; with 351.00 more out on 1 September it reads 0. A 100.00 payment from Savings on 10 August in place of the 50.00 empties the June parcel and reads 44; the same payment from a Goal empties nothing and reads 105. An `acct:` row opens no parcel.

**Not:** Dropping the shortfall: the parcels would hold money Savings does not have.

**Since:** 2026-11-25 · builder · own words

### D-R97 · Age of Money over the months

**Rule:** The trend of Age of Money has one point per month for the trend length in the settings, 6 on a fresh Profile, each read on that month's last day, except the current month's, read on the day asked. A month that ended before the first movement into Savings has no point; a month read as 0 after it keeps its point.

**Evidence:** Sci-Fin's spec, section 5.1, and `engine/Reports.kt` at `2892110`.

**Acceptance:** With 100.00 in on 20 August 2026, a two-month trend read on 14 September is 11 days for August and 25 for September. With 300.00 in on 10 July, 400.00 out on 5 August and 200.00 in on 1 September, a six-month trend read on 14 September has points for July, August and September only, and August's is 0.

**Not:** Points of 0 for the months before any money came in: they would read as money spent at once.

**Since:** 2026-11-25 · builder · own words

### D-R98 · Coverage

**Rule:** Coverage is how many days Savings would last at the recent daily outflow: Savings divided by the money out of Savings (R95) in the last three complete calendar months, over the days in them, rounded to the nearest day with a half to the even day. It is worked out in whole cents, and the cases below give no figure or 0.

**Values:**

| Case | Coverage |
|---|---|
| Savings unset (R49) | none |
| the earliest ledger row or payment is dated after the first of the three months | none |
| nothing went out of Savings in the three months | none |
| Savings 0 or less | 0 |
| otherwise | as the rule says |

**Evidence:** Sci-Fin's spec, section 5.1, and `engine/Reports.kt` at `2892110`.

**Acceptance:** Read on 14 September 2026, the months are 1 June to 31 August, 92 days. Savings of 920.00 with 920.00 out on 1 July and a first row on 1 May covers 92 days; at -5.00 it covers 0; with the first row on 1 July there is no figure. A 920.00 payment from Savings in place of the row gives 92; charged to a card, there is no figure. Savings of 25.00 against 920.00 out covers 2 days and 35.00 covers 4.

**Not:** A trailing 30 days: a window that straddles the first of the month, when sweeps fall, would halve the figure overnight.

**Since:** 2026-11-25 · builder · own words

### D-R99 · Spending

**Rule:** Spending breaks the current calendar month into the slices below: what was spent in it, from the parts of the payments, the `cat:` adjustments and the sweep rows dated in it, so a bimonthly or yearly expense counts its share each month (R62), not its whole amount in the month the bank pays it. Money moved between Savings, Goals and Accounts is a transfer and in no slice, so nothing counts twice. Slices that come to 0 are left out, the rest are ordered largest first, and their sum is the month's total; when a slice is below 0 or the total is 0 or less, the month is given as a plain list of figures rather than as shares.

**Why:** A split shopping trip is groceries and household goods at once, and one paid by card or from a Goal is still spending (R22). Money put into a Goal or paying off an Account is not yet spent: counted there, it would count again when a payment spends it.

**Values:**

| Slice | Holds |
|---|---|
| each category | its spent figure for the month (R25): its parts of payments from any source (Savings, a Goal or an Account), a refund's lowering it (R21), its `cat:` adjustments, the sweep rows filed under it (R67), other than an Account payoff's, the payoff rows of a card whose charges are not logged, filed under it as their payoff category (R109), a card's interest and fees filed under it (R110), and the `savings` or `spend:` rows of a Loan's payments by hand filed under it while nothing was charged to the Loan (R89) |
| a category in the Trash | its spent figure for the month, marked deleted (R124) |
| Uncategorised | parts with no category (R21), sweep rows with no category (R19), and the spent figure of a category deleted for good (R124) |
| no slice | `goal:` rows, `savings` rows of either sign (income, corrections, a payment off an Account, R89), `acct:` rows, `spend:` rows, and the sweep rows of an Account payoff, a card's payment or a Loan's with a payment charged to it (R87), each unless filed under a category (R109) |

**Evidence:** Sci-Fin's spec, section 5.2, and `engine/Reports.kt` at `2892110`, with the slices of payments and sweeps from R22, R25 and R67; the transfers left out: owner, 2026-11-25.

**Acceptance:** A 42.30 payment split 30.00 Groceries and 12.30 Household counts 30.00 in Groceries and 12.30 in Household. In a month with 100.00 and 50.00 into two Goals, a 30.00 adjustment on Food, 1,450.00 of income, a -20.00 `savings` row, 60.00 paid off a card from Savings and a 99.00 `acct:` row, the one slice is Food 30.00, totalling 30.00. A 60.00 sweep filed under Phone counts in Phone; a 40.00 payment by card and a 25.00 one from a Goal, both under Food, count 65.00 in Food. 40.00 charged to a card under Food and the card's 40.00 payment swept under Food give one slice, Food 40.00, totalling 40.00. Food at 30.00 and -50.00 in one month is a plain list. A 40.00 payment under Food and a 15.00 refund under Food in the month give one slice, Food 25.00. 60.00 paid off a card whose charges are not logged, from Savings, gives one slice of 60.00 in its payoff category, and a 12.50 fee on a card filed under Fees one slice of 12.50 in Fees. A 600.00 yearly expense under Insurance gives one slice, Insurance 50.00, in each month of its cycle, the month its bill is paid included.

**Not:** Sci-Fin's Goals slice, its slice per recurring expense, and its Spending without card charges: Goal money counts when a payment spends it, a sweep in its category, and a card-paid trip in its categories.

**Since:** 2026-11-25 · builder · own words

### D-R100 · Savings vs Owed

**Rule:** Savings vs Owed is Savings less the balances of all Accounts (R85); money held in Goals is not counted. Its trend has one point per month for the trend length in the settings, each read on the month's last day, replaying from zero both the movements of Savings (R95) and what is owed: the `acct:` rows and the payments charged to an Account. It starts off (R18), and switching it on shows the months already recorded.

**Evidence:** Sci-Fin's spec, section 5.3, and `engine/Reports.kt` at `2892110`.

**Acceptance:** Read on 14 September 2026, with 1,000.00 into Savings on 10 July, a card opened at 640.00 on 1 September and a 40.00 payment charged to it on 5 September, it reads 320.00; a three-month trend reads 1,000.00 for July and August and 320.00 for September.

**Not:** Counting Goal money against what is owed: money set aside for a purpose is not there to pay a debt.

**Since:** 2026-11-25 · builder · own words

### D-R101 · Income vs Expenses

**Rule:** For each month of the trend length, income is the sum of the income rows dated in it (R77), and expenses are what was spent in it, the month's Spending total (R99): the parts of every payment from any source, the `cat:` adjustments and the sweep rows. Money moved between Savings, Goals and Accounts is a transfer and no expense, so nothing counts twice. It is shown only while Income is on (R18).

**Values:**

| Dated in the month | Counts in expenses |
|---|---|
| a payment, from Savings, a Goal or an Account | its parts, which add up to its total (R23); a refund's, negative, lower the expenses and are no income (R21) |
| a sweep row or a `cat:` adjustment | its amount |
| a sweep row of an Account payoff: a card's payment (R93), or a Loan's with a payment charged to it (R87) | nothing: what it pays counts through the parts of the payments charged |
| a card's payoff row filed under its payoff category: a sweep, a `savings` or a `spend:` row (R109) | its amount: the card's charges are not logged, so its payoff stands for them |
| a `goal:` row | nothing: a transfer, counted when a payment spends the Goal's money |
| a `savings` row, a payment off an Account among them (R89) | nothing: a transfer, an income or a correction |
| an `acct:` row of a card's interest or fees, filed under a category (R110) | its amount |
| the `savings` or `spend:` row of a Loan's payment by hand, filed under a category while nothing was charged to the Loan (R89) | minus its amount: the payment is the only trace of what the Loan bought |
| any other `acct:` or `spend:` row | nothing |

**Evidence:** Sci-Fin's spec, section 5.4, and `engine/Reports.kt` at `2892110`, with every expense a payment (R21); the transfers left out: owner, 2026-11-25.

**Acceptance:** With Income on, 1,450.00 of income, 200.00 into a Goal and a 30.00 payment from Savings in September 2026 read 1,450.00 in and 30.00 out; an 80.00 payment from the Goal makes 110.00 out; a 40.00 payment charged to a card makes 150.00; paying 60.00 off the card from Savings leaves 150.00, and a 15.00 refund to Savings makes 135.00 out with 1,450.00 still in. A month with a 100.00 sweep and nothing else reads 100.00 out, and one with 40.00 charged to a card and the card's 40.00 payment swept, and nothing else, reads 40.00 out.

**Not:** Counting money put into a Goal, or paid off an Account, by hand or by a card's payment, as spent: it would count again when spent.

**Since:** 2026-11-25 · owner · agreed rewording

## 12. Overtime calculator

### D-R102 · Overtime calculator

**Rule:** The overtime calculator works out what extra hours in a month come to at a rate per hour: the rate times the hours, rounded to the cent with a half cent rounded up, and twelve times that for a year; hours below 0 count as 0. The rate can always be typed in, is pre-filled as below, and the calculator records nothing. It starts off (R18).

**Why:** It shows what unclaimed hours are worth in the user's own figures, with Income on or off.

**Values:**

| Case | Figure |
|---|---|
| Income off | no pre-filled rate |
| hourly income (R79) | the hourly rate pre-filled; none at 0 |
| salaried income | a month's pay divided by the hours expected in a month, 160 unless the user gives another, rounded to the cent; none with no pay or no hours |
| a month's pay, paid weekly | the pay times 52, divided by 12, rounded to the cent with a half cent rounded up |
| a month's pay, paid every two weeks | the pay times 26, divided by 12, rounded the same way |
| a month's pay, paid twice a month | the pay times 2 |
| a month's pay, paid monthly | the pay |

**Evidence:** Sci-Fin's spec, section 10.2, and `engine/TimeWorth.kt` at `2892110`.

**Acceptance:** 22.00 an hour for 10 hours is 220.00 a month and 2,640.00 a year; for 0.5 hours, 11.00 and 132.00; for -3 hours, 0.00 and 0.00. A month's pay is 3,200.00 from 3,200.00 paid monthly or 1,600.00 paid twice a month, and 2,600.00 from 1,200.00 every two weeks or 600.00 weekly. Salaried at 3,200.00 a month over 160 hours pre-fills 20.00; over 0 hours, nothing; with Income off or a pay of 0.00, nothing. Hourly at 20.00 pre-fills 20.00 and has no month's pay.

**Not:** Logging the figure as income or a Goal: the calculator explores and writes nothing.

**Since:** 2026-11-25 · builder · own words

## 13. Statement import

### D-R26 · Statement import

**Rule:** A bank statement in CSV or OFX is read on the device, and every bank row lands in a review as a suggestion (R107); nothing is recorded until one Confirm, which names the money out and the money in apart (R107). Each recorded bank row keeps the two keys below, once per row; a row arrives unticked as already recorded when its FITID was recorded, or, by synthetic key where it or the recorded row has no FITID, while the statement's rows with that key, taken in order, are no more than the rows recorded with it, kept by the import or held by a payment. A payee once filed by the user under one category pre-fills that category (R108).

**Values:**

| Key | Built from |
|---|---|
| FITID key | `fit:` and the OFX FITID; only for a row that has one |
| synthetic key | `syn:`, then the day as `YYYY-MM-DD`, the signed amount with two decimals and a point, and the first 40 characters of the description, with each run of spaces, tabs and non-breaking spaces made one space and the ends trimmed first, in lower case, separated by vertical bars |

**Evidence:** Sci-Fin's spec, section 4.3, and `engine/Import.kt` at `2892110`, whose key took the description as written; the spacing made one: builder, 2026-11-25.

**Acceptance:** Importing the same OFX statement twice records nothing the second time; importing a CSV export of the same period also records nothing; a payee filed under a category once is pre-filled on the next statement. A row of -12.40 on 2026-03-03 described `POS MERCATO VERDE 0412` has the synthetic key `syn:2026-03-03|-12.40|pos mercato verde 0412`, and so does the same row with two spaces, a tab or a non-breaking space between its words, or a space before them; after the OFX row is recorded, that CSV row arrives as already recorded. With a -4.50 row F1 recorded, a row F2 of the same day, amount and text arrives as new; of two such rows with no FITID, one recorded, one arrives as recorded and one as new; a row of money into Savings, once recorded, arrives as recorded.

**Not:** Checking the FITID only: a CSV export of a period already imported as OFX carries no FITID and would be recorded twice. Keying the description as written: two exports that space one payee differently would record it twice. Matching any one key: two equal purchases of one day would hide each other.

**Since:** 2026-11-26 · owner · agreed rewording

### D-R103 · Statement formats

**Rule:** An OFX statement gives one bank row per transaction block: the day from the first eight digits of its posted date, its amount, its name (else its memo, else `Statement entry`) and its FITID. A CSV statement gives one bank row per record after its header (R105); a line break inside quotes stays in its field and reads as a space, and the records are split on semicolons when the header has more semicolons than commas, else on commas. A row with no readable day (R106) or amount (R104), or an amount of zero, is dropped.

**Evidence:** Sci-Fin's spec, section 4.3, and `engine/Import.kt` at `2892110`.

**Acceptance:** An OFX block posted `20260303120000` of `-12.40` reads as 2026-03-03 and -12.40; a block with a memo and no name takes the memo; a block of `0.00` gives no row. A semicolon CSV with the quoted description `POS BAR` and `LA PIAZZETTA` on two lines gives one row described `POS BAR LA PIAZZETTA`.

**Since:** 2026-11-25 · builder · own words

### D-R104 · Statement amounts

**Rule:** A statement amount is read into whole cents with whole-number arithmetic only (R20): one three-letter currency code before or after the figure is ignored; a text still holding `/`, `:` or a letter other than a trailing `DR` or `CR` is no amount, since a day or a time is not one, and other characters besides digits, `.`, `,`, signs and brackets are ignored. Brackets, a leading or trailing minus, written `-`, `−` or `–`, or a trailing `DR` make it negative, and a trailing `CR` makes it positive. When both `.` and `,` appear, the last one is the decimal point; when one appears, exactly three digits after its last occurrence mark a thousands separator, and any other count a decimal point, with a third decimal rounding the cent half up.

**Values:**

| Text | Cents |
|---|---|
| `1.234,56` | 123456 |
| `1,234.56` | 123456 |
| `-1.234,56` | -123456 |
| `(12,40)` | -1240 |
| `12,40-` | -1240 |
| `0,5` | 50 |
| `12` | 1200 |
| `1,450` | 145000 |
| `€ 9.5` | 950 |
| `12,345.678` | 1234568 |
| `−12,40`, `–12,40` | -1240 |
| `12,40 DR` | -1240 |
| `12,40 CR` | 1240 |
| `EUR 12,40`, `12,40 USD` | 1240 |
| empty, `abc`, `04/03/2026`, `10:30`, `EURO 12,40`, `DR 12,40` | no amount |

**Evidence:** Sci-Fin's spec, section 4.3, and `engine/Import.kt` at `2892110`, whose amounts went through a floating-point number. The minus signs, the `DR` and `CR` suffixes and the texts that are no amount: builder, 2026-11-25, after review of PR 3.

**Acceptance:** Each text in the table reads as its figure in cents; the parser holds no floating-point value.

**Not:** Sci-Fin's floating-point parser: a cent can be lost on the way to a whole number. Reading the digits of a day as an amount: `04/03/2026` would be 403,202.60.

**Since:** 2026-11-25 · builder · own words

### D-R105 · CSV columns by header

**Rule:** A CSV statement's columns are found from the words of its header, never by position: the fields in the order below, each trying its keywords in order and never taking a column another field took. A keyword matches a header that is the keyword, the keyword plus one more word, or a single word containing it, and a header equal to the keyword wins over those; the amount keyword `value` never matches a header holding the word `date`, and the date keywords before `value date` never match a header holding `value` or `valuta`, so the booking date wins and a value-date column is the date only when no other is. Each row takes the signed amount when it reads, else its debit as money out, else its credit as money in; a statement with no date column, or with no amount, debit or credit column, gives no rows.

**Why:** The value date is when interest starts to count; the booking date is the day the money moved, which is the day a payment belongs to (R25).

**Values:**

| Field | Keywords, in the order tried |
|---|---|
| date | data contabile, data operazione, booking date, transaction date, date, data, datum, fecha, value date, data valuta |
| description | description, descrizione operazione, descrizione, payee, merchant, details, dettagli, beschreibung, verwendungszweck, concepto, narrative, reference, causale |
| debit | debit, dare, addebiti, addebito, uscite, uscita, soll, withdrawal, paid out |
| credit | credit, avere, accrediti, accredito, entrate, entrata, haben, deposit, paid in |
| amount | amount, importo, betrag, value, importe, bedrag |

**Evidence:** Sci-Fin's spec, section 4.3, and `engine/Import.kt` at `2892110`. The Italian headers and the booking date over the value date: builder, 2026-11-25, from Italian bank exports.

**Acceptance:** Headers `Booking date,Value date,Description,Amount` with `01/09/2026,02/09/2026,POS CAFFE DEL MOLO,-3.50` give one row of -3.50 on 2026-09-01, and headers `Booking date,Value date,Description,Debit,Credit` with the same days, a debit of `3.50` and an empty credit give the same row. Headers `Value date,Booking date,Description,Amount` with `02/09/2026,01/09/2026` give 2026-09-01 too. Headers `Data contabile;Data valuta;Causale;Descrizione operazione;Dare;Avere` with `03/03/2026;05/03/2026;PAGAMENTO POS;MERCATO VERDE;1.234,56;` give one row of -1,234.56 on 2026-03-03 described `MERCATO VERDE`, and an Avere of `1.450,00` gives 1,450.00. Headers `Data;Descrizione;Addebiti;Accrediti` with `03/03/2026;MERCATO VERDE;12,40;` give -12.40. Headers `Date,Credit card number,Description,Debit,Credit` with a debit of `800.00` and a credit of `12.00` give -800.00 and 12.00. Headers `Buchungsdatum;Verwendungszweck;Umsatzbetrag` with `01.09.2026;MIETE WOHNUNG;-800,00` give one row of -800.00. Headers `a;b;c` give no rows.

**Not:** Reading columns by position: banks order them differently, and a wrong column would file a day as an amount. Taking the value date: a payment booked on 30 September with a value date of 1 October would land in the wrong month.

**Since:** 2026-11-25 · builder · own words

### D-R106 · Statement dates

**Rule:** A statement day starting with four digits is read year, month, day; any other is read day first, so `03/04/2026` is 3 April. Dashes, slashes and dots all separate, and a two-digit year is in the 2000s. A day that does not exist, such as 31 February, is not read.

**Why:** Statements in euros write the day first; the review shows each day before anything is recorded (R107).

**Values:**

| Text | Day |
|---|---|
| `03/04/2026` | 2026-04-03 |
| `2026-04-03` | 2026-04-03 |
| `03.04.26` | 2026-04-03 |
| `31/02/2026` | none |
| `yesterday` | none |

**Evidence:** Sci-Fin's spec, sections 4.3 and 12, and `engine/Import.kt` at `2892110`.

**Acceptance:** Each text in the table reads as its day; `05/03/2026` in a CSV statement is 5 March.

**Not:** Month first: 05/03/2026 would land in May.

**Since:** 2026-11-25 · builder · own words

### D-R107 · The import review

**Rule:** Each bank row arrives in the review with a status, ticked or not, as below, newest first. A ticked outflow is recorded as a payment of its amount on its day, from Savings unless the user picks a Goal or an Account, with parts that add up (R23, R24, R54) and its keys (R26), or as a payment off an Account, taking its money from Savings, a Goal or nowhere as R89 has it, by R89's default unless the user picks another for the row; a ticked row of money in is added to Savings, and on into a Goal when the user picks one, as money put into a Goal is (R50), and is not counted as income, or is recorded as a refund to Savings, a Goal or an Account, a payment of minus its amount with parts that add up (R21, R23) and its keys. Confirm stays off while a ticked outflow is neither or is sent into a Goal, a ticked row's parts do not add up, a refund is also sent into a Goal, a payoff from a Goal is above what the Goal holds after the ticked rows before it, or a payoff of a Loan that counts as spending has no category (R89); a row paid from a Goal that holds less than it after the ticked rows before it carries a warning and takes the rest from Savings (R54); Confirm records the ticked rows oldest first, those of one day in the order the review lists them, checks them in that same order against what the rows before each have recorded, and names the count of ticked rows, the money out and the money in, each as a positive figure; a row recorded since the review was built, by another review's Confirm or a pair, is neither recorded nor counted again.

**Why:** A recurring expense's sweep already moved Savings (R63); recording its bank row too would count it twice. A bank seldom writes the name the user gave the expense, so the amount and the day find it too; a card's payment is left out, since its bank row is the payoff that lowers the card (R89), which its sweep never does.

**Values:**

| Status | When, checked in this order | Ticked |
|---|---|---|
| `DUPLICATE` | recorded already, as R26 counts | no |
| `INCOMING` | its amount is positive | yes |
| `PAIR` | an outflow suggested as the pair of a receipt's payment (R118); Confirm records nothing for it until the user turns the suggestion down, and then records it as a `READY` row when ticked | no |
| `RECURRING` | an outflow dated no earlier than the later of a recurring expense's first due date (R61) and the day it was created (R60), or than its earliest sweep row of a due date when that is earlier, whose payee's words hold the expense's name's words, or are held in them; or such an outflow of the expense's amount, or of what its rows dated on that due date hold, on a day within 3 days of one of its due dates, or of what its sweep rows of a due date dated on a day hold, within 3 days of that day; a share (R62, R63) pays toward a bill still to come and counts in neither; neither for an expense that is a card's payment (R93) | no |
| `READY` | any other outflow | yes |

**Evidence:** Sci-Fin's spec, section 4.3, and `engine/Import.kt` at `2892110`, whose Confirm said how many entries were added with the money out and the money in apart, and whose payoff always lowered Savings; the payoff's default as R89's: builder, 2026-11-25, after review of PR 3; a recurring row found by its amount and day, a card's payment left out of both matches, and neither match before the first due date: builder, 2026-11-25; nor before the day it was created: owner, 2026-11-25.

**Acceptance:** With Savings at 1,000.00, a recurring expense named Rent and the March OFX statement: `AFFITTO CASA RENT` -950.00 arrives `RECURRING` and unticked, `STIPENDIO MARZO` 1,450.00 `INCOMING`; filing -12.40 under Groceries, splitting -42.30 into Pharmacy 30.00 and Household 12.30, and paying off a card at 300.00 with -60.00 names 4 rows, 114.70 out and 1,450.00 in, and leaves Savings at 2,335.30 and the card at 240.00. The same 1,450.00 sent on into a Goal holding 110.00 leaves the Goal at 1,560.00 and Savings at 885.30, with a `savings` row of +1,450.00 and a `goal:` row of +1,450.00 dated 7 March; a Goal deleted before Confirm leaves it in Savings. Rows of -14.00 on 7 March and -100.00 on 8 March, both from a Goal holding 110.00, give the later one a warning, and confirmed leave the Goal at 0.00 and Savings 4.00 lower; the -100.00 alone leaves the Goal at 10.00. A -60.00 row paying off a card at 300.00 that is linked to its fixed payment (R93) leaves the card at 240.00 and Savings unchanged by it, and with Savings picked for the row, 60.00 lower. The -60.00 payoff of the card at 300.00 taken from a Goal holding 110.00 leaves the card at 240.00, the Goal at 50.00 and Savings unchanged by it; from a Goal holding 50.00, Confirm stays off. With a 29.90 monthly expense named Telefono due the 10th, `TIM S.P.A.` -29.90 on 12 March arrives `RECURRING` and unticked, and so does -29.90 on 10 March after the swept expense was edited to 34.90 with the difference declined; -29.90 on 20 March arrives `READY`, and so does a card's 60.00 fixed payment's bank row on its due date, and `ORBITA CARD PAYMENT 0412`, whose words hold the name of that fixed payment, Orbita card payment. With Telefono first due 10 March 2026, `TELEFONO ABBONAMENTO` -12.00 on 7 March and `TIM S.P.A.` -29.90 on 10 February arrive `READY`, and the first on 10 March arrives `RECURRING`; with Telefono first due 5 March but created on 14 March, `TELEFONO ABBONAMENTO` on 10 March and `TIM S.P.A.` -29.90 on 5 March arrive `READY`, and the first on 14 March `RECURRING`. Telefono created on 1 March 2026, first due 10 March, swept that day and on 15 March moved to fall due on 12 April, with the difference declined, still finds on 16 March `TELEFONO ABBONAMENTO` -29.90 on 10 March and `TIM S.P.A.` -29.90 on 11 March, both `RECURRING`. A yearly 1,200.00 Assicurazione auto created on 14 September 2026, first due 15 July 2027, with shares of 120.00 swept on 1 October and 1 November, leaves `ASSICURAZIONE AUTO RCA` -45.00 on 12 November and `BONIFICO` -120.00 on 2 October `READY`. A Loan with no category of its own, a -500.00 row of 1 March charged to it and a -100.00 payoff of it from Savings on 20 March, which the review lists first: Confirm is on, the charge is recorded first, and the payoff counts as a transfer, in no category (R89). 1,450.00 sent into a Goal holding 110.00 on 7 March pays a -200.00 row from that Goal on 8 March with no warning and a -300.00 payoff of the card from it on 9 March, leaving the Goal at 1,060.00. `RIMBORSO CALZATURE ORSA` +25.00 on 20 March filed as a refund under Household records a payment of -25.00 with its keys, leaves Savings 25.00 higher and Household's March spent 25.00 lower, and arrives `DUPLICATE` on the next import; filed as a refund to the card at 300.00, it leaves the card at 275.00. Two reviews open on one +100.00 row, or one -50.00 payoff of the card, both confirmed, record it once.

**Not:** An outflow recorded with nowhere filed: Savings would drop with nothing to show where the money went. Matching a row dated before the expense's first due date, or before the day it was created: no sweep covered it, so left unticked it would be recorded nowhere. Reading the floor and the due dates from the expense alone: an edit that moves its schedule starts the chain again (R65), and a bill its sweep already paid would arrive `READY` and be recorded twice. Recording the rows newest first, as the review lists them: a Loan's payoff would be decided before the charge to it made earlier, and both would count. A `PAIR` row that only pairing can record: a different purchase of the same amount, suggested by mistake, could never be recorded.

**Since:** 2026-11-26 · owner · agreed rewording

### D-R108 · Payee rules

**Rule:** A row's payee is its description in lower case, with every character other than letters, digits and spaces read as a space and runs of spaces made one, and its rule name is that payee less the words the table drops, or the whole payee when none is left. Confirming a ticked outflow filed under one category, from any source, teaches its rule name that category; a row split across two or more categories teaches nothing and leaves an earlier rule as it was. A row whose rule name, else whose payee, has a rule whose category still exists arrives as a draft from Savings with one part in that category, and no category is guessed otherwise.

**Why:** Bank descriptions carry dates, times, terminal ids and card numbers that differ on every row, so a rule kept under the whole payee would rarely match the next statement. A split row has no one category that stands for its payee.

**Values:**

| Dropped from the rule name | Such as |
|---|---|
| every word holding a digit: dates, times, terminal ids, card numbers | `03`, `2026`, `10`, `0412`, `1234` |
| every word of three or more x's, as a masked card number shows | `xxxx` |
| the bank's words for a card payment | `pos`, `pagamento`, `carta`, `ore`, `card`, `payment` |

**Evidence:** Sci-Fin's spec, section 4.3, and `engine/Import.kt` at `2892110`, whose rule was kept under the whole payee; the split row: the phase 1 plan's provisional choices, 2026-11-24; the rule name: builder, 2026-11-25, from Italian bank statements.

**Acceptance:** `POS MERCATO VERDE 0412` filed under Groceries in March arrives in April's review with one part of 18.75 in Groceries, from Savings; `FARMACIA SAN LORENZO` split into Pharmacy and Household teaches no rule; the payee of `POS*MERCATO-VERDE  0412` is `pos mercato verde 0412`. `POS 03/03 MERCATO VERDE 0412` and `PAGAMENTO POS 12/03/2026 ORE 10:22 MERCATO VERDE CARTA ****1234` both have the rule name `mercato verde`, and the first filed under Groceries pre-fills the second; `POS 0412` keeps the rule name `pos 0412`. A rule kept under the payee `pos mercato verde 0412` still pre-fills `POS MERCATO VERDE 0412`.

**Not:** Guessing a category from a payee's words: a wrong guess files spending where the user never put it.

**Since:** 2026-11-25 · builder · own words

## 14. Chatelaine receipts

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

### D-R112 · The receipt Profile

**Rule:** The app keeps which Profile holds the receipt folder, and that Profile alone keeps the receipt settings: the folder, how new files are found (R29), the recorded receipt ids, the category links (R32) and each store's last source (R35), except that a Profile restored from the Trash while another holds the folder keeps its own with no folder (R126). A folder can be chosen in a Profile only while no other Profile holds one, and only the Profile that holds a folder scans it and confirms receipts (R115).

**Acceptance:** With the folder chosen in Profile A, Profile B is offered no folder and choosing one there is refused; a receipt opened for review in B cannot be confirmed, and the same receipt in A records one payment in A and none in B.

**Not:** Receipt settings in every Profile: a second Profile's links and recorded ids would wait for receipts that never reach it.

**Since:** 2026-11-25 · builder · own words

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

### D-R116 · Scanning the receipt folder

**Rule:** A scan reads, in name order, the receipt folder's files whose names end in `.json`, passes over every other name, such as the temporary name Chatelaine writes under (Chatelaine's spec, rule 123), and writes nothing, to the folder or to the Profile (R27, R29). Each file read takes the first row below that fits: a receipt is new while its id is not among the receipt Profile's recorded ids (R112, R115), and one id met in two files of a scan is offered once, from the first. The count of receipts waiting is the number ready to review, and a Profile that does not hold the receipt folder scans nothing.

**Values:**

| Check, in this order | The file is |
|---|---|
| it cannot be read, is not a receipt file, or reading it fails in any way | refused as unreadable, and the scan goes on (R30) |
| its format is above the highest Tesoriere reads, 1 | refused, naming its format (R30) |
| its receipt id is recorded | left out, not listed (R30) |
| its receipt id was met in an earlier file of this scan | left out, not listed |
| its currency is not the Profile's | refused, naming both currencies (R30, R47) |
| none of the above | ready to review (R29) |

**Evidence:** Only names ending in `.json` are read: builder, 2026-11-25, after Chatelaine's spec, rule 123, which writes a file under a temporary name and renames it once whole.

**Acceptance:** Three new files give three receipts ready, a count of 3, and the Profile unchanged; a file whose receipt id is recorded is not listed; a format-2 file is refused naming 2; a USD file in a EUR Profile is refused naming USD and EUR; a file that is not JSON is refused as unreadable; two files holding one receipt id give one receipt; a file named `chatelaine-receipt-rc-9.part` is not read. Each leaves the Profile unchanged.

**Not:** Recording on a scan: nothing is recorded before Confirm (R29, R31). Moving or deleting a file once it is recorded: the folder is read only (R27).

**Since:** 2026-11-25 · builder · own words

### D-R117 · Reading a receipt file

**Rule:** A receipt file is read as UTF-8 JSON in the format of Chatelaine's spec, rule 123, in three steps: a file over the size or depth cap below is unreadable before anything in it is read; then its `format` key, where a JSON whole number above the highest format Tesoriere reads, 1, refuses the file naming that format, whatever else the file holds (R30); then the rest, read whole or not at all as the rows below say. A key rule 123 does not name is passed over, `pack` may be absent, and a file that fails any other row is unreadable (R30); reading a file never fails in a way that stops a scan (R116).

**Values:**

| The file | Read as |
|---|---|
| larger than 1 MiB (1,048,576 bytes), or nesting arrays and objects more than 64 deep | unreadable, before any key is read |
| not UTF-8, not JSON, or not one JSON object | unreadable |
| `format` a JSON whole number above 1 | newer format, naming it, or naming 2,147,483,647 when it is larger |
| `format` missing, not a JSON whole number, or below 1 | unreadable |
| a key rule 123 gives the receipt or a line missing, `pack` aside | unreadable |
| `receiptId`, `store`, `currency`, or a line's `name`, `categoryId` or `categoryName`, not JSON text, or blank | unreadable |
| `pack` present and not JSON text | unreadable |
| `totalPaid`, `quantity` or `pricePaid` not a JSON number written as a whole number, such as `"318"` in quotes or `1.5` | unreadable |
| `date` not a calendar day written `YYYY-MM-DD` | unreadable |
| `lines` not an array of objects, or empty | unreadable |
| `totalPaid` zero or below, a `pricePaid` below zero, or a `quantity` below 1 | unreadable |
| `totalPaid` above 100,000,000,000 cents (1,000,000,000.00), and so any `pricePaid` above it | unreadable |
| `totalPaid` other than the sum of the lines' `pricePaid`, or a sum too large to hold | unreadable |
| a line with no `pack`, or a `pack` that is empty or blank | a line with no pack |
| `currency` in small letters, such as `eur` | read in capitals, `EUR`, and compared with the Profile's ignoring capitals (R30) |
| a key rule 123 does not name | passed over |

**Evidence:** Chatelaine's spec, rule 123, with its JSON encoding as merged in Chatelaine, which settled the phase 1 plan's owner answer 1; read 2026-11-25. A blank receipt id or an empty `lines` read as unreadable: builder, 2026-11-25, since a receipt is the items ticked at one store (Chatelaine's spec, rule 124) and its id is what R30 tells a recorded file by. An empty `pack` read as none; `currency` read in capitals; a total above zero and no price paid below zero, since a closed Chatelaine receipt is a purchase; the size and depth caps; numbers in quotes refused, reversing the builder's first reading of 2026-11-25, which took them, since rule 123 writes whole cents as JSON numbers: builder, 2026-11-25, after review of Tasks 14 to 16. The cap on `totalPaid`, far above any receipt, so no figure built from one overflows: builder, 2026-11-26, after review of the receipts and pairs pull request.

**Acceptance:** The example file of Chatelaine's spec, rule 123, reads as a receipt of 10.99 at Supermarket A on 2026-11-24 in EUR with three lines, the batteries with no pack. The same file with `format` 2 and a key of its own gives newer format 2, and so it does with no `lines`; with `format` 99999999999 it gives newer format 2,147,483,647. With any one key of rule 123 other than `pack` taken out, with `totalPaid` 11.00, 0.00 or `"1099"`, a `quantity` of 1.5 or 0, a line of -1.00 among lines that add up to the total, or a blank `store`, it is unreadable; so is a file of 3,000 nested arrays, and one over 1 MiB. With a key `note` added it reads as before. A `pack` of `""` reads as no pack, and `currency` `eur` reads as `EUR`. A format-2 file over 1 MiB is unreadable, not a newer format. With `totalPaid` 1,000,000,000.00 and lines adding up to it, it reads; with 1,000,000,000.01, it is unreadable.

**Not:** Reading what can be read of a file that fails a row: a receipt half read would record a payment that is not the one made. Taking `totalPaid` when the lines add up otherwise: the payment's parts could not add up to it (R23). A file that could never be confirmed, such as one of total zero: it would wait in the folder for good (R115).

**Since:** 2026-11-26 · builder · own words

### D-R31 · Receipt review

**Rule:** A receipt is reviewed as one payment: its store, date and total, and its lines each in the Tesoriere category its category link gives (R32). A line can be moved to another category for this receipt only; no line can be left out. One Confirm, naming the total, records the payment; closing the review records nothing.

**Why:** Chatelaine sends no line without a category (Chatelaine's spec, rule 126), so every line has a place.

**Acceptance:** A receipt of five lines in two categories records one payment of the receipt's total with two parts; moving one line to a third category gives three parts and changes no category link; closing the review leaves no payment.

**Not:** Excluding a line from the payment: the payment would no longer equal the bank row.

**Since:** 2026-11-24 · owner · agreed rewording

### D-R32 · Category links

**Rule:** The user gives categories equal names in both apps, and Tesoriere links them by name, ignoring capitals, the first time a Chatelaine category id appears; from then on the link is by id, so renaming the category in Chatelaine keeps it. A Chatelaine category with no equal name asks, in the review, to add it as a new Tesoriere category or to link it to an existing one. The review cannot be confirmed while a line of a Chatelaine category with no link is in no category; once the user has moved each such line by hand to a Tesoriere category it can be, nothing is linked for that Chatelaine category, and the next receipt with it asks again.

**Values:**

| Case | Result |
|---|---|
| Known id | the linked category, whatever the name |
| New id, a Tesoriere category of equal name ignoring capitals | linked to it |
| New id, no equal name, user adds it | a new Tesoriere category with the Chatelaine name, linked |
| New id, no equal name, user links an existing one | linked; the review reminds the user to rename the Chatelaine category to the Tesoriere name |
| Known id whose name differs from the linked category's | linked as before, with a note that the names differ |

**Acceptance:** A receipt with category Pulizia links to Tesoriere's pulizia; a later receipt with the same id named Pulizie stays linked and shows the names-differ note; a new category with no equal name keeps Confirm off until it is added or linked, or until each of its lines is moved by hand to a Tesoriere category, after which the next receipt with it asks again.

**Not:** Linking by name on every receipt: a rename in Chatelaine would read as a new category. Renaming the Tesoriere category to the Chatelaine name: the owner keeps Tesoriere's names.

**Since:** 2026-11-25 · owner · agreed rewording

### D-R33 · Closing a review

**Rule:** Category links, and categories added in a review, are kept only when the review is confirmed. Closing a review that holds changes, a line moved, the source changed, a link made or a category added, first shows a prompt warning that they will be lost; the user can dismiss it and stay in the review, or close and lose them.

**Acceptance:** Adding a category in a review and closing it shows the warning; dismissing it returns to the review with the change intact; closing anyway leaves no new category and no new link. Moving one line, or changing the source, and closing shows the same warning.

**Not:** Keeping links from a closed review: a link would exist with no recorded receipt behind it.

**Since:** 2026-11-25 · owner · agreed rewording

### D-R34 · Changing a link

**Rule:** A category link can be changed later. The change applies to receipts recorded after it; lines already recorded stay in the category they were filed under.

**Acceptance:** After relinking Snack from Eating out to Groceries, last month's Eating out spent figure is unchanged and the next receipt's Snack lines go to Groceries.

**Not:** Moving past lines too: past months' figures would shift on their own.

**Since:** 2026-11-24 · owner · agreed rewording

### D-R35 · A receipt's source

**Rule:** A receipt's source is Savings unless the user changes it in the review, and the source last confirmed for a store is pre-filled on that store's next receipt. Pairing with a bank row keeps the receipt's source, except that a receipt paired with a bank row recorded first, from a store with no source kept and its source left as the review opened it, takes the bank payment's own source.

**Why:** The receipt file does not say how it was paid (Chatelaine's spec, rule 123), and a store is usually paid the same way.

**Acceptance:** The first receipt from a store shows Savings; after it is confirmed with a card as source, the next receipt from that store shows the card. A -42.30 row charged to a card recorded first, then a receipt from a store with no source kept, paired with it as it opened, leaves the payment on the card.

**Not:** Asking for the source on every receipt: one more choice per receipt.

**Since:** 2026-11-26 · owner · agreed rewording

### D-R113 · Opening a receipt review

**Rule:** A receipt review opens each line in the Tesoriere category its Chatelaine category gives, as below, and with the source last confirmed for the store, named as the receipt file writes it (R35). A store with no source kept, or whose kept Goal or Account does not exist, opens with Savings.

**Values:**

| The line's Chatelaine category | Opens in |
|---|---|
| linked before, to a category that exists | the linked category, whatever either name (R32) |
| not linked, or linked to a category that does not exist, with a Tesoriere category of equal name ignoring capitals | that category, linked by name on Confirm |
| not linked, with no equal name | no category |

**Evidence:** Savings in place of a store's Goal or Account that no longer exists: builder, 2026-11-25.

**Acceptance:** With Tesoriere categories Groceries and pulizia, a first receipt with lines under Chatelaine's Groceries and Pulizia opens them in Groceries and pulizia, from Savings, and a line under Garden in no category. A store last confirmed with a card opens with the card, and with Savings once the card is deleted.

**Since:** 2026-11-25 · builder · own words

### D-R114 · Answering a receipt review

**Rule:** In the review the user can move one line to another category, for this receipt only and changing no link (R31); link a Chatelaine category to an existing Tesoriere category; or add it as a new Tesoriere category with its Chatelaine name and 0.00 planned (R32). Linking or adding files every line of that Chatelaine category the user has not moved by hand under the answer, whatever category it opened in, a line moved back to the category it would hold unmoved (the one it opened in, or the answer given since) counting as not moved, and a later answer for the same Chatelaine category replaces the earlier one, dropping a category the earlier one added when no line holds it. The review holds changes, for R33's warning, once a line is moved, the source changed, a link made or a category added; the notes below say what still waits for the user, one per Chatelaine category, in the order the lines first name them, then a pair available when there is one (R118).

**Values:**

| Note | For a Chatelaine category |
|---|---|
| unlinked | with no link, no equal name and no answer in this review; Confirm stays off while a line of it is in no category (R32) |
| pair available | none: the receipt meets R37 with a payment recorded from a bank row (R118); Confirm stays on |
| names differ | linked before, whose name differs from its Tesoriere category's ignoring capitals |
| rename in Chatelaine | linked by the user in this review to an existing category whose name differs from it ignoring capitals |

**Acceptance:** A line under Garden, with no Garden in Tesoriere, is noted unlinked; linked to Household it is filed under Household with a rename-in-Chatelaine note; added instead, it is filed under a new category named Garden with 0.00 planned, and the review holds changes. A Garden line moved to Fun before the answer stays under Fun. Snack, opened under Eating out by its link and linked in the review to Groceries, files its lines under Groceries. A review whose categories all link by name holds no changes until a line is moved or the source changed, and moving a line changes no link; a line moved to Fun and back to Groceries, where it opened, leaves no change, and a later link of its Chatelaine category files it again.

**Not:** Counting a link made by name as a change: the next open makes it again, so closing loses nothing.

**Since:** 2026-11-25 · builder · own words

### D-R115 · Confirming a receipt

**Rule:** Confirm records the receipt as one payment, the way every payment is recorded (R21, R54): on the receipt's day, of its total paid, with the store as payee, the review's source, and one part per Tesoriere category whose lines add up to other than zero, in the order the lines first name them. The payment keeps the receipt id and every line with the category it was filed under, a line whose category adds up to zero too; the receipt id joins the Profile's recorded ids (R30), and the review's links, by name or by the user, its added categories and the store's source (R35) are kept. Confirm stays off while a row below holds.

**Values:**

| Confirm stays off while |
|---|
| the Profile is not the receipt Profile (R112) |
| the receipt id is recorded already (R30) |
| the receipt's currency is not the Profile's (R30, R47) |
| a line of a Chatelaine category with no link is in no category (R32, R114) |
| a line has no category, or one that does not exist |
| the payment could not be saved: its parts do not meet R23, or its source does not exist |

**Evidence:** A line whose category adds up to zero kept among the lines but given no part: builder, 2026-11-25 (R23 allows no zero part).

**Acceptance:** A 16.50 receipt with Groceries lines of 1.89, 2.40 and 3.15 and Household lines of 6.90 and 2.16, from Savings at 1,000.00, records one payment of 16.50 with parts Groceries 7.44 and Household 9.06 and all five lines, records its receipt id, and leaves Savings at 983.50; a 0.00 line under Gifts adds no part and is kept among the lines. The same receipt from a Goal holding 10.00 carries R54's warning, and confirmed leaves the Goal at 0.00 and Savings at 993.50. A receipt id recorded once cannot be confirmed again.

**Not:** A part of 0.00: R23 has no zero part. Building the payment apart from the payment path: a short Goal, a card whose charges are not logged (R109) or a deleted source would be handled twice, two ways.

**Since:** 2026-11-25 · owner · agreed rewording

## 15. One payment, two sources

### D-R36 · Store links

**Rule:** The first time a receipt from a store is paired with a bank row, Tesoriere remembers that store's name with the bank row's payee, less the dates, terminal ids and card words that change on every row (R120). Later suggestions for that store rank the bank rows that match the link first, and still offer after them any other row whose total and date meet R37.

**Why:** A bank row's text rarely equals the store's name, so the store is linked once rather than compared.

**Acceptance:** After pairing an Esselunga receipt with POS ESSELUNGA MI 0423, the next Esselunga receipt is suggested first with the bank rows whose payee matches that link, such as POS ESSELUNGA MI 0611, and then with any other row that meets R37.

**Since:** 2026-11-26 · owner · agreed rewording

### D-R120 · The text a store link keeps

**Rule:** A store link keeps the store's name, as the receipt file writes it, with the rule name (R108) of the first bank row the store is paired with, by suggestion or by hand; a later pair for the store leaves the link as it is. A bank row matches the link when its rule name equals the one kept, and a match only ranks the row first among the store's suggestions: no row is left out for not matching (R36). The link is found through the payment's payee, the store, so a payee edited after Confirm is not found by its store's link.

**Why:** The rule name drops the terminal ids, dates and card words that differ on every row of one store (R108).

**Acceptance:** Pairing an Esselunga receipt with `POS ESSELUNGA MI 0423` keeps `esselunga mi`; `POS ESSELUNGA MI 0611` matches it and is suggested before `POS ESSELUNGA TO 0611`, which does not match and is still suggested; a later Esselunga pair with `ESSELUNGA SPA` by hand keeps `esselunga mi`.

**Not:** Moving the link to each new pair's row: one unusual row would rank first over every usual one. Hiding the rows that do not match: a store paid at a second branch would never be suggested.

**Since:** 2026-11-25 · owner · agreed rewording

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

**Rule:** When a pair's totals differ within the tolerance (R37), the bank row's amount is the payment's total, and the difference becomes one more part, marked as the difference part, that the user files under a category; the pair cannot be confirmed until it is filed. The difference is the bank amount less the receipt's total, so it is negative when the bank row is below the receipt, and as the difference part it may have the other sign from the total (R23).

**Why:** The bank amount is what left the account.

**Acceptance:** A EUR 42.10 receipt paired with a EUR 42.30 bank row records a EUR 42.30 payment with a EUR 0.20 difference part in the category the user chose, and the pair's confirm is off until it is chosen. A EUR 42.30 receipt paired with a EUR 42.10 bank row records a EUR 42.10 payment with a EUR -0.20 difference part.

**Not:** Filing the difference with the largest line, or spreading it over the lines: both guess where the money went.

**Since:** 2026-11-25 · owner · agreed rewording

### D-R40 · Pairing by hand

**Rule:** The user can pair a receipt and a bank row by hand, from either side, also when the pairing rule (R37) does not suggest them. The bank row's amount is the total, and any difference is a part filed as in R39.

**Why:** A pair the rule misses would otherwise count the payment twice.

**Acceptance:** A EUR 40.00 receipt and a EUR 41.20 bank row, not suggested, can be paired by hand into one EUR 41.20 payment with a EUR 1.20 part the user files.

**Since:** 2026-11-24 · owner · agreed rewording

### D-R118 · Suggesting a pair

**Rule:** A receipt's payment and a bank row are suggested as a pair when the row is money out not recorded yet (R26), the payment holds a receipt, no bank keys and a total above zero, and their totals and days meet R37. In the import review such a row arrives `PAIR`, unticked, naming the payment, the pairs chosen across the whole statement by taking every possible row and payment in the order below and naming them together when both are free and naming them still lets as many rows as possible name a payment, each row and each payment once at most, and Confirm records nothing for a row that names a pair: only pairing it records it (R119), unless the user turns the suggestion down, after which the row names no pair, and a row the user pairs by hand names that payment from then on, its twins left as they are. A receipt opened for review is suggested in the same way with each payment recorded from a bank row that holds no receipt, read as that bank row, and its review notes the best one as a pair available, leaving Confirm on (R114).

**Values:**

| Taken first, in this order | Before |
|---|---|
| a row matching the store's link (R36, R120) | a row that does not |
| equal totals | totals that differ within the tolerance (R37) |
| the row dated closest to the receipt | rows dated further from it |
| the row dated earlier, then the one the statement lists first | later rows |
| the payment recorded first | payments recorded later |

**Acceptance:** A 42.30 receipt recorded on 10 March and a statement row of -42.30 on 12 March: the row arrives `PAIR`, unticked, naming the receipt's payment, and the import's Confirm leaves one payment and Savings as it was, ticked or not; with the suggestion turned down and the row ticked under Groceries, Confirm records it as a second payment; the same row on 14 March arrives `READY`; +42.30 arrives `INCOMING`. A refund, or a payment that holds bank keys already, is never named. With rows of -42.30 on 11 and 12 March, only the 11 March row names it; with rows of -42.40 on 11 March and -42.30 on 12 March, the 12 March row does. Two 42.30 receipts of 10 March and rows of -42.30 on 11 and 12 March: the 11 March row names the receipt recorded first and the 12 March row the other. 42.30 receipts of 10 and 11 March with rows of -42.30 on 11 and 14 March: the 14 March row, which only the 11 March receipt meets, names it, and the 11 March row names the 10 March receipt. A 42.30 receipt of 10 March and a 42.50 receipt of 11 March with rows of -42.30 on 10 and 12 March and -42.50 on 13 March: the 10 March row names the 42.30 receipt, the 13 March row the 42.50 one, and the 12 March row arrives `READY`. A named payment removed while the review is open leaves its row unrecorded by that Confirm, and the next import of the statement offers the row `READY`. A receipt opened while a -42.30 payment recorded from a bank row on 12 March holds no receipt notes it as a pair available, and can still be confirmed. Two equal -4.50 rows and one 4.50 receipt, the suggestion turned down, the first row unticked and the second paired by hand and left ticked: Confirm records nothing more, one coffee in all.

**Since:** 2026-11-26 · builder · own words

### D-R119 · Recording a pair

**Rule:** Pairing a receipt and a bank row, suggested or by hand (R40) and from either side, edits the payment the first one recorded, through the payment path (R54), into the one payment below. A difference other than zero (R39) must be filed under a category that exists before the pair can be recorded. A payment that already holds a receipt and bank keys, a refund, a row of money in and a row recorded already cannot be paired, a row being recorded already as R26 counts, among the rows of its import review that did not arrive `DUPLICATE`.

**Values:**

| Field | After pairing |
|---|---|
| total | the bank row's amount |
| date | the receipt's day |
| source | the receipt's: the payment's own when the receipt came first, the review's when the bank row did (R35); but the bank payment's own when the store has no source kept, or one that no longer exists, and the user left the review's source as it opened; the store then keeps the pair's source |
| payee | the store |
| parts | one per category whose receipt lines add up to other than zero (R115), and the difference part (R39) |
| receipt id and lines | the receipt's; its id joins the recorded ids (R30) |
| bank keys | the bank row's, also kept among the import's keys, so the row arrives `DUPLICATE` on a later import (R26) |
| links, added categories and the store's source | kept as a receipt's Confirm keeps them (R115), when the receipt came second |
| store link | made the first time the store pairs (R120) |
| the import review still open | records nothing more for the row on its Confirm, ticked or not, and leaves it out of its figures, the row not having arrived `DUPLICATE`, each payment that took a row's keys since the review was built standing for one row only, so a second row with the same keys is still recorded; a ticked `DUPLICATE` row is recorded as R107 says |
| spending | counts in the receipt's month, the payment's day, even when the bank row is dated in the next (R25) |

**Acceptance:** A 42.30 receipt from Savings at 1,000.00, then its -42.30 row: one payment of 42.30 from Savings, dated the receipt's day, with the row's keys, Savings at 957.70, and the row `DUPLICATE` on the next import. A -42.30 row filed under Groceries from Savings, then a receipt of Groceries 30.00 and Household 12.30 confirmed with a card: one payment of 42.30 from the card with those two parts, the receipt's day and the row's keys, Savings back at 1,000.00 and the card 42.30 higher. A 42.10 receipt from a Goal holding 42.10, paired with a -42.30 row, warns as R54 says and takes 0.20 from Savings. A -42.30 row dated 14 March, 4 days after its receipt, ticked in the import review and paired by hand before Confirm, leaves one payment and Savings 42.30 lower once. A receipt of 30 March paired with its row of 2 April counts in March's spent. Two rows of -4.50 `BAR CENTRALE` on 11 March with no FITID and one 4.50 receipt under Food: the first pairs, and the second, ticked under Food, is recorded at Confirm, leaving two payments, Savings at 991.00 and Food's March spent at 9.00. Two 4.50 receipts from Bar Centrale on 10 March under Food and those two rows: both arrive `PAIR`, each pairs with one receipt, leaving two payments, Savings at 991.00 and Food at 9.00; so they do with FITIDs F1 and F2, the F1 row then recorded and the F2 row not. A -42.30 row charged to a card at 0.00 recorded first, then a receipt from a store with no source kept, paired as it opened: the payment stays on the card, the card at 42.30 and Savings unchanged; with Savings picked in the review it moves to Savings; for a store whose kept source is a Goal, to the Goal.

**Not:** Writing the pair outside the payment path: a short Goal, a card whose charges are not logged (R109) or a deleted source would be handled twice, two ways. Comparing every key: two equal purchases with FITIDs of their own share the synthetic key, and the second could never pair.

**Since:** 2026-11-26 · owner · agreed rewording

### D-R128 · Unpairing

**Rule:** Unpairing a pair, a payment holding a receipt and bank keys, turns it back through the payment path (R54) into the receipt's payment alone, as below. Its bank keys leave the payment, and its row's keys the import's keys once, so its bank row arrives as new on the next import and can be recorded on its own (R26); its receipt id stays recorded (R30) and the store link stays (R36). Any other payment, or one that does not exist, is not unpaired and nothing changes.

**Values:**

| Field | After unpairing |
|---|---|
| total | the sum of the receipt lines' price paid |
| parts | one per category whose lines add up to other than zero, under the categories the lines are filed under at the unpairing (R115); no difference part |
| date and source | unchanged; a short Goal or a deleted source as R54 says |
| receipt id and lines | unchanged |
| bank keys | none |

**Evidence:** Owner, 2026-11-26.

**Acceptance:** A 42.10 receipt from Savings at 1,000.00, paired by hand with a -42.30 row and its 0.20 filed under Fun, unpaired: one payment of 42.10 with the receipt's parts and no keys, Savings at 957.90 and Fun's spent back; the row then arrives `PAIR` again, and turned down and ticked it is recorded on its own, Savings at 915.60. Paired with row F1 while its twin F2 is recorded, then unpaired, F1 arrives as new and F2 as recorded. Unpairing a receipt payment with no bank keys changes nothing.

**Not:** Dropping the store link: a link only ranks (R36).

**Since:** 2026-11-26 · owner · agreed rewording

## 16. The Trash

### D-R121 · The Trash

**Rule:** A delete happens at once and settles its money then, as its own rule says, and what it removed is kept in the Trash for 30 days with what a restore needs, as below; on the 30th day after the delete it goes for good, and Delete forever takes it sooner, neither moving any money. While in the Trash an item is out of the Profile: it sweeps nothing, reminds of nothing, counts towards no figure and is the source of no new payment. One notice is due for an item on the 20th day after its delete, and once given it is not due again; the engine names the items due a notice and the items to go, and how the notice reads is the design pass's (R16).

**Values:**

| Deleted | Kept with it |
|---|---|
| a Goal (R58) | the rows its delete wrote, and what each payment from it took from it |
| a category (R123) | its category links (R42) |
| a recurring expense (R66) | the Account linked to it, the rows its delete wrote, and what each payment charged to that Account took |
| an Account (R87, R88) | its linked recurring expense, the rows its delete wrote, and what each payment charged to it took |
| a pair of ledger rows (R53) | both rows |
| an Account quick-add, undone (R111) | its rows |
| a payment (R125) | the whole payment, and the row its removal wrote for a deleted source (R54) |
| a Profile (R126) | everything it holds |

**Evidence:** Sci-Fin's spec, section 3.6, and `engine/Trash.kt` at `2892110`; the quick-add, the payment, and the payments kept with a Goal or an Account: builder, 2026-11-25.

**Acceptance:** An item deleted on 1 October 2026 is due its notice on 21 October and not on 20 October, and once given is not due again; it is kept on 30 October and goes on 31 October. A monthly expense in the Trash over two month crossings writes no sweep row.

**Not:** Asking the money question again when an item goes for good: it was answered at the delete, so Delete forever and the 30th day have nothing left to settle.

**Since:** 2026-11-25 · builder · own words

### D-R122 · Restoring from the Trash

**Rule:** Restore puts back what a delete removed as it was, ids, dates, parts, links and marks included, and undoes each row the delete wrote with a row of the opposite amount on the same key, dated the same day, filed under the same category with the same payoff and share marks (R51, R67), noted `Restored from the Trash`; so Savings, each Goal, each Account's balance and each month's spent figures are what they were before the delete, and both rows stay in the ledger. A restored recurring expense counts the month of the restore as swept, catching up no month it missed, and its due dates move on to that day (R61). Restore is refused with a code, changing nothing, in the cases below; an item whose id is in the Profile again is left as it is.

**Values:**

| Refused when | Code |
|---|---|
| a payment from the deleted Goal, or charged to the deleted Account, was since removed, moved to another source or changed in what it took from it, or a payment was since restored onto it, each of which wrote a row in its place (R54) | `WRITTEN_SINCE` |
| a restored recurring expense or Account would file later rows under a category that no longer exists (R123), and no existing category is given to take its place | `CATEGORY_MISSING` |
| another payment holds the restored payment's receipt id, or its bank row is held by more payments than at the delete (R107, R125) | `RECORDED_AGAIN` |
| the rows of an undone quick-add move a Goal or an Account deleted since (R111) | `TARGET_DELETED` |
| the rows of an undone quick-add take more from a Goal than it holds | `GOAL_SHORT` |
| a payment restored as a pair cannot be paired as R119 says, such as with a difference filed under no category (R127) | `CANNOT_PAIR` |

**Evidence:** Sci-Fin's spec, section 3.6, and `engine/Trash.kt` at `2892110`, whose restore rows were dated the day of the restore; the refusal for rows written since: owner, 2026-11-25; the restore's rows dated and filed as the delete's, and the other refusals: builder, 2026-11-25.

**Acceptance:** With Savings at 1,000.00 and a Goal holding 300.00, deleting the Goal with its money returned on 14 September 2026 leaves 1,300.00; restored on 2 October it holds 300.00, Savings is 1,000.00, and a `goal:` row of +300.00 dated 14 September is noted `Restored from the Trash` beside the delete's -300.00. A semimonthly 300.00 expense under Rent created on 1 September from Savings of 2,000.00, deleted on 20 September with its 300.00 returned and restored on 2 October, leaves Savings at 1,400.00 and Rent's September spent at 600.00, and is marked swept for October. A card at 140.00 deleted and restored reads 140.00. A 30.00 payment from a deleted Goal, removed after the delete, refuses the Goal's restore with `WRITTEN_SINCE`; one whose parts were only filed again does not.

**Not:** Dating the restore's rows on the day of the restore, as Sci-Fin: a return in September undone in October would lower September's spent and raise October's. Restoring over a row written since: the Goal or the Account would count that payment twice, in the row and in its own figure.

**Since:** 2026-11-26 · owner · agreed rewording

### D-R123 · Deleting a category

**Rule:** Deleting a category removes it and the category links to it (R42), and the parts of payments and the ledger rows filed under it keep its id (R124). When a recurring expense or an Account names it, as an expense's category (R67), a finished expense's too, a card's payoff or fees category (R109, R110), a payoff category while the card's charges are logged too, or a Loan's own category (R89), the delete takes another existing category and moves each of them to it in the same step, their past rows staying where they were filed, so no stored field ever names a category that is gone; without one, or with one that does not exist, the delete is refused with a code and nothing changes. Restoring the category moves none of them back.

**Values:**

| Case | Code |
|---|---|
| no such category | `NO_SUCH_CATEGORY` |
| the category given in its place does not exist, or is the one deleted | `REPLACEMENT_UNKNOWN` |
| a recurring expense or an Account files under it and no category is given in its place | `REPLACEMENT_NEEDED` |

**Evidence:** Sci-Fin's spec, section 4.3, and `engine/CategoryEditing.kt` at `2892110`, where no expense had a category; the move of recurring expenses: owner, 2026-11-25; the move of an Account's categories, the finished expenses and logged cards counted, and the codes: builder, 2026-11-25.

**Acceptance:** Deleting Phone while a 60.00 monthly expense files under it is refused with `REPLACEMENT_NEEDED`; with Internet given, the expense files under Internet, and Phone's August spent stays 60.00 with its sweep row under Phone; restoring Phone leaves the expense under Internet. A card whose payoff or fees category is Phone, and a Loan whose own category is Phone, move to Internet in the same step. Deleting a category nothing files under needs no other category.

**Not:** Leaving an expense under a category that is gone: every expense keeps an existing category (R67). Moving past rows: a past month's spent would shift on its own (R67).

**Since:** 2026-11-25 · owner · agreed rewording

### D-R124 · Spending under a deleted category

**Rule:** A deleted category's spending keeps its id. While the category is in the Trash, Spending reads it as a slice of that category marked deleted, a code and not text, so it shows under the category's name; once the category leaves the Trash for good, as Uncategorised (R99); restored, as the category's own slice again. Which slice holds it never changes a month's total, in Spending or in Income vs Expenses (R101).

**Acceptance:** With Fun at 40.00 and Groceries at 30.00 in September 2026, Fun deleted to the Trash gives a slice of Fun marked deleted at 40.00 and a total of 70.00; Fun gone for good gives Uncategorised at 40.00 and 70.00; Fun restored gives its own slice of 40.00 again.

**Not:** Moving the parts to Uncategorised at the delete: a restore could not bring the name back, and the parts would no longer be as written.

**Since:** 2026-11-25 · owner · agreed rewording

### D-R125 · Deleting and restoring a payment

**Rule:** Deleting a payment removes it through the payment path (R54), undoing what its source moved, and keeps it whole in the Trash, its parts, the share Savings paid, its receipt id and lines and its bank keys; its receipt id stays recorded (R41) and its bank keys stay among the import's keys, so neither the file nor the bank row arrives as new (R26, R30), also when a twin of its row is unpaired since (R128). Restoring it moves its source again as recorded, or as below when the source has changed since, unless it is restored as another payment's pair (R127). Its bank keys count as held again (R122) only when more other payments hold them than at its delete, a payment's keys and its own compared by FITID when both have one, else by the synthetic key, so a twin of one day recorded with it does not refuse it.

**Values:**

| Its source at the restore | Restoring it |
|---|---|
| as at the delete | moves it as recorded: the total, and for a Goal the same share from Savings |
| a Goal holding less than its share | takes all the Goal holds and the rest from Savings, after a warning the user can dismiss, the share Savings paid growing by the rest (R54) |
| a Goal or an Account deleted since the payment's delete, in the Trash or for good | moves as a payment from a deleted Goal or charged to a deleted Account does (R54), after a warning the user can dismiss: a `savings` row in the Goal's place, or an `acct:` row that keeps the Account's rows and payments adding up to zero |
| deleted before the payment's delete and still gone | undoes the row the payment's delete wrote (R122), with no warning: Savings moves back as it was before the payment's delete |
| deleted before the payment's delete and restored since | moves it as recorded, as at the delete, and leaves the row the payment's delete wrote, which the row of its earlier restore onto the deleted source cancels |

**Evidence:** Builder, 2026-11-25, from R41 and R54; a source restored since: builder, 2026-11-26, after review of the receipts and pairs pull request.

**Acceptance:** With Savings at 1,000.00, a 42.30 receipt payment from Savings deleted leaves 1,000.00, and restored leaves 957.70 with its parts, lines and keys. A 30.00 payment from a Goal holding 50.00 after it, deleted, leaves the Goal at 80.00; with 70.00 spent from the Goal since, restoring it warns and leaves the Goal at 0.00 and Savings 20.00 lower. Deleted, then its Goal deleted with 80.00 returned, restoring it warns and leaves Savings 30.00 lower with a `savings` row of -30.00 noted `Payment from a deleted goal`. With Savings at 900.00 and a Goal holding 100.00, a 30.00 payment from the Goal deleted, the Goal deleted with its money returned, the payment restored, deleted again, and the Goal restored, leaves Savings at 900.00 and the Goal at 100.00; restoring the payment then leaves Savings at 900.00 and the Goal at 70.00, with no warning. The same steps with a 40.00 payment charged to a card at 100.00 leave the card at 140.00. Of two -4.50 rows of one day recorded in one import, with FITIDs F1 and F2 or with none, one payment deleted is restored as it was; with no FITID, once the row is ticked as `DUPLICATE` and recorded again, its restore is refused with `RECORDED_AGAIN`. A payment in the Trash, edited, changes nothing. Of two such rows with no FITID, one payment deleted and the other paired with a receipt and unpaired, the deleted one's row stays recorded and only one of the two arrives as new on the next import; with FITIDs F1 and F2, F2's deleted, only F1's does.

**Since:** 2026-11-26 · builder · own words

### D-R127 · Restoring a payment as a pair

**Rule:** A payment restored while a payment recorded since would be its pair, as below, carries a notice naming that payment with the warnings the pair itself would carry (R54, R109), and may come back as that pair instead of on its own: the two are paired as R119 says, the side in the Profile completed by the side in the Trash, which does not come back on its own and whose delete's rows stay. A receipt side keeps the parts it held when deleted, less any difference part, the difference worked out again and filed under a category the user picks, by default the category of its own difference part when it held one and that category still exists; a part or line whose category is gone takes a category given in its place, else the restore is refused with `CATEGORY_MISSING`, and a pair R119 refuses is refused with `CANNOT_PAIR`. Without that choice the payment comes back on its own (R125), refused with `RECORDED_AGAIN` while another payment holds its receipt id or its bank keys hold again as R125 counts them (R122); when R119 asks whether a bank side's row is recorded, its twins, the payments holding its keys at its delete, each stand for one other row of its review, and a bank side with a FITID is told apart by it from the rows recorded with none.

**Values:**

| In the Trash | Recorded since, in the Profile | Named | Restored as the pair |
|---|---|---|---|
| a receipt payment holding no bank keys | a payment from a bank row holding no receipt, meeting it as R37 says | the best in R118's order | the bank row's payment takes the receipt's parts, day, source, store, id and lines, with the links kept (R119) |
| a receipt payment holding bank keys | a payment holding one of its bank keys and no receipt, recorded from its row ticked as `DUPLICATE` (R107) | that payment | the same |
| a payment from a bank row holding no receipt | a receipt payment holding no bank keys, meeting it as R37 says, the payment read as its row's day, amount and text | the best in R118's order | the receipt payment takes the row's amount as its total, its keys and the difference part (R119) |

**Evidence:** Builder, 2026-11-25, after review of Task 18 and of the receipts and pairs pull request: without it, the purchase would count twice.

**Acceptance:** With Savings at 1,000.00, a 42.30 receipt of 10 March, Groceries 30.00 and Household 12.30, deleted, then its -42.30 row of 12 March recorded under Groceries, which leaves 957.70: restoring the receipt names that payment; on its own it leaves 915.40 and two payments, and as the pair one payment of 42.30 with Groceries 30.00 and Household 12.30, the receipt's id, lines and day and the row's keys, and Savings at 957.70. With a -42.50 row instead, the pair is refused with `CANNOT_PAIR` until the 0.20 difference is filed under a category. A 4.50 receipt filed under Food and moved by hand to Groceries, deleted and restored as its row's pair, keeps Groceries 4.50. A 4.50 bank payment of 11 March under Food deleted, then a 4.50 receipt of 10 March under Food recorded: restoring the bank payment names the receipt's payment, and as the pair leaves one payment of 4.50 with the row's keys, Savings at 995.50 and Food's March spent at 4.50. A paired 42.30 receipt payment deleted, then its row ticked as `DUPLICATE` and recorded under Groceries: restoring it on its own is refused with `RECORDED_AGAIN`, and as that payment's pair leaves one payment with the receipt and the keys, and Savings at 957.70. Of two -4.50 bank payments of 11 March under Food, with no FITID or with FITIDs F1 and F2, one deleted, then a 4.50 receipt of 10 March under Food recorded: the deleted one comes back as the receipt's pair, leaving two payments, Savings at 991.00 and Food at 9.00. A 42.30 receipt paired with a -42.50 row, its 0.20 under Fun, deleted, then its row recorded again under Groceries: restored as that payment's pair with no category picked, the 0.20 is under Fun again and Savings at 957.50; with Household picked, under Household; with Fun deleted since, it is refused with `CANNOT_PAIR` until one is picked. A -4.50 coffee recorded from a CSV, as a payment or a payoff, then an OFX of F1 and F2, F2 recorded and its payment deleted, then a 4.50 receipt recorded: F2 comes back as the receipt's pair.

**Since:** 2026-11-26 · builder · own words

### D-R126 · A Profile in the Trash

**Rule:** A deleted Profile is kept in the Trash whole: its Savings, settings, import memory, Goals, categories, recurring expenses, Accounts, ledger, shifts, payments and receipt settings. Deleting the Profile that holds the receipt folder frees the folder for another Profile (R112). Restored, it comes back as one more Profile, not opened, and holds the receipt folder again while no other Profile holds one; otherwise it comes back with no folder and keeps its recorded receipt ids, category links and stores' sources, so no recorded receipt is offered again if a folder is chosen there later.

**Evidence:** Sci-Fin's spec, sections 3.6 and 8.3, at `2892110`; the payments, the receipt settings and the folder held by another Profile: builder, 2026-11-25.

**Acceptance:** A Profile holding the receipt folder and a 42.30 receipt payment, deleted, frees the folder; restored while no other Profile holds one, it holds the folder again and equals the Profile as deleted. Restored after Profile B chose a folder, B keeps it, and the restored Profile holds no folder and keeps its recorded receipt id.

**Since:** 2026-11-25 · builder · own words

## 17. Edge cases

### D-R41 · Deleting a receipt payment

**Rule:** A payment recorded from a receipt is deleted through the Trash like any other payment (R125). Its receipt id stays recorded, so the file is not offered again; restoring it brings back the payment with its parts, receipt lines, bank keys and pair, and the bank row of its pair stays recorded throughout (R26). When its bank row was recorded as a payment of its own while it was in the Trash, it may be restored as that payment's pair (R127), so the purchase counts once.

**Evidence:** Not checked: the owner has not ruled on whether a deleted receipt should be offered again; the phase 1 plan keeps it recorded.

**Acceptance:** Deleting a receipt payment leaves its file out of the next scan (R116). A 42.30 receipt split 30.00 Groceries and 12.30 Household and paired with its bank row, deleted and restored, has its two parts, its two lines and its bank keys again, and the row arrives `DUPLICATE` on an import before and after the restore.

**Since:** 2026-11-25 · builder · own words

### D-R42 · Deleting a linked category

**Rule:** Deleting a Tesoriere category removes the category links to it (R123); the next receipt with that Chatelaine category asks again, as for a new one (R32). Restoring the category brings back each removed link whose Chatelaine category has not been linked since.

**Evidence:** The restore of links: the phase 1 plan's provisional choice, 2026-11-24, as the Trash restores what a delete removed (R122).

**Acceptance:** After deleting Pulizia, the next receipt with Chatelaine's Pulizia keeps Confirm off until its line is linked or added. A Chatelaine category Pulizie linked to Pulizia, after Pulizia is deleted and restored, opens in Pulizia again with Confirm on.

**Since:** 2026-11-25 · builder · own words

## 18. Test plan

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

## 19. Rollout

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
- R49 · Savings: A fresh Profile's Savings is unset; a 60.00 movement out of an unset Savings leaves it at -60.00; a direct edit to 0.00 leaves it at 0.00, no longer unset; with Income off and Savings unset, R56's comparison and R57's amounts are skipped.
- R50 · Goals hold money out of Savings: Adding 150.00 to a Goal raises its saved money by 150.00 and lowers Savings by 150.00.
- R51 · The ledger: A 20.00 adjustment on a category is stored as `cat:<id>` with amount 2000 and lowers Savings by 20.00; a 100.00 sweep is `rec:<id>` with amount 10000 and lowers Savings by 100.00; an `acct:` row leaves Savings as it was; a 60.00 payoff of a card from a Goal is `spend:<goalId>` with amount -6000 and leaves Savings as it was.
- R52 · Direct edits log the change: Setting Savings from 150.00 to 1,000.00 logs a `savings` row of +850.00; setting a Goal's saved money from 400.00 to 300.00 logs a `goal:<id>` row of -100.00 and raises Savings by 100.00.
- R53 · Undoing a mistaken entry: Rows of +40.00 and -40.00 on one Goal logged 47 hours apart pair; 49 hours apart they do not; four rows +40.00, -40.00, +40.00, -40.00 logged in that order make two pairs, the first two and the last two. A -60.00 `savings` row filed under Shopping and a +60.00 `savings` correction logged an hour later do not pair; two such rows both filed under Shopping in one month do.
- R21 · Payment: A recorded receipt payment holds every receipt line; a paired payment holds both its `receiptId` and its `bankKeys`; a recurring expense's sweep records no payment. With Savings at 1,000.00 and a 40.00 payment under Fun, a 15.00 refund from Savings under Fun leaves Savings at 975.00 and Fun's spent for the month at 25.00, and logs no ledger row and no income.
- R22 · Source and parts are separate: A EUR 40 receipt charged to a card adds its parts to their categories' spent figures and EUR 40 to the card's balance, and moves nothing in Savings.
- R54 · What a payment's source moves: A 42.30 payment from Savings lowers Savings by 42.30; a 30.00 payment from a Goal holding 50.00 leaves it 20.00 and Savings unchanged, and one of 60.00, after a warning, leaves it 0.00 and Savings 10.00 lower; removed, the Goal holds 50.00 and Savings is as it was; edited to 55.00 it takes 5.00 from Savings, and to 40.00 none, leaving the Goal 10.00; a 40.00 payment charged to a card with a 0.00 balance leaves the balance at 40.00, and a 15.00 refund to it then leaves 25.00; a 15.00 refund into a Goal holding 50.00 leaves it 65.00. With Savings at 1,000.00: 100.00 into a Goal, a 30.00 payment from it, the Goal deleted with its 70.00 returned leaves 970.00; removing the payment then gives 1,000.00 with a `savings` row of +30.00, re-filing its parts leaves 970.00 and logs nothing, and lowering its total to 25.00 gives 975.00 with a row of +5.00; after each, the movements of Savings add up to Savings. A 40.00 payment charged to a card that is deleted since carries a warning, can be re-filed into two parts or removed with Savings unchanged, and a new payment charged to that card cannot be saved. A 40.00 payment charged to a card at 0.00 that is deleted since, removed, logs an `acct:` row of +40.00 for that card; moved to a second card instead, it raises that card to 40.00 and logs the same row; after each, Savings vs Owed's point for the month equals its figure of the day (R100).
- R23 · Parts add up: A EUR 42.30 payment with parts of EUR 30.00 and EUR 12.00 shows EUR 0.30 left and cannot be saved; with a third part of EUR 0.30 it can. A EUR -15.00 refund with parts of EUR -20.00 and EUR 5.00 cannot be saved, and with one part of EUR -15.00 it can; a total of EUR 0.00 cannot be saved. A EUR 42.10 pair with item parts of EUR 30.00 and EUR 12.30 and a difference part of EUR -0.20 can be saved, and so can an edit of it that moves its item parts; the same EUR -0.20 as an item part, two difference parts, or a difference part entered by hand, cannot.
- R24 · Any payment can be split: A bank row and a hand-logged payment can each be saved with two parts in two categories.
- R25 · Spent is derived: Re-filing a payment's parts changes the spent figures of the categories involved, for the payment's month, and writes no correction row. A 60.00 sweep filed under Phone and dated in March adds 60.00 to Phone's spent for March, and no payment exists for it.
- R55 · Goal calculator: A 1,200.00 goal with 400.00 saved and a target date six calendar months ahead needs 133.34 a month; the same goal at 150.00 a month takes 6 months.
- R56 · Where a goal fits: With Income off, Savings 1,000.00, recurring expenses of 300.00 and another goal at 200.00 a month, a goal at 133.34 a month adds up to 633.34 against 1,000.00 and fits; if the other goal has reached its target, it adds nothing.
- R57 · Gentler monthly amounts: With Income off and Savings 2,400.00, a goal at 250.00 a month is offered 120.00, 150.00 and 200.00; one at 200.00 a month is offered nothing.
- R58 · Deleting a goal: Deleting a Goal holding 300.00 with the money returned logs a `goal:<id>` row of -300.00 and raises Savings by 300.00; without, Savings is unchanged; both remove the Goal.
- R59 · Plan against spent: A category planned at 300.00 with payments of 320.00 in March reads planned 300.00 and spent 320.00 for March.
- R75 · Income switch: A fresh Profile has Income off, salaried, with automatic deposit off. Switched on on 14 September 2026, Income has that day as its switch-on day and its payday. A semimonthly income of 1,450.00 on the 15th and the 30th with automatic deposit on, switched off, has an amount of 0.00, automatic deposit off, and no payday, switch-on day, last logged day or last deposit. An hourly income with two shifts logged, switched off, has no shift and is salaried at a rate of 0.00.
- R76 · Paydays: A biweekly income first paid 11 September 2026 is next paid 11 September read that day, 25 September read on 12 September and 9 October read on 26 September; a weekly one is next paid 18 September read on 12 September. A monthly income paid on 31 January 2026 is next paid 28 February read on 1 February, then 31 March. A semimonthly income on the 15th and the 30th is next paid 15 September read on 2 September, 30 September read on 16 September and 15 October read on 1 October; one on the 15th and the 31st is paid 28 February 2026. A semimonthly income changed to monthly has no payday, no second payday and no last deposit; set to the cadence it has, it is unchanged.
- R77 · Income received this month: With Income switched on on 5 September 2026 and read on 14 September: 1,450.00 of income dated 11 September counts; 1,450.00 of income dated 28 August, 300.00 of income dated 2 September and 50.00 into Savings not logged as income do not; the figure is 1,450.00.
- R78 · No income logged: A biweekly income last logged 20 August 2026 gives 25 on 14 September, none on 3 September and 15 on 4 September. A weekly income switched on on 10 September 2026 with nothing logged gives none on 14 September and 8 on 18 September. A monthly income on the 10th last logged 10 August 2026 gives none on 9 September and 31 on 10 September. A semimonthly income on the 15th and the 30th last logged 1 September 2026 gives none on 14 September and 15 on 16 September. With automatic deposit on, or hourly, it gives none.
- R79 · Salaried or hourly: A salaried income with automatic deposit on, changed to hourly on 2 November 2026, has automatic deposit off and asks about no payday on or before 2 November. Changed to hourly on its payday of 13 November 2026, it does not ask about 13 November and asks about 27 November. An hourly income with automatic deposit set and 1,000.00 per paycheck deposits nothing by 31 December 2026 and gives no count.
- R80 · Logged hours: A 6-hour shift at 20.00 logged on 3 November 2026 leaves Savings, the ledger and the income received as they were. Shifts of 6 and 8.5 hours at 20.00 come to 290.00 before deductions. A shift of 1.5 hours at 13.33 comes to 20.00.
- R81 · Hourly payday: A biweekly hourly income at 20.00, switched on on 1 November 2026 and paid from 13 November, with shifts of 6, 8.5 and 4 hours dated 3, 10 and 14 November, asks nothing on 12 November and on 13 November asks about 13 November with 14.5 hours and 290.00. Confirmed with 232.00, Savings goes from 1,000.00 to 1,232.00 by one income row dated 13 November with 14.5 hours at 20.00, the 4-hour shift stays, nothing is asked on 20 November and 27 November is asked on that day. Answered with nothing this time, a 6-hour shift waits for 27 November; with no hours logged, 13 November is still asked.
- R82 · Hourly estimate: 10 hours at 20.00 are estimated at 200.00 before deductions. After four paydays of 10 hours at 20.00 at which 160.00, 160.00, 100.00 and 200.00 arrived, 10 more hours at 20.00 are estimated at 153.33, from the last three.
- R83 · Leaving hourly: Shifts of 6 and 4 hours settled on 2 November 2026 with 170.00 leave no shift and one income row of 170.00 dated 2 November with 10 hours; discarded, they leave no shift and no row, and the income can then be made salaried. Changing the type with a shift still logged is refused.
- R84 · A salaried payday logged by hand: A 1,450.00 monthly income on the 10th, switched on on 1 November 2026, offers nothing on 9 November, 10 November on 10 November, nothing on 12 November, and 10 December on 11 December. With 1,450.00 logged on 10 November, with 10 November already offered, or with automatic deposit on, nothing is offered on 11 November.
- R60 · Recurring expenses: A weekly expense first due 2 January 2026 falls due 5 times in January and 4 times in February; a biweekly one falls due 3 times in January (2, 16 and 30) and twice in February. A monthly interval expense first due 1 January 2026 falls due on 1 and 31 January, none in February, then 2 March. A weekly expense saved with fixed-date mode keeps no date mode. An expense with no creation day, as from a Sci-Fin backup, first due 5 March 2026, reads as created on 5 March.
- R61 · The chain of due dates: A monthly expense due 10 September 2026 is still due 10 September on that day, due 10 October on 11 September, and due 10 January 2027 when read on 25 December 2026. A fixed-date rent on the 31st falls due on 28 February 2026 and then 31 March 2026; a monthly interval expense due 31 January falls due next on 2 March. An expense entered on 14 September 2026 with a first due date of 5 March 2026 keeps 5 March as its first due date and is next due 5 October. A monthly expense on the 20th with an end date of 20 September 2026, read on 2 October, stays due 20 September.
- R62 · Apportioned share: A 1,200.00 yearly expense due 15 July 2027 has a share of 100.00 from a cycle that started 15 July 2026, and of 120.00 when created on 14 September 2026. A 1,000.00 yearly expense due 1 April 2026 has a share of 333.34 from 1 January 2026; a 1,200.00 one due 25 June 2026 has a share of 1,200.00 from 20 June. An expense holding 300.00 whose due date of 15 July 2026 has passed holds 100.00 for its next cycle. Renaming an expense keeps its share of 100.00; changing its amount to 2,400.00 on 14 September 2026 gives 240.00. A 600.00 yearly expense created on 15 July 2026 and due 15 July 2027 counts 50.00 in each month from August 2026 to July 2027; the bank's -600.00 on 15 July 2027 arrives `RECURRING`, and July 2027 still counts 50.00.
- R63 · Sweeps: A semimonthly 300.00 expense on the 15th and the 31st falls due on 15 and 28 February 2026. A monthly 100.00 expense on the 20th with an end date of 20 September 2026 sweeps in September and not in October; with an end date of 19 September it sweeps nothing in September. A yearly expense holding a 50.00 share sweeps 50.00 for September 2026; a bimonthly 90.00 expense with no share held sweeps 45.00. Two sweeps on 1 August and 1 September 2026 leave two rows dated to those days.
- R64 · Creating a recurring expense: On 14 September 2026 with Savings at 1,000.00: a 100.00 monthly expense first due 20 September leaves 900.00 and one row dated 20 September; first due 10 September, it leaves 1,000.00; a biweekly one first due 10 September leaves 1,000.00 though 24 September is ahead; a yearly 1,200.00 one due 15 July 2027 leaves 1,000.00 and a share of 120.00. On 25 September a monthly one first due 5 October leaves 1,000.00. Each is marked swept for September 2026 and keeps the day it was created; one entered on 14 September with a first due date of 5 March keeps 14 September.
- R65 · Editing a recurring expense: On 10 September 2026, a 1,000.00 monthly rent due the 20th, swept from Savings of 2,000.00 and edited to 1,200.00, offers 200.00 dated 20 September; accepted, Savings is 800.00. The same edit before the month has swept offers nothing. A swept 260.00 Loan payment due the 20th edited to 300.00 and accepted leaves the Loan at 3,960.00 from 4,000.00, with a -40.00 `acct:` row dated 20 September. A fixed-date rent on the 1st moved to the 15th on 10 September falls due on 15 November.
- R66 · Deleting a recurring expense: A semimonthly 300.00 expense due the 15th and 30th, created on 1 September 2026 from Savings of 2,000.00, offers 300.00 when deleted on 20 September; accepted, Savings is 1,700.00 and three rows remain. A rent swept at 1,000.00 and edited to 1,200.00 with the difference declined offers 1,000.00. An expense due 1 September offers its 100.00 on 1 September and nothing on 2 September. A yearly expense that swept 100.00 on 1 August and on 1 September 2026 offers 200.00. A yearly expense whose 100.00 share swept on 1 August under Phone and, after a move to Internet, on 1 September under Internet, deleted on 14 September with the return accepted, writes -100.00 under Phone and -100.00 under Internet. A yearly insurance of 1,200.00 under Insurance whose 100.00 share swept on 1 August and 1 September 2026, deleted on 14 September with the return accepted, leaves Insurance's September spent at -100.00 and its August spent at 100.00. Deleting an expense linked to a Loan of 5,000.00 removes both and logs a -5,000.00 `acct:` row.
- R67 · Every recurring expense has a category: An expense with no category, or naming a category that does not exist, cannot be saved. A 60.00 monthly expense under Phone adds 60.00 to Phone's spent in each month it sweeps; a yearly 600.00 expense sweeps a 50.00 share into its category each month; a return of 300.00 on 20 September 2026 lowers its category's spent for September by 300.00, and a yearly insurance's 200.00 of unused shares returned on 14 September leaves Insurance's September spent at -100.00 after its 100.00 share. After an expense moves from Phone to Internet, Phone's spent for the past months is unchanged and the next sweep counts under Internet; a 300.00 return of sweeps filed under Phone, written after the move, lowers Phone by 300.00 and leaves Internet as it was.
- R68 · Recurring threshold note: With Income off and Savings at 12,000.00, a scheduled 900.00 shows no note and 1,100.00 shows one holding 1,100.00 and 12,000.00; with Savings at -5.00 the note is marked as having no base; with Savings unset, or nothing scheduled, there is none. With Income on and nothing received, 900.00 shows a note with no base; after 1,450.00 received it holds a base of 1,450.00; after 2,900.00 there is none.
- R69 · Reminder dates: A rent on the 31st with a 3-day lead reminds on 25 February 2026 and then on 28 March 2026. With the import reminder on day 1, read on 14 September 2026, it falls on 1 October. The New Month reminder read at 08:30 on 1 October 2026 is 1 October at 09:00; read at 09:00 that day, 1 November at 09:00; read at 23:00 on 31 December 2026, 1 January 2027 at 09:00. An hourly income paid every 14 days from 13 November 2026, read at 12:00 on 2 November, reminds on 13 November; read at 09:00 on 13 November, on 27 November. A salaried income with automatic deposit on, an income with the payday reminder off, and income switched off have no payday reminder.
- R70 · The month cycle: Opened on 1 September 2026 with Savings at 5,000.00, a 950.00 rent due the 1st and a 45.00 phone bill due the 10th, both swept for August, leave 4,005.00 and two rows, dated 1 and 10 September; opened again that day, nothing changes. Opened on 14 November 2026 instead, the rent writes rows dated 1 September, 1 October and 1 November and is next due 1 December. A category with 120.00 spent in August reads 0.00 for September after the open, and the categories are unchanged. A Profile with no recurring expense, last processed in June 2026 and opened on 14 September 2026, processes 3 months and is marked processed for September. A Profile created on 14 September 2026 is marked processed for September and processes no month that day. A 1,000.00 yearly expense due 1 April 2026 with a share of 333.34, swept for February and opened on 10 May 2026, sweeps 333.34 for March and for April, then 83.34 for May, and is next due 1 April 2027.
- R71 · Automatic deposits on the same open: Opened on 2 October 2026 with Savings at 5,000.00, a 950.00 rent swept for September, and a 1,450.00 biweekly income first paid 11 September 2026 with automatic deposit on: one month is processed, two deposits are dated 11 and 25 September, and Savings is 6,950.00. A 1,450.00 monthly income paid the 5th, last deposited 5 June 2026, with automatic deposit switched back on on 14 September 2026, deposits nothing that day and one row dated 5 October when opened on 5 October; switched on for the first time on 14 September, a 100.00 monthly income paid from 5 August deposits rows dated 5 August and 5 September.
- R72 · End of a term: A 260.00 payment due the 15th, ending 15 August 2026 and linked to a fixed-rate Loan of 120.00, finishes on 1 September 2026 when opened that day: the Loan is 0.00 after one -120.00 `acct:` row, and the notice holds its id, its name and the code `loanPaidOff`. On an interest-only Loan of 5,000.00 the same payment leaves 5,000.00 with no closing row and the code `interestOnlyEnded`. A 40.00 gym fee due the 15th, swept for August and ending 15 October 2026, opened on 20 November, writes rows dated 15 September and 15 October, leaves Savings 80.00 lower and ends with `ended`. A payment ending 15 October 2026 is not finished when opened on 20 October; opened on 2 November, it finishes on 1 November.
- R73 · A Loan that follows its payment: A Loan of 6,000.00 at 6 % with a 260.00 payment due the 15th, opened on 1 September 2026, falls to 5,770.00 after 30.00 of interest, with a -230.00 row dated 15 September; Savings falls by the 260.00 sweep only. A Loan of 200.00 at 0 % falls to 0.00 and its payment finishes though it ends in 2030, with a notice holding its id, its name and `loanPaidOff`. A variable-rate Loan of 100.00 cleared by its final 260.00 payment ends with `loanPaidOff`.
- R74 · Catch-up summary: A 950.00 rent swept for August, opened on 14 November 2026, keeps a summary of 3 months and 2,850.00 swept; opened again that day it still reads 3 months; dismissed, there is none. Opened on 14 October 2026 it reads 2 months; opened again on 3 November, still 2; then on 3 January 2027, 4. Opened on 1 September 2026 alone, it keeps none. A finished Car loan payment deleted before the summary is read is still named Car loan payment on it; a notice kept with no name, as summaries kept before names were, or with an empty name, reads with its id and code and no name.
- R85 · Accounts: A fresh Profile has Accounts off. A card at 100.00 with a 40.00 payment charged to it reads 140.00, and Savings is unchanged.
- R86 · Creating and editing an Account: A card created at 640.00 on 14 September 2026 logs one `acct:` row of +640.00 noted `Opening balance` and leaves Savings at 1,000.00; edited to 600.00, it logs a -40.00 row noted `Correction`. A Loan saved to follow its payment with no rate is saved not following, with 14 September 2026 as its start day. A Loan with no category of its own is saved; one naming a category that does not exist is not. An Account with a blank name is not saved.
- R87 · An Account and its linked payment: A Loan linked to a 200.00 payment and linked again to a 250.00 one leaves the 200.00 payment linked to nothing, and deleting it keeps the Loan. A Loan of 4,000.00 whose 260.00 payment, due the 20th, swept on creation on 10 September 2026 from Savings of 5,000.00, deleted with the return accepted, leaves no Account, no expense and Savings at 5,000.00. A Loan of 9,800.00 deleted removes its payment too and logs a -9,800.00 row noted `Account closed`. With 40.00 charged to a card under Food in September 2026 and the card's 40.00 payment, filed under Food, sweeping that month, Food's spent is 40.00; a Loan's 260.00 payment under Car adds 260.00 to Car in September 2026, and after a 9,800.00 car is charged to that Loan in October, Car's October spent is 9,800.00 with the payment's October sweep a transfer, and its September stays 260.00.
- R88 · Deleting an Account with payments charged to it: A card opened at 100.00 with a 40.00 payment charged to it under Groceries in September 2026, deleted, logs a -140.00 closing row; the payment stays, Groceries' spent for September is 40.00, and Savings is unchanged; the payment removed afterwards logs a +40.00 `acct:` row, and the card's rows add up to zero.
- R89 · Quick-add on an Account: A card at 100.00 paid 60.00 from Savings of 1,000.00 reads 40.00 with Savings at 940.00 and a `savings` row of -60.00 noted `Payment to Card`; from nowhere, Savings stays 1,000.00. Paid 60.00 from a Goal holding 80.00 it reads 40.00, the Goal 20.00 and Savings 1,000.00, with a `spend:` row of -60.00 noted `Payment to Card`; 90.00 from that Goal is refused. A Loan of 4,000.00 increased by 500.00 reads 4,500.00 with Savings unchanged. An increase on a card with no fees category and none picked is refused. A Loan linked to its 260.00 payment under Car, with nothing charged to it, paid 200.00 by hand from Savings adds 200.00 to Car; after a 9,800.00 car is charged to it, 100.00 paid from a Goal adds nothing; with the car removed again, Car keeps the 200.00 and the 100.00 still counts nowhere. A Loan linked to nothing, paid from Savings with no category picked and none of its own, is refused; with Car as its own category, 200.00 counts in Car, and with Groceries picked for the payment, in Groceries; with a picked category that does not exist it is refused, not counted in Car. A Loan with Car as its own category and a linked payment under Bills counts a payment by hand in Car, and the payment's sweeps in Bills. Savings is the default for an Account linked to nothing and nowhere for a linked one.
- R111 · Undoing an Account quick-add: A card at 100.00 paid 60.00 from Savings of 1,000.00 and undone reads 100.00 with Savings at 1,000.00 and neither row left; paid 60.00 from a Goal holding 80.00 and undone, the Goal holds 80.00; a card whose charges are not logged, paid 60.00 under Shopping and undone, leaves Shopping's spent as it was; a 12.50 fee filed under Fees, undone, leaves the card at 300.00 and Fees as it was. A quick-add on a card deleted since, or from a Goal deleted since, is refused with a code and changes nothing; a row with no group and a sweep are not undone. A statement payoff of -60.00, undone, arrives on the next import of the same statement `DUPLICATE` and unticked, and ticked and filed again it is recorded. On a Profile whose Savings is unset, a card paid 60.00 from Savings and undone leaves Savings at 0.00, not unset.
- R90 · Loan calculator: 12,000.00 at 6.5 % over 48 months, fixed: 284.58 a month, 13,659.84 repaid, 1,659.84 of interest, nothing left. Interest-only: 65.00 a month, 3,120.00 repaid, 12,000.00 left. At 0 %: 250.00 a month.
- R91 · Taking the loan or saving for it: 9,800.00 at 5.9 % over 48 months, taken on 14 September 2026 from Savings of 1,000.00, creates a Loan of 9,800.00 with one +9,800.00 `Opening balance` row and a payment of 229.70 first due 14 October 2026, ending 14 September 2030, marked swept for September 2026, the Loan taking the payment's category as its own; Savings stays 1,000.00. With no category, or one that does not exist, nothing is created. Saving for 12,000.00 over 48 months on 14 September 2026 creates a goal of 12,000.00 by 14 September 2030 named `Savings goal`.
- R92 · Card payoff outlook: 640.00 at 21.9 % paid 50.00 a month clears in 15 months with 95.63 of interest; at 11.00 a month it is never cleared; at 0 % it clears in 13 months; a balance of 0.00 clears in 0 months. The minimum on 640.00 at 21.9 % starts at 25.00 and clears in 35 months with 230.40 of interest; on 3,000.00 at 0 % it starts at 60.00; 1,000.00 at 5 % a month is never cleared.
- R93 · Committing a card's fixed payment: Committing 50.00 on 14 September 2026 for a card named Credit Card creates `Credit Card payment` of 50.00, first due 14 October 2026, with no end date, linked both ways; the card does not follow it, and its 50.00 sweeps add nothing to the chosen category's spent. A card that linked an earlier expense releases it.
- R94 · Changing an Account's type: A Loan at 5 % following its payment, changed to a card, has no rate, principal, term, rate type, start day or category of its own and does not follow. A card at 20 % changed to a Loan has no rate and cannot follow, and one whose charges were not logged under Shopping has the switch off, no payoff category and no fees category. A Loan of 4,000.00 whose 260.00 payment, due the 20th, swept on creation on 10 September 2026 from Savings of 5,000.00, changed to a card with the return accepted, stays as a card linked to nothing, its payment is gone, and Savings is 5,000.00; changed to a card whose balance is raised to 4,100.00 with no choice of interest or fees or a correction, nothing changes, and with the correction chosen the change goes through at 4,100.00.
- R109 · A card whose charges are not logged: With the switch on and Shopping as the payoff category, 60.00 paid off the card from Savings in September 2026 adds 60.00 to Shopping's spent for September and to its Spending, and 60.00 paid from a Goal holding 80.00 adds 60.00 more; its 50.00 fixed payment's sweep adds 50.00 and its return takes it off again, and a statement payoff of 40.00 adds 40.00. With the switch turned off in October after the 60.00 from Savings, Shopping's September stays 60.00 and a 30.00 payoff in October adds nothing; a card with the switch off whose September payoff counted nothing, turned on in October, leaves September as it was. Deleting the card leaves September as it was. The switch on with no payoff category, or one that does not exist, is not saved. A payment charged to the card carries the warning, and so does a statement row of money in filed as a refund to it (R107).
- R110 · Card interest and fees: A card at 300.00 with Fees as its fees category, increased by 12.50 on 30 September 2026, reads 312.50, adds 12.50 to Fees' September spent and to Spending, and leaves Savings as it was; with Bank charges picked for it, it counts there. Edited from 312.50 to 320.00 as interest or fees it adds 7.50 to Fees; as a correction it adds nothing, also on a card with no fees category; with no choice made it is not saved, except on a card whose charges are not logged, which saves it under no category with no choice; edited down to 310.00 it adds nothing; a card created at 640.00 adds nothing. A card with no fees category refuses an increase and an edit as interest or fees unless a category is picked; one whose charges are not logged takes them under none. A card with Fees as its fees category refuses an increase or an edit as interest or fees with a picked category that does not exist. Paying 320.00 off the card from Savings afterwards adds nothing to Fees or to Spending.
- R95 · Savings movements in a report: In a month with a 100.00 sweep, a 42.30 payment from Savings, a 30.00 payment from a Goal and a 40.00 payment charged to a card, 142.30 went out of Savings; a 60.00 payment from a Goal holding 50.00 takes 10.00 more out. A 30.00 refund to Savings is 30.00 in: read on 14 September 2026 after 100.00 in on 1 June and a 100.00 payment from Savings on 10 August, a 30.00 refund to Savings on 1 September gives an Age of Money of 13 days (R96), and a 460.00 refund on 1 August leaves Coverage's outflow as it was (R98).
- R96 · Age of Money: Read on 14 September 2026: 100.00 in on 1 June, 100.00 in on 1 August and 50.00 out on 10 August reads 105 days; with 60.00 more out on 1 September it reads 44. 100.00 in on 1 June and 350.00 out on 10 June reads 0; with 1,000.00 in on 1 July and 400.00 out on 1 August it reads 75, the July parcel holding 350.00; with 351.00 more out on 1 September it reads 0. A 100.00 payment from Savings on 10 August in place of the 50.00 empties the June parcel and reads 44; the same payment from a Goal empties nothing and reads 105. An `acct:` row opens no parcel.
- R97 · Age of Money over the months: With 100.00 in on 20 August 2026, a two-month trend read on 14 September is 11 days for August and 25 for September. With 300.00 in on 10 July, 400.00 out on 5 August and 200.00 in on 1 September, a six-month trend read on 14 September has points for July, August and September only, and August's is 0.
- R98 · Coverage: Read on 14 September 2026, the months are 1 June to 31 August, 92 days. Savings of 920.00 with 920.00 out on 1 July and a first row on 1 May covers 92 days; at -5.00 it covers 0; with the first row on 1 July there is no figure. A 920.00 payment from Savings in place of the row gives 92; charged to a card, there is no figure. Savings of 25.00 against 920.00 out covers 2 days and 35.00 covers 4.
- R99 · Spending: A 42.30 payment split 30.00 Groceries and 12.30 Household counts 30.00 in Groceries and 12.30 in Household. In a month with 100.00 and 50.00 into two Goals, a 30.00 adjustment on Food, 1,450.00 of income, a -20.00 `savings` row, 60.00 paid off a card from Savings and a 99.00 `acct:` row, the one slice is Food 30.00, totalling 30.00. A 60.00 sweep filed under Phone counts in Phone; a 40.00 payment by card and a 25.00 one from a Goal, both under Food, count 65.00 in Food. 40.00 charged to a card under Food and the card's 40.00 payment swept under Food give one slice, Food 40.00, totalling 40.00. Food at 30.00 and -50.00 in one month is a plain list. A 40.00 payment under Food and a 15.00 refund under Food in the month give one slice, Food 25.00. 60.00 paid off a card whose charges are not logged, from Savings, gives one slice of 60.00 in its payoff category, and a 12.50 fee on a card filed under Fees one slice of 12.50 in Fees. A 600.00 yearly expense under Insurance gives one slice, Insurance 50.00, in each month of its cycle, the month its bill is paid included.
- R100 · Savings vs Owed: Read on 14 September 2026, with 1,000.00 into Savings on 10 July, a card opened at 640.00 on 1 September and a 40.00 payment charged to it on 5 September, it reads 320.00; a three-month trend reads 1,000.00 for July and August and 320.00 for September.
- R101 · Income vs Expenses: With Income on, 1,450.00 of income, 200.00 into a Goal and a 30.00 payment from Savings in September 2026 read 1,450.00 in and 30.00 out; an 80.00 payment from the Goal makes 110.00 out; a 40.00 payment charged to a card makes 150.00; paying 60.00 off the card from Savings leaves 150.00, and a 15.00 refund to Savings makes 135.00 out with 1,450.00 still in. A month with a 100.00 sweep and nothing else reads 100.00 out, and one with 40.00 charged to a card and the card's 40.00 payment swept, and nothing else, reads 40.00 out.
- R102 · Overtime calculator: 22.00 an hour for 10 hours is 220.00 a month and 2,640.00 a year; for 0.5 hours, 11.00 and 132.00; for -3 hours, 0.00 and 0.00. A month's pay is 3,200.00 from 3,200.00 paid monthly or 1,600.00 paid twice a month, and 2,600.00 from 1,200.00 every two weeks or 600.00 weekly. Salaried at 3,200.00 a month over 160 hours pre-fills 20.00; over 0 hours, nothing; with Income off or a pay of 0.00, nothing. Hourly at 20.00 pre-fills 20.00 and has no month's pay.
- R26 · Statement import: Importing the same OFX statement twice records nothing the second time; importing a CSV export of the same period also records nothing; a payee filed under a category once is pre-filled on the next statement. A row of -12.40 on 2026-03-03 described `POS MERCATO VERDE 0412` has the synthetic key `syn:2026-03-03|-12.40|pos mercato verde 0412`, and so does the same row with two spaces, a tab or a non-breaking space between its words, or a space before them; after the OFX row is recorded, that CSV row arrives as already recorded. With a -4.50 row F1 recorded, a row F2 of the same day, amount and text arrives as new; of two such rows with no FITID, one recorded, one arrives as recorded and one as new; a row of money into Savings, once recorded, arrives as recorded.
- R103 · Statement formats: An OFX block posted `20260303120000` of `-12.40` reads as 2026-03-03 and -12.40; a block with a memo and no name takes the memo; a block of `0.00` gives no row. A semicolon CSV with the quoted description `POS BAR` and `LA PIAZZETTA` on two lines gives one row described `POS BAR LA PIAZZETTA`.
- R104 · Statement amounts: Each text in the table reads as its figure in cents; the parser holds no floating-point value.
- R105 · CSV columns by header: Headers `Booking date,Value date,Description,Amount` with `01/09/2026,02/09/2026,POS CAFFE DEL MOLO,-3.50` give one row of -3.50 on 2026-09-01, and headers `Booking date,Value date,Description,Debit,Credit` with the same days, a debit of `3.50` and an empty credit give the same row. Headers `Value date,Booking date,Description,Amount` with `02/09/2026,01/09/2026` give 2026-09-01 too. Headers `Data contabile;Data valuta;Causale;Descrizione operazione;Dare;Avere` with `03/03/2026;05/03/2026;PAGAMENTO POS;MERCATO VERDE;1.234,56;` give one row of -1,234.56 on 2026-03-03 described `MERCATO VERDE`, and an Avere of `1.450,00` gives 1,450.00. Headers `Data;Descrizione;Addebiti;Accrediti` with `03/03/2026;MERCATO VERDE;12,40;` give -12.40. Headers `Date,Credit card number,Description,Debit,Credit` with a debit of `800.00` and a credit of `12.00` give -800.00 and 12.00. Headers `Buchungsdatum;Verwendungszweck;Umsatzbetrag` with `01.09.2026;MIETE WOHNUNG;-800,00` give one row of -800.00. Headers `a;b;c` give no rows.
- R106 · Statement dates: Each text in the table reads as its day; `05/03/2026` in a CSV statement is 5 March.
- R107 · The import review: With Savings at 1,000.00, a recurring expense named Rent and the March OFX statement: `AFFITTO CASA RENT` -950.00 arrives `RECURRING` and unticked, `STIPENDIO MARZO` 1,450.00 `INCOMING`; filing -12.40 under Groceries, splitting -42.30 into Pharmacy 30.00 and Household 12.30, and paying off a card at 300.00 with -60.00 names 4 rows, 114.70 out and 1,450.00 in, and leaves Savings at 2,335.30 and the card at 240.00. The same 1,450.00 sent on into a Goal holding 110.00 leaves the Goal at 1,560.00 and Savings at 885.30, with a `savings` row of +1,450.00 and a `goal:` row of +1,450.00 dated 7 March; a Goal deleted before Confirm leaves it in Savings. Rows of -14.00 on 7 March and -100.00 on 8 March, both from a Goal holding 110.00, give the later one a warning, and confirmed leave the Goal at 0.00 and Savings 4.00 lower; the -100.00 alone leaves the Goal at 10.00. A -60.00 row paying off a card at 300.00 that is linked to its fixed payment (R93) leaves the card at 240.00 and Savings unchanged by it, and with Savings picked for the row, 60.00 lower. The -60.00 payoff of the card at 300.00 taken from a Goal holding 110.00 leaves the card at 240.00, the Goal at 50.00 and Savings unchanged by it; from a Goal holding 50.00, Confirm stays off. With a 29.90 monthly expense named Telefono due the 10th, `TIM S.P.A.` -29.90 on 12 March arrives `RECURRING` and unticked, and so does -29.90 on 10 March after the swept expense was edited to 34.90 with the difference declined; -29.90 on 20 March arrives `READY`, and so does a card's 60.00 fixed payment's bank row on its due date, and `ORBITA CARD PAYMENT 0412`, whose words hold the name of that fixed payment, Orbita card payment. With Telefono first due 10 March 2026, `TELEFONO ABBONAMENTO` -12.00 on 7 March and `TIM S.P.A.` -29.90 on 10 February arrive `READY`, and the first on 10 March arrives `RECURRING`; with Telefono first due 5 March but created on 14 March, `TELEFONO ABBONAMENTO` on 10 March and `TIM S.P.A.` -29.90 on 5 March arrive `READY`, and the first on 14 March `RECURRING`. Telefono created on 1 March 2026, first due 10 March, swept that day and on 15 March moved to fall due on 12 April, with the difference declined, still finds on 16 March `TELEFONO ABBONAMENTO` -29.90 on 10 March and `TIM S.P.A.` -29.90 on 11 March, both `RECURRING`. A yearly 1,200.00 Assicurazione auto created on 14 September 2026, first due 15 July 2027, with shares of 120.00 swept on 1 October and 1 November, leaves `ASSICURAZIONE AUTO RCA` -45.00 on 12 November and `BONIFICO` -120.00 on 2 October `READY`. A Loan with no category of its own, a -500.00 row of 1 March charged to it and a -100.00 payoff of it from Savings on 20 March, which the review lists first: Confirm is on, the charge is recorded first, and the payoff counts as a transfer, in no category (R89). 1,450.00 sent into a Goal holding 110.00 on 7 March pays a -200.00 row from that Goal on 8 March with no warning and a -300.00 payoff of the card from it on 9 March, leaving the Goal at 1,060.00. `RIMBORSO CALZATURE ORSA` +25.00 on 20 March filed as a refund under Household records a payment of -25.00 with its keys, leaves Savings 25.00 higher and Household's March spent 25.00 lower, and arrives `DUPLICATE` on the next import; filed as a refund to the card at 300.00, it leaves the card at 275.00. Two reviews open on one +100.00 row, or one -50.00 payoff of the card, both confirmed, record it once.
- R108 · Payee rules: `POS MERCATO VERDE 0412` filed under Groceries in March arrives in April's review with one part of 18.75 in Groceries, from Savings; `FARMACIA SAN LORENZO` split into Pharmacy and Household teaches no rule; the payee of `POS*MERCATO-VERDE  0412` is `pos mercato verde 0412`. `POS 03/03 MERCATO VERDE 0412` and `PAGAMENTO POS 12/03/2026 ORE 10:22 MERCATO VERDE CARTA ****1234` both have the rule name `mercato verde`, and the first filed under Groceries pre-fills the second; `POS 0412` keeps the rule name `pos 0412`. A rule kept under the payee `pos mercato verde 0412` still pre-fills `POS MERCATO VERDE 0412`.
- R27 · The receipt contract: The receipt parser's tests cite Chatelaine's rule 123 field by field; no Tesoriere code opens the receipt folder for writing.
- R28 · Receipt folder: With the folder set in Profile A, receipts are reviewed and recorded only in Profile A; Profile B offers no receipt folder while A has one.
- R112 · The receipt Profile: With the folder chosen in Profile A, Profile B is offered no folder and choosing one there is refused; a receipt opened for review in B cannot be confirmed, and the same receipt in A records one payment in A and none in B.
- R29 · Picking up receipts: With the default, opening the app with three new files offers three receipts and runs no background work; with the daily check on, three new files give one notification that day; no receipt is recorded before its Confirm.
- R30 · Receipt files Tesoriere cannot take: One file per row, put in the receipt folder, is listed with its reason (or, for a recorded id, not at all) and changes no figure.
- R116 · Scanning the receipt folder: Three new files give three receipts ready, a count of 3, and the Profile unchanged; a file whose receipt id is recorded is not listed; a format-2 file is refused naming 2; a USD file in a EUR Profile is refused naming USD and EUR; a file that is not JSON is refused as unreadable; two files holding one receipt id give one receipt; a file named `chatelaine-receipt-rc-9.part` is not read. Each leaves the Profile unchanged.
- R117 · Reading a receipt file: The example file of Chatelaine's spec, rule 123, reads as a receipt of 10.99 at Supermarket A on 2026-11-24 in EUR with three lines, the batteries with no pack. The same file with `format` 2 and a key of its own gives newer format 2, and so it does with no `lines`; with `format` 99999999999 it gives newer format 2,147,483,647. With any one key of rule 123 other than `pack` taken out, with `totalPaid` 11.00, 0.00 or `"1099"`, a `quantity` of 1.5 or 0, a line of -1.00 among lines that add up to the total, or a blank `store`, it is unreadable; so is a file of 3,000 nested arrays, and one over 1 MiB. With a key `note` added it reads as before. A `pack` of `""` reads as no pack, and `currency` `eur` reads as `EUR`. A format-2 file over 1 MiB is unreadable, not a newer format. With `totalPaid` 1,000,000,000.00 and lines adding up to it, it reads; with 1,000,000,000.01, it is unreadable.
- R31 · Receipt review: A receipt of five lines in two categories records one payment of the receipt's total with two parts; moving one line to a third category gives three parts and changes no category link; closing the review leaves no payment.
- R32 · Category links: A receipt with category Pulizia links to Tesoriere's pulizia; a later receipt with the same id named Pulizie stays linked and shows the names-differ note; a new category with no equal name keeps Confirm off until it is added or linked, or until each of its lines is moved by hand to a Tesoriere category, after which the next receipt with it asks again.
- R33 · Closing a review: Adding a category in a review and closing it shows the warning; dismissing it returns to the review with the change intact; closing anyway leaves no new category and no new link. Moving one line, or changing the source, and closing shows the same warning.
- R34 · Changing a link: After relinking Snack from Eating out to Groceries, last month's Eating out spent figure is unchanged and the next receipt's Snack lines go to Groceries.
- R35 · A receipt's source: The first receipt from a store shows Savings; after it is confirmed with a card as source, the next receipt from that store shows the card. A -42.30 row charged to a card recorded first, then a receipt from a store with no source kept, paired with it as it opened, leaves the payment on the card.
- R113 · Opening a receipt review: With Tesoriere categories Groceries and pulizia, a first receipt with lines under Chatelaine's Groceries and Pulizia opens them in Groceries and pulizia, from Savings, and a line under Garden in no category. A store last confirmed with a card opens with the card, and with Savings once the card is deleted.
- R114 · Answering a receipt review: A line under Garden, with no Garden in Tesoriere, is noted unlinked; linked to Household it is filed under Household with a rename-in-Chatelaine note; added instead, it is filed under a new category named Garden with 0.00 planned, and the review holds changes. A Garden line moved to Fun before the answer stays under Fun. Snack, opened under Eating out by its link and linked in the review to Groceries, files its lines under Groceries. A review whose categories all link by name holds no changes until a line is moved or the source changed, and moving a line changes no link; a line moved to Fun and back to Groceries, where it opened, leaves no change, and a later link of its Chatelaine category files it again.
- R115 · Confirming a receipt: A 16.50 receipt with Groceries lines of 1.89, 2.40 and 3.15 and Household lines of 6.90 and 2.16, from Savings at 1,000.00, records one payment of 16.50 with parts Groceries 7.44 and Household 9.06 and all five lines, records its receipt id, and leaves Savings at 983.50; a 0.00 line under Gifts adds no part and is kept among the lines. The same receipt from a Goal holding 10.00 carries R54's warning, and confirmed leaves the Goal at 0.00 and Savings at 993.50. A receipt id recorded once cannot be confirmed again.
- R36 · Store links: After pairing an Esselunga receipt with POS ESSELUNGA MI 0423, the next Esselunga receipt is suggested first with the bank rows whose payee matches that link, such as POS ESSELUNGA MI 0611, and then with any other row that meets R37.
- R120 · The text a store link keeps: Pairing an Esselunga receipt with `POS ESSELUNGA MI 0423` keeps `esselunga mi`; `POS ESSELUNGA MI 0611` matches it and is suggested before `POS ESSELUNGA TO 0611`, which does not match and is still suggested; a later Esselunga pair with `ESSELUNGA SPA` by hand keeps `esselunga mi`.
- R37 · Pairing rule: Each row of the table, as a receipt and a bank row, gives the suggestion its row says; no pair is recorded without the user's confirm.
- R38 · Whichever arrives first: Recording a EUR 42.30 receipt and then importing its bank row leaves one payment of EUR 42.30; importing the bank row first under Groceries and then its receipt with Groceries 30.00 and Household 12.30 leaves one payment with those two parts.
- R39 · Difference part: A EUR 42.10 receipt paired with a EUR 42.30 bank row records a EUR 42.30 payment with a EUR 0.20 difference part in the category the user chose, and the pair's confirm is off until it is chosen. A EUR 42.30 receipt paired with a EUR 42.10 bank row records a EUR 42.10 payment with a EUR -0.20 difference part.
- R40 · Pairing by hand: A EUR 40.00 receipt and a EUR 41.20 bank row, not suggested, can be paired by hand into one EUR 41.20 payment with a EUR 1.20 part the user files.
- R118 · Suggesting a pair: A 42.30 receipt recorded on 10 March and a statement row of -42.30 on 12 March: the row arrives `PAIR`, unticked, naming the receipt's payment, and the import's Confirm leaves one payment and Savings as it was, ticked or not; with the suggestion turned down and the row ticked under Groceries, Confirm records it as a second payment; the same row on 14 March arrives `READY`; +42.30 arrives `INCOMING`. A refund, or a payment that holds bank keys already, is never named. With rows of -42.30 on 11 and 12 March, only the 11 March row names it; with rows of -42.40 on 11 March and -42.30 on 12 March, the 12 March row does. Two 42.30 receipts of 10 March and rows of -42.30 on 11 and 12 March: the 11 March row names the receipt recorded first and the 12 March row the other. 42.30 receipts of 10 and 11 March with rows of -42.30 on 11 and 14 March: the 14 March row, which only the 11 March receipt meets, names it, and the 11 March row names the 10 March receipt. A 42.30 receipt of 10 March and a 42.50 receipt of 11 March with rows of -42.30 on 10 and 12 March and -42.50 on 13 March: the 10 March row names the 42.30 receipt, the 13 March row the 42.50 one, and the 12 March row arrives `READY`. A named payment removed while the review is open leaves its row unrecorded by that Confirm, and the next import of the statement offers the row `READY`. A receipt opened while a -42.30 payment recorded from a bank row on 12 March holds no receipt notes it as a pair available, and can still be confirmed. Two equal -4.50 rows and one 4.50 receipt, the suggestion turned down, the first row unticked and the second paired by hand and left ticked: Confirm records nothing more, one coffee in all.
- R119 · Recording a pair: A 42.30 receipt from Savings at 1,000.00, then its -42.30 row: one payment of 42.30 from Savings, dated the receipt's day, with the row's keys, Savings at 957.70, and the row `DUPLICATE` on the next import. A -42.30 row filed under Groceries from Savings, then a receipt of Groceries 30.00 and Household 12.30 confirmed with a card: one payment of 42.30 from the card with those two parts, the receipt's day and the row's keys, Savings back at 1,000.00 and the card 42.30 higher. A 42.10 receipt from a Goal holding 42.10, paired with a -42.30 row, warns as R54 says and takes 0.20 from Savings. A -42.30 row dated 14 March, 4 days after its receipt, ticked in the import review and paired by hand before Confirm, leaves one payment and Savings 42.30 lower once. A receipt of 30 March paired with its row of 2 April counts in March's spent. Two rows of -4.50 `BAR CENTRALE` on 11 March with no FITID and one 4.50 receipt under Food: the first pairs, and the second, ticked under Food, is recorded at Confirm, leaving two payments, Savings at 991.00 and Food's March spent at 9.00. Two 4.50 receipts from Bar Centrale on 10 March under Food and those two rows: both arrive `PAIR`, each pairs with one receipt, leaving two payments, Savings at 991.00 and Food at 9.00; so they do with FITIDs F1 and F2, the F1 row then recorded and the F2 row not. A -42.30 row charged to a card at 0.00 recorded first, then a receipt from a store with no source kept, paired as it opened: the payment stays on the card, the card at 42.30 and Savings unchanged; with Savings picked in the review it moves to Savings; for a store whose kept source is a Goal, to the Goal.
- R128 · Unpairing: A 42.10 receipt from Savings at 1,000.00, paired by hand with a -42.30 row and its 0.20 filed under Fun, unpaired: one payment of 42.10 with the receipt's parts and no keys, Savings at 957.90 and Fun's spent back; the row then arrives `PAIR` again, and turned down and ticked it is recorded on its own, Savings at 915.60. Paired with row F1 while its twin F2 is recorded, then unpaired, F1 arrives as new and F2 as recorded. Unpairing a receipt payment with no bank keys changes nothing.
- R121 · The Trash: An item deleted on 1 October 2026 is due its notice on 21 October and not on 20 October, and once given is not due again; it is kept on 30 October and goes on 31 October. A monthly expense in the Trash over two month crossings writes no sweep row.
- R122 · Restoring from the Trash: With Savings at 1,000.00 and a Goal holding 300.00, deleting the Goal with its money returned on 14 September 2026 leaves 1,300.00; restored on 2 October it holds 300.00, Savings is 1,000.00, and a `goal:` row of +300.00 dated 14 September is noted `Restored from the Trash` beside the delete's -300.00. A semimonthly 300.00 expense under Rent created on 1 September from Savings of 2,000.00, deleted on 20 September with its 300.00 returned and restored on 2 October, leaves Savings at 1,400.00 and Rent's September spent at 600.00, and is marked swept for October. A card at 140.00 deleted and restored reads 140.00. A 30.00 payment from a deleted Goal, removed after the delete, refuses the Goal's restore with `WRITTEN_SINCE`; one whose parts were only filed again does not.
- R123 · Deleting a category: Deleting Phone while a 60.00 monthly expense files under it is refused with `REPLACEMENT_NEEDED`; with Internet given, the expense files under Internet, and Phone's August spent stays 60.00 with its sweep row under Phone; restoring Phone leaves the expense under Internet. A card whose payoff or fees category is Phone, and a Loan whose own category is Phone, move to Internet in the same step. Deleting a category nothing files under needs no other category.
- R124 · Spending under a deleted category: With Fun at 40.00 and Groceries at 30.00 in September 2026, Fun deleted to the Trash gives a slice of Fun marked deleted at 40.00 and a total of 70.00; Fun gone for good gives Uncategorised at 40.00 and 70.00; Fun restored gives its own slice of 40.00 again.
- R125 · Deleting and restoring a payment: With Savings at 1,000.00, a 42.30 receipt payment from Savings deleted leaves 1,000.00, and restored leaves 957.70 with its parts, lines and keys. A 30.00 payment from a Goal holding 50.00 after it, deleted, leaves the Goal at 80.00; with 70.00 spent from the Goal since, restoring it warns and leaves the Goal at 0.00 and Savings 20.00 lower. Deleted, then its Goal deleted with 80.00 returned, restoring it warns and leaves Savings 30.00 lower with a `savings` row of -30.00 noted `Payment from a deleted goal`. With Savings at 900.00 and a Goal holding 100.00, a 30.00 payment from the Goal deleted, the Goal deleted with its money returned, the payment restored, deleted again, and the Goal restored, leaves Savings at 900.00 and the Goal at 100.00; restoring the payment then leaves Savings at 900.00 and the Goal at 70.00, with no warning. The same steps with a 40.00 payment charged to a card at 100.00 leave the card at 140.00. Of two -4.50 rows of one day recorded in one import, with FITIDs F1 and F2 or with none, one payment deleted is restored as it was; with no FITID, once the row is ticked as `DUPLICATE` and recorded again, its restore is refused with `RECORDED_AGAIN`. A payment in the Trash, edited, changes nothing. Of two such rows with no FITID, one payment deleted and the other paired with a receipt and unpaired, the deleted one's row stays recorded and only one of the two arrives as new on the next import; with FITIDs F1 and F2, F2's deleted, only F1's does.
- R127 · Restoring a payment as a pair: With Savings at 1,000.00, a 42.30 receipt of 10 March, Groceries 30.00 and Household 12.30, deleted, then its -42.30 row of 12 March recorded under Groceries, which leaves 957.70: restoring the receipt names that payment; on its own it leaves 915.40 and two payments, and as the pair one payment of 42.30 with Groceries 30.00 and Household 12.30, the receipt's id, lines and day and the row's keys, and Savings at 957.70. With a -42.50 row instead, the pair is refused with `CANNOT_PAIR` until the 0.20 difference is filed under a category. A 4.50 receipt filed under Food and moved by hand to Groceries, deleted and restored as its row's pair, keeps Groceries 4.50. A 4.50 bank payment of 11 March under Food deleted, then a 4.50 receipt of 10 March under Food recorded: restoring the bank payment names the receipt's payment, and as the pair leaves one payment of 4.50 with the row's keys, Savings at 995.50 and Food's March spent at 4.50. A paired 42.30 receipt payment deleted, then its row ticked as `DUPLICATE` and recorded under Groceries: restoring it on its own is refused with `RECORDED_AGAIN`, and as that payment's pair leaves one payment with the receipt and the keys, and Savings at 957.70. Of two -4.50 bank payments of 11 March under Food, with no FITID or with FITIDs F1 and F2, one deleted, then a 4.50 receipt of 10 March under Food recorded: the deleted one comes back as the receipt's pair, leaving two payments, Savings at 991.00 and Food at 9.00. A 42.30 receipt paired with a -42.50 row, its 0.20 under Fun, deleted, then its row recorded again under Groceries: restored as that payment's pair with no category picked, the 0.20 is under Fun again and Savings at 957.50; with Household picked, under Household; with Fun deleted since, it is refused with `CANNOT_PAIR` until one is picked. A -4.50 coffee recorded from a CSV, as a payment or a payoff, then an OFX of F1 and F2, F2 recorded and its payment deleted, then a 4.50 receipt recorded: F2 comes back as the receipt's pair.
- R126 · A Profile in the Trash: A Profile holding the receipt folder and a 42.30 receipt payment, deleted, frees the folder; restored while no other Profile holds one, it holds the folder again and equals the Profile as deleted. Restored after Profile B chose a folder, B keeps it, and the restored Profile holds no folder and keeps its recorded receipt id.
- R41 · Deleting a receipt payment: Deleting a receipt payment leaves its file out of the next scan (R116). A 42.30 receipt split 30.00 Groceries and 12.30 Household and paired with its bank row, deleted and restored, has its two parts, its two lines and its bank keys again, and the row arrives `DUPLICATE` on an import before and after the restore.
- R42 · Deleting a linked category: After deleting Pulizia, the next receipt with Chatelaine's Pulizia keeps Confirm off until its line is linked or added. A Chatelaine category Pulizie linked to Pulizia, after Pulizia is deleted and restored, opens in Pulizia again with Confirm on.
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
