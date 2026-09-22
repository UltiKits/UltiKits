# Changelog

All notable changes to this project are documented in this file.
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

本文件记录本项目的所有重要更改，格式基于 [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)。

## [Unreleased]

### Fixed

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
  applies a change without a server restart. A kit browser that was already open when the switch
  was flipped is not force-closed, but clicking a kit in it now gets the same refusal instead of a
  claim, so no kit can be taken while the switch is off (UltiKits/UltiKits#13).
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
  `/ul reload UltiTools-Kits` 即可生效，无需重启服务器。开关切换时玩家已经打开的浏览界面不会被强制关闭，
  但在其中点击礼包现在会收到同样的拒绝提示而不是领取成功，因此开关关闭期间无法领取到任何礼包
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
