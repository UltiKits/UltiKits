package com.ultikits.plugins.kits.commands;

import com.ultikits.plugins.kits.config.KitsConfig;
import com.ultikits.plugins.kits.gui.KitBrowserGui;
import com.ultikits.plugins.kits.gui.KitEditorGui;
import com.ultikits.plugins.kits.model.KitDefinition;
import com.ultikits.plugins.kits.service.KitService;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.abstracts.command.BaseCommandExecutor;
import com.ultikits.ultitools.annotations.command.*;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Kit command executor.
 * 礼包命令执行器。
 */
@CmdExecutor(
        permission = "ultikits.kits.use",
        description = "kits.command.description",
        alias = {"kits", "kit"}
)
public class KitCommands extends BaseCommandExecutor {

    private final UltiToolsPlugin plugin;
    private final KitService kitService;
    /**
     * The module's live configuration object, not a snapshot of its values. {@code ConfigManager}
     * re-reads the file into this same instance on {@code /ul reload}, so {@link #refusedAsDisabled}
     * reads {@code enabled} at the moment a command runs and a flip of the switch applies to the
     * very next command without a server restart.
     * <p>
     * 模块的实时配置对象（而非取值快照）。{@code /ul reload} 会把文件重新读入同一个实例，
     * 因此总开关在下一条命令上即刻生效，无需重启服务器。
     */
    private final KitsConfig config;

    public KitCommands(UltiToolsPlugin plugin, KitService kitService, KitsConfig config) {
        this.plugin = plugin;
        this.kitService = kitService;
        this.config = config;
    }

    /**
     * Refuses the command when the kit system's master switch ({@code config.yml: enabled}) is off, and
     * says so, so an operator who turned the module off gets a reason rather than silence.
     * <p>
     * The switch is enforced here, at each command entry point, rather than by withholding the
     * command registration: an unregistered command answers with Bukkit's own "Unknown command",
     * which reads as a typo rather than a deliberate setting. Registration-time gating
     * ({@code @ConditionalOnConfig}) is also evaluated once at component scan, so a change would
     * need a full server restart in either direction; read here, the switch follows
     * {@code /ul reload}. This is not the only way a browser can appear, and the javadoc used to
     * say it was: the browser's own page arrows open a further one, which is refused inside
     * {@code KitBrowserGui}'s single page-turn method. Claiming is refused at the gateway,
     * {@link com.ultikits.plugins.kits.service.KitService#claimKit}, so it holds for callers that
     * do not exist yet. No open inventory is force-closed.
     * <p>
     * 总开关（{@code config.yml: enabled}）关闭时拒绝命令并给出提示，而不是静默无响应。
     *
     * @param sender the command sender to answer / 命令发送者
     * @return true when the command was refused and the caller must return immediately /
     *         为 true 时表示命令已被拒绝，调用方应立即返回
     */
    private boolean refusedAsDisabled(CommandSender sender) {
        if (config.isEnabled()) {
            return false;
        }
        sender.sendMessage(ChatColor.RED + plugin.i18n("kits.disabled"));
        return true;
    }

    /**
     * /kits - Open kit browser GUI.
     */
    @CmdMapping(format = "")
    @CmdTarget(CmdTarget.CmdTargetType.PLAYER)
    public void onOpenGui(@CmdSender Player player) {
        if (refusedAsDisabled(player)) {
            return;
        }
        new KitBrowserGui(player, plugin, kitService, config, 0).open();
    }

    /**
     * /kits claim <name> - Claim a kit.
     */
    @CmdMapping(format = "claim <name>")
    @CmdTarget(CmdTarget.CmdTargetType.PLAYER)
    public void onClaim(
            @CmdSender Player player,
            @CmdParam(value = "name", suggest = "suggestKitNames") String name) {
        if (refusedAsDisabled(player)) {
            return;
        }
        KitService.ClaimResult result = kitService.claimKit(player, name);
        handleClaimResult(player, name, result);
    }

    /**
     * /kits list - List all kits.
     */
    @CmdMapping(format = "list")
    public void onList(@CmdSender CommandSender sender) {
        if (refusedAsDisabled(sender)) {
            return;
        }
        List<KitDefinition> allKits;

        if (sender instanceof Player) {
            allKits = kitService.getAvailableKits((Player) sender);
        } else {
            allKits = kitService.getKitNames().stream()
                    .map(kitService::getKit)
                    .collect(Collectors.toList());
        }

        if (allKits.isEmpty()) {
            sender.sendMessage(ChatColor.RED + plugin.i18n("kits.list.empty"));
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "=== " + plugin.i18n("kits.list.title") + " ===");
        for (KitDefinition kit : allKits) {
            String displayName = ChatColor.translateAlternateColorCodes('&', kit.getDisplayName());
            String info = ChatColor.YELLOW + kit.getName() + ChatColor.GRAY + " - " + displayName;
            if (!kit.isFree()) {
                info += ChatColor.GOLD + " ($" + kit.getPrice() + ")";
            }
            sender.sendMessage(info);
        }
    }

    /**
     * /kits edit <name> - Open kit editor GUI.
     */
    @CmdMapping(format = "edit <name>")
    @CmdTarget(CmdTarget.CmdTargetType.PLAYER)
    public void onEdit(
            @CmdSender Player player,
            @CmdParam(value = "name", suggest = "suggestKitNames") String name) {
        if (refusedAsDisabled(player)) {
            return;
        }
        if (!player.hasPermission("ultikits.kits.admin")) {
            player.sendMessage(ChatColor.RED + plugin.i18n("kits.command.no_permission"));
            return;
        }

        KitDefinition kit = kitService.getKit(name);
        if (kit == null) {
            player.sendMessage(ChatColor.RED + String.format(plugin.i18n("kits.kit.not_found"), name));
            return;
        }

        new KitEditorGui(player, plugin, kitService, kit).open();
    }

    /**
     * /kits create <name> - Create kit from current inventory.
     */
    @CmdMapping(format = "create <name>")
    @CmdTarget(CmdTarget.CmdTargetType.PLAYER)
    public void onCreate(@CmdSender Player player, @CmdParam("name") String name) {
        if (refusedAsDisabled(player)) {
            return;
        }
        if (!player.hasPermission("ultikits.kits.admin")) {
            player.sendMessage(ChatColor.RED + plugin.i18n("kits.command.no_permission"));
            return;
        }

        KitService.CreateResult result = kitService.createKit(player, name);
        switch (result) {
            case SUCCESS:
                player.sendMessage(ChatColor.GREEN + String.format(plugin.i18n("kits.create.success"), name));
                break;
            case ALREADY_EXISTS:
                player.sendMessage(ChatColor.RED + String.format(plugin.i18n("kits.create.already_exists"), name));
                break;
            case INVALID_NAME:
                player.sendMessage(ChatColor.RED + plugin.i18n("kits.create.invalid_name"));
                break;
            case EMPTY_INVENTORY:
                player.sendMessage(ChatColor.RED + plugin.i18n("kits.create.empty_inventory"));
                break;
            default:
                player.sendMessage(ChatColor.RED + plugin.i18n("kits.create.error"));
                break;
        }
    }

    /**
     * /kits delete <name> - Delete a kit.
     */
    @CmdMapping(format = "delete <name>")
    public void onDelete(
            @CmdSender CommandSender sender,
            @CmdParam(value = "name", suggest = "suggestKitNames") String name) {
        if (refusedAsDisabled(sender)) {
            return;
        }
        if (!sender.hasPermission("ultikits.kits.admin")) {
            sender.sendMessage(ChatColor.RED + plugin.i18n("kits.command.no_permission"));
            return;
        }

        switch (kitService.deleteKit(name)) {
            case DELETED:
                sender.sendMessage(ChatColor.GREEN + String.format(plugin.i18n("kits.delete.success"), name));
                break;
            case FILE_NOT_DELETED:
                // The kit is still loaded and its file will load again on reload, so saying
                // "Deleted" here would be false (UltiKits/UltiKits#23).
                sender.sendMessage(ChatColor.RED + String.format(plugin.i18n("kits.delete.file_not_deleted"), name));
                break;
            default:
                sender.sendMessage(ChatColor.RED + String.format(plugin.i18n("kits.kit.not_found"), name));
                break;
        }
    }

    /**
     * /kits reload - Reload kit configurations.
     */
    @CmdMapping(format = "reload")
    public void onReload(@CmdSender CommandSender sender) {
        if (refusedAsDisabled(sender)) {
            return;
        }
        if (!sender.hasPermission("ultikits.kits.admin")) {
            sender.sendMessage(ChatColor.RED + plugin.i18n("kits.command.no_permission"));
            return;
        }

        kitService.reload();
        int count = kitService.getAllKits().size();
        sender.sendMessage(ChatColor.GREEN + String.format(plugin.i18n("kits.reload.success"), count));
    }

    /**
     * Prints the usage summary - and refuses it while the master switch is off, like every other
     * sub-command.
     * <p>
     * This one is not an {@code @CmdMapping} method: {@code BaseCommandExecutor#onCommand}
     * short-circuits a literal {@code help} argument, AND any argument vector that matches no
     * mapping, straight to here before any mapped method runs. Without this call the switch would
     * have a hole shaped exactly like {@code /kits help} and {@code /kits <anything unrecognised>},
     * which would list seven sub-commands that all refuse - an answer more misleading than silence.
     * <p>
     * 该方法不是 @CmdMapping 方法，框架会把 help 以及任何未匹配的参数直接短路到这里，
     * 因此总开关必须在此也检查一次，否则会列出七条全部会被拒绝的子命令。
     *
     * @param sender the command sender / 命令发送者
     */
    @Override
    protected void handleHelp(CommandSender sender) {
        if (refusedAsDisabled(sender)) {
            return;
        }
        sender.sendMessage(ChatColor.GOLD + "=== UltiKits ===");
        sender.sendMessage(ChatColor.YELLOW + "/kits" + ChatColor.GRAY + " - " + plugin.i18n("kits.help.open"));
        sender.sendMessage(ChatColor.YELLOW + "/kits claim <name>" + ChatColor.GRAY + " - " + plugin.i18n("kits.help.claim"));
        sender.sendMessage(ChatColor.YELLOW + "/kits list" + ChatColor.GRAY + " - " + plugin.i18n("kits.help.list"));
        sender.sendMessage(ChatColor.YELLOW + "/kits edit <name>" + ChatColor.GRAY + " - " + plugin.i18n("kits.help.edit"));
        sender.sendMessage(ChatColor.YELLOW + "/kits create <name>" + ChatColor.GRAY + " - " + plugin.i18n("kits.help.create"));
        sender.sendMessage(ChatColor.YELLOW + "/kits delete <name>" + ChatColor.GRAY + " - " + plugin.i18n("kits.help.delete"));
        sender.sendMessage(ChatColor.YELLOW + "/kits reload" + ChatColor.GRAY + " - " + plugin.i18n("kits.help.reload"));
    }

    private void handleClaimResult(Player player, String kitName, KitService.ClaimResult result) {
        switch (result) {
            case SUCCESS:
                player.sendMessage(ChatColor.GREEN + String.format(plugin.i18n("kits.claim.success"), kitName));
                break;
            case NOT_FOUND:
                player.sendMessage(ChatColor.RED + String.format(plugin.i18n("kits.kit.not_found"), kitName));
                break;
            case NO_PERMISSION:
                player.sendMessage(ChatColor.RED + plugin.i18n("kits.claim.no_permission"));
                break;
            case INSUFFICIENT_LEVEL:
                KitDefinition kit = kitService.getKit(kitName);
                int level = kit != null ? kit.getLevelRequired() : 0;
                player.sendMessage(ChatColor.RED + String.format(plugin.i18n("kits.claim.level_too_low"), level));
                break;
            case INSUFFICIENT_FUNDS:
                KitDefinition fundKit = kitService.getKit(kitName);
                String price = fundKit != null ? String.valueOf(fundKit.getPrice()) : "?";
                player.sendMessage(ChatColor.RED + String.format(plugin.i18n("kits.claim.insufficient_funds_price"), price));
                break;
            case PAYMENT_FAILED:
                player.sendMessage(ChatColor.RED + plugin.i18n("kits.claim.payment_failed"));
                break;
            case ALREADY_CLAIMED:
                player.sendMessage(ChatColor.RED + plugin.i18n("kits.claim.already_claimed"));
                break;
            case ON_COOLDOWN:
                KitDefinition cdKit = kitService.getKit(kitName);
                long remaining = cdKit != null ? kitService.getRemainingCooldown(player, cdKit) : 0;
                String timeStr = kitService.formatCooldown(remaining);
                player.sendMessage(ChatColor.RED + String.format(plugin.i18n("kits.claim.on_cooldown"), timeStr));
                break;
            case INVENTORY_FULL:
                player.sendMessage(ChatColor.RED + plugin.i18n("kits.claim.inventory_full"));
                break;
            case EMPTY_KIT:
                player.sendMessage(ChatColor.RED + plugin.i18n("kits.claim.empty_kit"));
                break;
            case NOT_RECORDED:
                player.sendMessage(ChatColor.RED + plugin.i18n("kits.claim.not_recorded"));
                break;
            case NOT_RECORDED_REFUND_FAILED:
                player.sendMessage(ChatColor.RED + plugin.i18n("kits.claim.not_recorded_refund_failed"));
                break;
            case SYSTEM_DISABLED:
                // Not reached from /kits claim, which refuses at refusedAsDisabled before the
                // service is called. It is here because this method is the module's shared renderer
                // for a claim outcome: a caller added later that does not pre-check would otherwise
                // fall into default: and tell the player "Error claiming kit" for a kit system the
                // operator switched off on purpose.
                player.sendMessage(ChatColor.RED + plugin.i18n("kits.disabled"));
                break;
            default:
                player.sendMessage(ChatColor.RED + plugin.i18n("kits.claim.error"));
                break;
        }
    }

    private List<String> suggestKitNames(Player player) {
        return kitService.getKitNames();
    }
}
