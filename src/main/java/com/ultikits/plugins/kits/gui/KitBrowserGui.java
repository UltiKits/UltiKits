package com.ultikits.plugins.kits.gui;

import com.ultikits.plugins.kits.config.KitsConfig;
import com.ultikits.plugins.kits.model.KitDefinition;
import com.ultikits.plugins.kits.service.KitService;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.utils.EconomyUtils;
import mc.obliviate.inventory.Gui;
import mc.obliviate.inventory.Icon;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI for browsing and claiming kits.
 * 礼包浏览界面。
 */
public class KitBrowserGui extends Gui {

    private final Player player;
    private final UltiToolsPlugin plugin;
    private final KitService kitService;
    /**
     * The module's live configuration object, not a snapshot of its values. {@code ConfigManager}
     * re-reads the file into this same instance on {@code /ul reload}, so every read below happens
     * at the moment the value is used - at open for the page size, at click for the debounce
     * window, at a page turn for the master switch - and a reload therefore takes effect without
     * reopening the browser or restarting. The switch is not read on the claim path here; {@link
     * com.ultikits.plugins.kits.service.KitService#claimKit} owns that.
     * <p>
     * 模块的实时配置对象（而非取值快照）。{@code /ul reload} 会把文件重新读入同一个实例，
     * 因此下面每一处都在用到的那一刻才读取，重载无需重启即可生效。
     */
    private final KitsConfig config;
    private final int page;
    private long lastClickTime = 0;

    public KitBrowserGui(Player player, UltiToolsPlugin plugin, KitService kitService,
                         KitsConfig config, int page) {
        super(player, "kit_browser_" + page,
                ChatColor.translateAlternateColorCodes('&', "&6&l" + plugin.i18n("礼包列表")),
                6);
        this.player = player;
        this.plugin = plugin;
        this.kitService = kitService;
        this.config = config;
        this.page = page;
    }

    @Override
    public void onOpen(InventoryOpenEvent event) {
        // Read once per frame so the page count, the start index and the end index below cannot
        // disagree with each other, and re-read on every open so a reloaded value applies at once.
        int kitsPerPage = config.getKitsPerPage();
        List<KitDefinition> availableKits = kitService.getAvailableKits(player);
        int totalPages = Math.max(1, (int) Math.ceil((double) availableKits.size() / kitsPerPage));
        // The page index this browser was built with can have stopped existing since: `kits_per_page`
        // is live, so raising it collapses the page count, and `/kits reload` can shrink the
        // catalogue. Rendering such an index unclamped produces an empty inventory titled with an
        // impossible `Page 5/2`. Clamping HERE rather than at the callers covers every way a browser
        // is built, now and later, because obliviate calls onOpen on every open whoever constructed
        // it. Only the rendered page is clamped; the `page` field keeps what it was built with.
        int currentPage = Math.min(Math.max(page, 0), totalPages - 1);
        int startIndex = currentPage * kitsPerPage;
        int endIndex = Math.min(startIndex + kitsPerPage, availableKits.size());

        // Fill separator row (row 5, slots 36-44)
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        if (glassMeta != null) {
            glassMeta.setDisplayName(" ");
            glass.setItemMeta(glassMeta);
        }
        for (int i = 36; i <= 44; i++) {
            Icon separator = new Icon(glass);
            separator.onClick(e -> e.setCancelled(true));
            addItem(i, separator);
        }

        // Add kit items (slots 0-35, up to config.kits_per_page per page)
        for (int i = startIndex; i < endIndex; i++) {
            int slot = i - startIndex;
            if (slot >= 36) break;

            KitDefinition kit = availableKits.get(i);
            Icon kitIcon = buildKitIcon(kit);
            addItem(slot, kitIcon);
        }

        // Navigation - Previous page (slot 45)
        if (currentPage > 0) {
            ItemStack prevItem = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevItem.getItemMeta();
            if (prevMeta != null) {
                prevMeta.setDisplayName(ChatColor.YELLOW + plugin.i18n("上一页"));
                prevItem.setItemMeta(prevMeta);
            }
            Icon prevIcon = new Icon(prevItem);
            prevIcon.onClick(e -> {
                e.setCancelled(true);
                openPage(currentPage - 1);
            });
            addItem(45, prevIcon);
        }

        // Page indicator (slot 49)
        ItemStack pageItem = new ItemStack(Material.PAPER);
        ItemMeta pageMeta = pageItem.getItemMeta();
        if (pageMeta != null) {
            pageMeta.setDisplayName(ChatColor.WHITE + String.format(plugin.i18n("第 %d/%d 页"), currentPage + 1, totalPages));
            pageItem.setItemMeta(pageMeta);
        }
        Icon pageIcon = new Icon(pageItem);
        pageIcon.onClick(e -> e.setCancelled(true));
        addItem(49, pageIcon);

        // Navigation - Next page (slot 53)
        if (currentPage < totalPages - 1) {
            ItemStack nextItem = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextItem.getItemMeta();
            if (nextMeta != null) {
                nextMeta.setDisplayName(ChatColor.YELLOW + plugin.i18n("下一页"));
                nextItem.setItemMeta(nextMeta);
            }
            Icon nextIcon = new Icon(nextItem);
            nextIcon.onClick(e -> {
                e.setCancelled(true);
                openPage(currentPage + 1);
            });
            addItem(53, nextIcon);
        }
    }

    /**
     * Opens another page of this browser, and the only place in this class that does.
     * <p>
     * Both page arrows route through here so the master switch is consulted once rather than once
     * per control: a new browser opened while {@code config.yml: enabled} is off would serve a
     * fresh page of the catalogue at the same moment every {@code /kits} sub-command is answering
     * that the system is disabled. A control added to this GUI later that needs to change page must
     * call this method rather than constructing a {@link KitBrowserGui} itself, which is what keeps
     * the switch from being one enumeration short again (UltiKits/UltiKits#13).
     * <p>
     * A refused page turn leaves the current page open: the player keeps what they were already
     * looking at, and nothing new is rendered. Claiming from that still-open page is refused
     * separately, by {@link com.ultikits.plugins.kits.service.KitService#claimKit}.
     * <p>
     * The switch is read inside the scheduled task, not before scheduling it. The reopen happens a
     * tick after the click, and an operator's {@code /ul reload UltiTools-Kits} can land in that
     * window: a check taken at click time would already have passed, and the callback would then
     * open a fresh catalogue page for a system that is now off. Reading it in the task is one check
     * at the moment the browser would actually be built, rather than one check plus a race.
     * {@code closeInventory()} moved in with it for the same reason - the current page is closed
     * only once the replacement is certain, so a refusal costs the player nothing.
     * <p>
     * 翻页只经由此方法。开关在延迟任务内部读取（而非调度之前），因为重开发生在下一 tick，
     * 运维的 `/ul reload` 可能正落在这个窗口里。被拒绝时保留当前页面，不渲染新页面。
     *
     * @param targetPage the zero-based page to open / 目标页码（从 0 开始）
     */
    private void openPage(int targetPage) {
        org.bukkit.plugin.Plugin ultiTools = Bukkit.getPluginManager().getPlugin("UltiTools");
        if (ultiTools == null) {
            return;
        }
        Bukkit.getScheduler().runTask(ultiTools, () -> {
            if (!config.isEnabled()) {
                player.sendMessage(ChatColor.RED + plugin.i18n("礼包系统当前已关闭"));
                return;
            }
            player.closeInventory();
            new KitBrowserGui(player, plugin, kitService, config, targetPage).open();
        });
    }

    Icon buildKitIcon(KitDefinition kit) {
        Material material;
        try {
            material = Material.valueOf(kit.getIcon().toUpperCase());
        } catch (IllegalArgumentException e) {
            material = Material.CHEST;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            // Display name
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', kit.getDisplayName()));

            // Build lore
            List<String> lore = new ArrayList<>();

            // Description lines
            for (String desc : kit.getDescription()) {
                lore.add(ChatColor.translateAlternateColorCodes('&', desc));
            }

            if (!kit.getDescription().isEmpty()) {
                lore.add("");
            }

            // Price
            if (kit.isFree()) {
                lore.add(ChatColor.GRAY + plugin.i18n("价格") + ": " + ChatColor.GREEN + plugin.i18n("免费"));
            } else {
                String priceStr = EconomyUtils.isAvailable() ? EconomyUtils.format(kit.getPrice()) : String.valueOf(kit.getPrice());
                lore.add(ChatColor.GRAY + plugin.i18n("价格") + ": " + ChatColor.GOLD + priceStr);
            }

            // Level requirement
            if (kit.hasLevelRequirement()) {
                lore.add(ChatColor.GRAY + plugin.i18n("等级要求") + ": " + ChatColor.YELLOW + kit.getLevelRequired());
            }

            // Status
            lore.add("");
            lore.add(ChatColor.GRAY + plugin.i18n("状态") + ": " + getStatusText(kit));

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        Icon icon = new Icon(item);
        icon.onClick(e -> {
            e.setCancelled(true);
            handleKitClick(kit);
        });

        return icon;
    }

    /**
     * Handles a click on a kit icon: debounce, then claim, then render the outcome.
     * <p>
     * The master switch is NOT re-checked here. A browser outlives the command that opened it, so a
     * click can certainly reach a disabled kit system - but the refusal comes from {@link
     * com.ultikits.plugins.kits.service.KitService#claimKit} returning {@link
     * KitService.ClaimResult#SYSTEM_DISABLED}, which the switch below renders, so this handler is
     * one more caller of the single guarded gateway rather than a second copy of the guard. An
     * inventory already on screen is deliberately NOT force-closed: closing a window out from under
     * a player to enforce a setting is a larger and more surprising action than declining what they
     * clicked.
     * <p>
     * The debounce runs first, which has two consequences worth stating together. It stops a
     * disabled module answering every click and becoming a message-spam surface - the reverse
     * ordering really would do that. It also means the REFUSAL is rate-limited by
     * {@code config.yml: click_cooldown_ms}, a key whose documented purpose is claim debouncing: at
     * the legal maximum of 5000 a player who clicks twice four seconds apart gets one message and
     * then silence, which reads as a broken GUI rather than a disabled system. The trade is
     * accepted because the alternative is worse, not because the second half does not exist.
     * <p>
     * 点击处理顺序：防抖 -> 领取 -> 渲染结果。开关不在此重复检查，拒绝来自 claimKit 返回的
     * SYSTEM_DISABLED。防抖在前，既避免刷屏，也意味着拒绝提示同样受 click_cooldown_ms 限流。
     *
     * @param kit the kit whose icon was clicked / 被点击的礼包
     */
    void handleKitClick(KitDefinition kit) {
        long now = System.currentTimeMillis();
        if (now - lastClickTime < config.getClickCooldownMs()) {
            return;
        }
        lastClickTime = now;

        KitService.ClaimResult result = kitService.claimKit(player, kit.getName());

        switch (result) {
            case SUCCESS:
                player.sendMessage(ChatColor.GREEN + String.format(plugin.i18n("成功领取礼包: %s"), kit.getDisplayName()));
                if (!kit.isFree() && EconomyUtils.isAvailable()) {
                    player.sendMessage(ChatColor.YELLOW + String.format(plugin.i18n("已扣除 %s"), EconomyUtils.format(kit.getPrice())));
                }
                player.closeInventory();
                break;
            case NOT_FOUND:
                player.sendMessage(ChatColor.RED + String.format(plugin.i18n("礼包 '%s' 不存在"), kit.getName()));
                break;
            case NO_PERMISSION:
                player.sendMessage(ChatColor.RED + plugin.i18n("你没有权限使用此礼包"));
                break;
            case INSUFFICIENT_LEVEL:
                player.sendMessage(ChatColor.RED + String.format(plugin.i18n("等级不足，需要 %d 级"), kit.getLevelRequired()));
                break;
            case INSUFFICIENT_FUNDS:
                player.sendMessage(ChatColor.RED + plugin.i18n("余额不足"));
                break;
            case PAYMENT_FAILED:
                player.sendMessage(ChatColor.RED + plugin.i18n("扣款失败，礼包未领取"));
                break;
            case ALREADY_CLAIMED:
                player.sendMessage(ChatColor.RED + plugin.i18n("你已经领取过此礼包"));
                break;
            case ON_COOLDOWN:
                long remaining = kitService.getRemainingCooldown(player, kit);
                player.sendMessage(ChatColor.RED + String.format(plugin.i18n("礼包冷却中，剩余: %s"), kitService.formatCooldown(remaining)));
                break;
            case INVENTORY_FULL:
                player.sendMessage(ChatColor.RED + plugin.i18n("背包空间不足"));
                break;
            case EMPTY_KIT:
                player.sendMessage(ChatColor.RED + plugin.i18n("礼包内容为空"));
                break;
            case SYSTEM_DISABLED:
                player.sendMessage(ChatColor.RED + plugin.i18n("礼包系统当前已关闭"));
                break;
            default:
                player.sendMessage(ChatColor.RED + plugin.i18n("领取礼包时发生错误"));
                break;
        }
    }

    String getStatusText(KitDefinition kit) {
        // Check level
        if (kit.hasLevelRequirement() && player.getLevel() < kit.getLevelRequired()) {
            return ChatColor.RED + plugin.i18n("等级不足");
        }

        // Check economy
        if (!kit.isFree()) {
            if (!EconomyUtils.isAvailable() || !EconomyUtils.has(player, kit.getPrice())) {
                return ChatColor.RED + plugin.i18n("余额不足");
            }
        }

        // Check cooldown / one-time
        long remaining = kitService.getRemainingCooldown(player, kit);
        if (remaining < 0) {
            return ChatColor.RED + plugin.i18n("已领取");
        }
        if (remaining > 0) {
            return ChatColor.YELLOW + plugin.i18n("冷却中") + ": " + kitService.formatCooldown(remaining);
        }

        return ChatColor.GREEN + plugin.i18n("可领取");
    }
}
