package com.ultikits.plugins.kits.service;

import com.ultikits.plugins.kits.config.KitsConfig;
import com.ultikits.plugins.kits.entity.KitClaimData;
import com.ultikits.plugins.kits.model.KitDefinition;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.Service;
import com.ultikits.ultitools.interfaces.DataOperator;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;
import com.ultikits.ultitools.utils.EconomyUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import javax.annotation.Nullable;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Implementation of KitService.
 * 礼包服务实现。
 */
@Service
public class KitServiceImpl implements KitService {

    private final UltiToolsPlugin plugin;
    /**
     * The module's live configuration object, not a snapshot of its values. {@code ConfigManager}
     * re-reads the file into this same instance on {@code /ul reload}, so {@link #claimKit} reads
     * {@code enabled} at the moment a claim is attempted and a flip applies to the very next one.
     * <p>
     * 模块的实时配置对象（而非取值快照）；{@code /ul reload} 会把文件重新读入同一个实例。
     */
    private final KitsConfig config;
    private final PluginLogger logger;
    private final Map<String, KitDefinition> kits = new LinkedHashMap<>();
    /**
     * Kits whose refused-withdrawal warning has already been written this session, so the console
     * gets one line per kit rather than one per attempt. Claiming is player-triggered behind only
     * the browser's 200ms debounce, and {@code /kits claim} has no cooldown at all, so during an
     * economy outage the unthrottled form buries the log in the window an operator most needs to
     * read it. This follows the framework's own precedent - {@code EconomyUtils} emits exactly one
     * line per calling module per server session for the adjacent condition. Bounded by the size
     * of the kit catalogue, and cleared by {@link #loadKits()} so a reload re-arms it.
     * <p>
     * 每个礼包每个会话只记录一次扣款被拒的警告，避免经济系统故障时刷屏；重新加载礼包会重置。
     */
    private final Set<String> refusalWarnedKits = Collections.synchronizedSet(new LinkedHashSet<>());
    private DataOperator<KitClaimData> claimOperator;

    public KitServiceImpl(UltiToolsPlugin plugin, KitsConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.logger = plugin.getLogger();
        this.claimOperator = plugin.getDataOperator(KitClaimData.class);
        loadKits();
    }

    @Override
    public void loadKits() {
        kits.clear();
        refusalWarnedKits.clear();

        File kitsFolder = new File(plugin.getResourceFolderPath(), "kits");
        if (!kitsFolder.exists()) {
            kitsFolder.mkdirs();
            copyExampleKit(kitsFolder);
        }

        File[] files = kitsFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            logger.warn(plugin.i18n("kits.log.no_kit_files"));
            return;
        }

        int loadedCount = 0;
        for (File file : files) {
            KitDefinition kit = parseKitFile(file);
            if (kit != null) {
                String kitName = file.getName().replace(".yml", "").toLowerCase();
                kit.setName(kitName);
                kits.put(kitName, kit);
                loadedCount++;
            }
        }

        logger.info(String.format(plugin.i18n("kits.log.loaded_count"), loadedCount));
    }

    @Override
    public void reload() {
        loadKits();
    }

    @Nullable
    @Override
    public KitDefinition getKit(String name) {
        return kits.get(name.toLowerCase());
    }

    @Override
    public Collection<KitDefinition> getAllKits() {
        return Collections.unmodifiableCollection(kits.values());
    }

    @Override
    public List<KitDefinition> getAvailableKits(Player player) {
        return kits.values().stream()
                .filter(kit -> !kit.hasPermission() || player.hasPermission(kit.getPermission()))
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getKitNames() {
        return new ArrayList<>(kits.keySet());
    }

    @Override
    public CreateResult createKit(Player player, String name) {
        String normalizedName = name.toLowerCase().trim();
        if (normalizedName.isEmpty() || normalizedName.length() > 32) {
            return CreateResult.INVALID_NAME;
        }

        if (kits.get(normalizedName) != null) {
            return CreateResult.ALREADY_EXISTS;
        }

        // Filter out air and null items from player inventory
        ItemStack[] validItems = Arrays.stream(player.getInventory().getStorageContents())
                .filter(item -> item != null && item.getType() != Material.AIR)
                .toArray(ItemStack[]::new);

        if (validItems.length == 0) {
            return CreateResult.EMPTY_INVENTORY;
        }

        String serializedItems = serializeItems(validItems);
        if (serializedItems == null) {
            return CreateResult.ERROR;
        }

        // Create kit definition
        KitDefinition kit = new KitDefinition();
        kit.setName(normalizedName);
        kit.setDisplayName("&f" + name);
        kit.setIcon(validItems[0].getType().name());
        kit.setItems(serializedItems);

        // Save to YAML
        if (!saveKitToFile(normalizedName, kit)) {
            return CreateResult.ERROR;
        }

        kits.put(normalizedName, kit);
        return CreateResult.SUCCESS;
    }

    @Override
    public boolean deleteKit(String name) {
        String normalizedName = name.toLowerCase().trim();
        if (kits.get(normalizedName) == null) {
            return false;
        }

        File kitFile = new File(plugin.getResourceFolderPath(), "kits/" + normalizedName + ".yml");
        if (kitFile.exists()) {
            kitFile.delete(); // NOPMD
        }

        kits.remove(normalizedName);
        return true;
    }

    /**
     * Writes a kit's item contents, and the only place in this module that does - the kit editor's
     * Save button is its single caller.
     * <p>
     * The master switch is enforced here for the same reason it is enforced in {@link #claimKit}:
     * the editor GUI outlives the {@code /kits edit} command that opened it, so a check at that
     * command has already passed by the time Save is pressed and cannot stop a write while the
     * system is off. Guarding the gateway covers the editor and any writer added later.
     * <p>
     * 保存入口处执行总开关：编辑界面的生命周期长于打开它的命令，命令处的检查拦不住之后的保存。
     *
     * @param kitName the kit to write / 目标礼包名
     * @param items   its new contents / 新的物品内容
     * @return the outcome, never null / 结果，不为 null
     */
    @Override
    public SaveResult saveKitItems(String kitName, ItemStack[] items) {
        if (!config.isEnabled()) {
            return SaveResult.SYSTEM_DISABLED;
        }

        KitDefinition kit = getKit(kitName);
        if (kit == null) {
            return SaveResult.FAILED;
        }

        // Filter out null/air items
        ItemStack[] validItems = Arrays.stream(items)
                .filter(item -> item != null && item.getType() != Material.AIR)
                .toArray(ItemStack[]::new);

        String serialized = serializeItems(validItems);
        if (serialized == null) {
            return SaveResult.FAILED;
        }

        kit.setItems(serialized);
        return saveKitToFile(kit.getName(), kit) ? SaveResult.SUCCESS : SaveResult.FAILED;
    }

    /**
     * Claims a kit for a player. This method is the module's only gateway to a kit: {@code
     * deliverKit} is private with this as its single caller, and the item, command and claim-row
     * effects are reachable only from there.
     * <p>
     * The master switch ({@code config.yml: enabled}) is therefore enforced HERE and nowhere else
     * on the claim path. The two entry points that reach this method - {@code KitCommands} and the
     * browser's click handler - do not repeat the check; they render the {@link
     * ClaimResult#SYSTEM_DISABLED} this returns. That placement is deliberate and was chosen over
     * checking at each caller, because a guard repeated at N callers is only as good as whoever
     * remembers to add the N+1st: this module's own history has that enumeration coming up short
     * twice. Placed at the gateway, a caller added later cannot bypass the switch even by accident
     * (UltiKits/UltiKits#13).
     * <p>
     * 总开关只在这里执行一次。调用方不再各自检查，而是渲染本方法返回的 {@code SYSTEM_DISABLED}，
     * 这样将来新增的调用方无法绕过开关。
     *
     * @param player  the claiming player / 领取的玩家
     * @param kitName the kit's name / 礼包名
     * @return the outcome, never null / 领取结果，不为 null
     */
    @Override
    public ClaimResult claimKit(Player player, String kitName) {
        if (!config.isEnabled()) {
            return ClaimResult.SYSTEM_DISABLED;
        }

        KitDefinition kit = getKit(kitName);
        if (kit == null) {
            return ClaimResult.NOT_FOUND;
        }

        ClaimResult validationResult = validateClaim(player, kit);
        if (validationResult != null) {
            return validationResult;
        }

        ItemStack[] items = deserializeItems(kit.getItems());
        if (items == null || items.length == 0) {
            return ClaimResult.EMPTY_KIT;
        }

        if (countEmptySlots(player) < items.length) {
            return ClaimResult.INVENTORY_FULL;
        }

        return deliverKit(player, kit, items);
    }

    /**
     * Validates player eligibility to claim a kit.
     * Returns null if all checks pass, or the failure result.
     */
    @Nullable
    ClaimResult validateClaim(Player player, KitDefinition kit) {
        ClaimResult prereq = checkPrerequisites(player, kit);
        if (prereq != null) {
            return prereq;
        }
        ClaimResult cooldown = checkCooldown(player, kit);
        if (cooldown != null) {
            return cooldown;
        }
        return kit.hasItems() ? null : ClaimResult.EMPTY_KIT;
    }

    @Nullable
    private ClaimResult checkPrerequisites(Player player, KitDefinition kit) {
        if (kit.hasPermission() && !player.hasPermission(kit.getPermission())) {
            return ClaimResult.NO_PERMISSION;
        }
        if (kit.hasLevelRequirement() && player.getLevel() < kit.getLevelRequired()) {
            return ClaimResult.INSUFFICIENT_LEVEL;
        }
        if (!kit.isFree() && !canAfford(player, kit.getPrice())) {
            return ClaimResult.INSUFFICIENT_FUNDS;
        }
        return null;
    }

    private boolean canAfford(Player player, double price) {
        return EconomyUtils.isAvailable() && EconomyUtils.has(player, price);
    }

    @Nullable
    private ClaimResult checkCooldown(Player player, KitDefinition kit) {
        KitClaimData claim = getClaimData(player.getUniqueId(), kit.getName());
        if (claim == null) {
            return null;
        }
        if (kit.isOneTime()) {
            return ClaimResult.ALREADY_CLAIMED;
        }
        return getRemainingCooldown(player, kit) > 0 ? ClaimResult.ON_COOLDOWN : null;
    }

    private int countEmptySlots(Player player) {
        int count = 0;
        for (ItemStack slot : player.getInventory().getStorageContents()) {
            if (slot == null || slot.getType() == Material.AIR) {
                count++;
            }
        }
        return count;
    }

    /**
     * Charges for the kit, then delivers it. The order is the load-bearing part.
     * <p>
     * The price is taken <b>first</b> and its result checked, so a refused payment leaves nothing
     * half-applied - no items, no reward commands, no claim record. Paying after delivery was
     * rejected: undoing a delivery means reclaiming items the player may already have moved,
     * equipped or traded, which is not a compensation anyone can trust.
     * <p>
     * After the money moves, the claim record is written <b>before</b> the reward commands, because
     * those commands are the step most likely to fail: {@code player.performCommand} propagates
     * {@code CommandException} out of any third-party executor that throws, and a kit pointing at a
     * broken command would otherwise take the money, hand over the items and never record the
     * claim - silently making a one-time kit claimable again, which is the whole product of a
     * one-time kit. No <em>reordering</em> here can produce "recorded but not delivered", because the
     * record is written after the items have been added to the inventory - subject to the
     * pre-existing overflow path this ordering does not address, where {@code countEmptySlots}
     * counts stacks against slots and {@code addItem}'s leftovers are discarded, so an over-sized
     * stack can be recorded as claimed while only partly delivered (UltiKits/UltiKits#24).
     * <p>
     * The cost of putting the record first, stated rather than left implicit: a storage fault in
     * {@code updateClaimData} now also skips the reward commands, where the previous order would
     * have run them. That is the favourable side of the trade - a claim that goes unrecorded now
     * duplicates fewer effects when it is made again - but it is a real change, and what to do
     * about the unguarded write itself is owned by UltiKits/UltiKits#26, not decided here.
     * <p>
     * 顺序：先扣款并检查结果，再发放物品，随后写入领取记录，最后执行奖励命令。
     *
     * @return {@link ClaimResult#PAYMENT_FAILED} when the price could not be withdrawn, otherwise
     *         {@link ClaimResult#SUCCESS}
     */
    private ClaimResult deliverKit(Player player, KitDefinition kit, ItemStack[] items) {
        // No isAvailable() term here on purpose: it would short-circuit this whole condition to
        // false if the Vault provider were deregistered after checkPrerequisites ran, delivering
        // the paid kit free - the very outcome this guard exists to stop (UltiKits/UltiKits#20,
        // gate-1 WR-01). The framework's bridge already returns false when no provider is
        // registered, so the term bought nothing and could only turn a refusal into a giveaway.
        if (!kit.isFree() && !EconomyUtils.withdraw(player, kit.getPrice())) {
            // checkPrerequisites saw the player could afford this, so reaching here means the
            // balance moved in between, the economy rejected the transaction, or the provider went
            // away.
            warnRefusedWithdrawalOnce(player, kit);
            return ClaimResult.PAYMENT_FAILED;
        }
        for (ItemStack item : items) {
            player.getInventory().addItem(item.clone());
        }
        updateClaimData(player.getUniqueId(), kit.getName());
        executePlayerCommands(player, kit.getPlayerCommands());
        executeConsoleCommands(player, kit.getConsoleCommands());
        return ClaimResult.SUCCESS;
    }

    /**
     * Writes one console warning per kit per server session for a refused withdrawal. See
     * {@link #refusalWarnedKits} for why it is throttled and how an operator re-arms it.
     */
    private void warnRefusedWithdrawalOnce(Player player, KitDefinition kit) {
        if (!refusalWarnedKits.add(kit.getName())) {
            return;
        }
        logger.warn("Kit '" + kit.getName() + "' was not delivered to " + player.getName()
                + ": the economy refused to withdraw " + kit.getPrice()
                + ". Further refusals for this kit are not logged until the kits are reloaded.");
    }

    @Override
    public long getRemainingCooldown(Player player, KitDefinition kit) {
        if (kit.isOneTime()) {
            KitClaimData claim = getClaimData(player.getUniqueId(), kit.getName());
            return claim != null ? -1 : 0;
        }

        KitClaimData claim = getClaimData(player.getUniqueId(), kit.getName());
        if (claim == null) {
            return 0;
        }

        long cooldownEnd = claim.getLastClaim() + (kit.getCooldown() * 1000);
        long remaining = cooldownEnd - System.currentTimeMillis();
        return Math.max(0, remaining);
    }

    @Override
    public String formatCooldown(long millis) {
        if (millis <= 0) {
            return plugin.i18n("kits.status.available");
        }

        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;

        StringBuilder sb = new StringBuilder();
        if (hours > 0) {
            sb.append(String.format(plugin.i18n("kits.cooldown.hours"), hours)).append(" ");
        }
        if (minutes > 0) {
            sb.append(String.format(plugin.i18n("kits.cooldown.minutes"), minutes)).append(" ");
        }
        if (seconds > 0 || sb.length() == 0) {
            sb.append(String.format(plugin.i18n("kits.cooldown.seconds"), seconds));
        }

        return sb.toString().trim();
    }

    @Override
    public String serializeItems(ItemStack[] items) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);

            dataOutput.writeInt(items.length);
            for (ItemStack item : items) {
                dataOutput.writeObject(item);
            }

            dataOutput.close();
            return Base64Coder.encodeLines(outputStream.toByteArray());
        } catch (IOException e) {
            logger.error("Failed to serialize kit items: " + e.getMessage());
            return null;
        }
    }

    @Nullable
    @Override
    public ItemStack[] deserializeItems(String data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);

            int size = dataInput.readInt();
            ItemStack[] items = new ItemStack[size];

            for (int i = 0; i < size; i++) {
                items[i] = (ItemStack) dataInput.readObject();
            }

            dataInput.close();
            return items;
        } catch (IOException | ClassNotFoundException e) {
            logger.error("Failed to deserialize kit items: " + e.getMessage());
            return null;
        }
    }

    // --- Internal methods ---

    @Nullable
    KitClaimData getClaimData(UUID playerUuid, String kitName) {
        List<KitClaimData> claims = claimOperator.query()
                .where("player_uuid").eq(playerUuid.toString())
                .list();

        return claims.stream()
                .filter(c -> c.getKitName().equalsIgnoreCase(kitName))
                .findFirst()
                .orElse(null);
    }

    void updateClaimData(UUID playerUuid, String kitName) {
        KitClaimData existing = getClaimData(playerUuid, kitName);

        if (existing != null) {
            existing.setLastClaim(System.currentTimeMillis());
            existing.setClaimCount(existing.getClaimCount() + 1);
            try {
                claimOperator.update(existing);
            } catch (IllegalAccessException e) {
                logger.error("Failed to update kit claim data: " + e.getMessage());
            }
        } else {
            KitClaimData claim = KitClaimData.builder()
                    .uuid(UUID.randomUUID().toString())
                    .playerUuid(playerUuid.toString())
                    .kitName(kitName)
                    .lastClaim(System.currentTimeMillis())
                    .claimCount(1)
                    .build();
            claimOperator.insert(claim);
        }
    }

    @Nullable
    KitDefinition parseKitFile(File file) {
        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

            KitDefinition kit = new KitDefinition();
            kit.setDisplayName(config.getString("displayName", "&7Kit"));
            kit.setDescription(config.getStringList("description"));
            kit.setPrice(config.getDouble("price", 0));
            kit.setLevelRequired(config.getInt("levelRequired", 0));
            kit.setPermission(config.getString("permission", ""));
            kit.setReBuyable(config.getBoolean("reBuyable", false));
            kit.setCooldown(config.getLong("cooldown", 0));
            kit.setPlayerCommands(config.getStringList("playerCommands"));
            kit.setConsoleCommands(config.getStringList("consoleCommands"));
            kit.setItems(config.getString("items", ""));

            // Validate icon material
            String iconStr = config.getString("icon", "CHEST");
            try {
                Material.valueOf(iconStr.toUpperCase());
                kit.setIcon(iconStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                logger.warn(plugin.i18n("kits.log.load_failed") + file.getName() + " - invalid icon: " + iconStr);
                kit.setIcon("CHEST");
            }

            return kit;
        } catch (Exception e) {
            logger.warn(plugin.i18n("kits.log.load_failed") + file.getName() + " - " + e.getMessage());
            return null;
        }
    }

    boolean saveKitToFile(String name, KitDefinition kit) {
        try {
            File kitFile = new File(plugin.getResourceFolderPath(), "kits/" + name + ".yml");
            YamlConfiguration config = new YamlConfiguration();

            config.set("displayName", kit.getDisplayName());
            config.set("description", kit.getDescription());
            config.set("icon", kit.getIcon());
            config.set("price", kit.getPrice());
            config.set("levelRequired", kit.getLevelRequired());
            config.set("permission", kit.getPermission());
            config.set("reBuyable", kit.isReBuyable());
            config.set("cooldown", kit.getCooldown());
            config.set("playerCommands", kit.getPlayerCommands());
            config.set("consoleCommands", kit.getConsoleCommands());
            config.set("items", kit.getItems());

            config.save(kitFile);
            return true;
        } catch (IOException e) {
            logger.error("Failed to save kit file: " + name + " - " + e.getMessage());
            return false;
        }
    }

    private void copyExampleKit(File folder) {
        try (InputStream is = plugin.getClass().getClassLoader().getResourceAsStream("kits/starter.yml")) {
            File exampleFile = new File(folder, "starter.yml");
            if (is != null && !exampleFile.exists()) {
                Files.copy(is, exampleFile.toPath());
            }
        } catch (IOException e) {
            logger.warn("Failed to copy example kit: " + e.getMessage());
        }
    }

    private void executePlayerCommands(Player player, List<String> commands) {
        if (commands == null || commands.isEmpty()) {
            return;
        }
        for (String cmd : commands) {
            String processed = cmd.replace("{player}", player.getName());
            player.performCommand(processed);
        }
    }

    private void executeConsoleCommands(Player player, List<String> commands) {
        if (commands == null || commands.isEmpty()) {
            return;
        }
        org.bukkit.plugin.Plugin ultiToolsPlugin = Bukkit.getPluginManager().getPlugin("UltiTools");
        if (ultiToolsPlugin == null) {
            return;
        }
        for (String cmd : commands) {
            String processed = cmd.replace("{player}", player.getName());
            String finalCmd = processed;
            Bukkit.getScheduler().runTask(ultiToolsPlugin, () ->
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd));
        }
    }
}
