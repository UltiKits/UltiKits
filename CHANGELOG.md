# Changelog

All notable changes to this project are documented in this file.
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

本文件记录本项目的所有重要更改，格式基于 [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)。

## [Unreleased]

### Changed

- The example kit created on first start now follows the server's `language`: an English server gets
  a `Starter Kit` with English lore, a Chinese one `新手礼包`, and a language the module has no
  example for gets the English one. Before, the one shipped example was Chinese under every language.
  Existing installs are not affected: the example is copied only when the kits folder does not exist
  yet (UltiKits/UltiKits#33).
- 首次启动时创建的示例礼包现在跟随服务器的 `language`：英文服务器得到英文说明的 `Starter Kit`，中文服务器得到
  `新手礼包`，模块没有示例的语言使用英文版。此前唯一的示例在任何语言下都是中文。已有安装不受影响：只有礼包文件夹尚不
  存在时才会复制示例（UltiKits/UltiKits#33）。
- Language keys were renamed from Chinese sentences to ASCII keys (for example `kits.claim.success`).
  This module's official language files (`lang/en.json` and `lang/zh.json` under
  `plugins/UltiTools/pluginConfig/UltiTools-Kits/`) belong to UltiTools: from UltiTools-API 6.3.0 an edited
  official file is restored to the shipped text at every start, its edited copy kept as a backup and
  named in the server log, so edits made there are not kept. To customise messages, copy the official
  file under a new name that starts with its language code and a hyphen (`en.json` to `en-myserver.json`),
  edit the copy against the new keys, and set `language: en-myserver` in `plugins/UltiTools/config.yml`.
  A server whose language files were never edited needs no action.
- 语言键已从中文句子改为 ASCII 键（例如 `kits.claim.success`）。本模块的官方语言文件
  （`plugins/UltiTools/pluginConfig/UltiTools-Kits/` 下的 `lang/en.json` 和 `lang/zh.json`）归 UltiTools 所有：
  自 UltiTools-API 6.3.0 起，被修改过的官方文件会在每次启动时恢复为自带内容，修改过的副本作为备份保留并在服务器日志中注明，
  因此在其中所做的修改不会保留。要自定义消息，请把官方文件复制为以语言代码加连字符开头的新名称（`zh.json` → `zh-myserver.json`），
  按新键修改副本，然后在 `plugins/UltiTools/config.yml` 中设置 `language: zh-myserver`。从未改过语言文件的服务器无需任何操作。

### Removed

- The "Error claiming kit" message and the claim outcome behind it were removed: no claim ever
  produced that outcome, so the message could not be shown. Every claim outcome keeps its own reply.
  An operator who translated the `kits.claim.error` entry can drop it (UltiKits/UltiKits#27).
- 删除了「领取礼包时发生错误」消息及其背后的领取结果：没有任何领取会产生这个结果，因此该消息从来无法显示。每种领取结果
  仍有各自的回复。翻译过 `kits.claim.error` 条目的运维可以删掉它（UltiKits/UltiKits#27）。
- Four language entries that no code ever displayed were removed from both language files (a
  "Kit System" title, a per-kit "Kit loaded:" line, an "Economy system is not available" message and a
  players-only command message). Nothing an operator or player sees changes. The economy message
  describes a state the module reports today as an insufficient balance; showing it is tracked in
  UltiKits/UltiKits#32.
- 从两份语言文件中删除了四条从未被任何代码显示过的条目（「礼包系统」标题、逐个礼包的「已加载礼包:」日志、
  「经济系统不可用」消息和仅限玩家执行的命令提示）。运维和玩家看到的内容没有任何变化。其中经济系统消息描述的状态，
  模块目前报告为余额不足；显示该消息由 UltiKits/UltiKits#32 跟踪。

### Fixed

- Saving in the kit editor (`/kits edit <kit>`, then Save) now writes only the kit's `items` into its file.
  Before, it rebuilt the whole file from the loaded kit: every comment and every key the module does not
  know was dropped, values the loader had replaced were written back (an invalid `icon` as `CHEST`, a
  negative or non-numeric `price`, `levelRequired` or `cooldown` as `0`, a mistyped `reBuyable` as
  `false`), and a hand edit made after the kit was loaded was reverted. Now every other line stays byte for
  byte. If the kit's file changed on disk since it was loaded, nothing is saved and the editor says so and
  asks for `/kits reload` first; if writing the items would change anything else in the file (YAML anchors,
  for example) or the file is not UTF-8 text, nothing is saved and the editor says the kit was not saved,
  with the reason in the server log. `/kits create` still only ever creates a new file
  (UltiKits/UltiKits#43).
- 礼包编辑器保存（`/kits edit <礼包>` 后点击保存）现在只把该礼包的 `items` 写入文件。此前它会按已加载的礼包重写整个文件：
  所有注释和模块不认识的键被删除，加载时被替换的值被写回（无效的 `icon` 写成 `CHEST`，负数或非数字的 `price`、`levelRequired`、
  `cooldown` 写成 `0`，拼错的 `reBuyable` 写成 `false`），加载后的手动修改被还原。现在其余每一行都逐字节保持不变。礼包文件在
  加载后被修改过时不保存，编辑器会提示先执行 `/kits reload`；写入物品会改动文件中的其他内容（例如 YAML 锚点）或文件不是 UTF-8
  文本时也不保存，编辑器提示未保存，原因见服务器日志。`/kits create` 仍然只会新建文件（UltiKits/UltiKits#43）。
- A kit's player reward commands (`playerCommands`) now run one tick after the claim, like its console
  commands, instead of inside the claim. Claiming from the kit browser ran them inside the inventory
  click, where a reward command that opens another GUI could not open it, or had it closed again at once
  by the browser's own close after a successful claim. The commands still run in list order and before the
  console commands; a command that does not run is still reported to the player and logged, now one tick
  later, and a player who has disconnected by then has no command run, with each logged as not run
  (UltiKits/UltiKits#41).
- 礼包的玩家奖励命令（`playerCommands`）现在和控制台命令一样，在领取后的下一刻执行，而不是在领取过程中执行。从礼包界面点击领取时，
  命令此前在背包点击事件内运行，会打开其他界面的奖励命令可能打不开，或在领取成功后被礼包界面自己的关闭立即关掉。命令仍按
  列表顺序、先于控制台命令执行；未执行的命令仍会告知玩家并记录警告，只是晚一刻（UltiKits/UltiKits#41）。
- A negative or non-numeric `price`, `cooldown` or `levelRequired` in a kit file is no longer read as
  "none" without a word. Before, `price: -5` made the kit free, `levelRequired: -1` removed the level
  requirement, a negative `cooldown` removed the cooldown, and a quoted `price: "250"` was read as `0`,
  none of them logged. Now loading the kit logs one warning naming the kit, the key and the value, and
  uses that key's default (`0`) instead; `0`, positive values and absent keys are unchanged
  (UltiKits/UltiKits#40).
- 礼包文件里负数或非数字的 `price`、`cooldown`、`levelRequired` 不再被无声地当作「没有」。此前 `price: -5` 会让礼包免费，
  `levelRequired: -1` 取消等级要求，负数 `cooldown` 取消冷却，带引号的 `price: "250"` 被当作 `0`，且都不记录日志。现在加载
  礼包时会记录一条警告，点名礼包、键和原值，并改用该键的默认值（`0`）；`0`、正数和未填写的键不变（UltiKits/UltiKits#40）。
- A kit claim is no longer refused with "Inventory full" on Paper 1.21.1 and earlier when an
  over-sized stack would fit. Those versions fill an empty slot up to the inventory's maximum (99), not
  the item's (64); the module now probes which behaviour the server has once at start and counts empty
  slots accordingly. Nothing was lost before: the claim was refused before any charge
  (UltiKits/UltiKits#38).
- 在 Paper 1.21.1 及更早版本上，放得下的超量物品堆不再被误判为「背包已满」而拒绝领取。这些版本向空格放入物品时按背包上限
  （99）而不是物品上限（64）放置；模块现在会在启动时探测一次服务器的实际行为并据此计算空格。此前不会丢失物品：领取在扣款
  前就被拒绝（UltiKits/UltiKits#38）。
- A kit reward command that does not run is now reported instead of being ignored. A player command
  that is unknown, refused or throws tells the player which reward did not run; every failed reward
  command, player or console, logs a warning naming the kit, the command and the player. The claim
  still stands and is not refunded, so an operator can make the reward good by hand. Before, both
  results were discarded and the claim reported plain success (UltiKits/UltiKits#25).
- 礼包奖励命令未能执行时，现在会如实报告，而不是被忽略。以玩家身份执行的命令若不存在、被拒绝或出错，会告诉玩家是哪项
  奖励没有执行；无论玩家命令还是控制台命令，失败都会记录一条写明礼包、命令和玩家的警告。领取仍然有效且不退款，运维可
  手动补发。此前两类命令的结果都被丢弃，领取直接报告成功（UltiKits/UltiKits#25）。
- A console reward command whose scheduling itself fails (`UltiTools` registered but disabled between
  the lookup and the scheduling call, or the scheduler otherwise refuses the task) is now logged the
  same way an unschedulable one already was, and every console command after it in the kit's list is
  still attempted. Before, that failure escaped the claim after payment, recording and item delivery
  had already run, produced no warning for that command, and silently skipped every console command
  after it in the list (UltiKits/UltiKits#39 review of #25).
- 现在控制台奖励命令的调度本身失败时（`UltiTools` 在查找之后、调度之前被禁用，或调度器出于其它原因拒绝该任务），
  会像既有的「无法调度」情形一样记录日志，且该礼包列表中排在它之后的控制台命令仍会继续尝试执行。此前该失败会在扣款、
  记录和物品发放都已完成之后逃出领取流程，既不为该命令记录任何警告，也会静默跳过列表中排在它之后的所有控制台命令
  （UltiKits/UltiKits#39 复查 #25）。
- `/kits list` and the "Insufficient balance, requires …" reply now write a kit's price in the server
  economy's own format, as the kit browser already did. Before, they printed a hard-coded `$` and the
  raw number (`($100.0)`), so a server whose currency is not dollars showed one price two ways
  (UltiKits/UltiKits#34).
- `/kits list` 和「余额不足，需要 …」回复现在按服务器经济系统自己的格式显示礼包价格，与礼包浏览界面一致。此前它们写死
  `$` 并显示原始数字（`($100.0)`），货币不是美元的服务器上同一价格会有两种写法（UltiKits/UltiKits#34）。
- A paid kit claimed on a server with no economy plugin now says so ("This kit has a price, but the
  server has no economy plugin to pay it with"), and the kit browser shows "No economy on this server"
  for it. Before, both said "Insufficient balance", which no balance could fix and which hid the missing
  economy (UltiKits/UltiKits#32).
- 在没有经济插件的服务器上领取付费礼包时，现在会如实提示（「此礼包需要付费，但服务器没有可用的经济插件」），礼包浏览界面
  显示「服务器没有经济系统」。此前两处都显示「余额不足」，但任何余额都无法满足，也掩盖了经济系统缺失（UltiKits/UltiKits#32）。
- The kit editor (`/kits edit`) now saves moves and additions: items can be moved within its grid and
  added from the player's own inventory, and Save writes them into the kit. Before, the GUI library
  cancelled every click in the grid, so Save re-saved the items the editor opened with. The bottom row
  of buttons stays fixed. When the editor closes without a successful save, the items the player put
  into the grid go back to the player instead of being thrown away (UltiKits/UltiKits#16).
- 礼包编辑界面（`/kits edit`）现在会保存移动和新增的物品：可以在格子内移动物品、从自己的背包放入物品，保存后写入礼包。
  此前 GUI 库会取消格子内的每一次点击，保存时只是把打开时的物品原样再存一遍。底部按钮行保持固定。未成功保存就关闭
  编辑界面时，玩家放入格子的物品会退还给玩家，而不是随界面一起丢弃（UltiKits/UltiKits#16）。
- Taking a pre-filled kit item out of the editor's grid and then cancelling (or closing the window
  any other way that is not a successful Save) no longer leaves the player holding a free duplicate.
  Cancelling never writes the kit file, so the kit still has the item; whatever the grid opened with
  that the grid no longer accounts for at close time is now reclaimed the same way an item they added
  is given back to them, from wherever it could be: the player's own inventory (the common case), or
  still on the cursor if Esc closed the window mid-drag, before it was placed anywhere -- reclaimed
  there too, before the server would otherwise hand it back to the player once the window finishes
  closing. A cancelled edit now costs nothing in either direction (UltiKits/UltiKits#38 review of #39).
- 现在在编辑界面的格子中取出预填充的礼包物品后按取消（或以非成功保存的其它方式关闭窗口），不会再让玩家白得一份重复物品。
  取消操作从不写入礼包文件，因此礼包仍持有该物品；关闭时格子里对不上打开时数量的部分，现在会像找回玩家新增的物品那样，
  从它可能所在的任何位置收回：玩家自己的背包（常见情形），或者——如果是在拖动途中按 Esc 关闭窗口、物品尚未放置时——
  仍停留在光标上；这种情况下也会在物品被服务器交还给玩家之前收回。取消编辑现在在两个方向上都不产生任何得失
  （UltiKits/UltiKits#38 复查 #39）。
- Pressing Q (or Ctrl+Q), or Creative-mode middle-clicking (clone), over a pre-filled item in the
  editor's grid no longer does anything. The first drops the item into the world, a place the
  reconciliation above cannot see or reach; the second leaves the original in place and puts a free
  copy on the cursor, so reconciliation never even sees anything missing to reclaim. Either way,
  cancelling afterward left the kit file unchanged and a free duplicate of its item, on the ground or
  in the player's inventory. Both are refused only for the grid: the GUI library this editor is built
  on only honours a refusal there, not for a click confined to the player's own inventory, where a
  pre-filled item taken out first (an ordinary move) and then Q-dropped or cloned still reaches the
  same outcome in two steps -- a known limitation reported for a decision, not fixed here
  (UltiKits/UltiKits#39 review).
- 现在在编辑界面的格子中对预填充物品按 Q（或 Ctrl+Q），或在创造模式下按住鼠标中键（复制），都不再产生任何效果。前者会把
  物品扔进世界，是上面的找回机制既看不到也够不到的地方；后者会保留原物品不动、在光标上放一份免费副本，因此找回机制根本
  不会发现有任何缺失需要收回。无论哪一种，此前取消编辑都会让礼包文件保持不变，同时多出一份该物品的重复品——落在地上，
  或进了玩家的背包。两者都只在格子内被拒绝：本编辑界面所基于的 GUI 库只在格子内才会执行这类拒绝，对于完全发生在玩家
  自己背包内的点击则不会——先把预填充物品移出格子（一次普通操作），再在背包内按 Q 丢弃或克隆，仍会在两步之内达到同样的
  结果；这是已知的限制，已上报供决策，本次未修复（UltiKits/UltiKits#39 复查）。
- Cancelling the editor with a full inventory now reclaims the kit's own copy before handing back an
  item the player added, so the returned item lands in the slot the reclaim just freed instead of
  being dropped at the player's feet for want of room. Before, the order was reversed: a personal item
  swapped into the grid for a pre-filled one could be dropped on the ground -- exposed to despawning or
  another player picking it up -- even though the exact slot it needed was about to be freed one step
  later (UltiKits/UltiKits#39 review).
- 现在在背包已满的情况下取消编辑，会先收回礼包自身的物品副本，再退还玩家新增的物品，使被退还的物品落入刚刚腾出的格位，
  而不是因为没有空位被丢在玩家脚下。此前顺序相反：把个人物品与预填充物品互换进格子后，即使下一步就会腾出所需的那个格位，
  该个人物品仍可能被丢在地上——面临消失或被他人拾取的风险（UltiKits/UltiKits#39 复查）。
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
  When more than one file defines the same kit (`VIP.yml` and `vip.yml` on a case-sensitive file
  system), `/kits edit`, the editor's Save button, `/kits create` and `/kits delete` change nothing
  and name the files; one of them still loads, so the kit can be claimed, and `/kits reload` names
  them on the console (UltiKits/UltiKits#23). `/kits create` now refuses a name holding `/`, `\` or
  `..`, which used to write the kit file outside the kits folder, and refuses a name that a file in
  the kits folder already loads as (placed by hand without a reload, or unreadable), naming the file
  instead of overwriting it (UltiKits/UltiKits#37). A new kit's file (`/kits create`) is still written the
  way earlier versions wrote it, so a crash during the create can leave it cut off; crash-safe writes for
  modules are tracked in UltiKits/UltiTools-Reborn#545. The editor's save replaces the file atomically since
  UltiKits/UltiKits#43.
- 修复：礼包文件删除失败（或礼包文件夹无法读取）时，`/kits delete` 不再报告删除成功。礼包会保持加载，执行者会被告知未删除，控制台会记录该文件或文件夹的路径，
  因此被告知已删除的礼包不会在下次 `/kits reload` 或重启后重新出现。删除和保存礼包现在使用礼包加载时的那个文件，因此文件名含大写字母的手放礼包文件（如 `VIP.yml`）
  也能像其他礼包一样被删除，并能从编辑界面保存。编辑界面保存失败时不再改动已加载的礼包，领取时发放的仍是文件中的物品。当多个文件定义同一礼包时（在区分大小写的文件系统上同时存在 `VIP.yml` 和 `vip.yml`），`/kits edit`、编辑界面的保存按钮、`/kits create` 和 `/kits delete` 不做任何修改并列出这些文件；其中一个仍会被加载，礼包照常可以领取，`/kits reload` 也会在控制台列出这些文件（UltiKits/UltiKits#23）。`/kits create` 现在会拒绝含 `/`、`\` 或 `..` 的礼包名（以前会把礼包文件写到 kits 文件夹之外），也会拒绝礼包文件夹中已有文件（手工放入但未 reload 的，或无法解析的）加载为该名字的礼包名，并列出该文件而不是覆盖它（UltiKits/UltiKits#37）。新礼包的文件（`/kits create`）仍按以前版本的方式写入，创建过程中崩服可能留下残缺文件；模块的崩溃安全写入在 UltiKits/UltiTools-Reborn#545 中跟踪。自 UltiKits/UltiKits#43 起，编辑界面的保存以原子方式替换文件。

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
