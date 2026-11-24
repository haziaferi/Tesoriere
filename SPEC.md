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
| Q-R09 | Minimum Android version | 2. The project |
| O-R10 | No valence colour | 3. Owner rules and visible design |
| O-R11 | No grading, no streaks | 3. Owner rules and visible design |
| O-R12 | Inform, never block | 3. Owner rules and visible design |
| O-R13 | Visible decisions are the owner's | 3. Owner rules and visible design |
| O-R14 | No stock look | 3. Owner rules and visible design |
| Q-R15 | Accessibility values | 3. Owner rules and visible design |
| Q-R16 | Visible design | 3. Owner rules and visible design |
| D-R17 | Port source | 4. The port from Sci-Fin |
| D-R18 | Ported pieces | 4. The port from Sci-Fin |
| D-R19 | Sci-Fin backup import | 4. The port from Sci-Fin |
| D-R20 | Money and time | 5. Money model |
| D-R21 | Payment | 5. Money model |
| D-R22 | Source and parts are separate | 5. Money model |
| D-R23 | Parts add up | 5. Money model |
| D-R24 | Any payment can be split | 5. Money model |
| D-R25 | Spent is derived | 5. Money model |
| D-R26 | Statement import | 6. Statement import |
| D-R27 | The receipt contract | 7. Chatelaine receipts |
| D-R28 | Receipt folder | 7. Chatelaine receipts |
| D-R29 | Picking up receipts | 7. Chatelaine receipts |
| D-R30 | Receipt files Tesoriere cannot take | 7. Chatelaine receipts |
| D-R31 | Receipt review | 7. Chatelaine receipts |
| D-R32 | Category links | 7. Chatelaine receipts |
| Q-R33 | A link from a closed review | 7. Chatelaine receipts |
| D-R34 | Changing a link | 7. Chatelaine receipts |
| Q-R35 | A receipt's source | 7. Chatelaine receipts |
| D-R36 | Store links | 8. One payment, two sources |
| D-R37 | Pairing rule | 8. One payment, two sources |
| D-R38 | Whichever arrives first | 8. One payment, two sources |
| D-R39 | Difference part | 8. One payment, two sources |
| Q-R40 | Pairing by hand | 8. One payment, two sources |
| D-R41 | Deleting a receipt payment | 9. Edge cases |
| D-R42 | Deleting a linked category | 9. Edge cases |
| D-R43 | Test plan | 10. Test plan |
| Q-R44 | Rollout phases | 11. Rollout |
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

### Q-R09 · Minimum Android version

**Rule:** Which Android version is the oldest Tesoriere runs on?

**Default:** API 30 (Android 11), Sci-Fin's `minSdk`, so ported code that relies on it keeps working. Changes: which phones can install it, and whether Sci-Fin's API 30 workarounds (no `DROP COLUMN` in SQLite) still apply.

**Why:** A lower version reaches older phones; a higher one drops workarounds.

**Since:** 2026-11-24 · builder · own words

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

### Q-R15 · Accessibility values

**Rule:** Which accessibility values does Tesoriere's new interface hold? The owner will reexamine Sci-Fin's values with the new UI.

**Default:** Sci-Fin's values stand until the design pass: touch targets of 48 dp on Android and 40 px on Windows, the operating system's text size, reduce motion, and a low-distraction mode. Changes: target sizes, the type scale and which settings exist.

**Why:** Sci-Fin's values were set for Sci-Fin's screens; Tesoriere's screens are new.

**Since:** 2026-11-24 · owner · agreed rewording

### Q-R16 · Visible design

**Rule:** How do Tesoriere's screens look and work? Each row below is decided with the owner in a design pass before any screen is built (R13).

**Default:** none proposed: every row is a visible decision the owner makes (R13); the design pass starts from the jobs and their frequency (R01).

**Why:** No Sci-Fin UI is inherited (R04), so every screen is open.

**Values:**

| Decision | Bears on |
|---|---|
| Navigation and what opens first | R01 |
| Screen for each job | R01 |
| Receipt review layout | R31, R32 |
| Pairing in the statement review | R37, R38 |
| Split editor | R24 |
| Palette and themes | R10, R14 |
| Type and spacing | R14, R15 |
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
| `parts` | one or more: category id and cents (R23) |
| `receiptId` | the receipt file's id, when the payment came from or was paired with a receipt |
| `receiptLines` | per line: name, pack, quantity, price paid, Chatelaine category id and name, Tesoriere category |
| `bankKeys` | the bank row's FITID and its date, amount and description key, when it came from or was paired with a bank row (R26) |

**Acceptance:** A recorded receipt payment holds every receipt line; a paired payment holds both its `receiptId` and its `bankKeys`.

**Since:** 2026-11-24 · builder · own words

### D-R22 · Source and parts are separate

**Rule:** A payment has one source, where the money came from, and its parts, what it was spent on. A payment charged to a credit card counts in its categories and raises the card's balance.

**Why:** A card-paid shopping trip is still groceries and household spending; in Sci-Fin a card charge replaced the category, so it counted in none.

**Acceptance:** A EUR 40 receipt charged to a card adds its parts to their categories' spent figures and EUR 40 to the card's balance, and moves nothing in Savings.

**Not:** Sci-Fin's model, where a charge to a card has no category.

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

## 6. Statement import

### D-R26 · Statement import

**Rule:** A bank statement in CSV or OFX is read on the device, and every bank row lands in a review as a suggestion; nothing is recorded until one Confirm, which names the total. Each recorded bank row keeps two keys, the OFX FITID where there is one and a key over date, amount and description, and both are checked, so a row already recorded arrives unticked with the reason shown. A payee once filed by the user pre-fills its category, as in Sci-Fin's spec, section 4.3.

**Acceptance:** Importing the same OFX statement twice records nothing the second time; importing a CSV export of the same period also records nothing; a payee filed under a category once is pre-filled on the next statement.

**Since:** 2026-11-24 · owner · agreed rewording

## 7. Chatelaine receipts

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

### Q-R33 · A link from a closed review

**Rule:** When a review that added or linked a category is closed without Confirm, is the link kept?

**Default:** No: links are kept on Confirm only, so every link has a recorded receipt behind it. Changes: a category added in a closed review is not created either.

**Why:** Keeping it saves asking again; dropping it keeps the link list to what receipts used.

**Since:** 2026-11-24 · builder · own words

### D-R34 · Changing a link

**Rule:** A category link can be changed later. The change applies to receipts recorded after it; lines already recorded stay in the category they were filed under.

**Acceptance:** After relinking Snack from Eating out to Groceries, last month's Eating out spent figure is unchanged and the next receipt's Snack lines go to Groceries.

**Not:** Moving past lines too: past months' figures would shift on their own.

**Since:** 2026-11-24 · owner · agreed rewording

### Q-R35 · A receipt's source

**Rule:** Where does a receipt's money come from? The receipt file does not say how it was paid (Chatelaine's spec, rule 123).

**Default:** Savings, changeable in the review; the source chosen for a store is pre-filled on its next receipt. Changes: a card-paid receipt is set to the card once per store; pairing with a bank row keeps the receipt's source.

**Why:** Asking every time is one more choice per receipt; never asking files card payments as Savings until the user corrects them.

**Since:** 2026-11-24 · builder · own words

## 8. One payment, two sources

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

### Q-R40 · Pairing by hand

**Rule:** Can the user pair a receipt and a bank row that the pairing rule does not suggest, such as totals more than EUR 0.50 apart?

**Default:** Yes: by hand, from either side, with the difference filed as in R39. Changes: a missed pair can be fixed instead of the payment being counted twice.

**Why:** Pairing by hand catches what the rule misses; refusing it keeps every pair inside the rule.

**Since:** 2026-11-24 · builder · own words

## 9. Edge cases

### D-R41 · Deleting a receipt payment

**Rule:** A payment recorded from a receipt is deleted through the Trash like any other. Its receipt id stays recorded, so the file is not offered again; restoring it from the Trash restores the payment with its parts and pairs.

**Evidence:** Not checked: the owner has not ruled on whether a deleted receipt should be offered again.

**Acceptance:** Deleting a receipt payment and reopening the app does not offer the receipt file again; restoring it brings back its parts.

**Since:** 2026-11-24 · builder · own words

### D-R42 · Deleting a linked category

**Rule:** Deleting a Tesoriere category removes the category links to it; the next receipt with that Chatelaine category asks again, as for a new one (R32).

**Acceptance:** After deleting Pulizia, the next receipt with Chatelaine's Pulizia keeps Confirm off until its line is linked or added.

**Since:** 2026-11-24 · builder · own words

## 10. Test plan

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

## 11. Rollout

### Q-R44 · Rollout phases

**Rule:** In what phases does Tesoriere replace Sci-Fin on the owner's devices?

**Default:** Phase 1: the engine port with receipts and pairs, tested on the JVM only; gate, every level of R43 green. Phase 2: the app on the owner's phone and PC beside Sci-Fin, data imported from a Sci-Fin backup (R19); gate, one month of receipts and one statement recorded with no double count; rollback, keep using Sci-Fin. Phase 3: Sci-Fin retired. Changes: how long both apps run side by side.

**Why:** Running both for a month catches a wrong pair before Sci-Fin's data stops being the record; retiring at once saves the double entry.

**Since:** 2026-11-24 · builder · own words

## Acceptance list

- R03 · A standalone app: The Android `applicationId`, the namespace and every Kotlin package start with `com.hazia.tesoriere`.
- R04 · A selective port of Sci-Fin: No file under Sci-Fin's `ui/` package is copied into Tesoriere; every ported engine file names its Sci-Fin source in its commit message.
- R05 · Repository and licence: `LICENSE` holds the GPL-3.0 text; `settings.gradle.kts` sits at the root; `publish_check` runs as the pre-push hook before the first push.
- R06 · Platforms: One Gradle build produces the Android app and the Windows desktop app from the shared module.
- R07 · Languages: Switching the language changes every interface string and the app's own ledger labels, and leaves user-typed names unchanged.
- R08 · Offline first: While the table lists no feature, the Android manifest omits `android.permission.INTERNET` and the Windows build makes no network call; a feature added to the table has a setting that is off on a fresh install.
- R10 · No valence colour: No theme defines a positive or warning colour pair; a category over its plan and one under it draw their bars in the same colour.
- R11 · No grading, no streaks: No screen or notification shows a score, badge, streak count, or a pass or fail word.
- R12 · Inform, never block: Every feasibility note sits beside an action that stays available; no such check disables a save.
- R14 · No stock look: The design pass (R16) scores each candidate on a boldness axis of its own, beside the layout axes.
- R17 · Port source: Each ported piece has its own rules in this spec before its code is merged, and its tests cite them as `SPEC:Rnn`.
- R18 · Ported pieces: On a fresh install the pieces marked off show no screen, row or notification until switched on; Profiles off means one Profile and no switcher.
- R19 · Sci-Fin backup import: Importing a Sci-Fin schema-10 backup yields one new Tesoriere Profile per Sci-Fin Profile, with the same Savings, Goals, Categories, Recurring expenses, Accounts and ledger counts the review showed; a schema-11 file is refused and nothing is written.
- R20 · Money and time: No floating-point type holds money in `shared/`; every date field is a calendar day and every logged time an instant.
- R21 · Payment: A recorded receipt payment holds every receipt line; a paired payment holds both its `receiptId` and its `bankKeys`.
- R22 · Source and parts are separate: A EUR 40 receipt charged to a card adds its parts to their categories' spent figures and EUR 40 to the card's balance, and moves nothing in Savings.
- R23 · Parts add up: A EUR 42.30 payment with parts of EUR 30.00 and EUR 12.00 shows EUR 0.30 left and cannot be saved; with a third part of EUR 0.30 it can.
- R24 · Any payment can be split: A bank row and a hand-logged payment can each be saved with two parts in two categories.
- R25 · Spent is derived: Re-filing a payment's parts changes the spent figures of the categories involved, for the payment's month, and writes no correction row.
- R26 · Statement import: Importing the same OFX statement twice records nothing the second time; importing a CSV export of the same period also records nothing; a payee filed under a category once is pre-filled on the next statement.
- R27 · The receipt contract: The receipt parser's tests cite Chatelaine's rule 123 field by field; no Tesoriere code opens the receipt folder for writing.
- R28 · Receipt folder: With the folder set in Profile A, receipts are reviewed and recorded only in Profile A; Profile B offers no receipt folder while A has one.
- R29 · Picking up receipts: With the default, opening the app with three new files offers three receipts and runs no background work; with the daily check on, three new files give one notification that day; no receipt is recorded before its Confirm.
- R30 · Receipt files Tesoriere cannot take: One file per row, put in the receipt folder, is listed with its reason (or, for a recorded id, not at all) and changes no figure.
- R31 · Receipt review: A receipt of five lines in two categories records one payment of the receipt's total with two parts; moving one line to a third category gives three parts and changes no category link; closing the review leaves no payment.
- R32 · Category links: A receipt with category Pulizia links to Tesoriere's pulizia; a later receipt with the same id named Pulizie stays linked and shows the names-differ note; a new category with no equal name keeps Confirm off until it is added or linked.
- R34 · Changing a link: After relinking Snack from Eating out to Groceries, last month's Eating out spent figure is unchanged and the next receipt's Snack lines go to Groceries.
- R36 · Store links: After pairing an Esselunga receipt with POS ESSELUNGA MI 0423, the next Esselunga receipt is suggested only with bank rows whose payee matches that link, when one exists.
- R37 · Pairing rule: Each row of the table, as a receipt and a bank row, gives the suggestion its row says; no pair is recorded without the user's confirm.
- R38 · Whichever arrives first: Recording a EUR 42.30 receipt and then importing its bank row leaves one payment of EUR 42.30; importing the bank row first under Groceries and then its receipt with Groceries 30.00 and Household 12.30 leaves one payment with those two parts.
- R39 · Difference part: A EUR 42.10 receipt paired with a EUR 42.30 bank row records a EUR 42.30 payment with a EUR 0.20 part in the category the user chose, and the pair's confirm is off until it is chosen.
- R41 · Deleting a receipt payment: Deleting a receipt payment and reopening the app does not offer the receipt file again; restoring it brings back its parts.
- R42 · Deleting a linked category: After deleting Pulizia, the next receipt with Chatelaine's Pulizia keeps Confirm off until its line is linked or added.
- R43 · Test plan: CI runs the first five levels on every pull request; a release records its device walk.

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
