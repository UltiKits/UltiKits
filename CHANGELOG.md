# Changelog

All notable changes to this project are documented in this file.
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

本文件记录本项目的所有重要更改，格式基于 [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)。

## [Unreleased]

### Fixed

- Unloading this module (`/upm uninstall UltiTools-Kits`, or server shutdown) now unregisters its
  `/kits` (`/kit`) command, and on `/upm uninstall` also runs the framework's listener
  unregistration (this module declares no event listeners of its own). Previously this module
  replaced the framework's unload method with an empty one, so command unregistration was skipped on
  both paths and listener unregistration on `/upm uninstall` (UltiKits/UltiKits#18).
- 卸载本模块（`/upm uninstall UltiTools-Kits` 或关闭服务器）现在会注销其 `/kits`（`/kit`）命令，
  `/upm uninstall` 时还会执行框架的监听器注销（本模块自身未声明事件监听器）。此前本模块用一个空方法替换了
  框架的卸载方法，因此两条路径都跳过了命令注销，`/upm uninstall` 还跳过了监听器注销
  （UltiKits/UltiKits#18）。
