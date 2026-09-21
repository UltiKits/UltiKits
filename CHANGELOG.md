# Changelog

All notable changes to this project are documented in this file.
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

本文件记录本项目的所有重要更改，格式基于 [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)。

## [Unreleased]

### Fixed

- A paid kit whose price could not actually be withdrawn is no longer delivered. Previously, when
  the balance check passed but the withdrawal itself did not go through — the balance was spent
  elsewhere in between, or the economy plugin rejected the transaction — `/kits claim <name>` and
  the kit browser still gave the player the items, ran the kit's configured player and console
  commands, recorded a one-time kit as claimed, and the browser told the player the price had been
  deducted. Both paths now reply `Payment failed - the kit was not claimed`, deliver nothing and
  record nothing, and the server console gets a `WARNING` naming the kit, the player and the amount
  (UltiKits/UltiKits#20).
- 付费礼包若扣款未真正成功，现在不再发放。此前只要余额检查通过而扣款本身失败——余额在两次调用之间被
  花掉，或经济插件拒绝了该笔交易——`/kits claim <名称>` 与礼包浏览界面仍会把物品发给玩家、执行礼包
  配置的玩家命令与控制台命令、把一次性礼包记为已领取，浏览界面还会提示价格“已扣除”。现在两条路径都会
  回复“扣款失败，礼包未领取”，既不发放也不记录，服务器控制台还会输出一条包含礼包名、玩家名与金额的
  `WARNING`（UltiKits/UltiKits#20）。
- After `/upm uninstall UltiTools-Kits`, this module's `/kits` (`/kit`) command is now really removed.
  Previously the command stayed registered until the server restarted, because this module replaced
  the framework's unload method with an empty one (UltiKits/UltiKits#18).
- 执行 `/upm uninstall UltiTools-Kits` 后，本模块的 `/kits`（`/kit`）命令现在会被真正移除。此前该命令会一直
  保留到服务器重启，因为本模块用一个空方法替换了框架的卸载方法（UltiKits/UltiKits#18）。
