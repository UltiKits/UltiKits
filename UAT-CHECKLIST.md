# UltiKits — UAT Checklist

This document is the executable companion to `FEATURES.md`: one row per feature stating the
steps to exercise it and the observable truth that proves it works. It is an internal reference
for real-machine verification, not user-facing documentation.

> Batches are dispatched at 60 rows or fewer, and a batch never spans two repositories. There are
> exactly two legitimate exits to `human-uat-pending`: a row needing the pixel layer while the
> real-client harness is not ready, and a row needing personal credentials. Every other row must
> reach `pass`, `fail`, or `blocked`.

## Conventions

- **Columns:** `ID`, `Preconditions`, `Steps`, `Expected`, `Layer`, `Covers`.
- **ID:** cites its `FEATURES.md` ID verbatim. A negative case suffixes the checklist ID only,
  as `.neg-<slug>`.
- **Layer**, copied verbatim from Laojun's own `ultitools-real-client-uat` skill: `protocol`,
  `java-client`, `os-input`, `pixel`, `server`, `human`.
- **Human-authenticated-session rows (D-27b):** this module has no panel-facing surface at all,
  so no row below is affected; the convention is stated here for template consistency.
- **Expected** must name an observable truth and never the words "it works". Unlike UltiSocial,
  this module's `i18n()` catalogue genuinely works — every row below whose Expected quotes a
  literal chat line therefore carries the precondition `language: en` in
  `plugins/UltiTools/config.yml` (the framework's shipped default is `zh`), EXCEPT the two
  hardcoded-English-regardless-of-language spots `FEATURES.md` names (`KitEditorGui`'s
  "Click to save kit contents" lore, and FOUR of `/kits help`'s seven lines), which render in
  English under either setting and are marked as such below.
- **Covers** back-references a Phase 9 GUI-excluded class name; left blank when no such class
  applies. This module owns both of its GUI-excluded classes (`KitBrowserGui`, `KitEditorGui`) —
  D-21 requires at least one Covers citation per class, not exactly one; below, each is named on
  the command row that opens it AND on its own dedicated `gui`-Kind row, since both are genuine,
  independently-run assertions that exercise that class.
- A row whose Preconditions name a prior row must appear after that row in file order — asserted
  mechanically: for every row, every checklist ID cited in its Preconditions cell must have a
  strictly smaller line number in this file (sweep class 8, D-27a).
- **Config-per-file rule (D-06):** one checklist row per `@ConfigEntity`-annotated class or per
  shipped yml file. This module has exactly one such file (`config/config.yml`, shipped as a real
  resource, unlike UltiBackup/UltiSocial's generated-on-first-boot files), so exactly one
  config-per-file row appears below (`ultikits.config.config-yml`).
- This document never cites the pre-6.3.0 command-executor base class or the pre-6.3.0
  data-entity base class, both removed outright in 6.3.0 — this module was already migrated off
  both before this phase (`KitCommands` extends `BaseCommandExecutor`, `KitClaimData` extends
  `BaseDataEntity<String>`).

## Kit Commands

| ID | Preconditions | Steps | Expected | Layer | Covers |
|---|---|---|---|---|---|
| ultikits.kits.create | `language: en`; `Tester1` holds `ultikits.kits.use` and `ultikits.kits.admin`; no kit named `test1` currently exists; `Tester1`'s inventory contains at least one non-air item | `Tester1` runs `/kits create test1` | Chat shows `Created kit: test1` (green); a new file `plugins/UltiTools/UltiKits/kits/test1.yml` exists, with `displayName: "&ftest1"`, `icon` matching the first non-air item's material, and `items` a non-empty serialized string; the new kit appears in `/kits list` | server | |
| ultikits.kits.create.neg-no-permission | `language: en`; `Tester1` holds `ultikits.kits.use` but NOT `ultikits.kits.admin` | `Tester1` runs `/kits create test2` | Chat shows `You don't have permission to execute this command` (red); no `test2.yml` file is created | server | |
| ultikits.kits.create.neg-already-exists | `language: en`; `Tester1` holds `ultikits.kits.admin`; a kit named `test1` already exists (from `ultikits.kits.create`) | `Tester1` runs `/kits create test1` again | Chat shows `Kit name already exists: test1` (red); the EXISTING `test1.yml` file's contents are unchanged (not overwritten) | server | |
| ultikits.kits.create.neg-empty-inventory | `language: en`; `Tester1` holds `ultikits.kits.admin`; `Tester1`'s inventory is completely empty (all air) | `Tester1` runs `/kits create test3` | Chat shows `Inventory is empty` (red); no `test3.yml` file is created | server | |
| ultikits.kits.list | `language: en`; the shipped `starter` kit present with no `permission` requirement; a second kit `restricted` exists with a `permission` key the running player does NOT hold | `Tester1` (lacking the `restricted` permission) runs `/kits list` | Chat shows `=== Kit List ===` (gold) followed by one line per AVAILABLE kit including `starter` (name in yellow, display name in gray, price in gold if not free) — `restricted` does NOT appear, since `getAvailableKits` filters it out for a player lacking its permission | server | |
| ultikits.kits.list.neg-console-sees-all | Same two kits as above (`starter`, `restricted`) | Run `/kits list` from the server console | BOTH `starter` AND `restricted` appear — `KitCommands#onList`'s console branch calls `getKitNames()`/`getKit()` directly, unconditionally, never `getAvailableKits`'s permission filter, which only applies to a `Player` sender | server | |
| ultikits.kits.open | `Tester1` holds `ultikits.kits.use`, with at least one kit available | Run bare `/kits` (no arguments) | The `ultikits.gui.kit-browser` GUI opens for `Tester1`; this row proves only that the bare command opens the browser — its own interactions are covered by `ultikits.gui.kit-browser` below | server | KitBrowserGui |
| ultikits.kits.claim | `language: en`; a FREE kit `starter` exists with no permission/level requirement, `reBuyable: false`, non-empty items, and configured with one `playerCommands` entry (e.g. `say {player} claimed starter`) and one `consoleCommands` entry (e.g. `broadcast {player} got the starter kit`); `Tester1` has never claimed it; `Tester1` has enough empty inventory slots for its item count | `Tester1` runs `/kits claim starter` | Chat shows `Successfully claimed kit: starter` (green) — `KitCommands#handleClaimResult` formats this with the RAW `kitName` command argument, not `kit.getDisplayName()` (unlike the GUI's own click-claim path, `ultikits.gui.kit-browser`, which does use the display name); the kit's items now appear in `Tester1`'s inventory; the configured `playerCommands` entry runs AS `Tester1` (chat shows the `say` output with `{player}` replaced by `Tester1`'s name) and the configured `consoleCommands` entry runs as the SERVER CONSOLE one tick later via `Bukkit.getScheduler().runTask` (the broadcast reaches every online player, also with `{player}` substituted); a new `kits_claims` row exists for `Tester1`+`starter` | server | |
| ultikits.kits.claim.neg-not-found | `language: en`; `Tester1` online | `Tester1` runs `/kits claim does-not-exist` | Chat shows `Kit 'does-not-exist' does not exist` (red) | server | |
| ultikits.kits.claim.neg-no-permission | `language: en`; a kit `restricted` exists with a `permission` key `Tester1` does NOT hold | `Tester1` runs `/kits claim restricted` | Chat shows `You don't have permission to use this kit` (red); no items are delivered | server | |
| ultikits.kits.claim.neg-insufficient-level | `language: en`; a kit `veteran` exists with `levelRequired` higher than `Tester1`'s current experience level | `Tester1` runs `/kits claim veteran` | Chat shows `Level too low, requires level N` (red, `N` matching the kit's `levelRequired`); no items are delivered | server | |
| ultikits.kits.claim.neg-already-claimed | `language: en`; `Tester1` has already claimed the one-time (`reBuyable: false`) `starter` kit (from `ultikits.kits.claim`) | `Tester1` runs `/kits claim starter` again | Chat shows `You have already claimed this kit` (red); no second delivery occurs, no second `kits_claims` row is created (the existing one's `claimCount`/`lastClaim` are also unchanged — only a successful claim updates them) | server | |
| ultikits.kits.claim.neg-on-cooldown | `language: en`; a RE-BUYABLE kit `daily` exists with `cooldown` greater than 0, non-empty items, no permission/level requirement; `Tester1` claimed it once, less than `cooldown` seconds ago | `Tester1` runs `/kits claim daily` again | Chat shows `Kit on cooldown, remaining: <time>` (red), `<time>` matching `formatCooldown`'s own `%dh %dm %ds` composition (only the units with a nonzero value appear, and seconds always appears if hours/minutes are both zero); no items are delivered | server | |
| ultikits.kits.claim.neg-inventory-full | `language: en`; a free kit `starter` with at least 1 item, no permission/level requirement, not yet claimed by `Tester1`; `Tester1`'s inventory has fewer empty slots than the kit's item count (fill it deliberately) | `Tester1` runs `/kits claim starter` | Chat shows `Inventory full` (red); no items are delivered, no `kits_claims` row is created | server | |
| ultikits.kits.edit | `language: en`; `Tester1` holds `ultikits.kits.admin`; a kit `test1` exists (from `ultikits.kits.create`) | `Tester1` runs `/kits edit test1` | The `ultikits.gui.kit-editor` GUI opens for `Tester1`, pre-filled with `test1`'s existing items in slots 0-44; this row proves only that the command opens the correct kit's editor — its own interactions are covered by `ultikits.gui.kit-editor` below | server | KitEditorGui |
| ultikits.kits.edit.neg-no-permission | `language: en`; `Tester1` holds `ultikits.kits.use` but NOT `ultikits.kits.admin` | `Tester1` runs `/kits edit test1` | Chat shows `You don't have permission to execute this command` (red); no inventory opens | server | |
| ultikits.kits.edit.neg-not-found | `language: en`; `Tester1` holds `ultikits.kits.admin`; no kit named `does-not-exist` | `Tester1` runs `/kits edit does-not-exist` | Chat shows `Kit 'does-not-exist' does not exist` (red); no inventory opens | server | |
| ultikits.kits.delete | `language: en`; `Tester1` holds `ultikits.kits.admin`; a kit `test1` exists (from `ultikits.kits.create`) | `Tester1` runs `/kits delete test1` | Chat shows `Deleted kit: test1` (green); `plugins/UltiTools/UltiKits/kits/test1.yml` no longer exists on disk; `test1` no longer appears in `/kits list` | server | |
| ultikits.kits.delete.neg-no-permission | `language: en`; `Tester1` holds `ultikits.kits.use` but NOT `ultikits.kits.admin`; a kit `starter` exists | `Tester1` runs `/kits delete starter` | Chat shows `You don't have permission to execute this command` (red); `starter.yml` is unaffected | server | |
| ultikits.kits.delete.neg-not-found | `language: en`; `Tester1` holds `ultikits.kits.admin`; no kit named `does-not-exist` | `Tester1` runs `/kits delete does-not-exist` | Chat shows `Kit 'does-not-exist' does not exist` (red) | server | |
| ultikits.kits.reload | `language: en`; `Tester1` holds `ultikits.kits.admin`; a new kit file `plugins/UltiTools/UltiKits/kits/manual.yml` is hand-placed on disk (valid YAML, a `displayName` and `icon` at minimum) WITHOUT using `/kits create` | `Tester1` runs `/kits reload` | Chat shows `Reloaded N kits` (green), `N` including the hand-placed `manual` kit; `manual` now appears in `/kits list` even though it was never created through this module's own commands — proving `reload` genuinely re-scans the directory rather than only refreshing already-tracked entries | server | |
| ultikits.kits.reload.neg-no-permission | `language: en`; `Tester1` holds `ultikits.kits.use` but NOT `ultikits.kits.admin` | `Tester1` runs `/kits reload` | Chat shows `You don't have permission to execute this command` (red); the kit catalogue is unchanged | server | |
| ultikits.kits.help | `language: en`; `Tester1` online | `Tester1` runs `/kits help` | Chat shows `=== UltiKits ===` (gold) followed by seven lines: `/kits` (list kits, i18n), `/kits claim <name>` (available, i18n), `/kits list` (list kits, i18n), `/kits edit <name>` (hardcoded English "Edit kit", NOT i18n — renders identically regardless of `language`), `/kits create <name>` (hardcoded English "Create kit"), `/kits delete <name>` (hardcoded English "Delete kit"), `/kits reload` (hardcoded English "Reload kits" — FOUR of the seven lines are hardcoded English, not three; only the first three lines route through `i18n()`) | server | |
| ultikits.kits.list.neg-empty | No kits present at all (every `.yml` file removed from `plugins/UltiTools/UltiKits/kits/`, then `/kits reload` run to clear the in-memory catalogue) | `Tester1` runs `/kits list` | Chat shows `No kits available` (red), no header line, no item lines — the ONLY correct empty-state text, not a bare header with zero entries | server | |

## GUI

| ID | Preconditions | Steps | Expected | Layer | Covers |
|---|---|---|---|---|---|
| ultikits.gui.kit-browser | `language: en`; `Tester1` has at least one AVAILABLE free kit with no requirements, and at least one kit `Tester1` has already claimed (one-time); the browser open via `ultikits.kits.open` | Read the status line lore on the available kit's icon, then the status line on the already-claimed kit's icon; separately, click the available kit's icon twice in rapid succession (well under 200ms apart) | The available kit's status line reads `Available` (green); the already-claimed kit's reads `Claimed` (red); clicking the available kit once claims it exactly as `ultikits.kits.claim` does and closes the inventory; the SECOND rapid click (within `KitBrowserGui`'s own hardcoded 200ms debounce window, `handleKitClick`'s `lastClickTime` check — NOT the dead `click_cooldown_ms` config key, see `FEATURES.md`) is silently dropped, producing no second claim message and no second `kits_claims` update | pixel | KitBrowserGui |
| ultikits.gui.kit-browser.neg-pagination | More than 28 kits available to `Tester1` (the real, hardcoded page size — NOT the dead `kits_per_page` config key, see `FEATURES.md`); the browser open via `ultikits.kits.open` | Read the page indicator (slot 49), then click the next-page arrow (slot 53) | The page indicator reads `Page 1/N` with `N` matching `ceil(available kit count / 28)`; clicking next-page advances to a NEW `KitBrowserGui` instance showing kits 29 onward, with the indicator now reading `Page 2/N` | pixel | KitBrowserGui |
| ultikits.gui.kit-editor | `language: en`; `Tester1` holds `ultikits.kits.admin`; a kit `test1` exists with at least one item already stored; the editor open via `ultikits.kits.edit` | Move the pre-filled item to a different slot within the 45-item grid, add one new item from the player's own inventory into an empty grid slot, then click the Save button (slot 45); separately (re-open, fresh attempt), instead click the Cancel button (slot 53, hardcoded English "Click to save kit contents" lore regardless of `language` — this string appears on the SAVE button's lore, not Cancel's own, a naming mismatch worth noting verbatim as read, not corrected) without touching Save | Save: chat shows `Saved kit: test1` (green); `test1.yml`'s `items` field now CONTAINS both the moved original item and the newly added one — `KitEditorGui#handleSave` walks slots 0-44 in order and collects only non-air items into a dense, gap-free list (`saveKitItems` serializes that compacted array), so the saved data reflects PRESENCE and RELATIVE ORDER, not the items' literal absolute grid slot numbers; claiming this kit afterward delivers both items via `Inventory#addItem` (which itself ignores original slot position). Cancel: the inventory simply closes with no chat message and no file write — `test1.yml` is UNCHANGED from before this row, even though items were moved around in the grid before cancelling | pixel | KitEditorGui |

## Data Persistence

| ID | Preconditions | Steps | Expected | Layer | Covers |
|---|---|---|---|---|---|
| ultikits.storage.claim-history-survives-restart | `Tester1` has claimed a re-buyable kit with a cooldown still in effect (from `ultikits.kits.claim.neg-on-cooldown`'s own setup, or a fresh claim of a cooldown kit) | Stop the server completely, then start it again, then have `Tester1` attempt to claim the same kit again | The cooldown is STILL enforced — chat shows `Kit on cooldown, remaining: <time>` with a plausible remaining duration, not a fresh `Successfully claimed` as if the cooldown had reset; the `kits_claims` row's `lastClaim`/`claimCount` are unchanged from before the restart | server | |
| ultikits.storage.kit-contents-survive-restart | A kit `test1` exists with known display name, price, and item contents (from `ultikits.kits.create`) | Stop the server completely, then start it again (a genuine process restart, not `/kits reload`), then run `/kits list` and `/kits claim test1` | `test1` still appears in `/kits list` with the SAME display name and price; claiming it delivers the SAME items as before the restart — the on-disk YAML file is the only thing that ever held this state, so a bare process restart cannot lose it | server | |

## Configuration

One row per shipped yml file (D-06's config-per-file rule): `config/config.yml` (3 keys, shipped
as a real packaged resource, unlike UltiBackup/UltiSocial's generated-on-first-boot files). This
row confirms every key is present at its documented default. This module's own `FEATURES.md`
documents ALL THREE keys as having zero observable effect (`UltiKits/UltiKits#13`) — so unlike
every other module's config row in this fan-out, this one DOES flip all three keys, but each
flip is a NEGATIVE assertion: the row confirms the configured value has NO effect, as a direct
confirmation of the filed defect, rather than confirming a behavioural change follows.

| ID | Preconditions | Steps | Expected | Layer | Covers |
|---|---|---|---|---|---|
| ultikits.config.config-yml | Fresh `plugins/UltiTools/UltiKits/config/config.yml` at its shipped default (a real packaged resource, not hand-edited); a kit `paid` exists with `price` higher than `Tester1` can afford (or no economy plugin installed at all, which `KitBrowserGui#getStatusText` also treats as insufficient funds) | Load the file; confirm all 3 keys (`enabled`, `click_cooldown_ms`, `kits_per_page`) are present at their documented defaults (`true`, `200`, `28`); THEN, as a confirmation of the filed defect rather than a behavioural test, exercise all three in separate configurations: (a) set `enabled: false`, restart, and confirm `/kits`/`/kits list` still work identically; (b) set `kits_per_page: 5`, restart, and confirm the browser GUI still shows up to 28 items per page; (c) set `click_cooldown_ms: 5000` (a large, obviously-different value from the default 200ms), restart, open the kit browser, and click the UNAFFORDABLE `paid` kit's icon twice, roughly 1 second apart — a failed claim does NOT close the inventory (`KitBrowserGui#handleKitClick` only calls `closeInventory()` on the SUCCESS branch), so both clicks land on the SAME open GUI instance without needing to reopen it; a 1-second gap is well under the configured 5000ms but well OVER the real hardcoded 200ms debounce, so if the hardcoded constant is what actually governs it (as `FEATURES.md` documents), the second click still produces its own independent failure message rather than being silently dropped | All 3 keys present at their documented defaults before the change; (a) `/kits`/`/kits list` behave EXACTLY as before, `enabled: false` does not disable anything; (b) the browser GUI still shows up to 28 items per page despite `kits_per_page: 5`; (c) BOTH clicks on `paid` produce their own `Insufficient balance` message (two messages total, not one) — proving the second click was processed as an independent attempt rather than silently dropped, which the configured `click_cooldown_ms: 5000` would have required if it were live — confirming `UltiKits/UltiKits#13`'s claim that all three keys are dead, not merely under-tested | server | |
