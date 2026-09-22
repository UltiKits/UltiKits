# UltiKits — Feature Inventory

This document catalogues every operator- or player-visible function, command, content item and
configuration key in this repository, as read directly from source. It is an internal reference
for UAT execution and issue reconciliation — the public description of these features lives on
<https://doc.ultikits.com/>. Update this file in the same pull request as any feature change.

## Conventions

- **ID grammar:** `<repo-slug>.<area>.<action>`, dot-separated, every segment lowercase ASCII
  drawn from `[a-z0-9-]`. `<repo-slug>` is the repository name lowercased with no separators —
  `ultikits` here, `ultichat`, `ultitools`, `ultitools-example` for
  `UltiTools-External-Example`. `<area>` is the feature section's slug. `<action>` is the verb.
  A `config` row is the one shape that exceeds three segments and is exempt from the
  lowercase-ASCII rule for its key-path suffix:
  `<repo-slug>.config.<file-stem>.<yml key path>`, the key path keeping its own dots and its own
  casing verbatim from the yml file. An ID changes only when the feature's identity changes,
  never on rewording. IDs are unique within a repository.
- **Kind**, exactly these eight values: `command`, `config`, `event`, `gui`, `scheduled`,
  `placeholder`, `persistence`, `gate`. Each maps one-to-one onto a reconciliation-table line.
  This module has no `event` rows (0 `@EventListener` classes) and no `scheduled` rows (0
  `@Scheduled` methods) — both counts are the deliberate 0-against-0 reconciliation lines this
  document states below, not omissions. It also has no `placeholder` rows (`plugin.yml` declares
  `softdepend: [PlaceholderAPI, Vault]`, but PlaceholderAPI is never referenced anywhere in
  `src/main/java` — only `EconomyUtils` for Vault) and no `gate` rows (`@ConditionalOnConfig`
  count is 0). The `enabled` key reads like a gate and, since `UltiKits/UltiKits#13` was fixed,
  really is one — but it is enforced by a runtime check at each command entry point
  (`KitCommands#refusedAsDisabled`), not by conditional bean registration, so it stays a `config`
  row and the `gate` count stays 0. The reason that choice was made rather than
  `@ConditionalOnConfig` is recorded in `## Configuration` below.
- **Tier**, exactly three: `player`, `admin`, `internal`. Judged from what the feature is for.
  This module's five non-`list`/`help` commands split cleanly: `open`/`claim` act on the sender's
  own access to kits (`player`), while `edit`/`create`/`delete`/`reload` change the shared kit
  catalogue every player draws from (`admin`).
- **Manual**, exactly three: `detailed`, `brief`, `none`.
- **Target**, exactly four: `player`, `console`, `both`, or `n/a`. `KitCommands` carries NO
  class-level `@CmdTarget` at all — `SenderTypeValidator`'s own no-arg constructor defaults the
  class-level expectation to `BOTH` in that case, then `CmdTargetComposition.resolve` narrows per
  method only where a method-level `@CmdTarget` is actually present. Four of the seven
  `@CmdMapping` methods (`onOpenGui`, `onClaim`, `onEdit`, `onCreate`) carry an explicit
  `@CmdTarget(PLAYER)`; the other three (`onList`, `onDelete`, `onReload`) carry none at all and
  therefore stay at the class's own `BOTH` default — this is a real, sourced fact about the
  composition rule, not an oversight to flag.
- **Permission:** the literal node string, `none`, or `n/a`. `KitCommands` declares one
  class-level permission (`ultikits.kits.use`) with no `requireOp` and no per-`@CmdMapping`
  override (grep confirms zero uses of `@CmdMapping(permission=)` in this class), so every
  command row below reads `ultikits.kits.use`. Four commands (`edit`, `create`, `delete`,
  `reload`) additionally run a hand-coded `CommandSender#hasPermission("ultikits.kits.admin")`
  check inside the method body — a second, finer-grained node the framework's own permission
  system does not enforce structurally, named in each affected row's Feature text.
- **Source:** `ClassName#member` — the class and member that actually reads or applies the
  feature — for every Kind, `config` included: all 3 `config` rows below cite the member that
  reads the key and the member that applies it, both of which exist since `UltiKits/UltiKits#13`
  was fixed (see `## Configuration`).
- **Row order:** by section, then by ID ascending within the section.
- **No manual prose:** no troubleshooting column, no explanatory paragraphs, no draft page text.
  A hazard noticed while reading becomes a negative checklist row, not a note here.

### This module's own i18n usage is a real, working exception to a pattern seen elsewhere in this fan-out

Unlike UltiSocial (see that repository's own `FEATURES.md`), this module's `i18n()` keys ARE the
literal Simplified Chinese text (e.g. `plugin.i18n(<Chinese sentence meaning "no kits available">)`, `KitCommands.java:73`), not an English-named key like
`no_kits_available`), and `lang/en.json` genuinely maps every one of them to a real English
sentence — confirmed by reading both `lang/en.json` and `lang/zh.json` in full and cross-checking
every `i18n(...)` call site in `src/main/java` against a key present in both files. `language: en`
therefore DOES change what every command/GUI row below displays, for the substantial majority of
this module's text. Two hardcoded (non-i18n) English string literals exist regardless of
`language`: `KitEditorGui.java:69`'s lore line `"Click to save kit contents"`, and
`KitCommands.java:181-184`'s FOUR help lines for `edit`/`create`/`delete`/`reload` (`"Edit kit"`,
`"Create kit"`, `"Delete kit"`, `"Reload kits"` — the other three help lines in the same block
(the bare `/kits`, `claim`, and `list` descriptions) DO route through `i18n()`, so this help
command's own text is itself a partial mix, named in that row below).

### Reconciliation command family

The canonical form for counting an annotation site across this repository's real sources:

```bash
find <repo-root> -path '*/src/main/java/*' -name '*.java' -not -path '*/target/*' \
  -not -path '*/.worktrees/*' -print0 | xargs -0 grep -nE '^[[:space:]]*@AnnotationName\b' | wc -l
```

This form defeats three measured traps, each of which produces a wrong-but-plausible number
rather than an error: multi-root repositories (UltiBot), git worktrees/build output
(UltiEconomy's `.worktrees/economy-v2/`), and javadoc/string-literal mentions of an annotation
name (defeated by the `^[[:space:]]*@` line-start anchor). None of the three traps applies to
this single-root, worktree-free module, but the robust `find` form is used regardless — the same
command must work unmodified across all 18 repositories.

**GUI page classes** are found structurally, not by grepping for an annotation — neither
`KitBrowserGui` nor `KitEditorGui` carries a page-marking annotation; both extend the
`obliviate-invs` library's own `Gui` base class directly, under the module's own `gui` package:

```bash
find <repo-root>/src/main/java -path '*/gui/*' -name '*.java' -not -path '*/target/*' | wc -l
```

**Positive control:** the line-start form returns `@CmdExecutor` = 1, `@CmdMapping` = 7,
`@EventListener` = 0, `@EventHandler` = 0, `@Scheduled` = 0, `@ConditionalOnConfig` = 0,
`@ConfigEntity` = 1, `@ConfigEntry` = 3, `@Table` = 1 — confirmed by reading `KitCommands.java`
directly (7 `@CmdMapping` sites at lines 78, 90, 105, 139, 164, 198, 220: ``, `claim <name>`,
`list`, `edit <name>`, `create <name>`, `delete <name>`, `reload`) and `KitsConfig.java` (3
`@ConfigEntry` sites at lines 15, 18, 22). The `find`-based GUI-class count above returns 2,
matching Phase 9's own independently-derived GUI-exclusion register for this module
(`KitBrowserGui`, `KitEditorGui` — see `.planning/phases/09-module-ecosystem-readiness-and-test-coverage/gui-exclusions/UltiKits.md`;
note that same register file also names two pre-6.3.0 base-class types removed outright in
this milestone (the old command-executor base class and the old data-entity base class) — not
GUI classes at all; confirmed neither name appears anywhere in this module's own source,
a targeted grep for both returns zero hits; `KitCommands` extends `BaseCommandExecutor` and
`KitClaimData` extends `BaseDataEntity<String>`, the current, non-removed generation of each).

**Reconciliation note — `@EventListener` (0 against 0) and `@Scheduled` (0 against 0):** both
lines are written deliberately rather than omitted. This is the smallest command surface in the
ecosystem (7 `@CmdMapping` sites, 3 `@ConfigEntry` keys) — precisely the shape where an omitted
zero line would be least noticed, which is why it is written here instead. Neither Bukkit event
handling nor a background scheduled task exists anywhere in this module's 9 source files; kit
data loads synchronously in `KitServiceImpl`'s constructor (`loadKits()`) and again on-demand via
`/kits reload`, never on a timer.

**This document's command-row count (8) exceeds the `@CmdMapping` annotation-site count (7) by
one, for the same reason the framework's own `FEATURES.md` documents for `/upm help`/`/ulticloud
help`:** `KitCommands` overrides `handleHelp(CommandSender)` but has no `@CmdMapping(format =
"help")` site of its own — `BaseCommandExecutor#onCommand` short-circuits a literal `help`
argument (and, separately, `matchMethod` returning null for an unrecognized format) straight to
`#handleHelp` before format-matching ever runs. The row below cites `#handleHelp`, the method
that actually executes.

## Kit Commands

`KitCommands` — class-level `@CmdExecutor(permission = "ultikits.kits.use", description =
<Simplified Chinese "kit management commands">, alias = {"kits", "kit"})`. No class-level
`@CmdTarget` (see Conventions' own note on this).

**Every row in this section is additionally gated on the `enabled` master switch.** While
`config/config.yml: enabled` is `false`, each of the seven `@CmdMapping` methods returns
immediately with `The kit system is currently disabled on this server` — before its own
`ultikits.kits.admin` check where it has one, and before the browser is constructed for the bare
`/kits`. The switch is `true` by default; see `## Configuration` for where it is read and why it
is a runtime check rather than a registration-time gate.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultikits.kits.claim | Claim a named kit for the sender, subject to permission/level/economy/cooldown/one-time checks and inventory space; on success delivers the kit's items and runs its configured player/console commands. For a PAID kit the price is withdrawn first and the withdrawal's own result is checked before anything is handed over, so a withdrawal that does not go through (the balance moved after the earlier affordability check, or the economy rejected the transaction) delivers no items, runs no commands, writes no claim row and replies `Payment failed - the kit was not claimed` — a distinct reply from the affordability check's own `Insufficient balance, requires N`. The server console gets a `WARNING` naming the kit, the player and the amount, throttled to the FIRST refusal for each kit in each server session (further refusals for that kit are silent, and the line says so; `/kits reload` re-arms it). On success the price is taken first, then the items, then the claim row, then the kit's player and console commands — the claim row deliberately precedes the commands, so a reward command that throws cannot leave a one-time kit silently claimable again | command | `/kits claim <name>` | ultikits.kits.use | player | player | brief | KitCommands#onClaim, KitService#claimKit, KitServiceImpl#deliverKit |
| ultikits.kits.create | Create a new kit definition from the sender's own current inventory contents (air slots filtered out), using the first item's material as the icon and a plain `&f<name>` display name; additionally requires the sender hold `ultikits.kits.admin` (a hand-coded body check, not a declared `@CmdMapping` permission) | command | `/kits create <name>` | ultikits.kits.use | player | admin | brief | KitCommands#onCreate, KitService#createKit |
| ultikits.kits.delete | Delete a kit definition's on-disk YAML file and remove it from the in-memory catalogue; additionally requires the sender hold `ultikits.kits.admin` (hand-coded body check); reachable from console (no method-level `@CmdTarget`, stays at the class's `BOTH` default) | command | `/kits delete <name>` | ultikits.kits.use | both | admin | brief | KitCommands#onDelete, KitService#deleteKit |
| ultikits.kits.edit | Open the kit editor GUI (`ultikits.gui.kit-editor`) for a named kit, pre-filled with its current items; additionally requires the sender hold `ultikits.kits.admin` (hand-coded body check) | command | `/kits edit <name>` | ultikits.kits.use | player | admin | brief | KitCommands#onEdit |
| ultikits.kits.help | Print the `/kits` command usage summary; a partial mix of `i18n()`-routed and hardcoded-English lines — the `edit`/`create`/`delete`/`reload` lines' own description text (`"Edit kit"`, `"Create kit"`, `"Delete kit"`, `"Reload kits"`) is hardcoded English regardless of `language`, while the other THREE lines (bare `/kits`, `claim`, `list`) route through `i18n()` and DO respect it | command | `/kits help` (no `@CmdMapping` site of its own — see the reconciliation note above) | ultikits.kits.use | both | player | none | KitCommands#handleHelp |
| ultikits.kits.list | List every kit the sender may see: for a player, only kits whose permission (if any) the player holds; for console, every kit unconditionally. Each line shows the kit's name, display name, and price (if not free); reachable from console (no method-level `@CmdTarget`, stays at the class's `BOTH` default) | command | `/kits list` | ultikits.kits.use | both | player | brief | KitCommands#onList, KitService#getAvailableKits |
| ultikits.kits.open | Open the paginated kit browser GUI (`ultikits.gui.kit-browser`) showing every kit available to the sender; this is the module's default (bare-argument) command | command | `/kits` (bare, no arguments) | ultikits.kits.use | player | player | brief | KitCommands#onOpenGui |
| ultikits.kits.reload | Reload every kit definition from disk (`kits/*.yml`), replacing the in-memory catalogue entirely; additionally requires the sender hold `ultikits.kits.admin` (hand-coded body check); reports the reloaded kit count; reachable from console (no method-level `@CmdTarget`, stays at the class's `BOTH` default) | command | `/kits reload` | ultikits.kits.use | both | admin | brief | KitCommands#onReload, KitService#reload |

## GUI

Two GUI page classes, neither carrying a page-marking annotation — identified structurally (see
Conventions' own reconciliation note for this Kind). Both extend the `obliviate-invs` library's
own `Gui` base class directly (unlike UltiBackup's `ForceRestoreConfirmPage`, which uses the
framework's own `BaseConfirmationPage`) — neither of this module's GUI classes goes through any
of this framework's own three GUI generations at all. Both are Phase 9's complete GUI-exclusion
register for this module.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultikits.gui.kit-browser | Paginated chest GUI listing every kit available to the viewer, showing up to `config/config.yml: kits_per_page` kits per page (default 28) and a `Page x/y` indicator whose page count is derived from that same value; the page size is read from the configuration on every open, so a reloaded value applies to the next open without a restart, each showing display name, description lore, price-or-free, level requirement (if any), and a live status line (available / on cooldown with remaining time / already claimed / insufficient level / insufficient funds); clicking a kit attempts to claim it immediately (no confirmation step) via the same logic as `ultikits.kits.claim`; on success for a paid kit it additionally says the price was deducted, which is now emitted only when the withdrawal actually succeeded, and a refused withdrawal instead says `Payment failed - the kit was not claimed` and leaves the browser open. A hand-rolled click-debounce (`KitBrowserGui#handleKitClick` compares its own `lastClickTime` field against `config/config.yml: click_cooldown_ms`, default 200ms) guards against rapid double-clicks producing a double-claim; the window is read at click time, so a reloaded value applies to the next click | gui | `ultikits.kits.open` | n/a | n/a | player | detailed | KitBrowserGui#onOpen, KitBrowserGui#buildKitIcon, KitBrowserGui#handleKitClick |
| ultikits.gui.kit-editor | Chest GUI for editing a kit's item contents directly by moving real items into/out of a 45-slot grid (slots 0-44), pre-filled with the kit's existing items on open; a Save button (slot 45) collects whatever is currently in those 45 slots and overwrites the kit's stored item data; a Cancel button (slot 53, hardcoded English lore text regardless of `language`) discards changes by simply closing the inventory — items placed into the grid before cancelling are NOT returned to the editor's own inventory automatically, they remain wherever Bukkit's own inventory-close handling puts them | gui | `ultikits.kits.edit` | n/a | n/a | admin | detailed | KitEditorGui#onOpen, KitEditorGui#handleSave |

## Data Persistence

Per this plan's own instruction, a kit's CONTENTS and a player's CLAIM COOLDOWN are two
independently-persisted things, given their own rows rather than folded into one.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultikits.storage.claim-history-survives-restart | A player's claim history (last-claim timestamp, cumulative claim count) is stored per player-per-kit via `DataOperator`/`@Table("kits_claims")` in whichever ORM backend the framework has configured; a cooldown or one-time-claim restriction observed before a restart is still enforced identically after one, because `getRemainingCooldown`/`checkCooldown` read this same persisted row, not any in-memory state | persistence | claim a kit that has a cooldown or is not re-buyable, then restart the server and attempt to claim it again | n/a | n/a | admin | brief | KitClaimData#KitClaimData, KitServiceImpl#getClaimData, KitServiceImpl#updateClaimData |
| ultikits.storage.kit-contents-survive-restart | A kit's definition (display name, description, price, level requirement, permission, re-buyable flag, cooldown, player/console commands, and its serialized item contents) is stored as a plain YAML file under `plugins/UltiTools/pluginConfig/UltiTools-Kits/kits/<name>.yml`, independent of the ORM/database entirely — surviving a restart is not a database-durability question for this row, it is the trivial fact that the file itself is still on disk; `loadKits()` re-parses every `.yml` file in that folder on every boot and on `/kits reload` | persistence | create or edit a kit, then restart the server and run `/kits list`/`/kits claim <name>` to confirm its contents are unchanged | n/a | n/a | admin | brief | KitServiceImpl#loadKits, KitServiceImpl#parseKitFile, KitServiceImpl#saveKitToFile |

## Configuration

Every `@ConfigEntry`-annotated field on this module's one `@ConfigEntity` class (3 keys total,
matching the reconciliation table's own `@ConfigEntry` count of 3 exactly). This module ships a
real `config/config.yml` resource under `src/main/resources` with all three keys at the same
defaults `KitsConfig` itself declares.

**All three keys were declared but read nowhere until `UltiKits/UltiKits#13` was fixed.** All
three are now wired, and each declared default was deliberately left at the value the hardcoded
constant it replaced already had (28, 200, `true`), so wiring them changed nothing on a server
that never edited the file: the defect was that the key was dead, not that its value was wrong.

**Where the `enabled` master switch short-circuits, and why there.** It is checked at each
`@CmdMapping` entry point of `KitCommands` (`KitCommands#refusedAsDisabled`, called as the first
statement of all seven), not by withholding the command registration. Two reasons, both
observable: an unregistered command answers with Bukkit's own `Unknown command`, which reads to a
player as a typo rather than as a deliberate setting; and `@ConditionalOnConfig` is evaluated once
at component scan, so a flip either way would need a full server restart, while a read at command
time follows `/ul reload UltiTools-Kits`. What this placement does not cover, stated rather than
left to be discovered: the kit browser is reachable only through `/kits`, so no new browser can be
opened while the switch is off, but a browser a player already had open when the switch was
flipped is not force-closed and its clicks keep claiming until it is closed.

| ID | Feature | Kind | How to reach | Permission | Target | Tier | Manual | Source |
|---|---|---|---|---|---|---|---|---|
| ultikits.config.config.click_cooldown_ms | The kit browser GUI's click-debounce window, in milliseconds: a click on a kit icon less than this long after the previous one is dropped without a message and without a claim attempt. Read at click time (`KitBrowserGui.java:201`), so `/ul reload UltiTools-Kits` applies a change to the next click without reopening the browser or restarting. `@Range(min = 50, max = 5000)` | config | `config/config.yml: click_cooldown_ms (default: 200)` | n/a | n/a | admin | brief | KitsConfig#clickCooldownMs, KitBrowserGui#handleKitClick |
| ultikits.config.config.enabled | Master switch for the kit system. While `false`, all seven `/kits` (`/kit`) sub-commands reply `The kit system is currently disabled on this server` (red) and do nothing else — including the bare `/kits` browser, which is therefore never opened, and including the four admin sub-commands, which are refused before their own `ultikits.kits.admin` check runs. Read at command time (`KitCommands.java:68`), so `/ul reload UltiTools-Kits` applies a flip in either direction without a restart; see this section's own note on why it is not a `@ConditionalOnConfig` gate and on the already-open-browser case | config | `config/config.yml: enabled (default: true)` | n/a | n/a | admin | brief | KitsConfig#enabled, KitCommands#refusedAsDisabled |
| ultikits.config.config.kits_per_page | The kit browser GUI's page size: how many kit icons one page shows, which also determines the start index of every later page and the `y` in the browser's `Page x/y` indicator. Read once per open (`KitBrowserGui.java:59`), so `/ul reload UltiTools-Kits` applies a change to the next open without a restart. `@Range(min = 7, max = 28)`, so the configured size can never exceed the 36 slots the browser reserves for kit icons | config | `config/config.yml: kits_per_page (default: 28)` | n/a | n/a | admin | brief | KitsConfig#kitsPerPage, KitBrowserGui#onOpen |
