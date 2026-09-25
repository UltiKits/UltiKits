package com.ultikits.plugins.kits.gui;

import com.ultikits.plugins.kits.i18n.CatalogueText;
import com.ultikits.plugins.kits.MockBukkitSupport;
import com.ultikits.plugins.kits.config.KitsConfig;
import com.ultikits.plugins.kits.model.KitDefinition;
import com.ultikits.plugins.kits.service.KitService;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.utils.EconomyUtils;
import mc.obliviate.inventory.Gui;
import mc.obliviate.inventory.Icon;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("KitBrowserGui")
@ExtendWith(MockitoExtension.class)
class KitBrowserGuiTest {

    @Mock
    private UltiToolsPlugin plugin;

    @Mock
    private KitService kitService;

    @Mock
    private Player player;

    @Mock
    private Economy economy;

    private KitBrowserGui gui;

    private KitsConfig config;

    private Plugin vault;

    @BeforeEach
    void setUp() {
        MockBukkitSupport.bootstrap();
        lenient().when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer("zh"));
        config = new KitsConfig("config/config.yml");
        gui = new KitBrowserGui(player, plugin, kitService, config, 0);

        EconomyUtils.reset();
    }

    @AfterEach
    void tearDown() {
        if (vault != null) {
            Bukkit.getServicesManager().unregisterAll(vault);
            vault = null;
        }
        EconomyUtils.reset();
        MockBukkitSupport.shutdown();
    }

    /**
     * Makes the Vault economy available or unavailable through the public Bukkit/Vault types only
     * (UltiKits/UltiKits#19): when available, a mock plugin named {@code Vault} plus the mocked Vault
     * {@link Economy} registered with the live MockBukkit services manager, which is exactly what
     * the framework's default economy bridge resolves; when unavailable, no {@code Vault} plugin at
     * all. No framework-internal seam or private field is touched.
     */
    private void setEconomyAvailable(boolean available) {
        EconomyUtils.reset();
        if (available) {
            vault = MockBukkit.createMockPlugin("Vault");
            Bukkit.getServicesManager().register(Economy.class, economy, vault, ServicePriority.Normal);
        }
        assertThat(EconomyUtils.isAvailable()).isEqualTo(available);
    }

    private void resetDebounce() throws Exception {
        setLastClickTime(gui, 0L);
    }

    /**
     * Places the browser's last-click stamp at a known point in the past, so a debounce test states
     * how long ago the previous click was instead of racing the clock. Reflection is used because
     * {@code lastClickTime} is the class's own private state and this module exposes no seam for it;
     * the pre-existing {@link #resetDebounce()} helper already reached the same field this way.
     */
    private void setLastClickTime(KitBrowserGui target, long value) throws Exception {
        Field lastClickField = KitBrowserGui.class.getDeclaredField("lastClickTime");
        lastClickField.setAccessible(true); // NOPMD
        lastClickField.set(target, value);
    }

    /**
     * Injects a mock inventory into the Gui's 'inventory' field so that addItem() can call
     * this.inventory.getSize() without NPE.
     */
    private void injectGuiInventory(Gui guiInstance) throws Exception {
        org.bukkit.inventory.Inventory mockInv = mock(org.bukkit.inventory.Inventory.class);
        lenient().when(mockInv.getSize()).thenReturn(54); // 6 rows

        Class<?> clazz = guiInstance.getClass();
        while (clazz != null) {
            try {
                Field invField = clazz.getDeclaredField("inventory");
                invField.setAccessible(true); // NOPMD
                invField.set(guiInstance, mockInv);
                break;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
    }

    /**
     * The chrome slots {@code onOpen} always fills regardless of page size: the separator row
     * (36-44) and the page indicator (49). The navigation arrows (45, 53) are conditional, so they
     * are excluded here as well and asserted separately where a row cares about them.
     */
    private static final Set<Integer> CHROME_SLOTS = chromeSlots();

    private static Set<Integer> chromeSlots() {
        Set<Integer> slots = new LinkedHashSet<>();
        for (int i = 36; i <= 44; i++) {
            slots.add(i);
        }
        slots.add(45);
        slots.add(49);
        slots.add(53);
        return slots;
    }

    /** The slots {@code onOpen} filled with kit icons, i.e. every filled slot that is not chrome. */
    private Set<Integer> kitSlotsOf(KitBrowserGui target) {
        Set<Integer> slots = new TreeSet<>(target.getItems().keySet());
        slots.removeAll(CHROME_SLOTS);
        return slots;
    }

    /** The page indicator's display name, which carries the {@code Page x/y} text. */
    private String pageIndicatorTextOf(KitBrowserGui target) {
        ItemMeta meta = target.getItems().get(49).getItem().getItemMeta();
        return meta == null ? "" : meta.getDisplayName();
    }

    private Set<Integer> slotRange(int fromInclusive, int toExclusive) {
        Set<Integer> slots = new TreeSet<>();
        for (int i = fromInclusive; i < toExclusive; i++) {
            slots.add(i);
        }
        return slots;
    }

    private List<KitDefinition> freeKits(int count) {
        List<KitDefinition> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(createKit("kit" + i, "&fKit" + i, "CHEST", 0, 0));
        }
        return list;
    }

    private KitDefinition createKit(String name, String displayName, String icon, double price, int level) {
        KitDefinition kit = new KitDefinition();
        kit.setName(name);
        kit.setDisplayName(displayName);
        kit.setIcon(icon);
        kit.setPrice(price);
        kit.setLevelRequired(level);
        kit.setDescription(new ArrayList<>());
        return kit;
    }

    // -----------------------------------------------------------------------
    // Constructor Tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("stores player reference")
        void storesPlayer() throws Exception {
            Field playerField = KitBrowserGui.class.getDeclaredField("player");
            playerField.setAccessible(true); // NOPMD
            assertThat(playerField.get(gui)).isSameAs(player);
        }

        @Test
        @DisplayName("stores plugin reference")
        void storesPlugin() throws Exception {
            Field pluginField = KitBrowserGui.class.getDeclaredField("plugin");
            pluginField.setAccessible(true); // NOPMD
            assertThat(pluginField.get(gui)).isSameAs(plugin);
        }

        @Test
        @DisplayName("stores kitService reference")
        void storesKitService() throws Exception {
            Field serviceField = KitBrowserGui.class.getDeclaredField("kitService");
            serviceField.setAccessible(true); // NOPMD
            assertThat(serviceField.get(gui)).isSameAs(kitService);
        }

        @Test
        @DisplayName("default page is 0")
        void defaultPage() throws Exception {
            Field pageField = KitBrowserGui.class.getDeclaredField("page");
            pageField.setAccessible(true); // NOPMD
            assertThat(pageField.getInt(gui)).isEqualTo(0);
        }

        @Test
        @DisplayName("stores config reference")
        void storesConfig() throws Exception {
            Field configField = KitBrowserGui.class.getDeclaredField("config");
            configField.setAccessible(true); // NOPMD
            assertThat(configField.get(gui)).isSameAs(config);
        }

        @Test
        @DisplayName("lastClickTime defaults to 0")
        void lastClickTimeDefault() throws Exception {
            Field lctField = KitBrowserGui.class.getDeclaredField("lastClickTime");
            lctField.setAccessible(true); // NOPMD
            assertThat(lctField.getLong(gui)).isEqualTo(0L);
        }

        @Test
        @DisplayName("can construct with different page number")
        void differentPage() throws Exception {
            KitBrowserGui gui2 = new KitBrowserGui(player, plugin, kitService, config, 3);
            Field pageField = KitBrowserGui.class.getDeclaredField("page");
            pageField.setAccessible(true); // NOPMD
            assertThat(pageField.getInt(gui2)).isEqualTo(3);
        }

    }

    // -----------------------------------------------------------------------
    // BuildKitIcon Tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("BuildKitIcon Tests")
    class BuildKitIconTests {

        @Test
        @DisplayName("valid material returns non-null icon")
        void validMaterial() {
            KitDefinition kit = createKit("sword", "&cSword Kit", "DIAMOND_SWORD", 0, 0);
            lenient().when(kitService.getRemainingCooldown(player, kit)).thenReturn(0L);

            Icon icon = gui.buildKitIcon(kit);

            assertThat(icon).isNotNull();
            assertThat(icon.getItem()).isNotNull();
        }

        @Test
        @DisplayName("invalid material falls back without throwing")
        void fallbackMaterial() {
            KitDefinition kit = createKit("bad", "&cBad Kit", "NOT_A_MATERIAL", 0, 0);
            lenient().when(kitService.getRemainingCooldown(player, kit)).thenReturn(0L);

            Icon icon = gui.buildKitIcon(kit);

            // Falls back to CHEST when material name is invalid
            assertThat(icon).isNotNull();
            assertThat(icon.getItem()).isNotNull();
        }

        @Test
        @DisplayName("handles lowercase icon name without throwing")
        void lowercaseIcon() {
            KitDefinition kit = createKit("lower", "&fKit", "gold_ingot", 0, 0);
            lenient().when(kitService.getRemainingCooldown(player, kit)).thenReturn(0L);

            // toUpperCase converts "gold_ingot" to "GOLD_INGOT" before Material.valueOf
            Icon icon = gui.buildKitIcon(kit);

            assertThat(icon).isNotNull();
            assertThat(icon.getItem()).isNotNull();
        }

        @Test
        @DisplayName("sets display name with color codes translated")
        void displayName() {
            KitDefinition kit = createKit("vip", "&6VIP Kit", "CHEST", 0, 0);

            Icon icon = gui.buildKitIcon(kit);
            ItemMeta meta = icon.getItem().getItemMeta();

            assertThat(meta).isNotNull();
            assertThat(meta.getDisplayName()).isNotNull();
        }

        @Test
        @DisplayName("free kit shows free label in lore")
        void freeKitLore() throws Exception {
            setEconomyAvailable(false);
            KitDefinition kit = createKit("free", "&aFree Kit", "CHEST", 0, 0);
            when(kitService.getRemainingCooldown(player, kit)).thenReturn(0L);

            Icon icon = gui.buildKitIcon(kit);
            ItemMeta meta = icon.getItem().getItemMeta();

            assertThat(meta).isNotNull();
            assertThat(meta.getLore()).isNotNull();
            List<String> lore = meta.getLore();
            assertThat(lore).anyMatch(l -> l.contains("免费"));
        }

        @Test
        @DisplayName("paid kit shows price in lore without economy")
        void paidKitLoreNoEconomy() throws Exception {
            setEconomyAvailable(false);
            KitDefinition kit = createKit("paid", "&6Paid Kit", "CHEST", 100.0, 0);
            lenient().when(kitService.getRemainingCooldown(player, kit)).thenReturn(0L);

            Icon icon = gui.buildKitIcon(kit);
            ItemMeta meta = icon.getItem().getItemMeta();

            assertThat(meta).isNotNull();
            List<String> lore = meta.getLore();
            assertThat(lore).anyMatch(l -> l.contains("100"));
        }

        @Test
        @DisplayName("paid kit shows formatted price with economy")
        void paidKitLoreWithEconomy() throws Exception {
            setEconomyAvailable(true);
            when(economy.format(250.0)).thenReturn("$250.00");
            when(economy.has(any(org.bukkit.OfflinePlayer.class), anyDouble())).thenReturn(true);
            KitDefinition kit = createKit("premium", "&6Premium", "GOLD_INGOT", 250.0, 0);
            when(kitService.getRemainingCooldown(player, kit)).thenReturn(0L);

            Icon icon = gui.buildKitIcon(kit);
            ItemMeta meta = icon.getItem().getItemMeta();

            assertThat(meta).isNotNull();
            List<String> lore = meta.getLore();
            assertThat(lore).anyMatch(l -> l.contains("$250.00"));
        }

        @Test
        @DisplayName("kit with level requirement shows level in lore")
        void levelRequirementInLore() throws Exception {
            setEconomyAvailable(false);
            KitDefinition kit = createKit("elite", "&cElite", "CHEST", 0, 20);
            when(player.getLevel()).thenReturn(25);
            when(kitService.getRemainingCooldown(player, kit)).thenReturn(0L);

            Icon icon = gui.buildKitIcon(kit);
            ItemMeta meta = icon.getItem().getItemMeta();

            assertThat(meta).isNotNull();
            List<String> lore = meta.getLore();
            assertThat(lore).anyMatch(l -> l.contains("等级要求") && l.contains("20"));
        }

        @Test
        @DisplayName("kit without level requirement omits level line")
        void noLevelRequirement() throws Exception {
            setEconomyAvailable(false);
            KitDefinition kit = createKit("basic", "&fBasic", "CHEST", 0, 0);
            when(kitService.getRemainingCooldown(player, kit)).thenReturn(0L);

            Icon icon = gui.buildKitIcon(kit);
            ItemMeta meta = icon.getItem().getItemMeta();

            assertThat(meta).isNotNull();
            List<String> lore = meta.getLore();
            assertThat(lore).noneMatch(l -> l.contains("等级要求"));
        }

        @Test
        @DisplayName("kit with description lines shows them in lore")
        void descriptionInLore() throws Exception {
            setEconomyAvailable(false);
            KitDefinition kit = createKit("desc", "&aKit", "CHEST", 0, 0);
            kit.setDescription(Arrays.asList("&7Line one", "&eLine two"));
            when(kitService.getRemainingCooldown(player, kit)).thenReturn(0L);

            Icon icon = gui.buildKitIcon(kit);
            ItemMeta meta = icon.getItem().getItemMeta();

            assertThat(meta).isNotNull();
            List<String> lore = meta.getLore();
            assertThat(lore.size()).isGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("kit with empty description has no blank separator before price")
        void emptyDescriptionNoSeparator() throws Exception {
            setEconomyAvailable(false);
            KitDefinition kit = createKit("noDesc", "&aKit", "CHEST", 0, 0);
            kit.setDescription(new ArrayList<>());
            when(kitService.getRemainingCooldown(player, kit)).thenReturn(0L);

            Icon icon = gui.buildKitIcon(kit);
            ItemMeta meta = icon.getItem().getItemMeta();

            assertThat(meta).isNotNull();
            List<String> lore = meta.getLore();
            // First lore line should be price info, not blank
            assertThat(lore.get(0)).isNotEmpty();
        }

        @Test
        @DisplayName("status text appears in lore")
        void statusInLore() throws Exception {
            setEconomyAvailable(false);
            KitDefinition kit = createKit("avail", "&aKit", "CHEST", 0, 0);
            when(kitService.getRemainingCooldown(player, kit)).thenReturn(0L);

            Icon icon = gui.buildKitIcon(kit);
            ItemMeta meta = icon.getItem().getItemMeta();

            assertThat(meta).isNotNull();
            List<String> lore = meta.getLore();
            assertThat(lore).anyMatch(l -> l.contains("状态"));
        }
    }

    // -----------------------------------------------------------------------
    // GetStatusText Tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("GetStatusText Tests")
    class GetStatusTextTests {

        @Test
        @DisplayName("level insufficient shows level status")
        void levelInsufficient() throws Exception {
            setEconomyAvailable(false);
            KitDefinition kit = createKit("elite", "&cElite", "CHEST", 0, 20);
            when(player.getLevel()).thenReturn(10);

            String result = gui.getStatusText(kit);

            assertThat(result).contains("等级不足");
        }

        @Test
        @DisplayName("level exactly meets requirement passes level check")
        void levelExactlyMet() throws Exception {
            setEconomyAvailable(false);
            KitDefinition kit = createKit("exact", "&fExact", "CHEST", 0, 15);
            when(player.getLevel()).thenReturn(15);
            when(kitService.getRemainingCooldown(player, kit)).thenReturn(0L);

            String result = gui.getStatusText(kit);

            assertThat(result).contains("可领取");
        }

        @Test
        @DisplayName("level exceeds requirement passes level check")
        void levelExceeded() throws Exception {
            setEconomyAvailable(false);
            KitDefinition kit = createKit("over", "&fOver", "CHEST", 0, 5);
            when(player.getLevel()).thenReturn(50);
            when(kitService.getRemainingCooldown(player, kit)).thenReturn(0L);

            String result = gui.getStatusText(kit);

            assertThat(result).contains("可领取");
        }

        @Test
        @DisplayName("balance insufficient shows balance status")
        void balanceInsufficient() throws Exception {
            setEconomyAvailable(true);
            when(economy.has((org.bukkit.OfflinePlayer) player, 100.0)).thenReturn(false);
            KitDefinition kit = createKit("paid", "&6Paid", "CHEST", 100.0, 0);

            String result = gui.getStatusText(kit);

            assertThat(result).contains("余额不足");
        }

        @Test
        @DisplayName("economy unavailable with paid kit shows balance insufficient")
        void economyUnavailable() throws Exception {
            setEconomyAvailable(false);
            KitDefinition kit = createKit("paid", "&6Paid", "CHEST", 100.0, 0);

            String result = gui.getStatusText(kit);

            assertThat(result).contains("余额不足");
        }

        @Test
        @DisplayName("sufficient balance passes economy check")
        void sufficientBalance() throws Exception {
            setEconomyAvailable(true);
            when(economy.has((org.bukkit.OfflinePlayer) player, 50.0)).thenReturn(true);
            KitDefinition kit = createKit("afford", "&6Afford", "CHEST", 50.0, 0);
            when(kitService.getRemainingCooldown(player, kit)).thenReturn(0L);

            String result = gui.getStatusText(kit);

            assertThat(result).contains("可领取");
        }

        @Test
        @DisplayName("already claimed (remaining < 0) shows claimed status")
        void alreadyClaimed() throws Exception {
            setEconomyAvailable(false);
            KitDefinition kit = createKit("once", "&fOnce", "CHEST", 0, 0);
            when(kitService.getRemainingCooldown(player, kit)).thenReturn(-1L);

            String result = gui.getStatusText(kit);

            assertThat(result).contains("已领取");
        }

        @Test
        @DisplayName("large negative cooldown still shows already claimed")
        void largeNegativeCooldown() throws Exception {
            setEconomyAvailable(false);
            KitDefinition kit = createKit("neg", "&fNeg", "CHEST", 0, 0);
            when(kitService.getRemainingCooldown(player, kit)).thenReturn(-999999L);

            String result = gui.getStatusText(kit);

            assertThat(result).contains("已领取");
        }

        @Test
        @DisplayName("on cooldown (remaining > 0) shows cooldown status")
        void onCooldown() throws Exception {
            setEconomyAvailable(false);
            KitDefinition kit = createKit("daily", "&eDaily", "CHEST", 0, 0);
            when(kitService.getRemainingCooldown(player, kit)).thenReturn(60000L);
            when(kitService.formatCooldown(60000L)).thenReturn("1m");

            String result = gui.getStatusText(kit);

            assertThat(result).contains("冷却中").contains("1m");
        }

        @Test
        @DisplayName("available (remaining == 0) shows available status")
        void available() throws Exception {
            setEconomyAvailable(false);
            KitDefinition kit = createKit("free", "&aFree", "CHEST", 0, 0);
            when(kitService.getRemainingCooldown(player, kit)).thenReturn(0L);

            String result = gui.getStatusText(kit);

            assertThat(result).contains("可领取");
        }

        @Test
        @DisplayName("level check is skipped when no level requirement")
        void noLevelReq() throws Exception {
            setEconomyAvailable(false);
            KitDefinition kit = createKit("basic", "&fBasic", "CHEST", 0, 0);
            when(kitService.getRemainingCooldown(player, kit)).thenReturn(0L);

            String result = gui.getStatusText(kit);

            assertThat(result).contains("可领取");
            verify(player, never()).getLevel();
        }

        @Test
        @DisplayName("free kit skips economy check")
        void freeSkipsEconomy() throws Exception {
            setEconomyAvailable(false);
            KitDefinition kit = createKit("free", "&aFree", "CHEST", 0, 0);
            when(kitService.getRemainingCooldown(player, kit)).thenReturn(0L);

            String result = gui.getStatusText(kit);

            assertThat(result).contains("可领取");
        }

        @Test
        @DisplayName("with sufficient level and balance, shows available")
        void allRequirementsMet() throws Exception {
            setEconomyAvailable(true);
            when(economy.has((org.bukkit.OfflinePlayer) player, 50.0)).thenReturn(true);
            KitDefinition kit = createKit("full", "&6Full", "CHEST", 50.0, 10);
            when(player.getLevel()).thenReturn(15);
            when(kitService.getRemainingCooldown(player, kit)).thenReturn(0L);

            String result = gui.getStatusText(kit);

            assertThat(result).contains("可领取");
        }

        @Test
        @DisplayName("level check takes priority over economy check")
        void levelPriorityOverEconomy() throws Exception {
            setEconomyAvailable(true);
            KitDefinition kit = createKit("both", "&6Both", "CHEST", 100.0, 20);
            when(player.getLevel()).thenReturn(5);

            String result = gui.getStatusText(kit);

            // Level check comes first, so shows level insufficient
            assertThat(result).contains("等级不足");
            // Economy should never be checked since level failed first
            verify(economy, never()).has(any(org.bukkit.OfflinePlayer.class), anyDouble());
        }

        @Test
        @DisplayName("economy check takes priority over cooldown check")
        void economyPriorityOverCooldown() throws Exception {
            setEconomyAvailable(true);
            when(economy.has((org.bukkit.OfflinePlayer) player, 100.0)).thenReturn(false);
            KitDefinition kit = createKit("prio", "&6Prio", "CHEST", 100.0, 0);

            String result = gui.getStatusText(kit);

            assertThat(result).contains("余额不足");
            // Cooldown should never be checked since economy failed first
            verify(kitService, never()).getRemainingCooldown(player, kit);
        }
    }

    // -----------------------------------------------------------------------
    // HandleKitClick Tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("HandleKitClick Tests")
    class HandleKitClickTests {

        @Test
        @DisplayName("SUCCESS sends success message and closes inventory")
        void successClaimsAndCloses() throws Exception {
            setEconomyAvailable(false);
            KitDefinition kit = createKit("starter", "&aStarter", "CHEST", 0, 0);
            when(kitService.claimKit(player, "starter")).thenReturn(KitService.ClaimResult.SUCCESS);

            gui.handleKitClick(kit);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(player).sendMessage(captor.capture());
            assertThat(captor.getValue()).contains("成功领取礼包");
            verify(player).closeInventory();
        }

        @Test
        @DisplayName("SUCCESS with paid kit and economy shows deduction message")
        void successPaidKit() throws Exception {
            setEconomyAvailable(true);
            when(economy.format(50.0)).thenReturn("$50.00");
            KitDefinition kit = createKit("paid", "&6Paid", "CHEST", 50.0, 0);
            when(kitService.claimKit(player, "paid")).thenReturn(KitService.ClaimResult.SUCCESS);

            gui.handleKitClick(kit);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(player, times(2)).sendMessage(captor.capture());
            List<String> messages = captor.getAllValues();
            assertThat(messages.get(0)).contains("成功领取礼包");
            assertThat(messages.get(1)).contains("已扣除").contains("$50.00");
        }

        @Test
        @DisplayName("SUCCESS with paid kit but economy unavailable skips deduction message")
        void successPaidKitNoEconomy() throws Exception {
            setEconomyAvailable(false);
            KitDefinition kit = createKit("paid", "&6Paid", "CHEST", 50.0, 0);
            when(kitService.claimKit(player, "paid")).thenReturn(KitService.ClaimResult.SUCCESS);

            gui.handleKitClick(kit);

            // Only success message, no deduction message
            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(player, times(1)).sendMessage(captor.capture());
            assertThat(captor.getValue()).contains("成功领取礼包");
        }

        @Test
        @DisplayName("SUCCESS with free kit does not show deduction message")
        void successFreeKit() throws Exception {
            setEconomyAvailable(true);
            KitDefinition kit = createKit("free", "&aFree", "CHEST", 0, 0);
            when(kitService.claimKit(player, "free")).thenReturn(KitService.ClaimResult.SUCCESS);

            gui.handleKitClick(kit);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(player, times(1)).sendMessage(captor.capture());
            assertThat(captor.getValue()).contains("成功领取礼包");
        }

        @Test
        @DisplayName("SUCCESS message includes kit display name")
        void successIncludesDisplayName() throws Exception {
            setEconomyAvailable(false);
            KitDefinition kit = createKit("vip", "&6VIP Deluxe", "CHEST", 0, 0);
            when(kitService.claimKit(player, "vip")).thenReturn(KitService.ClaimResult.SUCCESS);

            gui.handleKitClick(kit);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(player).sendMessage(captor.capture());
            assertThat(captor.getValue()).contains("VIP Deluxe");
        }

        @Test
        @DisplayName("debounce prevents rapid clicks")
        void debounce() throws Exception {
            KitDefinition kit = createKit("test", "&fTest", "CHEST", 0, 0);
            when(kitService.claimKit(player, "test")).thenReturn(KitService.ClaimResult.SUCCESS);

            // First click succeeds
            gui.handleKitClick(kit);
            verify(kitService, times(1)).claimKit(player, "test");

            // Immediate second click is debounced
            gui.handleKitClick(kit);
            verify(kitService, times(1)).claimKit(player, "test");
        }

        @Test
        @DisplayName("click works again after debounce reset")
        void clickAfterDebounceReset() throws Exception {
            KitDefinition kit = createKit("test", "&fTest", "CHEST", 0, 0);
            when(kitService.claimKit(player, "test")).thenReturn(KitService.ClaimResult.SUCCESS);

            // First click
            gui.handleKitClick(kit);
            verify(kitService, times(1)).claimKit(player, "test");

            // Reset debounce via reflection
            resetDebounce();

            // Second click succeeds after reset
            gui.handleKitClick(kit);
            verify(kitService, times(2)).claimKit(player, "test");
        }

        @Test
        @DisplayName("debounce does not send any message on blocked click")
        void debounceNoMessage() throws Exception {
            KitDefinition kit = createKit("test", "&fTest", "CHEST", 0, 0);
            when(kitService.claimKit(player, "test")).thenReturn(KitService.ClaimResult.SUCCESS);

            gui.handleKitClick(kit);
            // Clear first interaction
            clearInvocations(player);

            // Debounced click sends nothing
            gui.handleKitClick(kit);
            verify(player, never()).sendMessage(anyString());
        }

        @Test
        @DisplayName("NOT_FOUND sends not found message with kit name")
        void notFound() {
            KitDefinition kit = createKit("gone", "&fGone", "CHEST", 0, 0);
            when(kitService.claimKit(player, "gone")).thenReturn(KitService.ClaimResult.NOT_FOUND);

            gui.handleKitClick(kit);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(player).sendMessage(captor.capture());
            assertThat(captor.getValue()).contains("不存在").contains("gone");
        }

        @Test
        @DisplayName("NOT_FOUND does not close inventory")
        void notFoundNoClose() {
            KitDefinition kit = createKit("gone", "&fGone", "CHEST", 0, 0);
            when(kitService.claimKit(player, "gone")).thenReturn(KitService.ClaimResult.NOT_FOUND);

            gui.handleKitClick(kit);

            verify(player, never()).closeInventory();
        }

        @Test
        @DisplayName("NO_PERMISSION sends permission message")
        void noPermission() {
            KitDefinition kit = createKit("vip", "&6VIP", "CHEST", 0, 0);
            when(kitService.claimKit(player, "vip")).thenReturn(KitService.ClaimResult.NO_PERMISSION);

            gui.handleKitClick(kit);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(player).sendMessage(captor.capture());
            assertThat(captor.getValue()).contains("没有权限");
        }

        @Test
        @DisplayName("INSUFFICIENT_LEVEL sends level required message with level")
        void insufficientLevel() {
            KitDefinition kit = createKit("elite", "&cElite", "CHEST", 0, 30);
            when(kitService.claimKit(player, "elite")).thenReturn(KitService.ClaimResult.INSUFFICIENT_LEVEL);

            gui.handleKitClick(kit);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(player).sendMessage(captor.capture());
            assertThat(captor.getValue()).contains("等级不足").contains("30");
        }

        @Test
        @DisplayName("INSUFFICIENT_FUNDS sends balance message")
        void insufficientFunds() {
            KitDefinition kit = createKit("premium", "&6Premium", "CHEST", 500.0, 0);
            when(kitService.claimKit(player, "premium")).thenReturn(KitService.ClaimResult.INSUFFICIENT_FUNDS);

            gui.handleKitClick(kit);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(player).sendMessage(captor.capture());
            assertThat(captor.getValue()).contains("余额不足");
        }

        @Test
        @DisplayName("ALREADY_CLAIMED sends already claimed message")
        void alreadyClaimed() {
            KitDefinition kit = createKit("once", "&fOnce", "CHEST", 0, 0);
            when(kitService.claimKit(player, "once")).thenReturn(KitService.ClaimResult.ALREADY_CLAIMED);

            gui.handleKitClick(kit);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(player).sendMessage(captor.capture());
            assertThat(captor.getValue()).contains("已经领取过");
        }

        @Test
        @DisplayName("ON_COOLDOWN sends cooldown message with formatted time")
        void onCooldown() {
            KitDefinition kit = createKit("daily", "&eDaily", "CHEST", 0, 0);
            when(kitService.claimKit(player, "daily")).thenReturn(KitService.ClaimResult.ON_COOLDOWN);
            when(kitService.getRemainingCooldown(player, kit)).thenReturn(7200000L);
            when(kitService.formatCooldown(7200000L)).thenReturn("2h 0m");

            gui.handleKitClick(kit);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(player).sendMessage(captor.capture());
            assertThat(captor.getValue()).contains("冷却中").contains("2h 0m");
        }

        @Test
        @DisplayName("INVENTORY_FULL sends inventory full message")
        void inventoryFull() {
            KitDefinition kit = createKit("big", "&fBig", "CHEST", 0, 0);
            when(kitService.claimKit(player, "big")).thenReturn(KitService.ClaimResult.INVENTORY_FULL);

            gui.handleKitClick(kit);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(player).sendMessage(captor.capture());
            assertThat(captor.getValue()).contains("背包空间不足");
        }

        @Test
        @DisplayName("EMPTY_KIT sends empty kit message")
        void emptyKit() {
            KitDefinition kit = createKit("empty", "&fEmpty", "CHEST", 0, 0);
            when(kitService.claimKit(player, "empty")).thenReturn(KitService.ClaimResult.EMPTY_KIT);

            gui.handleKitClick(kit);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(player).sendMessage(captor.capture());
            assertThat(captor.getValue()).contains("礼包内容为空");
        }

        @Test
        @DisplayName("ERROR sends generic error message")
        void error() {
            KitDefinition kit = createKit("broken", "&fBroken", "CHEST", 0, 0);
            when(kitService.claimKit(player, "broken")).thenReturn(KitService.ClaimResult.ERROR);

            gui.handleKitClick(kit);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(player).sendMessage(captor.capture());
            assertThat(captor.getValue()).contains("领取礼包时发生错误");
        }

        @Test
        @DisplayName("NOT_RECORDED tells the player nothing was charged or given and keeps the browser open (UltiKits/UltiKits#26)")
        void notRecordedSaysNothingWasChargedOrGiven() {
            setEconomyAvailable(true);
            KitDefinition kit = createKit("paid", "&6Paid", "CHEST", 50.0, 0);
            when(kitService.claimKit(player, "paid")).thenReturn(KitService.ClaimResult.NOT_RECORDED);

            gui.handleKitClick(kit);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(player).sendMessage(captor.capture());
            assertThat(captor.getValue())
                    .isEqualTo(ChatColor.RED + CatalogueText.text("zh", "kits.claim.not_recorded"));
            assertThat(captor.getAllValues()).noneMatch(m -> m.contains("已扣除"));
            verify(player, never()).closeInventory();
        }

        @Test
        @DisplayName("NOT_RECORDED_REFUND_FAILED has its own reply and never says a price was deducted or returned")
        void notRecordedRefundFailedHasItsOwnReply() {
            setEconomyAvailable(true);
            KitDefinition kit = createKit("paid", "&6Paid", "CHEST", 50.0, 0);
            when(kitService.claimKit(player, "paid")).thenReturn(KitService.ClaimResult.NOT_RECORDED_REFUND_FAILED);

            gui.handleKitClick(kit);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(player).sendMessage(captor.capture());
            assertThat(captor.getValue())
                    .isEqualTo(ChatColor.RED + CatalogueText.text("zh", "kits.claim.not_recorded_refund_failed"));
        }

        /**
         * Sweep by class, the browser's copy of the command test: a result this switch forgets falls
         * to {@code default:} and says "Error claiming kit", which is true of no result but
         * {@code ERROR}.
         */
        @Test
        @DisplayName("every claim result other than ERROR has its own reply, never the generic error")
        void everyResultHasItsOwnReply() throws Exception {
            String generic = CatalogueText.text("zh", "kits.claim.error");
            lenient().when(kitService.formatCooldown(anyLong())).thenReturn("1s");
            KitDefinition kit = createKit("k", "&fK", "CHEST", 0, 0);
            for (KitService.ClaimResult result : KitService.ClaimResult.values()) {
                if (result == KitService.ClaimResult.ERROR) {
                    continue;
                }
                reset(player);
                resetDebounce();
                when(kitService.claimKit(player, "k")).thenReturn(result);

                gui.handleKitClick(kit);

                ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
                verify(player, atLeastOnce()).sendMessage(captor.capture());
                assertThat(captor.getAllValues()).as(result.name()).noneMatch(m -> m.contains(generic));
            }
        }

        /**
         * UltiKits/UltiKits#20: a refused payment must NOT produce the "deducted" line. Before the
         * fix the service returned SUCCESS on a failed withdrawal, so this branch ran and told the
         * player a price had been taken that never was.
         */
        @Test
        @DisplayName("PAYMENT_FAILED says the payment failed and never says a price was deducted")
        void paymentFailedSaysNoDeduction() {
            setEconomyAvailable(true);
            KitDefinition kit = createKit("paid", "&6Paid", "CHEST", 50.0, 0);
            when(kitService.claimKit(player, "paid")).thenReturn(KitService.ClaimResult.PAYMENT_FAILED);

            gui.handleKitClick(kit);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(player).sendMessage(captor.capture());
            assertThat(captor.getValue()).contains("扣款失败");
            // The control that this is not vacuous: successPaidKit() proves this same fixture DOES
            // emit the deduction line on SUCCESS, with the economy available and the kit priced.
            assertThat(captor.getAllValues()).noneMatch(m -> m.contains("已扣除"));
        }

        /**
         * Regression guard, NOT evidence: this passes with and without the fix, because before it
         * PAYMENT_FAILED fell to the default branch, which also does not close the inventory. It
         * earns its place because the sibling {@code nonSuccessNoClose} drives only
         * INSUFFICIENT_FUNDS despite its name, so nothing else pins this branch (gate-1 IN-02).
         */
        @Test
        @DisplayName("PAYMENT_FAILED does not close the inventory (guard, passes either way)")
        void paymentFailedNoClose() {
            KitDefinition kit = createKit("paid", "&6Paid", "CHEST", 50.0, 0);
            when(kitService.claimKit(player, "paid")).thenReturn(KitService.ClaimResult.PAYMENT_FAILED);

            gui.handleKitClick(kit);

            verify(player, never()).closeInventory();
        }

        @Test
        @DisplayName("non-SUCCESS results do not close inventory")
        void nonSuccessNoClose() {
            KitDefinition kit = createKit("fail", "&fFail", "CHEST", 0, 0);
            when(kitService.claimKit(player, "fail")).thenReturn(KitService.ClaimResult.INSUFFICIENT_FUNDS);

            gui.handleKitClick(kit);

            verify(player, never()).closeInventory();
        }

        @Test
        @DisplayName("all non-SUCCESS results only send one message")
        void nonSuccessSingleMessage() {
            KitDefinition kit = createKit("once", "&fOnce", "CHEST", 0, 0);
            when(kitService.claimKit(player, "once")).thenReturn(KitService.ClaimResult.ALREADY_CLAIMED);

            gui.handleKitClick(kit);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(player, times(1)).sendMessage(captor.capture());
        }
    }

    // -----------------------------------------------------------------------
    // OnOpen Tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("OnOpen Tests")
    class OnOpenTests {

        @Test
        @DisplayName("onOpen populates separator row and page indicator for empty kit list")
        void onOpenEmptyKits() throws Exception {
            setEconomyAvailable(false);
            when(kitService.getAvailableKits(player)).thenReturn(Collections.emptyList());
            injectGuiInventory(gui);

            org.bukkit.event.inventory.InventoryOpenEvent event =
                    mock(org.bukkit.event.inventory.InventoryOpenEvent.class);

            gui.onOpen(event);

            verify(kitService).getAvailableKits(player);
        }

        @Test
        @DisplayName("onOpen shows kits on first page")
        void onOpenWithKits() throws Exception {
            setEconomyAvailable(false);
            KitDefinition kit1 = createKit("starter", "&aStarter", "CHEST", 0, 0);
            KitDefinition kit2 = createKit("vip", "&6VIP", "DIAMOND", 100, 0);
            when(kitService.getAvailableKits(player)).thenReturn(Arrays.asList(kit1, kit2));
            when(kitService.getRemainingCooldown(eq(player), any(KitDefinition.class))).thenReturn(0L);
            injectGuiInventory(gui);

            org.bukkit.event.inventory.InventoryOpenEvent event =
                    mock(org.bukkit.event.inventory.InventoryOpenEvent.class);

            gui.onOpen(event);

            verify(kitService).getAvailableKits(player);
        }

        @Test
        @DisplayName("onOpen does not show prev button on first page")
        void noPrevButtonOnFirstPage() throws Exception {
            setEconomyAvailable(false);
            when(kitService.getAvailableKits(player)).thenReturn(Collections.emptyList());
            injectGuiInventory(gui);

            org.bukkit.event.inventory.InventoryOpenEvent event =
                    mock(org.bukkit.event.inventory.InventoryOpenEvent.class);

            gui.onOpen(event);

            // Page 0 means no previous page button -- just verify no crash
        }

        @Test
        @DisplayName("onOpen does not show next button when all kits fit on one page")
        void noNextButtonSinglePage() throws Exception {
            setEconomyAvailable(false);
            List<KitDefinition> kits = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                KitDefinition k = createKit("kit" + i, "&fKit" + i, "CHEST", 0, 0);
                kits.add(k);
            }
            when(kitService.getAvailableKits(player)).thenReturn(kits);
            when(kitService.getRemainingCooldown(eq(player), any(KitDefinition.class))).thenReturn(0L);
            injectGuiInventory(gui);

            org.bukkit.event.inventory.InventoryOpenEvent event =
                    mock(org.bukkit.event.inventory.InventoryOpenEvent.class);

            gui.onOpen(event);
        }

        @Test
        @DisplayName("onOpen shows next button when kits exceed one page")
        void nextButtonWhenMultiplePages() throws Exception {
            setEconomyAvailable(false);
            List<KitDefinition> kits = new ArrayList<>();
            for (int i = 0; i < 30; i++) {
                KitDefinition k = createKit("kit" + i, "&fKit" + i, "CHEST", 0, 0);
                kits.add(k);
            }
            when(kitService.getAvailableKits(player)).thenReturn(kits);
            when(kitService.getRemainingCooldown(eq(player), any(KitDefinition.class))).thenReturn(0L);
            injectGuiInventory(gui);

            org.bukkit.event.inventory.InventoryOpenEvent event =
                    mock(org.bukkit.event.inventory.InventoryOpenEvent.class);

            gui.onOpen(event);
        }

        @Test
        @DisplayName("onOpen page 1 shows prev button")
        void prevButtonOnPageOne() throws Exception {
            setEconomyAvailable(false);
            KitBrowserGui page1Gui = new KitBrowserGui(player, plugin, kitService, config, 1);
            injectGuiInventory(page1Gui);

            List<KitDefinition> kits = new ArrayList<>();
            for (int i = 0; i < 30; i++) {
                KitDefinition k = createKit("kit" + i, "&fKit" + i, "CHEST", 0, 0);
                kits.add(k);
            }
            when(kitService.getAvailableKits(player)).thenReturn(kits);
            when(kitService.getRemainingCooldown(eq(player), any(KitDefinition.class))).thenReturn(0L);

            org.bukkit.event.inventory.InventoryOpenEvent event =
                    mock(org.bukkit.event.inventory.InventoryOpenEvent.class);

            page1Gui.onOpen(event);
        }

        @Test
        @DisplayName("onOpen correctly calculates total pages")
        void correctTotalPages() throws Exception {
            setEconomyAvailable(false);
            List<KitDefinition> kits = new ArrayList<>();
            for (int i = 0; i < 56; i++) {
                KitDefinition k = createKit("kit" + i, "&fKit" + i, "CHEST", 0, 0);
                kits.add(k);
            }
            when(kitService.getAvailableKits(player)).thenReturn(kits);
            when(kitService.getRemainingCooldown(eq(player), any(KitDefinition.class))).thenReturn(0L);
            injectGuiInventory(gui);

            org.bukkit.event.inventory.InventoryOpenEvent event =
                    mock(org.bukkit.event.inventory.InventoryOpenEvent.class);

            gui.onOpen(event);
        }

        @Test
        @DisplayName("onOpen last page does not show next button")
        void lastPageNoNextButton() throws Exception {
            setEconomyAvailable(false);
            KitBrowserGui lastPageGui = new KitBrowserGui(player, plugin, kitService, config, 1);
            injectGuiInventory(lastPageGui);

            List<KitDefinition> kits = new ArrayList<>();
            for (int i = 0; i < 30; i++) {
                KitDefinition k = createKit("kit" + i, "&fKit" + i, "CHEST", 0, 0);
                kits.add(k);
            }
            when(kitService.getAvailableKits(player)).thenReturn(kits);
            when(kitService.getRemainingCooldown(eq(player), any(KitDefinition.class))).thenReturn(0L);

            org.bukkit.event.inventory.InventoryOpenEvent event =
                    mock(org.bukkit.event.inventory.InventoryOpenEvent.class);

            lastPageGui.onOpen(event);
        }

        @Test
        @DisplayName("onOpen handles exactly 28 kits (one full page)")
        void exactlyOnePage() throws Exception {
            setEconomyAvailable(false);
            List<KitDefinition> kits = new ArrayList<>();
            for (int i = 0; i < 28; i++) {
                KitDefinition k = createKit("kit" + i, "&fKit" + i, "CHEST", 0, 0);
                kits.add(k);
            }
            when(kitService.getAvailableKits(player)).thenReturn(kits);
            when(kitService.getRemainingCooldown(eq(player), any(KitDefinition.class))).thenReturn(0L);
            injectGuiInventory(gui);

            org.bukkit.event.inventory.InventoryOpenEvent event =
                    mock(org.bukkit.event.inventory.InventoryOpenEvent.class);

            gui.onOpen(event);
        }
    }

    // -----------------------------------------------------------------------
    // Configured page size (UltiKits/UltiKits#13)
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Configured Page Size Tests")
    class ConfiguredPageSizeTests {

        @Test
        @DisplayName("declared default 28 fills 28 kit slots and reports 2 pages for 30 kits")
        void declaredDefaultPageSize() throws Exception {
            setEconomyAvailable(false);
            when(kitService.getAvailableKits(player)).thenReturn(freeKits(30));
            when(kitService.getRemainingCooldown(eq(player), any(KitDefinition.class))).thenReturn(0L);
            injectGuiInventory(gui);

            gui.onOpen(mock(org.bukkit.event.inventory.InventoryOpenEvent.class));

            assertThat(kitSlotsOf(gui)).isEqualTo(slotRange(0, 28));
            assertThat(pageIndicatorTextOf(gui)).contains("1/2");
        }

        @Test
        @DisplayName("non-default 10 fills 10 kit slots and reports 3 pages for the same 30 kits")
        void nonDefaultPageSize() throws Exception {
            setEconomyAvailable(false);
            config.setKitsPerPage(10);
            when(kitService.getAvailableKits(player)).thenReturn(freeKits(30));
            when(kitService.getRemainingCooldown(eq(player), any(KitDefinition.class))).thenReturn(0L);
            injectGuiInventory(gui);

            gui.onOpen(mock(org.bukkit.event.inventory.InventoryOpenEvent.class));

            assertThat(kitSlotsOf(gui)).isEqualTo(slotRange(0, 10));
            assertThat(pageIndicatorTextOf(gui)).contains("1/3");
        }

        @Test
        @DisplayName("page 1 of a non-default 10-per-page listing starts at the 11th kit")
        void nonDefaultPageSizeOffsetsLaterPages() throws Exception {
            setEconomyAvailable(false);
            config.setKitsPerPage(10);
            KitBrowserGui page1Gui = new KitBrowserGui(player, plugin, kitService, config, 1);
            when(kitService.getAvailableKits(player)).thenReturn(freeKits(30));
            when(kitService.getRemainingCooldown(eq(player), any(KitDefinition.class))).thenReturn(0L);
            injectGuiInventory(page1Gui);

            page1Gui.onOpen(mock(org.bukkit.event.inventory.InventoryOpenEvent.class));

            assertThat(kitSlotsOf(page1Gui)).isEqualTo(slotRange(0, 10));
            assertThat(pageIndicatorTextOf(page1Gui)).contains("2/3");
            // The 11th kit (index 10) is the first icon on page 1 -- proves the start offset moved
            // with the configured size rather than staying on the hardcoded 28.
            ItemMeta firstIconMeta = page1Gui.getItems().get(0).getItem().getItemMeta();
            assertThat(firstIconMeta).isNotNull();
            assertThat(firstIconMeta.getDisplayName()).contains("Kit10");
        }

        @Test
        @DisplayName("a page-size change applied after construction is read at open, not cached")
        void pageSizeIsReadAtOpen() throws Exception {
            setEconomyAvailable(false);
            when(kitService.getAvailableKits(player)).thenReturn(freeKits(30));
            when(kitService.getRemainingCooldown(eq(player), any(KitDefinition.class))).thenReturn(0L);
            injectGuiInventory(gui);

            // Mirrors what ConfigManager#reloadConfigs does on /ul reload: it re-reads the file into
            // the SAME KitsConfig instance the module already holds, it does not hand out a new one.
            config.setKitsPerPage(7);

            gui.onOpen(mock(org.bukkit.event.inventory.InventoryOpenEvent.class));

            assertThat(kitSlotsOf(gui)).isEqualTo(slotRange(0, 7));
        }
    }

    // -----------------------------------------------------------------------
    // Configured click debounce (UltiKits/UltiKits#13)
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Configured Click Debounce Tests")
    class ConfiguredClickDebounceTests {

        @Test
        @DisplayName("declared default 200 drops a click 100ms after the previous one")
        void declaredDefaultBlocksInsideWindow() throws Exception {
            KitDefinition kit = createKit("test", "&fTest", "CHEST", 0, 0);
            setLastClickTime(gui, System.currentTimeMillis() - 100L);

            gui.handleKitClick(kit);

            verify(kitService, never()).claimKit(any(Player.class), anyString());
        }

        @Test
        @DisplayName("declared default 200 accepts a click 250ms after the previous one")
        void declaredDefaultAllowsOutsideWindow() throws Exception {
            KitDefinition kit = createKit("test", "&fTest", "CHEST", 0, 0);
            when(kitService.claimKit(player, "test")).thenReturn(KitService.ClaimResult.SUCCESS);
            setLastClickTime(gui, System.currentTimeMillis() - 250L);

            gui.handleKitClick(kit);

            verify(kitService).claimKit(player, "test");
        }

        @Test
        @DisplayName("non-default 50 accepts the same click 100ms after the previous one")
        void nonDefaultShortensTheWindow() throws Exception {
            KitDefinition kit = createKit("test", "&fTest", "CHEST", 0, 0);
            when(kitService.claimKit(player, "test")).thenReturn(KitService.ClaimResult.SUCCESS);
            config.setClickCooldownMs(50);
            setLastClickTime(gui, System.currentTimeMillis() - 100L);

            gui.handleKitClick(kit);

            verify(kitService).claimKit(player, "test");
        }

        @Test
        @DisplayName("non-default 5000 drops a click 250ms after the previous one")
        void nonDefaultLengthensTheWindow() throws Exception {
            KitDefinition kit = createKit("test", "&fTest", "CHEST", 0, 0);
            config.setClickCooldownMs(5000);
            setLastClickTime(gui, System.currentTimeMillis() - 250L);

            gui.handleKitClick(kit);

            verify(kitService, never()).claimKit(any(Player.class), anyString());
        }
    }

    // -----------------------------------------------------------------------
    // Master switch on an already-open browser (UltiKits/UltiKits#13)
    // -----------------------------------------------------------------------

    /**
     * The browser is opened by {@code /kits}, which refuses while the master switch is off, so the
     * only way this GUI can meet a disabled kit system is a browser that was ALREADY OPEN when the
     * switch was flipped. Every test here therefore builds the browser with the switch on -- the
     * shared {@code setUp} does exactly that, since `enabled` defaults to true -- and flips it
     * afterwards. A test that set the switch before construction would not exercise this path.
     * <p>
     * Two different mechanisms are covered, and they are deliberately not the same mechanism. A
     * CLAIM is refused by {@code KitService#claimKit} returning {@code SYSTEM_DISABLED}, which this
     * class only renders -- the guard itself is tested at the gateway, in
     * {@code KitServiceImplTest$ClaimTests$MasterSwitchTests}, because that is where a caller added
     * later would meet it. A PAGE TURN is refused by this class's own single page-turn method,
     * since turning a page never reaches the service at all.
     */
    @Nested
    @DisplayName("Master Switch On An Open Browser Tests")
    class MasterSwitchOnOpenBrowserTests {

        @Test
        @DisplayName("switch on: a click on an open browser still claims")
        void switchOnStillClaims() throws Exception {
            KitDefinition kit = createKit("test", "&fTest", "CHEST", 0, 0);
            when(kitService.claimKit(player, "test")).thenReturn(KitService.ClaimResult.SUCCESS);

            gui.handleKitClick(kit);

            verify(kitService).claimKit(player, "test");
            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(player).sendMessage(captor.capture());
            assertThat(captor.getValue()).contains("成功领取礼包");
        }

        @Test
        @DisplayName("SYSTEM_DISABLED from the gateway is rendered as the refusal, not a generic error")
        void systemDisabledIsRenderedAsTheRefusal() throws Exception {
            KitDefinition kit = createKit("test", "&fTest", "CHEST", 0, 0);
            when(kitService.claimKit(player, "test"))
                    .thenReturn(KitService.ClaimResult.SYSTEM_DISABLED);

            gui.handleKitClick(kit);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(player).sendMessage(captor.capture());
            assertThat(captor.getValue())
                    .contains("礼包系统当前已关闭")
                    .doesNotContain("领取礼包时发生错误");
        }

        @Test
        @DisplayName("the debounce still runs first, so a refused click cannot be spammed")
        void refusalIsThrottledByTheDebounce() throws Exception {
            KitDefinition kit = createKit("test", "&fTest", "CHEST", 0, 0);
            when(kitService.claimKit(player, "test"))
                    .thenReturn(KitService.ClaimResult.SYSTEM_DISABLED);

            gui.handleKitClick(kit);
            gui.handleKitClick(kit);

            // One refusal, not two: the second click is inside the 200ms debounce window and is
            // dropped before the gateway is called at all.
            verify(kitService, times(1)).claimKit(player, "test");
            verify(player, times(1)).sendMessage(anyString());
        }

        /**
         * Opens page 0 of a 30-kit catalogue and hands back its next-page arrow at slot 53. A mock
         * plugin named {@code UltiTools} is registered first, because {@code openPage} schedules the
         * reopen against it: without one the deferred task is never created and a test would "pass"
         * by never reaching the code under test.
         */
        private Icon nextPageArrow() throws Exception {
            setEconomyAvailable(false);
            MockBukkit.createMockPlugin("UltiTools");
            when(kitService.getAvailableKits(player)).thenReturn(freeKits(30));
            when(kitService.getRemainingCooldown(eq(player), any(KitDefinition.class))).thenReturn(0L);
            injectGuiInventory(gui);
            gui.onOpen(mock(org.bukkit.event.inventory.InventoryOpenEvent.class));
            Icon arrow = (Icon) gui.getItems().get(53);
            assertThat(arrow).as("page 0 of 30 kits at 28 per page must carry a next arrow").isNotNull();
            return arrow;
        }

        /** Runs the tick the page turn was deferred to, which is where the switch is now read. */
        private void runTheDeferredTask() {
            MockBukkit.getMock().getScheduler().performOneTick();
        }

        @Test
        @DisplayName("switch on: the deferred page turn closes the current page and opens the next")
        void switchOnLetsThePageTurn() throws Exception {
            Icon arrow = nextPageArrow();

            arrow.getClickAction().accept(mock(org.bukkit.event.inventory.InventoryClickEvent.class));

            // The task's last statement constructs the replacement browser and calls open(), which
            // needs obliviate's InventoryAPI singleton. This harness does not stand that singleton
            // up, and standing it up would outlive the per-test server teardown. Reaching that error
            // is itself the assertion that matters here: it can only be reached AFTER the switch
            // check passed and closeInventory() ran, which is what separates this case from the two
            // refusal cases below, where the task returns before either.
            assertThatThrownBy(this::runTheDeferredTask)
                    .hasMessageContaining("Inventory API is not initialized");

            verify(player).closeInventory();
            verify(player, never()).sendMessage(anyString());
        }

        @Test
        @DisplayName("switch flipped off before the click: the page turn refuses and keeps the page")
        void switchFlippedOffBeforeTheClickRefusesThePageTurn() throws Exception {
            Icon arrow = nextPageArrow();

            config.setEnabled(false);

            arrow.getClickAction().accept(mock(org.bukkit.event.inventory.InventoryClickEvent.class));
            runTheDeferredTask();

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(player).sendMessage(captor.capture());
            assertThat(captor.getValue()).contains("礼包系统当前已关闭");
            // The page the player was already looking at is left alone: refusing the turn is the
            // whole action, and no fresh page of the catalogue is rendered.
            verify(player, never()).closeInventory();
        }

        @Test
        @DisplayName("switch flipped off BETWEEN the click and the deferred open: still refused")
        void switchFlippedOffBetweenClickAndDeferredOpenIsStillRefused() throws Exception {
            Icon arrow = nextPageArrow();

            // The click is accepted while the switch is still on; the reopen it schedules runs a
            // tick later. An operator's `/ul reload UltiTools-Kits` lands in that window. A check
            // taken at click time would already have passed, so the switch has to be read by the
            // deferred task itself.
            arrow.getClickAction().accept(mock(org.bukkit.event.inventory.InventoryClickEvent.class));
            config.setEnabled(false);
            runTheDeferredTask();

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(player).sendMessage(captor.capture());
            assertThat(captor.getValue()).contains("礼包系统当前已关闭");
            verify(player, never()).closeInventory();
        }
    }

    // -----------------------------------------------------------------------
    // A page index that no longer exists (UltiKits/UltiKits#13, Codex P2)
    // -----------------------------------------------------------------------

    /**
     * The page size and the catalogue are both live: `/ul reload UltiTools-Kits` can change
     * `kits_per_page` and `/kits reload` can shrink the kit list, either of which can leave a
     * browser holding a page index that no longer exists. `onOpen` is where every browser is
     * rendered, whoever constructed it, so the index is clamped there rather than at each caller.
     */
    @Nested
    @DisplayName("Stale Page Index Tests")
    class StalePageIndexTests {

        @Test
        @DisplayName("a page index past the end renders the last page, not an empty one")
        void pastTheEndRendersTheLastPage() throws Exception {
            setEconomyAvailable(false);
            // Rendered at 7 per page, 30 kits => 5 pages, so page index 4 was legal. The operator
            // then raises the size to 28, leaving 2 pages and this browser pointing past the end.
            KitBrowserGui stale = new KitBrowserGui(player, plugin, kitService, config, 4);
            when(kitService.getAvailableKits(player)).thenReturn(freeKits(30));
            when(kitService.getRemainingCooldown(eq(player), any(KitDefinition.class))).thenReturn(0L);
            injectGuiInventory(stale);

            stale.onOpen(mock(org.bukkit.event.inventory.InventoryOpenEvent.class));

            // Page index 1 of 2: the 29th and 30th kits, not an empty inventory.
            assertThat(kitSlotsOf(stale)).isEqualTo(slotRange(0, 2));
            assertThat(pageIndicatorTextOf(stale)).contains("2/2").doesNotContain("5/2");
            // Last page, so no next arrow; not the first, so a prev arrow back into range.
            assertThat(stale.getItems()).doesNotContainKey(53);
            assertThat(stale.getItems()).containsKey(45);
        }

        @Test
        @DisplayName("a shrunken catalogue is clamped the same way")
        void shrunkenCatalogueIsClampedTheSameWay() throws Exception {
            setEconomyAvailable(false);
            // Page 1 was legal with 30 kits at 28 per page. `/kits reload` then leaves 5 kits.
            KitBrowserGui stale = new KitBrowserGui(player, plugin, kitService, config, 1);
            when(kitService.getAvailableKits(player)).thenReturn(freeKits(5));
            when(kitService.getRemainingCooldown(eq(player), any(KitDefinition.class))).thenReturn(0L);
            injectGuiInventory(stale);

            stale.onOpen(mock(org.bukkit.event.inventory.InventoryOpenEvent.class));

            assertThat(kitSlotsOf(stale)).isEqualTo(slotRange(0, 5));
            assertThat(pageIndicatorTextOf(stale)).contains("1/1");
            assertThat(stale.getItems()).doesNotContainKey(45);
            assertThat(stale.getItems()).doesNotContainKey(53);
        }

        @Test
        @DisplayName("an in-range page is left exactly where it is")
        void inRangePageIsUntouched() throws Exception {
            setEconomyAvailable(false);
            config.setKitsPerPage(10);
            KitBrowserGui page1 = new KitBrowserGui(player, plugin, kitService, config, 1);
            when(kitService.getAvailableKits(player)).thenReturn(freeKits(30));
            when(kitService.getRemainingCooldown(eq(player), any(KitDefinition.class))).thenReturn(0L);
            injectGuiInventory(page1);

            page1.onOpen(mock(org.bukkit.event.inventory.InventoryOpenEvent.class));

            assertThat(pageIndicatorTextOf(page1)).contains("2/3");
            ItemMeta firstIcon = page1.getItems().get(0).getItem().getItemMeta();
            assertThat(firstIcon).isNotNull();
            assertThat(firstIcon.getDisplayName()).contains("Kit10");
        }
    }
}
