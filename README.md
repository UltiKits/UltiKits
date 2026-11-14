# UltiKits - 礼包模块 / Kit System Module

[![UltiTools-API](https://img.shields.io/badge/UltiTools--API-6.4.0%2B-blue)](https://github.com/UltiKits/UltiTools-Reborn)
[![License](https://img.shields.io/badge/License-MIT-green)](LICENSE)

A standalone kit/gift package module for UltiTools-API. Create, manage, and distribute item kits to players with economy integration, cooldowns, permissions, and GUI browsing.

UltiTools-API 的独立礼包模块。支持创建、管理和分发物品礼包，集成经济系统、冷却时间、权限控制和 GUI 浏览。

**Requires UltiTools-API 6.4.0 or later** (the module declares `api-version: 640`; an older framework refuses to load it). / 需要 UltiTools-API 6.4.0 或更高版本（本模块声明 `api-version: 640`，更早的框架会拒绝加载它）。

Upgrade UltiTools to 6.4.0 or later before replacing this module's JAR.

更换本模块 JAR 前，请先将 UltiTools 升级到 6.4.0 或更高版本。

## Features / 功能

- **GUI Browser** - Paginated chest GUI for browsing and claiming kits / 分页箱子 GUI 浏览和领取礼包
- **GUI Editor** - In-game kit content editor for admins / 游戏内礼包内容编辑器
- **Economy Integration** - Vault economy support for kit pricing / Vault 经济系统支持
- **Cooldowns** - Per-kit cooldown timers, one-time kits / 每礼包冷却计时器，一次性礼包
- **Level Requirements** - Minimum player level to claim / 最低玩家等级限制
- **Permissions** - Per-kit permission nodes / 每礼包权限节点
- **Commands on Claim** - Execute player and console commands when kits are claimed / 领取时执行命令
- **YAML Configuration** - One file per kit in `kits/` folder / 每礼包一个 YAML 配置文件
- **i18n** - Chinese and English language support / 中英文支持

## Commands / 命令

| Command | Description | Permission |
|---------|-------------|------------|
| `/kits` | Open kit browser GUI / 打开礼包浏览器 | `ultikits.kits.use` |
| `/kits claim <name>` | Claim a kit / 领取礼包 | `ultikits.kits.use` |
| `/kits list` | List all kits / 列出所有礼包 | `ultikits.kits.use` |
| `/kits edit <name>` | Open kit editor GUI / 编辑礼包内容 | `ultikits.kits.admin` |
| `/kits create <name>` | Create kit from inventory / 从物品栏创建礼包 | `ultikits.kits.admin` |
| `/kits delete <name>` | Delete a kit / 删除礼包 | `ultikits.kits.admin` |
| `/kits reload` | Reload kit configurations / 重新加载配置 | `ultikits.kits.admin` |

## Permissions / 权限

| Permission | Description |
|-----------|-------------|
| `ultikits.kits.use` | Basic kit access (browse, claim, list) / 基本礼包访问 |
| `ultikits.kits.admin` | Admin kit management (create, edit, delete, reload) / 管理员礼包管理 |

## Kit Configuration / 礼包配置

Each kit is a YAML file in the `kits/` folder:

```yaml
displayName: "&a&lStarter Kit"
description:
  - "&7Basic items for new players"
  - "&7新手基础物品"
icon: CHEST
price: 0
levelRequired: 0
permission: ""
reBuyable: false
cooldown: 0
playerCommands: []
consoleCommands:
  - "say {player} claimed the starter kit!"
items: "" # Base64 serialized - managed via /kits create or /kits edit
```

| Field | Type | Description |
|-------|------|-------------|
| `displayName` | String | Display name with color codes / 显示名称（支持颜色代码） |
| `description` | List | Lore lines with color codes / 描述行 |
| `icon` | String | Material name for GUI icon / GUI 图标材质名 |
| `price` | Double | Cost to claim (0 = free; never negative or non-numeric: such a value is refused with a warning and `0` is used) / 领取费用（0 = 免费；不能为负或非数字，否则会被警告并改用 0） |
| `levelRequired` | Int | Minimum player level (never negative or non-numeric: such a value is refused with a warning and `0` is used) / 最低玩家等级（不能为负或非数字，否则会被警告并改用 0） |
| `permission` | String | Required permission node / 所需权限节点 |
| `reBuyable` | Boolean | Can be claimed multiple times / 是否可重复领取 |
| `cooldown` | Long | Cooldown in seconds between claims (never negative or non-numeric: such a value is refused with a warning and `0` is used) / 领取冷却时间（秒；不能为负或非数字，否则会被警告并改用 0） |
| `playerCommands` | List | Commands run as the player / 以玩家身份执行的命令 |
| `consoleCommands` | List | Commands run from console / 以控制台执行的命令 |
| `items` | String | Base64 serialized items (auto-managed) / Base64 序列化物品 |

## Known limitations / 已知限制

On supported filesystems, explicit `/kits create` and editor Save actions use `CrashSafeYamlFiles`. A new kit is
published as a complete file with permissions `0600` (only its owner can read/write it); an edit changes only `items`,
preserving all unrelated bytes, the existing nine POSIX permission bits, owner and group. A semantic no-op does not
replace the file. A changed edit replaces the inode: hard-link aliases keep their old contents instead of following it.

在支持的文件系统上，显式 `/kits create` 和编辑器保存使用 `CrashSafeYamlFiles`。新礼包以 `0600` 权限发布完整文件
（仅所有者可读写）；编辑只修改 `items`，保留其他字节、原有九位 POSIX 权限、所有者和组。值未改变时不替换文件；
实际修改会替换 inode（文件身份），其他硬链接仍保留旧内容，不会跟随修改。

On Windows (or a filesystem whose parent/target exposes a Java ACL view or has no POSIX attribute view), kit files
fall back to an ordinary write without crash-safe protection. Editor saves use `OperatorFiles.write`, retaining
snapshot and unrelated-byte checks; creation uses the previous exclusive-claim-then-YAML-save path, so a crash can
leave an empty or partial file. Each successful create/save logs one WARN naming the file and the unsupported
capability. This is a compatibility choice, not a crash-safe fallback after a refused or failed publication.

Windows（或父目录、目标文件暴露 Java ACL 视图或没有 POSIX 属性视图的文件系统）上的礼包文件会退回不提供崩溃安全
保护的普通写入。编辑器使用 `OperatorFiles.write`，仍保留快照和其他字节检查；创建恢复为先排他占用文件再保存 YAML，
崩溃时可能留下空文件或残缺文件。每次成功创建或保存都记录一条 WARN，列出文件和不支持的能力。这是兼容性选择，
不会在安全发布被拒绝或失败后换个入口重试。

Crash-safe publication requires ordinary files on the default local POSIX filesystem with native atomic rename/link.
Kit files must be owned by, and writable by, the account running the server. A file created/uploaded as another user
(for example `root`) can load but fail editor Save: read-only files are refused; a failure to preserve a foreign
owner/group gives `Error saving kit`, with the exception class and reason in the log. On POSIX, fix ownership with
`chown <server-user> kits/<name>.yml` and grant that account write access, then `/kits reload` and reopen the editor.
Final symlinks, changed files and existing create entries do not trigger the ordinary writer. If attribute reads,
staging, owner/group preservation or atomic publication fails on a supported layout, the operation fails without
an in-place/copy retry: existing file contents and the loaded kit are unchanged. Do not use a layout that depends on
preserving hidden/default ACLs, xattrs, security labels, special permission bits, creation times, inode identity or
hard-link relationships; use ordinary kit files and independent backups. Not every incompatible layout is detectable.

崩溃安全发布要求普通文件位于支持原生原子 rename/link 的默认本地 POSIX 文件系统。礼包文件必须由服务器运行账号拥有，
且该账号有写权限。由其他用户（例如 `root`）创建或上传的文件可能可以加载，但无法从编辑器保存：只读文件会被拒绝；
保留其他所有者或组失败时显示保存错误，日志记录异常类名及原因。在 POSIX 上可用 `chown <服务器账号> kits/<名称>.yml`
修复所有者并赋予该账号写权限，再执行 `/kits reload` 并重新打开编辑器。末尾符号链接、磁盘冲突和已存在的创建目标不会
触发普通写入。在支持的布局上读取属性、暂存、保留所有者或组、原子发布失败时，操作失败，不会改用原地写入或复制重试；
原文件内容和已加载礼包不变。不要使用依赖隐藏或默认 ACL、扩展属性、安全标签、特殊权限位、创建时间、文件身份或硬链接
关系的布局；请使用普通礼包文件并保留独立备份。并非所有不兼容布局都能被检测。

Only on the crash-safe path, with a functioning OS/filesystem, a process crash leaves old/absent or complete-new content.
This is not a power-loss, remote-storage or multi-file transaction guarantee. A crash may leave a sibling named
`<name>.yml.ultitools-stage-<32 lower-case hex>.tmp`; no startup scan or automatic recovery promotes or deletes it.
Protect it like the kit file. Stop competing writers and the server, inspect the target and the named temporary
file, and delete only a temporary file known to be unneeded; otherwise keep it and restore an independently trusted
backup or explicitly recreate the kit. Do not edit kit files concurrently with create/save: there is no cross-process
lock, and a change between creation and its snapshot read may be adopted. After external edits, run `/kits reload`
and close/reopen old editors before saving. The automatic first-start `starter.yml` copy is unchanged and keeps the
shipped resource's comments; it is not an explicit operator action and does not use this helper.

仅在崩溃安全路径上，操作系统和文件系统正常时，进程崩溃后最终文件是旧内容、不存在或完整新内容；这不是断电、
远程存储或多文件事务保证。崩溃可能遗留同目录的 `<name>.yml.ultitools-stage-<32 位小写十六进制>.tmp`，启动时不会扫描、
提升或自动删除它。请像保护礼包文件一样保护它；先停止其他写入者和服务器，检查目标文件与该临时文件，只删除确认不再需要
的临时文件，否则保留它并使用独立可信备份恢复，或显式重新创建礼包。创建和保存时不要并发手改礼包文件：没有跨进程锁，
创建完成到读取快照之间的外部修改也可能成为初始快照。外部修改后先执行 `/kits reload`，关闭并重新打开旧编辑器再保存。
首次启动自动复制的 `starter.yml` 保持不变，保留随包资源的注释；它不是管理员显式操作，不使用此 helper。

## Soft Dependencies / 可选依赖

- **Vault** - Economy integration for kit pricing
- **PlaceholderAPI** - Placeholder support in commands

## UltiTools-API Features Used

- `@Service` + constructor injection (IoC)
- `@UltiToolsModule` plugin registration
- `@CmdExecutor` / `@CmdMapping` command system
- `@ConfigEntity` / `@ConfigEntry` / `@Range` config validation
- `BaseDataEntity` + `@Table` / `@Column` ORM
- Query DSL (`operator.query().where("x").eq(y).list()`)
- `DataOperator<T>` for claim persistence
- `UltiToolsPlugin.i18n()` for translations
- obliviate-invs GUI framework
