# Changelog

All notable changes to this project are documented in this file.
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

本文件记录本项目的所有重要更改，格式基于 [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)。

## [Unreleased]

### Changed

- Language keys were renamed from Chinese sentences to ASCII keys (for example `kits.claim.success`).
  An operator who edited this module's `lang/en.json` or `lang/zh.json` must re-apply those edits to
  the new keys; until then the renamed messages show the new built-in text. A server whose language
  files were never edited needs no action.
- 语言键已从中文句子改为 ASCII 键（例如 `kits.claim.success`）。改过本模块 `lang/en.json` 或
  `lang/zh.json` 的运维需要把改动重新套到新键上；在此之前，这些消息显示新的内置文本。从未改过语言文件的服务器无需任何操作。

### Removed

- Four language entries that no code ever displayed were removed from both language files (a
  "Kit System" title, a per-kit "Kit loaded:" line, an "Economy system is not available" message and a
  players-only command message). Nothing an operator or player sees changes. The economy message
  describes a state the module reports today as an insufficient balance; showing it is tracked in
  UltiKits/UltiKits#32.
- 从两份语言文件中删除了四条从未被任何代码显示过的条目（「礼包系统」标题、逐个礼包的「已加载礼包:」日志、
  「经济系统不可用」消息和仅限玩家执行的命令提示）。运维和玩家看到的内容没有任何变化。其中经济系统消息描述的状态，
  模块目前报告为余额不足；显示该消息由 UltiKits/UltiKits#32 跟踪。

### Fixed

- When a kit claim cannot be recorded, the claim is refused and any payment refunded, so a one-time
  kit can no longer be claimed twice after a storage failure. A claim is now charged, then recorded,
  then handed over; if the record cannot be written, nothing is given, no reward command runs and
  the player is told to try again. If the refund fails as well, the player is told so and the console
  logs an error naming the player, the kit and the amount (UltiKits/UltiKits#26).
- 修复：领取记录写入失败时拒绝领取并退款，一次性礼包不会因存储故障被重复领取。领取顺序改为先扣款、再写入记录、再发放；
  记录无法写入时不发放任何物品、不执行奖励命令，并提示玩家重试。若退款也失败，会如实告知玩家，并在控制台记录包含玩家、礼包和金额的错误（UltiKits/UltiKits#26）。

- A kit containing a stack larger than its item's maximum stack size is refused before payment when
  it will not fit — the claim now works out the fit the way the inventory fills, topping up matching
  partial stacks first and splitting each stack at its own maximum — and anything that still does not fit is dropped at the player's feet with a message
  instead of being destroyed (UltiKits/UltiKits#24).
- 修复：超出最大堆叠数的物品不再在领取时被销毁。领取前按背包实际的放置方式判断能否放下（先补满相同物品的未满堆，每个物品堆按自身的最大堆叠数拆分），放不下时先拒绝领取且不扣款；
  仍放不下的部分会掉落在玩家脚下并提示（UltiKits/UltiKits#24）。

- `/kits delete` no longer reports a kit as deleted when its file could not be removed (or the kits
  folder could not be read). The kit now stays loaded, the sender is told it was not deleted, and the
  console names the file or folder, so a kit an
  admin was told is gone can no longer come back on the next `/kits reload` or restart. Deleting and
  saving a kit now use the file the kit was loaded from, so a hand-placed kit file whose name has
  capital letters (`VIP.yml`) is deleted, and saved from the editor, like any other. A save from the
  editor that fails no longer changes the loaded kit, so a claim still hands out what the file holds.
  A kit file is now written through a temporary file moved into place in one step, so a failed save
  leaves the old file intact and a failed `/kits create` leaves no partial file; the file keeps its
  permissions, a symbolic link keeps pointing where it did, a read-only kit file is not replaced, and
  the example kit is copied the same way. When more than one
  file defines the same kit (`VIP.yml` and `vip.yml` on a case-sensitive file system), `/kits edit`,
  the editor's Save button, `/kits create` and `/kits delete` change nothing and name the files; one of them still
  loads, so the kit can be claimed, and `/kits reload` names them on the console
  (UltiKits/UltiKits#23).
- 修复：礼包文件删除失败（或礼包文件夹无法读取）时，`/kits delete` 不再报告删除成功。礼包会保持加载，执行者会被告知未删除，控制台会记录该文件或文件夹的路径，
  因此被告知已删除的礼包不会在下次 `/kits reload` 或重启后重新出现。删除和保存礼包现在使用礼包加载时的那个文件，因此文件名含大写字母的手放礼包文件（如 `VIP.yml`）
  也能像其他礼包一样被删除，并能从编辑界面保存。编辑界面保存失败时不再改动已加载的礼包，领取时发放的仍是文件中的物品。礼包文件现在先写入临时文件，再一次性移动到位，因此保存失败时原文件保持完整，`/kits create` 失败时也不会留下残缺文件；文件保留原有权限，符号链接仍指向原处，只读的礼包文件不会被替换，示例礼包也按同样方式复制。当多个文件定义同一礼包时（在区分大小写的文件系统上同时存在 `VIP.yml` 和 `vip.yml`），`/kits edit`、编辑界面的保存按钮、`/kits create` 和 `/kits delete` 不做任何修改并列出这些文件；其中一个仍会被加载，礼包照常可以领取，`/kits reload` 也会在控制台列出这些文件（UltiKits/UltiKits#23）。

- `language: zh` now applies to the text that was fixed English: the kit editor's save-button lore
  (`Click to save kit contents`), the `/kits help` lines for `edit`, `create`, `delete` and `reload`,
  and eight console lines (a refused kit payment, a kit file that fails to load or has an invalid
  icon, failures to save a kit file, copy the example kit, serialize or deserialize kit items, and
  update a claim record). The fallback display name of a kit file with no `displayName` is now
  `Kit` / `礼包` by language instead of always `Kit`. The `/kits` command description, which showed a
  Chinese sentence in every language because it was missing from both language files, is now
  `Kit management command` under `language: en`. English wording is unchanged except where it was wrong:
  the `/kits help` line for `claim` said `Available` (the GUI status label) and now says `Claim a kit`;
  a failed `/kits create` and a failed save in the kit editor said `Error claiming kit` and now say
  `Error creating kit` and `Error saving kit` (UltiKits/UltiKits#31).
- `language: zh` 现在对原先写死为英文的文本生效：礼包编辑器保存按钮的说明（`Click to save kit contents`）、
  `/kits help` 中 `edit`、`create`、`delete`、`reload` 四行，以及八条控制台日志（礼包扣款被拒、礼包文件加载失败或图标无效、
  保存礼包文件、复制示例礼包、序列化或反序列化礼包物品、更新领取记录失败）。没有 `displayName` 的礼包文件的后备显示名
  现在按语言为 `Kit` / `礼包`，不再总是 `Kit`。`/kits` 命令描述此前在两份语言文件中都缺失、在任何语言下都显示中文句子，
  现在 `language: en` 下为 `Kit management command`。英文措辞不变，错误之处除外：`/kits help` 中 `claim` 一行原为 `Available`
  （界面状态标签），现为 `Claim a kit`；`/kits create` 失败和礼包编辑器保存失败原先都显示 `Error claiming kit`，现分别为
  `Error creating kit` 和 `Error saving kit`（UltiKits/UltiKits#31）。

- Saving a kit from the kit editor is now refused while the kit system is switched off. An admin
  who had `/kits edit` open when an operator set `enabled: false` could still press Save and change
  the kit's contents; the Save button now answers `The kit system is currently disabled on this
  server` and writes nothing (UltiKits/UltiKits#13).
- 礼包系统关闭期间，礼包编辑界面的保存操作现在会被拒绝。此前管理员若在运维把 `enabled` 改为 `false`
  之前就打开了 `/kits edit`，仍可点击保存并修改礼包内容；现在保存按钮会回复“礼包系统当前已关闭”，
  且不写入任何内容（UltiKits/UltiKits#13）。
- The kit browser no longer shows an empty page titled with a page number that cannot exist. If the
  kit list shrinks while a browser is open — `/kits reload` removing kits, or `kits_per_page` being
  raised so there are fewer pages — the page the browser was on can stop existing; it now shows the
  last real page instead, with working navigation arrows (UltiKits/UltiKits#13).
- 礼包浏览界面不会再出现「页码超出总页数的空白页面」。当界面打开期间礼包列表变短时——`/kits reload`
  删除了礼包，或 `kits_per_page` 被调大导致总页数减少——原先所在的页可能已不存在；现在会改为显示最后
  一个真实存在的页面，翻页按钮同样正常（UltiKits/UltiKits#13）。
- `config/config.yml: kits_per_page` now decides how many kits one page of the kit browser shows.
  Previously the browser paged on a hardcoded 28 regardless of what the key said; it now follows
  `kits_per_page`, whose default is still 28, so a server that never edited the key sees no change.
  The page count in the browser's `Page x/y` indicator follows the same value
  (UltiKits/UltiKits#13).
- `config/config.yml: click_cooldown_ms` now decides the kit browser's click-debounce window.
  Previously the browser used a hardcoded 200 milliseconds regardless of what the key said; it now
  follows `click_cooldown_ms`, whose default is still 200, so a server that never edited the key
  sees no change (UltiKits/UltiKits#13).
- `config/config.yml: enabled` now really switches the kit system off. Previously nothing read it
  and the kit system was active whatever it said. With `enabled: false`, every `/kits` (`/kit`)
  sub-command — including the bare `/kits` browser, `claim`, `list`, `edit`, `create`, `delete` and
  `reload` — now replies `The kit system is currently disabled on this server` and does nothing
  else. The default is still `true`, so a server that never edited the key sees no change. The
  switch is read when a command runs, not when the module loads, so `/ul reload UltiTools-Kits`
  applies a change without a server restart. `/kits help`, and any unrecognised `/kits` argument,
  are refused too rather than printing a usage list for sub-commands that all refuse. A kit browser
  that was already open when the switch was flipped is not force-closed, but clicking a kit in it
  gets the same refusal instead of a claim and its page arrows refuse to open a further page, so
  while the switch is off no kit can be taken and no new page of the catalogue is shown. Claiming
  is refused inside the kit service itself rather than at each command, so the guarantee holds for
  any future way of claiming as well (UltiKits/UltiKits#13).
- `config/config.yml: kits_per_page` 现在真正决定礼包浏览界面一页显示多少个礼包。此前无论该键写什么，
  界面都按硬编码的 28 分页；现在按 `kits_per_page` 取值，其默认值仍为 28，因此从未修改过该键的服务器
  行为不变。界面 `Page x/y` 指示器的总页数同样依据该值（UltiKits/UltiKits#13）。
- `config/config.yml: click_cooldown_ms` 现在真正决定礼包浏览界面的点击防抖窗口。此前无论该键写什么，
  界面都使用硬编码的 200 毫秒；现在按 `click_cooldown_ms` 取值，其默认值仍为 200，因此从未修改过该键的
  服务器行为不变（UltiKits/UltiKits#13）。
- `config/config.yml: enabled` 现在真正可以关闭礼包系统。此前没有任何代码读取它，无论其取值如何礼包系统
  都处于启用状态。设为 `false` 后，`/kits`（`/kit`）的每一条子命令——包括不带参数的浏览界面、`claim`、
  `list`、`edit`、`create`、`delete` 与 `reload`——都会回复“礼包系统当前已关闭”并不再执行任何操作。默认值
  仍为 `true`，因此从未修改过该键的服务器行为不变。该开关在命令执行时读取，而非模块加载时读取，因此
  `/ul reload UltiTools-Kits` 即可生效，无需重启服务器。`/kits help` 以及任何无法识别的 `/kits` 参数同样会被
  拒绝，而不是列出一串全部会被拒绝的子命令。开关切换时玩家已经打开的浏览界面不会被强制关闭，但在其中点击礼包
  会收到同样的拒绝提示而不是领取成功，翻页按钮也会拒绝打开新页面，因此开关关闭期间既领取不到礼包，也看不到
  新的礼包列表页。领取的拦截放在礼包服务内部而非各个命令中，因此将来新增的领取入口同样受该开关约束
  （UltiKits/UltiKits#13）。
- A paid kit whose price could not actually be withdrawn is no longer delivered. Previously, when
  the balance check passed but the withdrawal itself did not go through — the balance was spent
  elsewhere in between, or the economy plugin rejected the transaction — `/kits claim <name>` and
  the kit browser still gave the player the items, ran the kit's configured player and console
  commands, recorded a one-time kit as claimed, and the browser told the player the price had been
  deducted. Both paths now reply `Payment failed - the kit was not claimed`, deliver nothing and
  record nothing, and the server console gets a `WARNING` naming the kit, the player and the
  amount — written for the first refusal of each kit in each server session, so an economy
  outage cannot bury the log; `/kits reload` re-arms it (UltiKits/UltiKits#20).
- A successful claim now records the claim before running the kit's configured player and
  console commands, instead of after. A reward command that fails used to leave the player
  charged and holding the items with no claim recorded, which silently made a one-time kit
  claimable again (UltiKits/UltiKits#20).
- 付费礼包若扣款未真正成功，现在不再发放。此前只要余额检查通过而扣款本身失败——余额在两次调用之间被
  花掉，或经济插件拒绝了该笔交易——`/kits claim <名称>` 与礼包浏览界面仍会把物品发给玩家、执行礼包
  配置的玩家命令与控制台命令、把一次性礼包记为已领取，浏览界面还会提示价格“已扣除”。现在两条路径都会
  回复“扣款失败，礼包未领取”，既不发放也不记录，服务器控制台还会输出一条包含礼包名、玩家名与金额的
  `WARNING`——每个礼包每个会话只输出一次，避免经济系统故障时刷屏；`/kits reload` 可重置
  （UltiKits/UltiKits#20）。
- 领取成功时，领取记录现在先于礼包配置的玩家命令与控制台命令写入，而非之后。此前奖励命令若执行
  失败，会出现玩家已被扣款、已拿到物品、却没有领取记录的状态，使一次性礼包被悄悄重新变为可领取
  （UltiKits/UltiKits#20）。
- After `/upm uninstall UltiTools-Kits`, this module's `/kits` (`/kit`) command is now really removed.
  Previously the command stayed registered until the server restarted, because this module replaced
  the framework's unload method with an empty one (UltiKits/UltiKits#18).
- 执行 `/upm uninstall UltiTools-Kits` 后，本模块的 `/kits`（`/kit`）命令现在会被真正移除。此前该命令会一直
  保留到服务器重启，因为本模块用一个空方法替换了框架的卸载方法（UltiKits/UltiKits#18）。
