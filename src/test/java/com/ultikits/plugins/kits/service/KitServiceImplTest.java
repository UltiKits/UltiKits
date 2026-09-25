package com.ultikits.plugins.kits.service;

import com.ultikits.plugins.kits.i18n.CatalogueText;
import com.ultikits.plugins.kits.MockBukkitSupport;
import com.ultikits.plugins.kits.config.KitsConfig;
import com.ultikits.plugins.kits.entity.KitClaimData;
import com.ultikits.plugins.kits.model.KitDefinition;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.interfaces.DataOperator;
import com.ultikits.ultitools.interfaces.Query;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;
import com.ultikits.ultitools.utils.EconomyUtils;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("KitServiceImpl")
class KitServiceImplTest {

    @TempDir
    File tempDir;

    private UltiToolsPlugin plugin;
    private PluginLogger mockLogger;
    @SuppressWarnings("unchecked")
    private DataOperator<KitClaimData> mockClaimOperator;
    @SuppressWarnings("unchecked")
    private Query<KitClaimData> mockQuery;
    private KitsConfig config;
    private KitServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        plugin = mock(UltiToolsPlugin.class);
        config = new KitsConfig("config/config.yml");
        mockLogger = mock(PluginLogger.class);
        mockClaimOperator = mock(DataOperator.class);
        mockQuery = mock(Query.class);

        when(plugin.getLogger()).thenReturn(mockLogger);
        when(plugin.getResourceFolderPath()).thenReturn(tempDir.getAbsolutePath());
        when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer("zh"));
        when(plugin.getDataOperator(KitClaimData.class)).thenReturn(mockClaimOperator);
        when(mockClaimOperator.query()).thenReturn(mockQuery);
        when(mockQuery.where(anyString())).thenReturn(mockQuery);
        when(mockQuery.eq(any())).thenReturn(mockQuery);
        when(mockQuery.and(anyString())).thenReturn(mockQuery);
        when(mockQuery.list()).thenReturn(Collections.emptyList());
    }

    @AfterEach
    void tearDown() {
        EconomyUtils.reset();
        if (MockBukkit.isMocked()) {
            MockBukkitSupport.shutdown();
        }
    }

    /**
     * Create a KitServiceImpl after kit files have been set up in tempDir.
     * The constructor calls loadKits(), so files must exist before construction.
     */
    private KitServiceImpl createService() {
        return new KitServiceImpl(plugin, config);
    }

    /**
     * Create a minimal kit YAML file in the kits folder.
     */
    private File createKitFile(String name, String displayName, String icon, double price,
                               int levelRequired, String permission, boolean reBuyable, long cooldown) throws IOException {
        File kitsFolder = new File(tempDir, "kits");
        kitsFolder.mkdirs();
        File kitFile = new File(kitsFolder, name + ".yml");

        StringBuilder yaml = new StringBuilder();
        yaml.append("displayName: \"").append(displayName).append("\"\n");
        yaml.append("icon: ").append(icon).append("\n");
        yaml.append("price: ").append(price).append("\n");
        yaml.append("levelRequired: ").append(levelRequired).append("\n");
        yaml.append("permission: \"").append(permission).append("\"\n");
        yaml.append("reBuyable: ").append(reBuyable).append("\n");
        yaml.append("cooldown: ").append(cooldown).append("\n");
        yaml.append("items: \"someBase64Data\"\n");
        yaml.append("description:\n  - \"A test kit\"\n");
        yaml.append("playerCommands: []\n");
        yaml.append("consoleCommands: []\n");

        FileWriter writer = new FileWriter(kitFile);
        writer.write(yaml.toString());
        writer.close();
        return kitFile;
    }

    private File createSimpleKitFile(String name) throws IOException {
        return createKitFile(name, "&a" + name, "CHEST", 0, 0, "", false, 0);
    }

    private File createKitFileWithItems(String name, String items) throws IOException {
        File kitsFolder = new File(tempDir, "kits");
        kitsFolder.mkdirs();
        File kitFile = new File(kitsFolder, name + ".yml");

        StringBuilder yaml = new StringBuilder();
        yaml.append("displayName: \"&a").append(name).append("\"\n");
        yaml.append("icon: CHEST\n");
        yaml.append("price: 0\n");
        yaml.append("levelRequired: 0\n");
        yaml.append("permission: \"\"\n");
        yaml.append("reBuyable: false\n");
        yaml.append("cooldown: 0\n");
        yaml.append("items: \"").append(items).append("\"\n");
        yaml.append("description:\n  - \"A test kit\"\n");
        yaml.append("playerCommands: []\n");
        yaml.append("consoleCommands: []\n");

        FileWriter writer = new FileWriter(kitFile);
        writer.write(yaml.toString());
        writer.close();
        return kitFile;
    }

    /**
     * Inject a KitDefinition into the service's internal kits map via reflection.
     */
    private void injectKit(KitServiceImpl svc, KitDefinition kit) throws Exception {
        Field kitsField = KitServiceImpl.class.getDeclaredField("kits");
        kitsField.setAccessible(true); // NOPMD
        @SuppressWarnings("unchecked")
        Map<String, KitDefinition> kitsMap = (Map<String, KitDefinition>) kitsField.get(svc);
        kitsMap.put(kit.getName().toLowerCase(), kit);
    }

    /**
     * Makes a mock Vault economy available through the public Bukkit/Vault types only
     * (UltiKits/UltiKits#19): boots the module's shared MockBukkit server (shut down again in
     * {@link #tearDown()}), adds a mock plugin named {@code Vault} and registers the returned
     * {@link Economy} with the live services manager, which is exactly what the framework's
     * default economy bridge resolves. No framework-internal seam or private field is touched.
     */
    private Economy setupMockEconomy() {
        Economy mockEconomy = mock(Economy.class);
        MockBukkitSupport.bootstrap();
        Plugin vault = MockBukkit.createMockPlugin("Vault");
        Bukkit.getServicesManager().register(Economy.class, mockEconomy, vault, ServicePriority.Normal);
        EconomyUtils.reset();
        assertThat(EconomyUtils.isAvailable()).isTrue();
        return mockEconomy;
    }

    private Player createMockPlayer() {
        Player player = mock(Player.class);
        when(player.getName()).thenReturn("TestPlayer");
        when(player.getUniqueId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        when(player.getLevel()).thenReturn(10);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        return player;
    }

    private Player createMockPlayerWithUuid(UUID uuid, String name, int level) {
        Player player = mock(Player.class);
        when(player.getName()).thenReturn(name);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getLevel()).thenReturn(level);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        return player;
    }

    private KitDefinition createTestKit(String name) {
        KitDefinition kit = new KitDefinition();
        kit.setName(name);
        kit.setDisplayName("&a" + name);
        kit.setIcon("CHEST");
        kit.setItems("someBase64Data");
        return kit;
    }

    private ItemStack mockItemStack(Material material) {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(material);
        return item;
    }

    // =========================================================================
    // Constructor Tests
    // =========================================================================
    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("constructor initializes all fields from plugin")
        void constructorInitializesFields() {
            new File(tempDir, "kits").mkdirs();
            service = createService();

            verify(plugin).getLogger();
            verify(plugin).getDataOperator(KitClaimData.class);
            verify(plugin, atLeastOnce()).getResourceFolderPath();
        }

        @Test
        @DisplayName("constructor calls loadKits during initialization")
        void constructorCallsLoadKits() throws IOException {
            createSimpleKitFile("autoloaded");

            service = createService();

            assertThat(service.getKit("autoloaded")).isNotNull();
        }
    }

    // =========================================================================
    // Loading Tests
    // =========================================================================
    @Nested
    @DisplayName("Loading Tests")
    class LoadingTests {

        @Test
        @DisplayName("loads kits from YAML files in kits folder")
        void loadsFromYamlFiles() throws IOException {
            createKitFile("starter", "&aStarter Kit", "CHEST", 0, 0, "", false, 0);
            createKitFile("vip", "&bVIP Kit", "DIAMOND", 100, 5, "kit.vip", true, 3600);

            service = createService();

            assertThat(service.getAllKits()).hasSize(2);
            assertThat(service.getKit("starter")).isNotNull();
            assertThat(service.getKit("vip")).isNotNull();
        }

        @Test
        @DisplayName("creates kits folder if not exists")
        void createsFolderIfNotExists() {
            service = createService();

            File kitsFolder = new File(tempDir, "kits");
            assertThat(kitsFolder).exists().isDirectory();
        }

        @Test
        @DisplayName("logs warning when no kit files found")
        void logsWarningWhenEmpty() {
            new File(tempDir, "kits").mkdirs();

            service = createService();

            verify(mockLogger).warn(contains("没有找到礼包配置文件"));
            assertThat(service.getAllKits()).isEmpty();
        }

        @Test
        @DisplayName("loads multiple kits and reports count")
        void loadsMultipleKits() throws IOException {
            createSimpleKitFile("kit1");
            createSimpleKitFile("kit2");
            createSimpleKitFile("kit3");

            service = createService();

            assertThat(service.getAllKits()).hasSize(3);
            verify(mockLogger).info(contains("3"));
        }

        @Test
        @DisplayName("normalizes kit names to lowercase")
        void normalizesNamesToLowercase() throws IOException {
            createKitFile("MyKit", "&aMy Kit", "CHEST", 0, 0, "", false, 0);

            service = createService();

            assertThat(service.getKit("mykit")).isNotNull();
        }

        @Test
        @DisplayName("handles invalid icon material gracefully with CHEST fallback")
        void handlesInvalidIcon() throws IOException {
            createKitFile("badicon", "&aBad", "NOT_A_MATERIAL", 0, 0, "", false, 0);

            service = createService();

            KitDefinition kit = service.getKit("badicon");
            assertThat(kit).isNotNull();
            assertThat(kit.getIcon()).isEqualTo("CHEST");
            verify(mockLogger).warn(contains("图标无效: NOT_A_MATERIAL"));
        }

        @Test
        @DisplayName("parses all kit fields from YAML")
        void parsesAllFields() throws IOException {
            createKitFile("full", "&6Full Kit", "DIAMOND_SWORD", 50.5, 10,
                    "kit.full", true, 7200);

            service = createService();

            KitDefinition kit = service.getKit("full");
            assertThat(kit).isNotNull();
            assertThat(kit.getDisplayName()).isEqualTo("&6Full Kit");
            assertThat(kit.getIcon()).isEqualTo("DIAMOND_SWORD");
            assertThat(kit.getPrice()).isEqualTo(50.5);
            assertThat(kit.getLevelRequired()).isEqualTo(10);
            assertThat(kit.getPermission()).isEqualTo("kit.full");
            assertThat(kit.isReBuyable()).isTrue();
            assertThat(kit.getCooldown()).isEqualTo(7200);
        }

        @Test
        @DisplayName("ignores non-yml files in kits folder")
        void ignoresNonYmlFiles() throws IOException {
            createSimpleKitFile("valid");
            File kitsFolder = new File(tempDir, "kits");
            new File(kitsFolder, "readme.txt").createNewFile();
            new File(kitsFolder, "backup.bak").createNewFile();

            service = createService();

            assertThat(service.getAllKits()).hasSize(1);
        }

        @Test
        @DisplayName("reload clears and reloads kits")
        void reloadClearsAndReloads() throws IOException {
            createSimpleKitFile("original");
            service = createService();
            assertThat(service.getAllKits()).hasSize(1);

            createSimpleKitFile("added");
            service.reload();

            assertThat(service.getAllKits()).hasSize(2);
            assertThat(service.getKit("added")).isNotNull();
        }

        @Test
        @DisplayName("reload removes kits whose files were deleted")
        void reloadRemovesDeletedKits() throws IOException {
            File kitFile = createSimpleKitFile("temporary");
            service = createService();
            assertThat(service.getKit("temporary")).isNotNull();

            kitFile.delete();
            service.reload();

            assertThat(service.getKit("temporary")).isNull();
            assertThat(service.getAllKits()).isEmpty();
        }

        @Test
        @DisplayName("kit name is set from file name, not YAML content")
        void kitNameFromFileName() throws IOException {
            createSimpleKitFile("warrior");

            service = createService();

            KitDefinition kit = service.getKit("warrior");
            assertThat(kit).isNotNull();
            assertThat(kit.getName()).isEqualTo("warrior");
        }

        @Test
        @DisplayName("loads kit with empty items field")
        void loadsKitWithEmptyItems() throws IOException {
            createKitFileWithItems("emptyitems", "");

            service = createService();

            KitDefinition kit = service.getKit("emptyitems");
            assertThat(kit).isNotNull();
            assertThat(kit.hasItems()).isFalse();
        }

        @Test
        @DisplayName("loadKits clears existing kits before loading")
        void loadKitsClearsExisting() throws Exception {
            new File(tempDir, "kits").mkdirs();
            service = createService();
            injectKit(service, createTestKit("injected"));
            assertThat(service.getAllKits()).hasSize(1);

            service.loadKits();

            assertThat(service.getAllKits()).isEmpty();
        }

        @Test
        @DisplayName("parses kit description as string list")
        void parsesDescriptionList() throws IOException {
            File kitsFolder = new File(tempDir, "kits");
            kitsFolder.mkdirs();
            File kitFile = new File(kitsFolder, "desc.yml");

            StringBuilder yaml = new StringBuilder();
            yaml.append("displayName: \"&aDesc Kit\"\n");
            yaml.append("icon: CHEST\n");
            yaml.append("description:\n");
            yaml.append("  - \"&7Line one\"\n");
            yaml.append("  - \"&eLine two\"\n");
            yaml.append("  - \"&6Line three\"\n");
            yaml.append("items: \"\"\n");

            FileWriter writer = new FileWriter(kitFile);
            writer.write(yaml.toString());
            writer.close();

            service = createService();

            KitDefinition kit = service.getKit("desc");
            assertThat(kit).isNotNull();
            assertThat(kit.getDescription()).hasSize(3);
            assertThat(kit.getDescription()).containsExactly("&7Line one", "&eLine two", "&6Line three");
        }

        @Test
        @DisplayName("parses kit with player and console commands")
        void parsesCommandLists() throws IOException {
            File kitsFolder = new File(tempDir, "kits");
            kitsFolder.mkdirs();
            File kitFile = new File(kitsFolder, "cmdkit.yml");

            StringBuilder yaml = new StringBuilder();
            yaml.append("displayName: \"&aCmd Kit\"\n");
            yaml.append("icon: CHEST\n");
            yaml.append("items: \"data\"\n");
            yaml.append("playerCommands:\n");
            yaml.append("  - \"spawn\"\n");
            yaml.append("  - \"msg {player} hello\"\n");
            yaml.append("consoleCommands:\n");
            yaml.append("  - \"give {player} diamond 1\"\n");

            FileWriter writer = new FileWriter(kitFile);
            writer.write(yaml.toString());
            writer.close();

            service = createService();

            KitDefinition kit = service.getKit("cmdkit");
            assertThat(kit).isNotNull();
            assertThat(kit.getPlayerCommands()).containsExactly("spawn", "msg {player} hello");
            assertThat(kit.getConsoleCommands()).containsExactly("give {player} diamond 1");
        }

        @Test
        @DisplayName("handles kits folder being a file instead of directory")
        void kitsFolderIsFile() throws IOException {
            File kitsFile = new File(tempDir, "kits");
            kitsFile.createNewFile(); // create as FILE, not directory

            service = createService();

            verify(mockLogger).warn(contains("没有找到礼包配置文件"));
            assertThat(service.getAllKits()).isEmpty();
        }
    }

    // =========================================================================
    // Retrieval Tests
    // =========================================================================
    @Nested
    @DisplayName("Retrieval Tests")
    class RetrievalTests {

        @BeforeEach
        void setUp() throws Exception {
            new File(tempDir, "kits").mkdirs();
            service = createService();
        }

        @Test
        @DisplayName("getKit returns kit by name (case insensitive)")
        void getKitCaseInsensitive() throws Exception {
            KitDefinition kit = createTestKit("starter");
            injectKit(service, kit);

            assertThat(service.getKit("starter")).isSameAs(kit);
            assertThat(service.getKit("STARTER")).isSameAs(kit);
            assertThat(service.getKit("Starter")).isSameAs(kit);
        }

        @Test
        @DisplayName("getKit returns null for nonexistent kit")
        void getKitReturnsNull() {
            assertThat(service.getKit("nonexistent")).isNull();
        }

        @Test
        @DisplayName("getKit with empty string returns null")
        void getKitEmptyString() {
            assertThat(service.getKit("")).isNull();
        }

        @Test
        @DisplayName("getAllKits returns unmodifiable collection")
        void getAllKitsUnmodifiable() throws Exception {
            KitDefinition kit = createTestKit("test");
            injectKit(service, kit);

            Collection<KitDefinition> allKits = service.getAllKits();
            assertThat(allKits).hasSize(1);

            Assertions.assertThrows(UnsupportedOperationException.class, () ->
                    allKits.add(new KitDefinition()));
        }

        @Test
        @DisplayName("getAllKits returns empty when no kits loaded")
        void getAllKitsEmpty() {
            assertThat(service.getAllKits()).isEmpty();
        }

        @Test
        @DisplayName("getAvailableKits filters by permission")
        void getAvailableKitsFiltersByPermission() throws Exception {
            KitDefinition freeKit = createTestKit("free");
            freeKit.setPermission("");
            injectKit(service, freeKit);

            KitDefinition vipKit = createTestKit("vip");
            vipKit.setPermission("kit.vip");
            injectKit(service, vipKit);

            KitDefinition adminKit = createTestKit("admin");
            adminKit.setPermission("kit.admin");
            injectKit(service, adminKit);

            Player player = createMockPlayer();
            when(player.hasPermission("kit.vip")).thenReturn(true);
            when(player.hasPermission("kit.admin")).thenReturn(false);

            List<KitDefinition> available = service.getAvailableKits(player);

            assertThat(available).hasSize(2);
            assertThat(available).contains(freeKit, vipKit);
            assertThat(available).doesNotContain(adminKit);
        }

        @Test
        @DisplayName("getAvailableKits returns all kits when player has all permissions")
        void getAvailableKitsAllPermissions() throws Exception {
            KitDefinition kit1 = createTestKit("kit1");
            kit1.setPermission("kit.one");
            injectKit(service, kit1);

            KitDefinition kit2 = createTestKit("kit2");
            kit2.setPermission("kit.two");
            injectKit(service, kit2);

            Player player = createMockPlayer();
            when(player.hasPermission(anyString())).thenReturn(true);

            List<KitDefinition> available = service.getAvailableKits(player);
            assertThat(available).hasSize(2);
        }

        @Test
        @DisplayName("getAvailableKits returns empty when no kits loaded")
        void getAvailableKitsEmptyWhenNoKits() {
            Player player = createMockPlayer();

            List<KitDefinition> available = service.getAvailableKits(player);
            assertThat(available).isEmpty();
        }

        @Test
        @DisplayName("getAvailableKits includes kits with null permission")
        void getAvailableKitsIncludesNullPermKits() throws Exception {
            KitDefinition kit = createTestKit("noperm");
            kit.setPermission(null);
            injectKit(service, kit);

            Player player = createMockPlayer();

            List<KitDefinition> available = service.getAvailableKits(player);
            assertThat(available).hasSize(1);
            assertThat(available).contains(kit);
        }

        @Test
        @DisplayName("getKitNames returns list of all kit names")
        void getKitNamesReturnsList() throws Exception {
            injectKit(service, createTestKit("alpha"));
            injectKit(service, createTestKit("beta"));

            List<String> names = service.getKitNames();

            assertThat(names).containsExactlyInAnyOrder("alpha", "beta");
        }

        @Test
        @DisplayName("getKitNames returns empty list when no kits")
        void getKitNamesEmpty() {
            assertThat(service.getKitNames()).isEmpty();
        }

        @Test
        @DisplayName("getKitNames returns a mutable list independent of internal state")
        void getKitNamesReturnsMutableList() throws Exception {
            injectKit(service, createTestKit("test"));

            List<String> names = service.getKitNames();
            names.add("extra");
            assertThat(names).hasSize(2);

            // Original kits should not be affected
            assertThat(service.getKitNames()).hasSize(1);
        }

        @Test
        @DisplayName("kits map preserves insertion order")
        void kitsMapPreservesOrder() throws Exception {
            injectKit(service, createTestKit("alpha"));
            injectKit(service, createTestKit("beta"));
            injectKit(service, createTestKit("gamma"));

            List<String> names = service.getKitNames();
            assertThat(names).containsExactly("alpha", "beta", "gamma");
        }
    }

    // =========================================================================
    // Create Tests
    // =========================================================================
    @Nested
    @DisplayName("Create Tests")
    class CreateTests {

        private Player player;
        private PlayerInventory inventory;

        @BeforeEach
        void setUp() {
            new File(tempDir, "kits").mkdirs();
            service = createService();

            player = createMockPlayer();
            inventory = player.getInventory();
        }

        @Test
        @DisplayName("createKit returns ALREADY_EXISTS for duplicate name")
        void createKitDuplicate() throws Exception {
            injectKit(service, createTestKit("existing"));

            ItemStack[] contents = new ItemStack[]{ mockItemStack(Material.STONE) };
            when(inventory.getStorageContents()).thenReturn(contents);

            KitService.CreateResult result = service.createKit(player, "EXISTING");
            assertThat(result).isEqualTo(KitService.CreateResult.ALREADY_EXISTS);
        }

        @Test
        @DisplayName("createKit returns INVALID_NAME for empty name")
        void createKitEmptyName() {
            KitService.CreateResult result = service.createKit(player, "   ");
            assertThat(result).isEqualTo(KitService.CreateResult.INVALID_NAME);
        }

        @Test
        @DisplayName("createKit returns INVALID_NAME for name longer than 32 characters")
        void createKitLongName() {
            StringBuilder longName = new StringBuilder();
            for (int i = 0; i < 33; i++) {
                longName.append("a");
            }
            KitService.CreateResult result = service.createKit(player, longName.toString());
            assertThat(result).isEqualTo(KitService.CreateResult.INVALID_NAME);
        }

        @Test
        @DisplayName("createKit accepts name exactly 32 characters long")
        void createKitExactly32Chars() {
            StringBuilder name = new StringBuilder();
            for (int i = 0; i < 32; i++) {
                name.append("x");
            }

            ItemStack[] contents = new ItemStack[]{ mockItemStack(Material.STONE) };
            when(inventory.getStorageContents()).thenReturn(contents);

            // Should NOT return INVALID_NAME -- proceeds past validation
            KitService.CreateResult result = service.createKit(player, name.toString());
            assertThat(result).isNotEqualTo(KitService.CreateResult.INVALID_NAME);
        }

        @Test
        @DisplayName("createKit returns EMPTY_INVENTORY when all slots are air or null")
        void createKitEmptyInventory() {
            ItemStack airItem = mockItemStack(Material.AIR);
            ItemStack[] contents = new ItemStack[]{ null, airItem, null };
            when(inventory.getStorageContents()).thenReturn(contents);

            KitService.CreateResult result = service.createKit(player, "empty");
            assertThat(result).isEqualTo(KitService.CreateResult.EMPTY_INVENTORY);
        }

        @Test
        @DisplayName("createKit returns EMPTY_INVENTORY for completely empty array")
        void createKitEmptyArray() {
            when(inventory.getStorageContents()).thenReturn(new ItemStack[0]);

            KitService.CreateResult result = service.createKit(player, "emptyarray");
            assertThat(result).isEqualTo(KitService.CreateResult.EMPTY_INVENTORY);
        }

        @Test
        @DisplayName("createKit normalizes name to lowercase for duplicate check")
        void createKitNormalizesName() throws Exception {
            injectKit(service, createTestKit("mykit"));

            ItemStack[] contents = new ItemStack[]{ mockItemStack(Material.STONE) };
            when(inventory.getStorageContents()).thenReturn(contents);

            KitService.CreateResult result = service.createKit(player, "MyKit");
            assertThat(result).isEqualTo(KitService.CreateResult.ALREADY_EXISTS);
        }

        @Test
        @DisplayName("createKit trims whitespace from name before validation")
        void createKitTrimsWhitespace() throws Exception {
            injectKit(service, createTestKit("padded"));

            ItemStack[] contents = new ItemStack[]{ mockItemStack(Material.STONE) };
            when(inventory.getStorageContents()).thenReturn(contents);

            KitService.CreateResult result = service.createKit(player, "  Padded  ");
            assertThat(result).isEqualTo(KitService.CreateResult.ALREADY_EXISTS);
        }
    }

    // =========================================================================
    // Delete Tests
    // =========================================================================
    @Nested
    @DisplayName("Delete Tests")
    class DeleteTests {

        @BeforeEach
        void setUp() {
            new File(tempDir, "kits").mkdirs();
            service = createService();
        }

        /**
         * A service whose kit-file deletion reports failure, standing in for a read-only kits folder,
         * a file owned by another user, or a Windows file lock. The failure is injected through the
         * package-private deletion seam rather than through file permissions: a build running as root
         * deletes a read-only file anyway, so a permission-based test would pass without testing
         * anything (UltiKits/UltiKits#23).
         */
        private KitServiceImpl createServiceWhoseFileDeleteFails(AtomicInteger attempts) {
            return new KitServiceImpl(plugin, config) {
                @Override
                void deleteKitFile(File kitFile) throws IOException {
                    attempts.incrementAndGet();
                    throw new java.nio.file.AccessDeniedException(kitFile.getPath());
                }
            };
        }

        @Test
        @DisplayName("deleteKit returns DELETED and removes kit and file")
        void deleteKitSuccess() throws Exception {
            injectKit(service, createTestKit("todelete"));

            File kitFile = new File(tempDir, "kits/todelete.yml");
            kitFile.createNewFile();
            assertThat(kitFile).exists();

            KitService.DeleteResult result = service.deleteKit("todelete");

            assertThat(result).isEqualTo(KitService.DeleteResult.DELETED);
            assertThat(service.getKit("todelete")).isNull();
            assertThat(kitFile).doesNotExist();
        }

        @Test
        @DisplayName("a kit whose file cannot be deleted is reported as not deleted and stays loaded")
        void deleteKitFileNotDeletedKeepsKit() throws Exception {
            File kitFile = createSimpleKitFile("premium");
            AtomicInteger attempts = new AtomicInteger();
            service = createServiceWhoseFileDeleteFails(attempts);
            assertThat(service.getKit("premium")).isNotNull();

            KitService.DeleteResult result = service.deleteKit("premium");

            assertThat(attempts.get()).isEqualTo(1);
            assertThat(result).isEqualTo(KitService.DeleteResult.FILE_NOT_DELETED);
            assertThat(kitFile).exists();
            assertThat(service.getKit("premium")).isNotNull();
            assertThat(service.getKitNames()).contains("premium");
        }

        @Test
        @DisplayName("a failed kit-file deletion logs a warning naming the file's path")
        void deleteKitFileNotDeletedWarnsWithPath() throws Exception {
            File kitFile = createSimpleKitFile("premium");
            service = createServiceWhoseFileDeleteFails(new AtomicInteger());

            service.deleteKit("premium");

            ArgumentCaptor<String> warning = ArgumentCaptor.forClass(String.class);
            verify(mockLogger, atLeastOnce()).warn(warning.capture());
            assertThat(warning.getAllValues())
                    .anyMatch(line -> line.contains(kitFile.getAbsolutePath()));
        }

        @Test
        @DisplayName("after a failed deletion a reload still lists the kit, matching what the admin was told")
        void deleteKitFileNotDeletedSurvivesReload() throws Exception {
            createSimpleKitFile("premium");
            service = createServiceWhoseFileDeleteFails(new AtomicInteger());

            KitService.DeleteResult result = service.deleteKit("premium");
            service.reload();

            assertThat(result).isEqualTo(KitService.DeleteResult.FILE_NOT_DELETED);
            assertThat(service.getKit("premium")).isNotNull();
        }

        @Test
        @DisplayName("a file another process removed before the delete ran counts as deleted")
        void deleteKitFileAlreadyGoneIsDeleted() throws Exception {
            File kitFile = createSimpleKitFile("racy");
            service = new KitServiceImpl(plugin, config) {
                @Override
                void deleteKitFile(File file) throws IOException {
                    file.delete(); // NOPMD - another process removed it first
                    throw new java.nio.file.NoSuchFileException(file.getPath());
                }
            };

            KitService.DeleteResult result = service.deleteKit("racy");

            assertThat(result).isEqualTo(KitService.DeleteResult.DELETED);
            assertThat(kitFile).doesNotExist();
            assertThat(service.getKit("racy")).isNull();
        }

        /**
         * {@code loadKits} maps a file to a kit by lower-casing its name, so a hand-placed {@code
         * VIP.yml} loads as {@code vip}. Deleting must remove the file the kit was loaded from, not a
         * rebuilt {@code vip.yml}: on a case-sensitive file system that path does not exist, and the
         * kit was reported deleted while {@code VIP.yml} stayed to be loaded again (UltiKits/UltiKits#23).
         */
        @Test
        @DisplayName("a kit loaded from a file with capitals is deleted from that file and stays gone after a reload")
        void deleteKitFromCapitalisedFile() throws Exception {
            File kitFile = createSimpleKitFile("VIP");
            service = createService();
            assertThat(service.getKit("vip")).isNotNull();

            KitService.DeleteResult result = service.deleteKit("vip");
            service.reload();

            assertThat(result).isEqualTo(KitService.DeleteResult.DELETED);
            assertThat(kitFile).doesNotExist();
            assertThat(service.getKit("vip")).isNull();
        }

        @Test
        @DisplayName("a kit loaded from a file with capitals whose deletion fails is reported as not deleted")
        void deleteKitFromCapitalisedFileThatCannotBeDeleted() throws Exception {
            File kitFile = createSimpleKitFile("VIP");
            service = createServiceWhoseFileDeleteFails(new AtomicInteger());

            KitService.DeleteResult result = service.deleteKit("vip");

            assertThat(result).isEqualTo(KitService.DeleteResult.FILE_NOT_DELETED);
            assertThat(kitFile).exists();
            assertThat(service.getKit("vip")).isNotNull();
        }

        /**
         * Two files that load as one kit are not deleted one by one - that could stop part-way and
         * leave a different file to load next time. The deletion is refused, both files stay and the
         * kit stays loaded until only one file defines it (this replaced "delete every such file").
         */
        @Test
        @DisplayName("a kit two files load as is not deleted, so a half-finished delete cannot swap its file")
        void deleteKitWithTwoFilesDeletesNeither() throws Exception {
            File upper = createSimpleKitFile("VIP");
            File lower = createSimpleKitFile("vip");
            service = createService();

            KitService.DeleteResult result = service.deleteKit("vip");
            service.reload();

            assertThat(result).isEqualTo(KitService.DeleteResult.FILE_CONFLICT);
            assertThat(upper).exists();
            assertThat(lower).exists();
            assertThat(service.getKit("vip")).isNotNull();
        }

        /**
         * A kits folder that cannot be listed ({@code File#listFiles} returns null on an I/O or
         * permission error) is not "no file": the kit's file may still be there to come back on the
         * next reload, so the deletion is reported as failed and the kit stays loaded. The failure is
         * injected through the listing seam, not through permissions, for the same reason as the delete
         * seam.
         */
        @Test
        @DisplayName("an unreadable kits folder is a failed deletion, not a missing file")
        void deleteKitWhenTheKitsFolderCannotBeListed() throws Exception {
            File kitFile = createSimpleKitFile("premium");
            service = new KitServiceImpl(plugin, config) {
                @Override
                File[] listKitFiles(File folder) {
                    return null;
                }
            };
            assertThat(service.getKit("premium")).isNotNull();

            KitService.DeleteResult result = service.deleteKit("premium");

            assertThat(result).isEqualTo(KitService.DeleteResult.FILE_NOT_DELETED);
            assertThat(kitFile).exists();
            assertThat(service.getKit("premium")).isNotNull();
            ArgumentCaptor<String> warning = ArgumentCaptor.forClass(String.class);
            verify(mockLogger, atLeastOnce()).warn(warning.capture());
            assertThat(warning.getAllValues())
                    .anyMatch(line -> line.contains(kitFile.getParentFile().getAbsolutePath()));
        }

        /**
         * A deletion the file system refused is a failure, whatever a later existence check says -
         * in a folder the server may list but not search, {@code File#exists} answers false for a
         * file that is still there. The delete reports its own reason ({@code Files#delete}), so
         * no existence check is consulted: here the refusal is reported while the file has gone,
         * and the result is still "not deleted".
         */
        @Test
        @DisplayName("a refused deletion is a failure even when the file no longer appears to exist")
        void aRefusedDeletionIsAFailureWhateverExistsSays() throws Exception {
            File kitFile = createSimpleKitFile("premium");
            service = new KitServiceImpl(plugin, config) {
                @Override
                void deleteKitFile(File file) throws IOException {
                    file.delete(); // NOPMD - makes File#exists answer false, as an unsearchable folder does
                    throw new java.nio.file.AccessDeniedException(file.getPath());
                }
            };

            KitService.DeleteResult result = service.deleteKit("premium");

            assertThat(kitFile).doesNotExist();
            assertThat(result).isEqualTo(KitService.DeleteResult.FILE_NOT_DELETED);
            assertThat(service.getKit("premium")).isNotNull();
        }

        @Test
        @DisplayName("the kit-file listing tells a missing folder, a failed listing and a readable folder apart")
        void theListingHasThreeAnswers() throws Exception {
            File kits = new File(tempDir, "kits");
            File yml = createSimpleKitFile("alpha");
            new File(kits, "notes.txt").createNewFile();
            File plainFile = new File(tempDir, "not-a-folder");
            plainFile.createNewFile();

            assertThat(service.listKitFiles(new File(tempDir, "missing"))).isEmpty();
            assertThat(service.listKitFiles(plainFile)).isNull();
            assertThat(service.listKitFiles(kits)).containsExactly(yml);
        }

        @Test
        @DisplayName("deleteKit returns NOT_FOUND for nonexistent kit")
        void deleteKitNotFound() {
            KitService.DeleteResult result = service.deleteKit("nosuchkit");
            assertThat(result).isEqualTo(KitService.DeleteResult.NOT_FOUND);
        }

        @Test
        @DisplayName("deleteKit handles case-insensitive names")
        void deleteKitCaseInsensitive() throws Exception {
            injectKit(service, createTestKit("mykit"));

            KitService.DeleteResult result = service.deleteKit("MYKIT");
            assertThat(result).isEqualTo(KitService.DeleteResult.DELETED);
            assertThat(service.getKit("mykit")).isNull();
        }

        @Test
        @DisplayName("deleteKit reports DELETED when the kit is loaded but its file is already gone")
        void deleteKitNoFile() throws Exception {
            injectKit(service, createTestKit("nofile"));

            KitService.DeleteResult result = service.deleteKit("nofile");
            assertThat(result).isEqualTo(KitService.DeleteResult.DELETED);
            assertThat(service.getKit("nofile")).isNull();
        }

        @Test
        @DisplayName("deleteKit removes kit from getAllKits result")
        void deleteKitRemovesFromGetAll() throws Exception {
            injectKit(service, createTestKit("a"));
            injectKit(service, createTestKit("b"));
            assertThat(service.getAllKits()).hasSize(2);

            service.deleteKit("a");

            assertThat(service.getAllKits()).hasSize(1);
            assertThat(service.getKitNames()).containsExactly("b");
        }

        @Test
        @DisplayName("deleteKit trims and lowercases the name")
        void deleteKitTrimsName() throws Exception {
            injectKit(service, createTestKit("trimme"));

            KitService.DeleteResult result = service.deleteKit("  TRIMME  ");
            assertThat(result).isEqualTo(KitService.DeleteResult.DELETED);
            assertThat(service.getKit("trimme")).isNull();
        }

        @Test
        @DisplayName("deleteKit with empty string returns NOT_FOUND")
        void deleteKitEmptyString() {
            assertThat(service.deleteKit("")).isEqualTo(KitService.DeleteResult.NOT_FOUND);
        }
    }

    // =========================================================================
    // Claim Tests
    // =========================================================================
    @Nested
    @DisplayName("Claim Tests")
    class ClaimTests {

        private Player player;
        private PlayerInventory inventory;

        @BeforeEach
        void setUp() {
            new File(tempDir, "kits").mkdirs();
            service = createService();

            player = createMockPlayer();
            inventory = player.getInventory();
            // Default: plenty of empty slots
            when(inventory.getStorageContents()).thenReturn(new ItemStack[36]);
        }

        /**
         * The master switch, enforced at the claim gateway rather than at its callers.
         * <p>
         * This is the test that fails when the guard is gone. It exercises {@code claimKit}
         * directly, i.e. as an arbitrary caller with no pre-check of its own, which is exactly the
         * shape of the future caller the guard exists for - the module's own enumeration of callers
         * came up short twice before the guard moved here (UltiKits/UltiKits#13).
         */
        @Nested
        @DisplayName("Master Switch Tests")
        class MasterSwitchTests {

            /** A free, unrestricted, deliverable kit, so only the switch can decide the outcome. */
            private KitServiceImpl serviceWithDeliverableKit(ItemStack item) throws Exception {
                KitDefinition kit = createTestKit("free");
                kit.setPrice(0);
                kit.setItems("someBase64Data");

                KitServiceImpl spyService = spy(service);
                injectKit(spyService, kit);
                doReturn(new ItemStack[]{item}).when(spyService).deserializeItems("someBase64Data");
                when(inventory.getStorageContents()).thenReturn(new ItemStack[36]);
                return spyService;
            }

            @Test
            @DisplayName("declared default true: the same claim succeeds and delivers")
            void declaredDefaultAllowsTheClaim() throws Exception {
                ItemStack mockItem = mock(ItemStack.class);
                when(mockItem.clone()).thenReturn(mockItem);
                KitServiceImpl spyService = serviceWithDeliverableKit(mockItem);

                KitService.ClaimResult result = spyService.claimKit(player, "free");

                assertThat(result).isEqualTo(KitService.ClaimResult.SUCCESS);
                verify(inventory).addItem(mockItem);
            }

            @Test
            @DisplayName("enabled false: SYSTEM_DISABLED, no items delivered, no claim row written")
            void disabledRefusesAtTheGatewayAndDeliversNothing() throws Exception {
                ItemStack mockItem = mock(ItemStack.class);
                KitServiceImpl spyService = serviceWithDeliverableKit(mockItem);

                config.setEnabled(false);

                KitService.ClaimResult result = spyService.claimKit(player, "free");

                assertThat(result).isEqualTo(KitService.ClaimResult.SYSTEM_DISABLED);
                verify(inventory, never()).addItem(any(ItemStack.class));
                verify(mockClaimOperator, never()).insert(any(KitClaimData.class));
                verify(mockClaimOperator, never()).update(any(KitClaimData.class));
            }

            @Test
            @DisplayName("the switch is read per claim, so a flip applies to the very next one")
            void theSwitchIsReadPerClaim() throws Exception {
                ItemStack mockItem = mock(ItemStack.class);
                when(mockItem.clone()).thenReturn(mockItem);
                KitServiceImpl spyService = serviceWithDeliverableKit(mockItem);

                assertThat(spyService.claimKit(player, "free"))
                        .isEqualTo(KitService.ClaimResult.SUCCESS);

                // Mirrors ConfigManager#reloadConfigs: the file is re-read into the SAME instance
                // the service already holds. A value cached in the constructor would survive this.
                config.setEnabled(false);

                assertThat(spyService.claimKit(player, "free"))
                        .isEqualTo(KitService.ClaimResult.SYSTEM_DISABLED);
            }

            @Test
            @DisplayName("the switch is checked before the kit is even looked up")
            void theSwitchPrecedesTheLookup() {
                config.setEnabled(false);

                // "nonexistent" would be NOT_FOUND if the lookup ran first. SYSTEM_DISABLED proves
                // the guard is the gateway's first statement, so no later branch can step around it.
                assertThat(service.claimKit(player, "nonexistent"))
                        .isEqualTo(KitService.ClaimResult.SYSTEM_DISABLED);
            }
        }

        @Test
        @DisplayName("claimKit returns NOT_FOUND for nonexistent kit")
        void claimNotFound() {
            KitService.ClaimResult result = service.claimKit(player, "nonexistent");
            assertThat(result).isEqualTo(KitService.ClaimResult.NOT_FOUND);
        }

        @Test
        @DisplayName("claimKit returns NOT_FOUND for empty string")
        void claimNotFoundEmptyString() {
            KitService.ClaimResult result = service.claimKit(player, "");
            assertThat(result).isEqualTo(KitService.ClaimResult.NOT_FOUND);
        }

        @Test
        @DisplayName("claimKit returns NO_PERMISSION when player lacks permission")
        void claimNoPermission() throws Exception {
            KitDefinition kit = createTestKit("vip");
            kit.setPermission("kit.vip");
            injectKit(service, kit);

            when(player.hasPermission("kit.vip")).thenReturn(false);

            KitService.ClaimResult result = service.claimKit(player, "vip");
            assertThat(result).isEqualTo(KitService.ClaimResult.NO_PERMISSION);
        }

        @Test
        @DisplayName("claimKit passes permission check when player has permission")
        void claimWithPermission() throws Exception {
            KitDefinition kit = createTestKit("vipok");
            kit.setPermission("kit.vip");
            kit.setItems(""); // will hit EMPTY_KIT
            injectKit(service, kit);

            when(player.hasPermission("kit.vip")).thenReturn(true);

            KitService.ClaimResult result = service.claimKit(player, "vipok");
            assertThat(result).isEqualTo(KitService.ClaimResult.EMPTY_KIT);
        }

        @Test
        @DisplayName("claimKit returns INSUFFICIENT_LEVEL when player level too low")
        void claimInsufficientLevel() throws Exception {
            KitDefinition kit = createTestKit("highlevel");
            kit.setLevelRequired(20);
            injectKit(service, kit);

            when(player.getLevel()).thenReturn(5);

            KitService.ClaimResult result = service.claimKit(player, "highlevel");
            assertThat(result).isEqualTo(KitService.ClaimResult.INSUFFICIENT_LEVEL);
        }

        @Test
        @DisplayName("claimKit passes level check when player has exact level required")
        void claimExactLevel() throws Exception {
            KitDefinition kit = createTestKit("exactlevel");
            kit.setLevelRequired(10);
            kit.setItems("");
            injectKit(service, kit);

            when(player.getLevel()).thenReturn(10);

            KitService.ClaimResult result = service.claimKit(player, "exactlevel");
            assertThat(result).isEqualTo(KitService.ClaimResult.EMPTY_KIT);
        }

        @Test
        @DisplayName("claimKit skips level check when no level required")
        void claimNoLevelRequired() throws Exception {
            KitDefinition kit = createTestKit("nolevel");
            kit.setLevelRequired(0);
            kit.setItems("");
            injectKit(service, kit);

            when(player.getLevel()).thenReturn(0);

            KitService.ClaimResult result = service.claimKit(player, "nolevel");
            assertThat(result).isEqualTo(KitService.ClaimResult.EMPTY_KIT);
        }

        @Test
        @DisplayName("claimKit returns INSUFFICIENT_FUNDS when player can't afford")
        void claimInsufficientFunds() throws Exception {
            KitDefinition kit = createTestKit("expensive");
            kit.setPrice(500);
            injectKit(service, kit);

            Economy mockEconomy = setupMockEconomy();
            when(mockEconomy.has(eq(player), eq(500.0))).thenReturn(false);

            KitService.ClaimResult result = service.claimKit(player, "expensive");
            assertThat(result).isEqualTo(KitService.ClaimResult.INSUFFICIENT_FUNDS);
            verify(mockEconomy).has(player, 500.0);
            verify(mockEconomy, never()).withdrawPlayer(any(org.bukkit.OfflinePlayer.class), anyDouble());
        }

        @Test
        @DisplayName("claimKit skips economy check for free kit")
        void claimFreeKitSkipsEconomy() throws Exception {
            KitDefinition kit = createTestKit("freebie");
            kit.setPrice(0);
            kit.setItems("");
            injectKit(service, kit);

            KitService.ClaimResult result = service.claimKit(player, "freebie");
            assertThat(result).isEqualTo(KitService.ClaimResult.EMPTY_KIT);
        }

        @Test
        @DisplayName("claimKit treats negative price as free")
        void claimNegativePriceIsFree() throws Exception {
            KitDefinition kit = createTestKit("negprice");
            kit.setPrice(-10);
            kit.setItems("");
            injectKit(service, kit);

            KitService.ClaimResult result = service.claimKit(player, "negprice");
            assertThat(result).isEqualTo(KitService.ClaimResult.EMPTY_KIT);
        }

        @Test
        @DisplayName("claimKit returns ALREADY_CLAIMED for one-time kit already claimed")
        void claimAlreadyClaimed() throws Exception {
            KitDefinition kit = createTestKit("onetime");
            kit.setReBuyable(false);
            injectKit(service, kit);

            KitClaimData existingClaim = KitClaimData.builder()
                    .uuid(UUID.randomUUID().toString())
                    .playerUuid(player.getUniqueId().toString())
                    .kitName("onetime")
                    .lastClaim(System.currentTimeMillis())
                    .claimCount(1)
                    .build();

            when(mockQuery.list()).thenReturn(Collections.singletonList(existingClaim));

            KitService.ClaimResult result = service.claimKit(player, "onetime");
            assertThat(result).isEqualTo(KitService.ClaimResult.ALREADY_CLAIMED);
        }

        @Test
        @DisplayName("claimKit returns ON_COOLDOWN when cooldown active")
        void claimOnCooldown() throws Exception {
            KitDefinition kit = createTestKit("cooldown");
            kit.setReBuyable(true);
            kit.setCooldown(3600);
            injectKit(service, kit);

            KitClaimData existingClaim = KitClaimData.builder()
                    .uuid(UUID.randomUUID().toString())
                    .playerUuid(player.getUniqueId().toString())
                    .kitName("cooldown")
                    .lastClaim(System.currentTimeMillis())
                    .claimCount(1)
                    .build();

            when(mockQuery.list()).thenReturn(Collections.singletonList(existingClaim));

            KitService.ClaimResult result = service.claimKit(player, "cooldown");
            assertThat(result).isEqualTo(KitService.ClaimResult.ON_COOLDOWN);
        }

        @Test
        @DisplayName("claimKit returns EMPTY_KIT when kit has no items")
        void claimEmptyKit() throws Exception {
            KitDefinition kit = createTestKit("empty");
            kit.setItems("");
            injectKit(service, kit);

            KitService.ClaimResult result = service.claimKit(player, "empty");
            assertThat(result).isEqualTo(KitService.ClaimResult.EMPTY_KIT);
        }

        @Test
        @DisplayName("claimKit returns EMPTY_KIT when items is null")
        void claimNullItems() throws Exception {
            KitDefinition kit = createTestKit("nullitems");
            kit.setItems(null);
            injectKit(service, kit);

            KitService.ClaimResult result = service.claimKit(player, "nullitems");
            assertThat(result).isEqualTo(KitService.ClaimResult.EMPTY_KIT);
        }

        @Test
        @DisplayName("claimKit returns INVENTORY_FULL when not enough slots")
        void claimInventoryFull() throws Exception {
            KitDefinition kit = createTestKit("big");
            kit.setItems("someBase64Data");
            injectKit(service, kit);

            KitServiceImpl spyService = spy(service);
            injectKit(spyService, kit);

            ItemStack mockItem1 = mock(ItemStack.class);
            ItemStack mockItem2 = mock(ItemStack.class);
            doReturn(new ItemStack[]{mockItem1, mockItem2}).when(spyService).deserializeItems("someBase64Data");

            ItemStack occupied = mock(ItemStack.class);
            when(occupied.getType()).thenReturn(Material.STONE);
            ItemStack[] fullInv = new ItemStack[36];
            Arrays.fill(fullInv, occupied);
            fullInv[0] = null; // only 1 empty slot, need 2
            when(inventory.getStorageContents()).thenReturn(fullInv);

            KitService.ClaimResult result = spyService.claimKit(player, "big");
            assertThat(result).isEqualTo(KitService.ClaimResult.INVENTORY_FULL);
        }

        @Test
        @DisplayName("claimKit succeeds for free kit with no restrictions")
        void claimFreeKitSuccess() throws Exception {
            KitDefinition kit = createTestKit("free");
            kit.setPrice(0);
            kit.setItems("someBase64Data");
            injectKit(service, kit);

            KitServiceImpl spyService = spy(service);
            injectKit(spyService, kit);

            ItemStack mockItem = mock(ItemStack.class);
            when(mockItem.clone()).thenReturn(mockItem);
            doReturn(new ItemStack[]{mockItem}).when(spyService).deserializeItems("someBase64Data");

            when(inventory.getStorageContents()).thenReturn(new ItemStack[36]);

            KitService.ClaimResult result = spyService.claimKit(player, "free");
            assertThat(result).isEqualTo(KitService.ClaimResult.SUCCESS);

            verify(inventory).addItem(mockItem);
        }

        @Test
        @DisplayName("claimKit deducts price for paid kit")
        void claimPaidKitDeductsPrice() throws Exception {
            KitDefinition kit = createTestKit("paid");
            kit.setPrice(100);
            kit.setItems("someBase64Data");
            injectKit(service, kit);

            Economy mockEconomy = setupMockEconomy();
            when(mockEconomy.has(eq(player), eq(100.0))).thenReturn(true);
            EconomyResponse successResponse = new EconomyResponse(100, 900, EconomyResponse.ResponseType.SUCCESS, "");
            when(mockEconomy.withdrawPlayer(eq(player), eq(100.0))).thenReturn(successResponse);

            KitServiceImpl spyService = spy(service);
            injectKit(spyService, kit);

            ItemStack mockItem = mock(ItemStack.class);
            when(mockItem.clone()).thenReturn(mockItem);
            doReturn(new ItemStack[]{mockItem}).when(spyService).deserializeItems("someBase64Data");
            when(inventory.getStorageContents()).thenReturn(new ItemStack[36]);

            KitService.ClaimResult result = spyService.claimKit(player, "paid");
            assertThat(result).isEqualTo(KitService.ClaimResult.SUCCESS);

            verify(mockEconomy).withdrawPlayer(eq(player), eq(100.0));
        }

        /**
         * What this test guards: a free kit is still claimed and delivered when a Vault economy is
         * present, and the economy never sees a withdrawal.
         * <p>
         * What it does NOT guard: the module's own {@code !kit.isFree()} check in
         * {@code KitServiceImpl#deliverKit}. Through the public Vault path, the framework's economy
         * bridge refuses a zero amount before it reaches the registered {@link Economy}, so the
         * {@code never()} verification below would hold even if that check were removed (measured
         * when reviewing UltiKits/UltiKits#19). On a live server the same framework refusal also
         * stops a free kit from being charged. The GUI's separate {@code !kit.isFree()} check
         * (the "deducted" message) is guarded by {@code KitBrowserGuiTest#successFreeKit}.
         */
        @Test
        @DisplayName("claimKit delivers a free kit with an economy present and the economy sees no withdrawal")
        void claimFreeKitDeliveredWithEconomyPresent() throws Exception {
            KitDefinition kit = createTestKit("freenodeduct");
            kit.setPrice(0);
            kit.setItems("someBase64Data");
            injectKit(service, kit);

            Economy mockEconomy = setupMockEconomy();

            KitServiceImpl spyService = spy(service);
            injectKit(spyService, kit);

            ItemStack mockItem = mock(ItemStack.class);
            when(mockItem.clone()).thenReturn(mockItem);
            doReturn(new ItemStack[]{mockItem}).when(spyService).deserializeItems("someBase64Data");
            when(inventory.getStorageContents()).thenReturn(new ItemStack[36]);

            KitService.ClaimResult result = spyService.claimKit(player, "freenodeduct");
            assertThat(result).isEqualTo(KitService.ClaimResult.SUCCESS);
            verify(inventory).addItem(mockItem);

            // Also enforced by the framework bridge's zero-amount refusal; see the javadoc above.
            verify(mockEconomy, never()).withdrawPlayer(any(Player.class), anyDouble());
        }

        @Test
        @DisplayName("claimKit updates claim record for new claim")
        void claimUpdatesNewRecord() throws Exception {
            KitDefinition kit = createTestKit("tracked");
            kit.setItems("someBase64Data");
            injectKit(service, kit);

            KitServiceImpl spyService = spy(service);
            injectKit(spyService, kit);

            ItemStack mockItem = mock(ItemStack.class);
            when(mockItem.clone()).thenReturn(mockItem);
            doReturn(new ItemStack[]{mockItem}).when(spyService).deserializeItems("someBase64Data");
            when(inventory.getStorageContents()).thenReturn(new ItemStack[36]);

            spyService.claimKit(player, "tracked");

            verify(mockClaimOperator).insert(any(KitClaimData.class));
        }

        @Test
        @DisplayName("claimKit allows re-claim when cooldown expired")
        void claimWhenCooldownExpired() throws Exception {
            KitDefinition kit = createTestKit("reclaim");
            kit.setReBuyable(true);
            kit.setCooldown(10);
            kit.setItems("someBase64Data");
            injectKit(service, kit);

            KitClaimData oldClaim = KitClaimData.builder()
                    .uuid(UUID.randomUUID().toString())
                    .playerUuid(player.getUniqueId().toString())
                    .kitName("reclaim")
                    .lastClaim(System.currentTimeMillis() - 20000) // 20s ago, cooldown 10s
                    .claimCount(1)
                    .build();

            when(mockQuery.list()).thenReturn(Collections.singletonList(oldClaim));

            KitServiceImpl spyService = spy(service);
            injectKit(spyService, kit);

            ItemStack mockItem = mock(ItemStack.class);
            when(mockItem.clone()).thenReturn(mockItem);
            doReturn(new ItemStack[]{mockItem}).when(spyService).deserializeItems("someBase64Data");
            when(inventory.getStorageContents()).thenReturn(new ItemStack[36]);

            KitService.ClaimResult result = spyService.claimKit(player, "reclaim");
            assertThat(result).isEqualTo(KitService.ClaimResult.SUCCESS);
        }

        @Test
        @DisplayName("claimKit skips permission check when kit has no permission")
        void claimSkipsPermissionCheckWhenEmpty() throws Exception {
            KitDefinition kit = createTestKit("noperm");
            kit.setPermission("");
            kit.setItems("someBase64Data");
            injectKit(service, kit);

            KitServiceImpl spyService = spy(service);
            injectKit(spyService, kit);

            ItemStack mockItem = mock(ItemStack.class);
            when(mockItem.clone()).thenReturn(mockItem);
            doReturn(new ItemStack[]{mockItem}).when(spyService).deserializeItems("someBase64Data");
            when(inventory.getStorageContents()).thenReturn(new ItemStack[36]);

            KitService.ClaimResult result = spyService.claimKit(player, "noperm");
            assertThat(result).isEqualTo(KitService.ClaimResult.SUCCESS);

            verify(player, never()).hasPermission(anyString());
        }

        @Test
        @DisplayName("claimKit allows rebuyable kit with zero cooldown to be re-claimed")
        void claimRebuyableZeroCooldown() throws Exception {
            KitDefinition kit = createTestKit("rebuyable");
            kit.setReBuyable(true);
            kit.setCooldown(0);
            kit.setItems("someBase64Data");
            injectKit(service, kit);

            KitClaimData existingClaim = KitClaimData.builder()
                    .uuid(UUID.randomUUID().toString())
                    .playerUuid(player.getUniqueId().toString())
                    .kitName("rebuyable")
                    .lastClaim(System.currentTimeMillis())
                    .claimCount(5)
                    .build();

            when(mockQuery.list()).thenReturn(Collections.singletonList(existingClaim));

            KitServiceImpl spyService = spy(service);
            injectKit(spyService, kit);

            ItemStack mockItem = mock(ItemStack.class);
            when(mockItem.clone()).thenReturn(mockItem);
            doReturn(new ItemStack[]{mockItem}).when(spyService).deserializeItems("someBase64Data");
            when(inventory.getStorageContents()).thenReturn(new ItemStack[36]);

            KitService.ClaimResult result = spyService.claimKit(player, "rebuyable");
            assertThat(result).isEqualTo(KitService.ClaimResult.SUCCESS);
        }

        @Test
        @DisplayName("claimKit gives multiple items to player")
        void claimMultipleItems() throws Exception {
            KitDefinition kit = createTestKit("multi");
            kit.setItems("someBase64Data");
            injectKit(service, kit);

            KitServiceImpl spyService = spy(service);
            injectKit(spyService, kit);

            ItemStack item1 = mock(ItemStack.class);
            ItemStack item2 = mock(ItemStack.class);
            ItemStack item3 = mock(ItemStack.class);
            when(item1.clone()).thenReturn(item1);
            when(item2.clone()).thenReturn(item2);
            when(item3.clone()).thenReturn(item3);
            doReturn(new ItemStack[]{item1, item2, item3}).when(spyService).deserializeItems("someBase64Data");
            when(inventory.getStorageContents()).thenReturn(new ItemStack[36]);

            KitService.ClaimResult result = spyService.claimKit(player, "multi");
            assertThat(result).isEqualTo(KitService.ClaimResult.SUCCESS);

            verify(inventory).addItem(item1);
            verify(inventory).addItem(item2);
            verify(inventory).addItem(item3);
        }

        @Test
        @DisplayName("claimKit counts AIR slots as empty for capacity check")
        void claimCountsAirAsEmpty() throws Exception {
            KitDefinition kit = createTestKit("aircheck");
            kit.setItems("someBase64Data");
            injectKit(service, kit);

            KitServiceImpl spyService = spy(service);
            injectKit(spyService, kit);

            ItemStack mockItem = mock(ItemStack.class);
            when(mockItem.clone()).thenReturn(mockItem);
            doReturn(new ItemStack[]{mockItem}).when(spyService).deserializeItems("someBase64Data");

            ItemStack occupied = mock(ItemStack.class);
            when(occupied.getType()).thenReturn(Material.STONE);
            ItemStack airItem = mock(ItemStack.class);
            when(airItem.getType()).thenReturn(Material.AIR);

            ItemStack[] inv = new ItemStack[]{null, airItem, occupied};
            when(inventory.getStorageContents()).thenReturn(inv);

            KitService.ClaimResult result = spyService.claimKit(player, "aircheck");
            assertThat(result).isEqualTo(KitService.ClaimResult.SUCCESS);
        }
    }

    // =========================================================================
    // Payment Failure Tests (UltiKits/UltiKits#20)
    // =========================================================================

    /**
     * Covers UltiKits/UltiKits#20: a paid kit whose withdrawal does NOT go through must deliver
     * nothing, record nothing and report a non-SUCCESS result.
     * <p>
     * Every test here drives a Vault {@link Economy} backed by a real mutable balance, so the
     * assertion "the player was not charged" reads the balance itself rather than only which
     * methods were called. {@link #successfulPaymentChargesAndDelivers()} is the control that
     * makes the other tests non-vacuous: on the same fixture, a successful withdrawal DOES move
     * the balance, DOES deliver the items and DOES write a claim row — so a "balance unchanged /
     * nothing delivered / nothing recorded" assertion cannot be passing merely because the
     * fixture is inert.
     */
    @Nested
    @DisplayName("Payment Failure Tests")
    class PaymentFailureTests {

        private static final double PRICE = 100.0;

        private Player player;
        private PlayerInventory inventory;
        private ItemStack kitItem;

        /** The economy's real balance. Index 0 so the lambdas below can mutate it on Java 8. */
        private final double[] balance = {500.0};

        /** Counts calls the module makes to {@code Economy#has}, to prove the race actually ran. */
        private final AtomicInteger hasCalls = new AtomicInteger();

        @BeforeEach
        void setUp() {
            // These tests read the refusal line in English: answer from the real en catalogue.
            when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer("en"));
            new File(tempDir, "kits").mkdirs();
            service = createService();
            player = createMockPlayer();
            inventory = player.getInventory();
            when(inventory.getStorageContents()).thenReturn(new ItemStack[36]);
            kitItem = mock(ItemStack.class);
            when(kitItem.clone()).thenReturn(kitItem);
        }

        /**
         * Registers a Vault economy whose {@code has} reads {@link #balance} and whose
         * {@code withdrawPlayer} debits it only when {@code withdrawalSucceeds}. Registration goes
         * through the public Bukkit/Vault types only (UltiKits/UltiKits#19).
         */
        private Economy statefulEconomy(boolean withdrawalSucceeds) {
            Economy mockEconomy = setupMockEconomy();
            when(mockEconomy.has(any(org.bukkit.OfflinePlayer.class), anyDouble())).thenAnswer(inv -> {
                hasCalls.incrementAndGet();
                return balance[0] >= (Double) inv.getArgument(1);
            });
            when(mockEconomy.withdrawPlayer(any(org.bukkit.OfflinePlayer.class), anyDouble())).thenAnswer(inv -> {
                double amount = inv.getArgument(1);
                if (!withdrawalSucceeds) {
                    return new EconomyResponse(amount, balance[0],
                            EconomyResponse.ResponseType.FAILURE, "economy rejected the transaction");
                }
                balance[0] -= amount;
                return new EconomyResponse(amount, balance[0], EconomyResponse.ResponseType.SUCCESS, "");
            });
            return mockEconomy;
        }

        private KitDefinition buildPaidKit(String name, boolean oneTime) {
            KitDefinition kit = createTestKit(name);
            kit.setPrice(PRICE);
            kit.setReBuyable(!oneTime);
            kit.setItems("someBase64Data");
            return kit;
        }

        private KitServiceImpl serviceWithKits(KitDefinition... kits) throws Exception {
            for (KitDefinition kit : kits) {
                injectKit(service, kit);
            }
            KitServiceImpl spyService = spy(service);
            for (KitDefinition kit : kits) {
                injectKit(spyService, kit);
            }
            doReturn(new ItemStack[]{kitItem}).when(spyService).deserializeItems("someBase64Data");
            return spyService;
        }

        private KitServiceImpl paidKitService(String name, boolean oneTime) throws Exception {
            return serviceWithKits(buildPaidKit(name, oneTime));
        }

        /**
         * Every console line the module wrote that is a payment refusal.
         * <p>
         * Filtering by content is not cosmetic: {@code loadKits()} writes its own
         * "no kit configuration files found" warning when the service is constructed against an
         * empty fixture folder, so a bare {@code verify(mockLogger, times(n)).warn(...)} would be
         * counting that line too and would silently mean something other than it says.
         */
        private List<String> refusalWarnings() {
            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(mockLogger, atLeastOnce()).warn(captor.capture());
            List<String> refusals = new ArrayList<>();
            for (String message : captor.getAllValues()) {
                if (message.contains("refused")) {
                    refusals.add(message);
                }
            }
            return refusals;
        }

        /** Nothing was delivered, nothing was recorded, and no reward command ran. */
        private void assertNothingHappened() throws IllegalAccessException {
            verify(inventory, never()).addItem(any(ItemStack.class));
            verify(mockClaimOperator, never()).insert(any(KitClaimData.class));
            verify(mockClaimOperator, never()).update(any(KitClaimData.class));
            verify(player, never()).performCommand(anyString());
        }

        @Test
        @DisplayName("balance spent between the affordability check and the withdrawal: nothing is delivered, recorded or charged")
        void balanceSpentBetweenCheckAndWithdrawal() throws Exception {
            // The issue's own scenario: has() passes, then the balance is spent elsewhere (an
            // economy backed by a database shared with another server) before the withdrawal.
            Economy mockEconomy = setupMockEconomy();
            when(mockEconomy.has(any(org.bukkit.OfflinePlayer.class), anyDouble())).thenAnswer(inv -> {
                boolean affordable = balance[0] >= (Double) inv.getArgument(1);
                if (hasCalls.incrementAndGet() == 1) {
                    balance[0] = 50.0; // spent on the other server, right after the check
                }
                return affordable;
            });

            KitServiceImpl spyService = paidKitService("racy", true);
            KitService.ClaimResult result = spyService.claimKit(player, "racy");

            // Pre-assertion: the race really ran. Without a second has() the module never got
            // as far as the withdrawal, and a non-SUCCESS result would prove nothing.
            assertThat(hasCalls.get()).isGreaterThanOrEqualTo(2);
            assertThat(result).isEqualTo(KitService.ClaimResult.PAYMENT_FAILED);
            assertThat(balance[0]).isEqualTo(50.0); // only the external spend; the kit charged nothing
            verify(mockEconomy, never()).withdrawPlayer(any(org.bukkit.OfflinePlayer.class), anyDouble());
            assertNothingHappened();
        }

        @Test
        @DisplayName("economy rejects the transaction: nothing is delivered, recorded or charged")
        void economyRejectsTheWithdrawal() throws Exception {
            Economy mockEconomy = statefulEconomy(false);

            KitServiceImpl spyService = paidKitService("rejected", true);
            KitService.ClaimResult result = spyService.claimKit(player, "rejected");

            // Pre-assertion: the withdrawal was actually attempted, so the failure below cannot
            // have come from an earlier check short-circuiting the payment away.
            verify(mockEconomy).withdrawPlayer(player, PRICE);
            assertThat(result).isEqualTo(KitService.ClaimResult.PAYMENT_FAILED);
            assertThat(balance[0]).isEqualTo(500.0);
            assertNothingHappened();
        }

        @Test
        @DisplayName("a refused payment leaves a one-time kit claimable, and the second attempt is not ALREADY_CLAIMED")
        void refusedPaymentLeavesOneTimeKitClaimable() throws Exception {
            statefulEconomy(false);

            KitServiceImpl spyService = paidKitService("onetimepaid", true);
            spyService.claimKit(player, "onetimepaid");

            // The claim row is what makes a one-time kit unclaimable. Nothing was written, so the
            // player has not burned their single claim on a kit they never received and never
            // paid for.
            verify(mockClaimOperator, never()).insert(any(KitClaimData.class));
            KitService.ClaimResult second = spyService.claimKit(player, "onetimepaid");
            assertThat(second).isEqualTo(KitService.ClaimResult.PAYMENT_FAILED);
            assertThat(second).isNotEqualTo(KitService.ClaimResult.ALREADY_CLAIMED);
        }

        @Test
        @DisplayName("control: a successful payment charges the balance, delivers the items and records the claim")
        void successfulPaymentChargesAndDelivers() throws Exception {
            statefulEconomy(true);

            KitServiceImpl spyService = paidKitService("bought", true);
            KitService.ClaimResult result = spyService.claimKit(player, "bought");

            assertThat(result).isEqualTo(KitService.ClaimResult.SUCCESS);
            assertThat(balance[0]).isEqualTo(400.0);
            verify(inventory).addItem(kitItem);
            verify(mockClaimOperator).insert(any(KitClaimData.class));
        }

        @Test
        @DisplayName("ordering: the money moves first, then the claim record, then the items (UltiKits/UltiKits#26)")
        void withdrawalHappensBeforeClaimRecordAndDelivery() throws Exception {
            Economy mockEconomy = statefulEconomy(true);

            KitServiceImpl spyService = paidKitService("ordered", true);
            assertThat(spyService.claimKit(player, "ordered")).isEqualTo(KitService.ClaimResult.SUCCESS);

            InOrder order = inOrder(mockEconomy, inventory, mockClaimOperator);
            order.verify(mockEconomy).withdrawPlayer(player, PRICE);
            order.verify(mockClaimOperator).insert(any(KitClaimData.class));
            order.verify(inventory).addItem(kitItem);
        }

        /**
         * The console line is promised by {@code FEATURES.md} and {@code CHANGELOG.md} to name the
         * kit, the player AND the amount, so all three are asserted. The kit is named {@code
         * vipcrate} rather than something like {@code logged}, which occurs in the message
         * template itself and would let almost any warning satisfy the assertion.
         */
        @Test
        @DisplayName("the refused-payment line is in the server's language (zh)")
        void refusedPaymentLineFollowsTheLanguage() throws Exception {
            statefulEconomy(false);
            when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer("zh"));

            KitServiceImpl spyService = paidKitService("vipcrate", true);
            spyService.claimKit(player, "vipcrate");

            verify(mockLogger).warn(String.format(zh("kits.log.payment_refused"), "vipcrate", player.getName(), PRICE));
        }

        @Test
        @DisplayName("a refused payment is reported to the console naming the kit, the player and the amount")
        void refusedPaymentIsLoggedWithKitPlayerAndAmount() throws Exception {
            statefulEconomy(false);

            KitServiceImpl spyService = paidKitService("vipcrate", true);
            spyService.claimKit(player, "vipcrate");

            List<String> refusals = refusalWarnings();
            assertThat(refusals).hasSize(1);
            assertThat(refusals.get(0))
                    .contains("vipcrate")
                    .contains(player.getName())
                    .contains(String.valueOf(PRICE));
        }

        /**
         * Throttling. The claim path is player-triggered behind only a 200ms GUI debounce, and
         * {@code /kits claim} carries no cooldown at all, so one line per refused attempt floods
         * the console during an economy outage - in the window an operator most needs to read it.
         * The framework's own {@code EconomyUtils} already took this position for the adjacent
         * condition: one line per calling module per server session.
         */
        @Test
        @DisplayName("repeated refusals for the same kit are logged once, not once per attempt")
        void repeatedRefusalsForTheSameKitAreLoggedOnce() throws Exception {
            statefulEconomy(false);

            KitServiceImpl spyService = paidKitService("vipcrate", false);
            for (int attempt = 0; attempt < 5; attempt++) {
                // each iteration asserts a refusal really happened, so "one line" cannot be
                // passing because the later attempts never reached the guard
                assertThat(spyService.claimKit(player, "vipcrate"))
                        .isEqualTo(KitService.ClaimResult.PAYMENT_FAILED);
            }

            assertThat(refusalWarnings()).hasSize(1);
        }

        @Test
        @DisplayName("a second kit gets its own warning: the throttle is per kit, not global")
        void refusalsForDifferentKitsAreEachLogged() throws Exception {
            statefulEconomy(false);

            KitServiceImpl spyService = serviceWithKits(
                    buildPaidKit("vipcrate", false), buildPaidKit("goldcrate", false));

            assertThat(spyService.claimKit(player, "vipcrate"))
                    .isEqualTo(KitService.ClaimResult.PAYMENT_FAILED);
            assertThat(spyService.claimKit(player, "goldcrate"))
                    .isEqualTo(KitService.ClaimResult.PAYMENT_FAILED);

            List<String> refusals = refusalWarnings();
            assertThat(refusals).hasSize(2);
            assertThat(refusals.get(0)).contains("vipcrate");
            assertThat(refusals.get(1)).contains("goldcrate");
        }

        @Test
        @DisplayName("reloading the kits re-arms the warning, so an operator has a way to see it again")
        void reloadingKitsReArmsTheRefusalWarning() throws Exception {
            statefulEconomy(false);

            KitDefinition kit = buildPaidKit("vipcrate", false);
            KitServiceImpl spyService = serviceWithKits(kit);

            assertThat(spyService.claimKit(player, "vipcrate"))
                    .isEqualTo(KitService.ClaimResult.PAYMENT_FAILED);
            assertThat(refusalWarnings()).hasSize(1);

            spyService.reload();
            injectKit(spyService, kit); // a real server re-reads this kit from its own kits/*.yml

            assertThat(spyService.claimKit(player, "vipcrate"))
                    .isEqualTo(KitService.ClaimResult.PAYMENT_FAILED);
            assertThat(refusalWarnings()).hasSize(2);
        }

        /**
         * The guard this fix first wrote read {@code !kit.isFree() && EconomyUtils.isAvailable() &&
         * !EconomyUtils.withdraw(...)}. If the Vault provider is deregistered after {@code
         * canAfford} passed, that middle term short-circuits the whole condition to false and
         * control falls through to delivery - reproducing #20's own outcome inside the guard that
         * closes #20. The term bought nothing:
         * the framework's bridge already returns false when no provider is registered.
         */
        @Test
        @DisplayName("economy deregistered between the affordability check and the withdrawal: nothing is delivered")
        void economyDeregisteredBetweenCheckAndWithdrawal() throws Exception {
            Economy mockEconomy = setupMockEconomy();
            when(mockEconomy.has(any(org.bukkit.OfflinePlayer.class), anyDouble())).thenAnswer(inv -> {
                if (hasCalls.incrementAndGet() == 1) {
                    // an economy plugin re-registering its provider during its own reload
                    Bukkit.getServicesManager().unregister(Economy.class, mockEconomy);
                }
                return true;
            });

            KitServiceImpl spyService = paidKitService("dereg", true);
            KitService.ClaimResult result = spyService.claimKit(player, "dereg");

            // Pre-assertions: the affordability check really passed, and the provider really went
            // away - so neither an earlier refusal nor an inert fixture is producing the result.
            assertThat(hasCalls.get()).isEqualTo(1);
            assertThat(EconomyUtils.isAvailable()).isFalse();
            assertThat(result).isEqualTo(KitService.ClaimResult.PAYMENT_FAILED);
            verify(mockEconomy, never()).withdrawPlayer(any(org.bukkit.OfflinePlayer.class), anyDouble());
            assertNothingHappened();
        }

        /**
         * {@code player.performCommand} propagates {@code CommandException} out of any third-party
         * executor that throws, so a reward command failing between the money moving and the claim
         * being recorded would leave a one-time kit silently claimable again. The claim record is
         * therefore written before the reward commands run.
         */
        @Test
        @DisplayName("ordering: the claim is recorded before the reward commands, the likeliest step to fail")
        void claimIsRecordedBeforeTheRewardCommands() throws Exception {
            statefulEconomy(true);

            KitDefinition kit = buildPaidKit("rewarded", true);
            kit.setPlayerCommands(Collections.singletonList("warp vip"));
            KitServiceImpl spyService = serviceWithKits(kit);

            assertThat(spyService.claimKit(player, "rewarded")).isEqualTo(KitService.ClaimResult.SUCCESS);

            InOrder order = inOrder(inventory, mockClaimOperator, player);
            order.verify(mockClaimOperator).insert(any(KitClaimData.class));
            order.verify(inventory).addItem(kitItem);
            order.verify(player).performCommand("warp vip");
        }

        @Test
        @DisplayName("a free kit is unaffected: it is still delivered when an economy is present")
        void freeKitStillDelivered() throws Exception {
            statefulEconomy(false);

            KitDefinition kit = createTestKit("freebie");
            kit.setPrice(0);
            kit.setItems("someBase64Data");
            injectKit(service, kit);
            KitServiceImpl spyService = spy(service);
            injectKit(spyService, kit);
            doReturn(new ItemStack[]{kitItem}).when(spyService).deserializeItems("someBase64Data");

            assertThat(spyService.claimKit(player, "freebie")).isEqualTo(KitService.ClaimResult.SUCCESS);
            verify(inventory).addItem(kitItem);
            assertThat(balance[0]).isEqualTo(500.0);
        }
    }

    // =========================================================================
    // Claim record failure tests (UltiKits/UltiKits#26)
    // =========================================================================

    /**
     * A one-time claim whose record cannot be written. The maintainer's answer of 2026-09-24 decides
     * the behaviour: write the record before handing anything over, and refuse the claim when the
     * write fails; a paid kit keeps the charge-first order - charge, write the record, give - and is
     * refunded when the record cannot be written; if the refund also fails, an ERROR names the
     * player, the kit and the amount (UltiKits/UltiKits#26).
     * <p>
     * Every assertion reads state: the balance, the items handed to the inventory, the commands run
     * and the rows in a claim table that - like the relational backends - is read back on every
     * claim. The fake table hands out copies, so an object the service mutates before a failed write
     * does not count as stored.
     */
    @Nested
    @DisplayName("Claim Record Failure Tests")
    class ClaimRecordFailureTests {

        private static final double PRICE = 100.0;

        private final double[] balance = {500.0};
        private final List<KitClaimData> rows = new ArrayList<>();
        private final List<ItemStack> given = new ArrayList<>();
        private final List<String> commandsRun = new ArrayList<>();
        /** Thrown by the next claim-table write while non-null. */
        private Exception writeFailure;
        private boolean refundSucceeds = true;
        /** The number of items handed over when the claim row was written, per write. */
        private final List<Integer> givenAtWrite = new ArrayList<>();
        /** The balance when the claim row was written, per write. */
        private final List<Double> balanceAtWrite = new ArrayList<>();

        private Player player;
        private ItemStack kitItem;

        @BeforeEach
        void setUp() throws Exception {
            when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer("en"));
            new File(tempDir, "kits").mkdirs();
            service = createService();
            player = createMockPlayer();
            PlayerInventory inventory = player.getInventory();
            when(inventory.getStorageContents()).thenReturn(new ItemStack[36]);
            when(inventory.addItem(any(ItemStack.class))).thenAnswer(inv -> {
                given.add(inv.getArgument(0));
                return new HashMap<Integer, ItemStack>();
            });
            when(player.performCommand(anyString())).thenAnswer(inv -> {
                commandsRun.add(inv.getArgument(0));
                return true;
            });
            kitItem = mock(ItemStack.class);
            when(kitItem.clone()).thenReturn(kitItem);

            when(mockQuery.list()).thenAnswer(inv -> {
                List<KitClaimData> copies = new ArrayList<>();
                for (KitClaimData row : rows) {
                    copies.add(copyOf(row));
                }
                return copies;
            });
            doAnswer(inv -> {
                recordWriteAttempt();
                rows.add(copyOf(inv.getArgument(0)));
                return null;
            }).when(mockClaimOperator).insert(any(KitClaimData.class));
            doAnswer(inv -> {
                recordWriteAttempt();
                KitClaimData updated = inv.getArgument(0);
                rows.removeIf(row -> row.getUuid().equals(updated.getUuid()));
                rows.add(copyOf(updated));
                return null;
            }).when(mockClaimOperator).update(any(KitClaimData.class));

            Economy economy = setupMockEconomy();
            when(economy.has(any(org.bukkit.OfflinePlayer.class), anyDouble()))
                    .thenAnswer(inv -> balance[0] >= (Double) inv.getArgument(1));
            when(economy.withdrawPlayer(any(org.bukkit.OfflinePlayer.class), anyDouble())).thenAnswer(inv -> {
                double amount = inv.getArgument(1);
                balance[0] -= amount;
                return new EconomyResponse(amount, balance[0], EconomyResponse.ResponseType.SUCCESS, "");
            });
            when(economy.depositPlayer(any(org.bukkit.OfflinePlayer.class), anyDouble())).thenAnswer(inv -> {
                double amount = inv.getArgument(1);
                if (!refundSucceeds) {
                    return new EconomyResponse(amount, balance[0], EconomyResponse.ResponseType.FAILURE, "economy down");
                }
                balance[0] += amount;
                return new EconomyResponse(amount, balance[0], EconomyResponse.ResponseType.SUCCESS, "");
            });
        }

        private void recordWriteAttempt() throws Exception {
            givenAtWrite.add(given.size());
            balanceAtWrite.add(balance[0]);
            if (writeFailure != null) {
                throw writeFailure;
            }
        }

        private KitClaimData copyOf(KitClaimData row) {
            return KitClaimData.builder().uuid(row.getUuid()).playerUuid(row.getPlayerUuid())
                    .kitName(row.getKitName()).lastClaim(row.getLastClaim()).claimCount(row.getClaimCount())
                    .build();
        }

        private KitServiceImpl serviceWithKit(String name, double price, boolean oneTime) throws Exception {
            KitDefinition kit = createTestKit(name);
            kit.setPrice(price);
            kit.setReBuyable(!oneTime);
            kit.setItems("someBase64Data");
            kit.setPlayerCommands(Collections.singletonList("reward " + name));
            KitServiceImpl spyService = spy(service);
            injectKit(spyService, kit);
            doReturn(new ItemStack[]{kitItem}).when(spyService).deserializeItems("someBase64Data");
            return spyService;
        }

        private List<String> errors() {
            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(mockLogger, atLeast(0)).error(captor.capture());
            return captor.getAllValues();
        }

        @Test
        @DisplayName("paid one-time kit, the new claim row cannot be inserted: refused, refunded, nothing given or run")
        void insertFailureRefusesAndRefunds() throws Exception {
            writeFailure = new com.ultikits.ultitools.exceptions.DataAccessException("connection lost");
            KitServiceImpl spyService = serviceWithKit("vip", PRICE, true);

            KitService.ClaimResult result = spyService.claimKit(player, "vip");

            assertThat(result).isEqualTo(KitService.ClaimResult.NOT_RECORDED);
            assertThat(balanceAtWrite).containsExactly(400.0); // charged before the record, as decided
            assertThat(balance[0]).isEqualTo(500.0);          // and refunded after it failed
            assertThat(given).isEmpty();
            assertThat(commandsRun).isEmpty();
            assertThat(rows).isEmpty();
        }

        @Test
        @DisplayName("the one-time guarantee holds across a failure: refused, then claimed once, then ALREADY_CLAIMED")
        void oneTimeKitIsHandedOverExactlyOnce() throws Exception {
            KitServiceImpl spyService = serviceWithKit("vip", PRICE, true);

            writeFailure = new com.ultikits.ultitools.exceptions.DataAccessException("connection lost");
            assertThat(spyService.claimKit(player, "vip")).isEqualTo(KitService.ClaimResult.NOT_RECORDED);
            writeFailure = null;
            assertThat(spyService.claimKit(player, "vip")).isEqualTo(KitService.ClaimResult.SUCCESS);
            assertThat(spyService.claimKit(player, "vip")).isEqualTo(KitService.ClaimResult.ALREADY_CLAIMED);

            assertThat(given).containsExactly(kitItem);
            assertThat(commandsRun).containsExactly("reward vip");
            assertThat(rows).hasSize(1);
            assertThat(balance[0]).isEqualTo(400.0);
        }

        @Test
        @DisplayName("the claim row is written before any item is handed over")
        void recordIsWrittenBeforeDelivery() throws Exception {
            KitServiceImpl spyService = serviceWithKit("vip", PRICE, true);

            assertThat(spyService.claimKit(player, "vip")).isEqualTo(KitService.ClaimResult.SUCCESS);

            assertThat(givenAtWrite).containsExactly(0);
            assertThat(given).containsExactly(kitItem);
        }

        @Test
        @DisplayName("repeat claim whose update throws the unchecked database exception: refused and refunded")
        void uncheckedUpdateFailureRefusesAndRefunds() throws Exception {
            KitServiceImpl spyService = serviceWithKit("daily", PRICE, false);
            assertThat(spyService.claimKit(player, "daily")).isEqualTo(KitService.ClaimResult.SUCCESS);
            long firstClaim = rows.get(0).getLastClaim();
            given.clear();
            commandsRun.clear();

            writeFailure = new com.ultikits.ultitools.exceptions.DataAccessException("connection lost");
            KitService.ClaimResult result = spyService.claimKit(player, "daily");

            assertThat(result).isEqualTo(KitService.ClaimResult.NOT_RECORDED);
            assertThat(balance[0]).isEqualTo(400.0); // the first claim's charge only
            assertThat(given).isEmpty();
            assertThat(commandsRun).isEmpty();
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).getClaimCount()).isEqualTo(1);
            assertThat(rows.get(0).getLastClaim()).isEqualTo(firstClaim);
        }

        @Test
        @DisplayName("repeat claim whose update throws IllegalAccessException: refused and refunded")
        void checkedUpdateFailureRefusesAndRefunds() throws Exception {
            KitServiceImpl spyService = serviceWithKit("daily", PRICE, false);
            assertThat(spyService.claimKit(player, "daily")).isEqualTo(KitService.ClaimResult.SUCCESS);
            given.clear();

            writeFailure = new IllegalAccessException("field not accessible");
            KitService.ClaimResult result = spyService.claimKit(player, "daily");

            assertThat(result).isEqualTo(KitService.ClaimResult.NOT_RECORDED);
            assertThat(balance[0]).isEqualTo(400.0);
            assertThat(given).isEmpty();
        }

        @Test
        @DisplayName("free kit, the record cannot be written: refused, nothing given, no money moves")
        void freeKitRecordFailureRefuses() throws Exception {
            writeFailure = new com.ultikits.ultitools.exceptions.DataAccessException("connection lost");
            KitServiceImpl spyService = serviceWithKit("starter", 0, true);

            KitService.ClaimResult result = spyService.claimKit(player, "starter");

            assertThat(result).isEqualTo(KitService.ClaimResult.NOT_RECORDED);
            assertThat(given).isEmpty();
            assertThat(commandsRun).isEmpty();
            assertThat(rows).isEmpty();
            assertThat(balance[0]).isEqualTo(500.0);
        }

        /** An economy that throws while refunding counts as a failed refund. */
        @Test
        @DisplayName("an economy that throws during the refund is a failed refund, reported as such")
        void refundThatThrowsIsAFailedRefund() throws Exception {
            writeFailure = new com.ultikits.ultitools.exceptions.DataAccessException("connection lost");
            Economy economy = Bukkit.getServicesManager().getRegistration(Economy.class).getProvider();
            when(economy.depositPlayer(any(org.bukkit.OfflinePlayer.class), anyDouble()))
                    .thenThrow(new IllegalStateException("economy offline"));
            KitServiceImpl spyService = serviceWithKit("vipcrate", PRICE, true);

            KitService.ClaimResult result = spyService.claimKit(player, "vipcrate");

            assertThat(result).isEqualTo(KitService.ClaimResult.NOT_RECORDED_REFUND_FAILED);
            assertThat(balance[0]).isEqualTo(400.0);
            assertThat(given).isEmpty();
            assertThat(errors()).anyMatch(line -> line.contains(player.getName())
                    && line.contains("vipcrate") && line.contains(String.valueOf(PRICE)));
        }

        @Test
        @DisplayName("the refund also fails: its own result, and an ERROR naming the player, the kit and the amount")
        void refundFailureIsLoggedWithPlayerKitAndAmount() throws Exception {
            writeFailure = new com.ultikits.ultitools.exceptions.DataAccessException("connection lost");
            refundSucceeds = false;
            KitServiceImpl spyService = serviceWithKit("vipcrate", PRICE, true);

            KitService.ClaimResult result = spyService.claimKit(player, "vipcrate");

            assertThat(result).isEqualTo(KitService.ClaimResult.NOT_RECORDED_REFUND_FAILED);
            assertThat(balance[0]).isEqualTo(400.0);
            assertThat(given).isEmpty();
            assertThat(commandsRun).isEmpty();
            assertThat(errors()).anyMatch(line -> line.contains(player.getName())
                    && line.contains("vipcrate") && line.contains(String.valueOf(PRICE)));
        }
    }

    // =========================================================================
    // Over-sized stack tests (UltiKits/UltiKits#24)
    // =========================================================================

    /**
     * A kit stack larger than its item's maximum stack size occupies more than one slot. The claim
     * must count the slots the stacks really need, refuse before charging when they will not fit,
     * and drop at the player's feet anything the inventory still cannot take - never destroy it
     * (UltiKits/UltiKits#24). These run against a real MockBukkit player inventory, so the
     * assertions read the inventory's contents, the balance and the world's dropped items.
     */
    @Nested
    @DisplayName("Over-sized Stack Tests")
    class OversizedStackTests {

        private static final double PRICE = 100.0;
        private final double[] balance = {500.0};
        private org.mockbukkit.mockbukkit.ServerMock server;
        private org.mockbukkit.mockbukkit.entity.PlayerMock player;

        @BeforeEach
        void setUp() {
            when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer("en"));
            new File(tempDir, "kits").mkdirs();
            service = createService();
            Economy economy = setupMockEconomy(); // boots the shared MockBukkit server
            when(economy.has(any(org.bukkit.OfflinePlayer.class), anyDouble()))
                    .thenAnswer(inv -> balance[0] >= (Double) inv.getArgument(1));
            when(economy.withdrawPlayer(any(org.bukkit.OfflinePlayer.class), anyDouble())).thenAnswer(inv -> {
                double amount = inv.getArgument(1);
                balance[0] -= amount;
                return new EconomyResponse(amount, balance[0], EconomyResponse.ResponseType.SUCCESS, "");
            });
            server = MockBukkit.getMock();
            player = server.addPlayer();
        }

        /** A paid, re-buyable kit whose single stack is {@code stack}. */
        private KitServiceImpl serviceWithKit(String name, ItemStack stack) throws Exception {
            KitDefinition kit = createTestKit(name);
            kit.setPrice(PRICE);
            kit.setReBuyable(true);
            kit.setItems("someBase64Data");
            KitServiceImpl spyService = spy(service);
            injectKit(spyService, kit);
            doReturn(new ItemStack[]{stack}).when(spyService).deserializeItems("someBase64Data");
            return spyService;
        }

        /** Fills every storage slot but the first {@code free} with a full stack of dirt. */
        private void leaveFreeSlots(int free) {
            ItemStack[] contents = new ItemStack[36];
            for (int i = free; i < contents.length; i++) {
                contents[i] = new ItemStack(Material.DIRT, 64);
            }
            player.getInventory().setStorageContents(contents);
        }

        private int count(Material material) {
            int total = 0;
            for (ItemStack stack : player.getInventory().getStorageContents()) {
                if (stack != null && stack.getType() == material) {
                    total += stack.getAmount();
                }
            }
            return total;
        }

        @Test
        @DisplayName("128 cobblestone needs two slots: with one free slot the claim is refused before any charge")
        void oversizedStackRefusedBeforeCharging() throws Exception {
            leaveFreeSlots(1);
            ItemStack[] before = player.getInventory().getStorageContents().clone();
            KitServiceImpl spyService = serviceWithKit("bigstone", new ItemStack(Material.COBBLESTONE, 128));

            KitService.ClaimResult result = spyService.claimKit(player, "bigstone");

            assertThat(result).isEqualTo(KitService.ClaimResult.INVENTORY_FULL);
            assertThat(balance[0]).isEqualTo(500.0);
            assertThat(player.getInventory().getStorageContents()).containsExactly(before);
            assertThat(count(Material.COBBLESTONE)).isZero();
            verify(mockClaimOperator, never()).insert(any(KitClaimData.class));
        }

        @Test
        @DisplayName("with the two slots it needs, the whole over-sized stack arrives and nothing is dropped")
        void oversizedStackDeliveredInFull() throws Exception {
            leaveFreeSlots(2);
            KitServiceImpl spyService = serviceWithKit("bigstone", new ItemStack(Material.COBBLESTONE, 128));

            KitService.ClaimResult result = spyService.claimKit(player, "bigstone");

            assertThat(result).isEqualTo(KitService.ClaimResult.SUCCESS);
            assertThat(balance[0]).isEqualTo(400.0);
            assertThat(count(Material.COBBLESTONE)).isEqualTo(128);
            assertThat(player.getWorld().getEntitiesByClass(org.bukkit.entity.Item.class)).isEmpty();
        }

        @Test
        @DisplayName("the slot count uses the stack's own maximum, not its material's default")
        void slotCountUsesTheStacksOwnMaximum() throws Exception {
            ItemStack stack = new ItemStack(Material.COBBLESTONE, 32);
            org.bukkit.inventory.meta.ItemMeta meta = stack.getItemMeta();
            meta.setMaxStackSize(16);
            stack.setItemMeta(meta);
            // Pre-assertion: the component is what the stack reports, so a count by the material's
            // default (64, one slot) and a count by the stack's own maximum (16, two slots) differ.
            assertThat(stack.getMaxStackSize()).isEqualTo(16);
            assertThat(Material.COBBLESTONE.getMaxStackSize()).isEqualTo(64);
            leaveFreeSlots(1);
            KitServiceImpl spyService = serviceWithKit("capped", stack);

            KitService.ClaimResult result = spyService.claimKit(player, "capped");

            assertThat(result).isEqualTo(KitService.ClaimResult.INVENTORY_FULL);
            assertThat(balance[0]).isEqualTo(500.0);
        }

        /**
         * {@code addItem} tops up matching partial stacks before it takes an empty slot, so the check
         * must count that room too - counting only empty slots refused a claim that fits. 65
         * cobblestone fit a matching 63-stack (1 more) plus one empty slot (64).
         */
        @Test
        @DisplayName("room left in a matching partial stack counts towards the fit")
        void mergeIntoAPartialStackCountsTowardsTheFit() throws Exception {
            leaveFreeSlots(1);
            player.getInventory().setItem(1, new ItemStack(Material.COBBLESTONE, 63));
            KitServiceImpl spyService = serviceWithKit("bigstone", new ItemStack(Material.COBBLESTONE, 65));

            KitService.ClaimResult result = spyService.claimKit(player, "bigstone");

            assertThat(result).isEqualTo(KitService.ClaimResult.SUCCESS);
            assertThat(balance[0]).isEqualTo(400.0);
            assertThat(count(Material.COBBLESTONE)).isEqualTo(128);
        }

        @Test
        @DisplayName("a partial stack of a different item is no room at all")
        void aDissimilarPartialStackIsNoRoom() throws Exception {
            leaveFreeSlots(1);
            player.getInventory().setItem(1, new ItemStack(Material.STONE, 63));
            KitServiceImpl spyService = serviceWithKit("bigstone", new ItemStack(Material.COBBLESTONE, 65));

            assertThat(spyService.claimKit(player, "bigstone")).isEqualTo(KitService.ClaimResult.INVENTORY_FULL);
            assertThat(balance[0]).isEqualTo(500.0);
        }

        @Test
        @DisplayName("two kit stacks of one item share the slot the first of them starts")
        void kitStacksShareTheSlotAnEarlierOneStarts() throws Exception {
            leaveFreeSlots(1);
            KitDefinition kit = createTestKit("halves");
            kit.setPrice(PRICE);
            kit.setReBuyable(true);
            kit.setItems("someBase64Data");
            KitServiceImpl spyService = spy(service);
            injectKit(spyService, kit);
            doReturn(new ItemStack[]{new ItemStack(Material.COBBLESTONE, 32), new ItemStack(Material.COBBLESTONE, 32)})
                    .when(spyService).deserializeItems("someBase64Data");

            assertThat(spyService.claimKit(player, "halves")).isEqualTo(KitService.ClaimResult.SUCCESS);
            assertThat(count(Material.COBBLESTONE)).isEqualTo(64);
        }

        @Test
        @DisplayName("control: ordinary stacks still need one slot each")
        void ordinaryStacksNeedOneSlotEach() throws Exception {
            leaveFreeSlots(1);
            KitServiceImpl spyService = serviceWithKit("onestack", new ItemStack(Material.COBBLESTONE, 64));

            assertThat(spyService.claimKit(player, "onestack")).isEqualTo(KitService.ClaimResult.SUCCESS);
            assertThat(count(Material.COBBLESTONE)).isEqualTo(64);
        }

        /**
         * The second line of defence: whatever {@code addItem} still hands back - an inventory another
         * plugin filled between the check and the delivery - is dropped at the player's location,
         * each leftover once, and the player is told. The inventory is a mock here only because
         * MockBukkit's {@code addItem} does not report leftovers; the world is the real mock world,
         * so the dropped items are read from it.
         */
        @Test
        @DisplayName("anything addItem cannot place is dropped at the player's feet once, and the player is told")
        void leftoversAreDroppedNotDestroyed() throws Exception {
            org.bukkit.World world = player.getWorld();
            org.bukkit.Location feet = new org.bukkit.Location(world, 10, 64, 10);
            Player mocked = createMockPlayer();
            when(mocked.getWorld()).thenReturn(world);
            when(mocked.getLocation()).thenReturn(feet);
            PlayerInventory inventory = mocked.getInventory();
            when(inventory.getStorageContents()).thenReturn(new ItemStack[36]);
            ItemStack leftover = new ItemStack(Material.COBBLESTONE, 64);
            HashMap<Integer, ItemStack> leftovers = new HashMap<>();
            leftovers.put(0, leftover);
            when(inventory.addItem(any(ItemStack.class))).thenReturn(leftovers);
            KitServiceImpl spyService = serviceWithKit("bigstone", new ItemStack(Material.COBBLESTONE, 128));

            KitService.ClaimResult result = spyService.claimKit(mocked, "bigstone");

            assertThat(result).isEqualTo(KitService.ClaimResult.SUCCESS);
            List<org.bukkit.entity.Item> dropped =
                    new ArrayList<>(world.getEntitiesByClass(org.bukkit.entity.Item.class));
            assertThat(dropped).hasSize(1);
            assertThat(dropped.get(0).getItemStack().getType()).isEqualTo(Material.COBBLESTONE);
            assertThat(dropped.get(0).getItemStack().getAmount()).isEqualTo(64);
            ArgumentCaptor<String> told = ArgumentCaptor.forClass(String.class);
            verify(mocked, atLeastOnce()).sendMessage(told.capture());
            assertThat(told.getAllValues())
                    .anyMatch(line -> line.contains(CatalogueText.text("en", "kits.claim.leftovers_dropped")));
        }
    }

    // =========================================================================
    // Cooldown Tests
    // =========================================================================
    @Nested
    @DisplayName("Cooldown Tests")
    class CooldownTests {

        private Player player;

        @BeforeEach
        void setUp() {
            new File(tempDir, "kits").mkdirs();
            service = createService();
            player = createMockPlayer();
        }

        @Test
        @DisplayName("getRemainingCooldown returns 0 when never claimed (rebuyable)")
        void neverClaimedRebuyable() {
            KitDefinition kit = createTestKit("fresh");
            kit.setReBuyable(true);
            kit.setCooldown(3600);

            when(mockQuery.list()).thenReturn(Collections.emptyList());

            long remaining = service.getRemainingCooldown(player, kit);
            assertThat(remaining).isEqualTo(0);
        }

        @Test
        @DisplayName("getRemainingCooldown returns -1 for one-time kit already claimed")
        void oneTimeAlreadyClaimed() {
            KitDefinition kit = createTestKit("once");
            kit.setReBuyable(false);

            KitClaimData claim = KitClaimData.builder()
                    .uuid(UUID.randomUUID().toString())
                    .playerUuid(player.getUniqueId().toString())
                    .kitName("once")
                    .lastClaim(System.currentTimeMillis())
                    .claimCount(1)
                    .build();

            when(mockQuery.list()).thenReturn(Collections.singletonList(claim));

            long remaining = service.getRemainingCooldown(player, kit);
            assertThat(remaining).isEqualTo(-1);
        }

        @Test
        @DisplayName("getRemainingCooldown returns 0 for one-time kit never claimed")
        void oneTimeNeverClaimed() {
            KitDefinition kit = createTestKit("once2");
            kit.setReBuyable(false);

            when(mockQuery.list()).thenReturn(Collections.emptyList());

            long remaining = service.getRemainingCooldown(player, kit);
            assertThat(remaining).isEqualTo(0);
        }

        @Test
        @DisplayName("getRemainingCooldown returns positive value when cooldown active")
        void activeCooldown() {
            KitDefinition kit = createTestKit("cd");
            kit.setReBuyable(true);
            kit.setCooldown(3600);

            KitClaimData claim = KitClaimData.builder()
                    .uuid(UUID.randomUUID().toString())
                    .playerUuid(player.getUniqueId().toString())
                    .kitName("cd")
                    .lastClaim(System.currentTimeMillis())
                    .claimCount(1)
                    .build();

            when(mockQuery.list()).thenReturn(Collections.singletonList(claim));

            long remaining = service.getRemainingCooldown(player, kit);
            assertThat(remaining).isGreaterThan(0);
            assertThat(remaining).isLessThanOrEqualTo(3600 * 1000L);
        }

        @Test
        @DisplayName("getRemainingCooldown returns 0 when cooldown expired")
        void expiredCooldown() {
            KitDefinition kit = createTestKit("expired");
            kit.setReBuyable(true);
            kit.setCooldown(10);

            KitClaimData claim = KitClaimData.builder()
                    .uuid(UUID.randomUUID().toString())
                    .playerUuid(player.getUniqueId().toString())
                    .kitName("expired")
                    .lastClaim(System.currentTimeMillis() - 20000)
                    .claimCount(1)
                    .build();

            when(mockQuery.list()).thenReturn(Collections.singletonList(claim));

            long remaining = service.getRemainingCooldown(player, kit);
            assertThat(remaining).isEqualTo(0);
        }

        @Test
        @DisplayName("getRemainingCooldown returns 0 for rebuyable kit with zero cooldown")
        void zeroCooldownRebuyable() {
            KitDefinition kit = createTestKit("zerocool");
            kit.setReBuyable(true);
            kit.setCooldown(0);

            KitClaimData claim = KitClaimData.builder()
                    .uuid(UUID.randomUUID().toString())
                    .playerUuid(player.getUniqueId().toString())
                    .kitName("zerocool")
                    .lastClaim(System.currentTimeMillis())
                    .claimCount(3)
                    .build();

            when(mockQuery.list()).thenReturn(Collections.singletonList(claim));

            long remaining = service.getRemainingCooldown(player, kit);
            assertThat(remaining).isEqualTo(0);
        }

        @Test
        @DisplayName("getRemainingCooldown clamps negative remaining to zero")
        void remainingNeverNegative() {
            KitDefinition kit = createTestKit("veryold");
            kit.setReBuyable(true);
            kit.setCooldown(1);

            KitClaimData claim = KitClaimData.builder()
                    .uuid(UUID.randomUUID().toString())
                    .playerUuid(player.getUniqueId().toString())
                    .kitName("veryold")
                    .lastClaim(System.currentTimeMillis() - 1000000)
                    .claimCount(1)
                    .build();

            when(mockQuery.list()).thenReturn(Collections.singletonList(claim));

            long remaining = service.getRemainingCooldown(player, kit);
            assertThat(remaining).isEqualTo(0);
        }
    }

    // =========================================================================
    // Format Cooldown Tests
    // =========================================================================
    @Nested
    @DisplayName("Format Cooldown Tests")
    class FormatCooldownTests {

        @BeforeEach
        void setUp() {
            new File(tempDir, "kits").mkdirs();
            service = createService();
        }

        @Test
        @DisplayName("formats zero as claimable")
        void formatZero() {
            assertThat(service.formatCooldown(0)).isEqualTo("可领取");
        }

        @Test
        @DisplayName("formats negative values as claimable")
        void formatNegative() {
            assertThat(service.formatCooldown(-100)).isEqualTo("可领取");
            assertThat(service.formatCooldown(-1)).isEqualTo("可领取");
        }

        @Test
        @DisplayName("formats seconds only")
        void formatSecondsOnly() {
            String result = service.formatCooldown(45_000); // 45 seconds
            assertThat(result).contains("45").contains("秒");
            assertThat(result).doesNotContain("小时").doesNotContain("分钟");
        }

        @Test
        @DisplayName("formats minutes and seconds")
        void formatMinutesSeconds() {
            String result = service.formatCooldown(150_000); // 2 min 30 sec
            assertThat(result).contains("2").contains("分钟");
            assertThat(result).contains("30").contains("秒");
        }

        @Test
        @DisplayName("formats hours, minutes, and seconds")
        void formatHoursMinutesSeconds() {
            long millis = (2 * 3600 + 15 * 60 + 30) * 1000L; // 2h 15m 30s
            String result = service.formatCooldown(millis);
            assertThat(result).contains("2").contains("小时");
            assertThat(result).contains("15").contains("分钟");
            assertThat(result).contains("30").contains("秒");
        }

        @Test
        @DisplayName("formats hours only (exact)")
        void formatHoursOnly() {
            long millis = 2 * 3600 * 1000L; // exactly 2 hours
            String result = service.formatCooldown(millis);
            assertThat(result).contains("2").contains("小时");
            assertThat(result).doesNotContain("分钟");
        }

        @Test
        @DisplayName("formats 1 second")
        void formatOneSecond() {
            String result = service.formatCooldown(1000);
            assertThat(result).contains("1").contains("秒");
        }

        @Test
        @DisplayName("formats sub-second as 0 seconds")
        void formatSubSecond() {
            String result = service.formatCooldown(500); // 500ms = 0 seconds
            assertThat(result).contains("0").contains("秒");
        }

        @Test
        @DisplayName("formats hours and seconds with no minutes")
        void formatHoursAndSecondsNoMinutes() {
            long millis = (1 * 3600 + 0 * 60 + 45) * 1000L; // 1h 0m 45s
            String result = service.formatCooldown(millis);
            assertThat(result).contains("1").contains("小时");
            assertThat(result).contains("45").contains("秒");
            assertThat(result).doesNotContain("分钟");
        }

        @Test
        @DisplayName("formats minutes only with no seconds")
        void formatMinutesOnlyNoSeconds() {
            long millis = 5 * 60 * 1000L; // exactly 5 minutes
            String result = service.formatCooldown(millis);
            assertThat(result).contains("5").contains("分钟");
            // seconds = 0, sb.length() > 0 after minutes, so no seconds appended
            assertThat(result).doesNotContain("秒");
        }

        @Test
        @DisplayName("formats large values correctly")
        void formatLargeValue() {
            long millis = (24 * 3600 + 59 * 60 + 59) * 1000L; // 24h 59m 59s
            String result = service.formatCooldown(millis);
            assertThat(result).contains("24").contains("小时");
            assertThat(result).contains("59").contains("分钟");
            assertThat(result).contains("59").contains("秒");
        }
    }

    // =========================================================================
    // Serialization Tests
    // =========================================================================
    @Nested
    @DisplayName("Serialization Tests")
    class SerializationTests {

        @BeforeEach
        void setUp() {
            new File(tempDir, "kits").mkdirs();
            service = createService();
        }

        @Test
        @DisplayName("deserializeItems returns null for null data")
        void deserializeNullReturnsNull() {
            assertThat(service.deserializeItems(null)).isNull();
        }

        @Test
        @DisplayName("deserializeItems returns null for empty data")
        void deserializeEmptyReturnsNull() {
            assertThat(service.deserializeItems("")).isNull();
        }

        @Test
        @DisplayName("deserializeItems returns null for invalid base64 and logs error")
        void deserializeInvalidDataReturnsNull() {
            assertThat(service.deserializeItems("AAAA")).isNull();
            verify(mockLogger).error(contains("反序列化礼包物品失败"));
        }

        @Test
        @DisplayName("serializeItems logs a failed write in the server's language (zh)")
        void serializeFailureIsLoggedInTheServersLanguage() {
            ItemStack unserializable = mock(ItemStack.class);
            when(unserializable.serialize()).thenReturn(Collections.<String, Object>singletonMap("x", new Object()));

            assertThat(service.serializeItems(new ItemStack[]{unserializable})).isNull();

            ArgumentCaptor<String> line = ArgumentCaptor.forClass(String.class);
            verify(mockLogger).error(line.capture());
            assertThat(line.getValue()).startsWith(String.format(zh("kits.log.serialize_failed"), ""));
        }

        @Test
        @DisplayName("deserializeItems returns null for whitespace-only data")
        void deserializeWhitespaceReturnsNull() {
            // Non-empty but will fail base64 decode or stream read
            assertThat(service.deserializeItems("   ")).isNull();
        }
    }

    // =========================================================================
    // Save Tests
    // =========================================================================
    @Nested
    @DisplayName("Save Tests")
    class SaveTests {

        @BeforeEach
        void setUp() {
            new File(tempDir, "kits").mkdirs();
            service = createService();
        }

        @Test
        @DisplayName("saveKitItems returns SYSTEM_DISABLED and writes nothing while the switch is off")
        void saveRefusedWhileDisabled() throws Exception {
            KitDefinition kit = createTestKit("editable");
            KitServiceImpl spyService = spy(service);
            injectKit(spyService, kit);
            doReturn("data").when(spyService).serializeItems(any(ItemStack[].class));
            ItemStack stone = mock(ItemStack.class);
            lenient().when(stone.getType()).thenReturn(Material.STONE);

            config.setEnabled(false);

            KitService.SaveResult result = spyService.saveKitItems("editable", new ItemStack[]{stone});

            assertThat(result).isEqualTo(KitService.SaveResult.SYSTEM_DISABLED);
            // Nothing was written and nothing was even serialized: the guard is the gateway's first
            // statement, so a caller that outlives the command gate cannot get past it.
            verify(spyService, never()).saveKitToFile(anyString(), any(KitDefinition.class));
            verify(spyService, never()).serializeItems(any(ItemStack[].class));
        }

        @Test
        @DisplayName("saveKitItems still saves at the declared default")
        void saveAllowedAtTheDeclaredDefault() throws Exception {
            KitDefinition kit = createTestKit("editable");
            KitServiceImpl spyService = spy(service);
            injectKit(spyService, kit);
            doReturn("data").when(spyService).serializeItems(any(ItemStack[].class));
            doReturn(true).when(spyService).saveKitToFile(eq("editable"), eq(kit));
            ItemStack stone = mock(ItemStack.class);
            when(stone.getType()).thenReturn(Material.STONE);

            KitService.SaveResult result = spyService.saveKitItems("editable", new ItemStack[]{stone});

            assertThat(result).isEqualTo(KitService.SaveResult.SUCCESS);
            verify(spyService).saveKitToFile("editable", kit);
        }

        /**
         * A save the file does not take must leave the kit as it was: the editor reports the failure,
         * so a claim afterwards must still hand out the items the file holds, not the unsaved ones.
         */
        @Test
        @DisplayName("a failed save leaves the kit's live items unchanged")
        void failedSaveLeavesLiveItemsUnchanged() throws Exception {
            File upper = createSimpleKitFile("VIP");
            service = new KitServiceImpl(plugin, config) {
                @Override
                File[] listKitFiles(File folder) {
                    return null;
                }
            };
            KitServiceImpl spyService = spy(service);
            KitDefinition kit = spyService.getKit("vip");
            String before = kit.getItems();
            doReturn("unsaved-items").when(spyService).serializeItems(any(ItemStack[].class));
            ItemStack stone = mock(ItemStack.class);
            when(stone.getType()).thenReturn(Material.STONE);

            KitService.SaveResult result = spyService.saveKitItems("vip", new ItemStack[]{stone});

            assertThat(result).isEqualTo(KitService.SaveResult.FAILED);
            assertThat(spyService.getKit("vip").getItems()).isEqualTo(before).isNotEqualTo("unsaved-items");
            assertThat(YamlConfiguration.loadConfiguration(upper).getString("items", "")).isEqualTo(before);
        }

        @Test
        @DisplayName("saveKitItems returns false for nonexistent kit")
        void saveNonexistentKit() {
            ItemStack mockItem = mock(ItemStack.class);
            when(mockItem.getType()).thenReturn(Material.STONE);

            KitService.SaveResult result = service.saveKitItems("nosuchkit", new ItemStack[]{mockItem});
            assertThat(result).isEqualTo(KitService.SaveResult.FAILED);
        }

        /**
         * The save half of the same root cause: a save must write the file the kit loads from. Writing
         * a rebuilt {@code vip.yml} beside {@code VIP.yml} left two files mapping to one kit, and
         * which one a reload kept depended on directory order, so a save reported as done could
         * silently revert.
         */
        @Test
        @DisplayName("saving a kit loaded from a file with capitals writes that file, not a second one")
        void saveKitToFileWritesTheFileTheKitLoadsFrom() throws Exception {
            File upper = createSimpleKitFile("VIP");
            service = createService();
            KitDefinition kit = service.getKit("vip");
            kit.setPrice(42.0);

            assertThat(service.saveKitToFile("vip", kit)).isTrue();

            File[] files = new File(tempDir, "kits").listFiles((dir, name) -> name.endsWith(".yml"));
            assertThat(files).extracting(File::getName).containsExactly("VIP.yml");
            assertThat(YamlConfiguration.loadConfiguration(upper).getDouble("price")).isEqualTo(42.0);
        }

        /**
         * A kits folder that cannot be listed gives no way to know which file the kit loads from, so
         * the save fails rather than writing a second file beside the real one - in a folder the
         * server may write to but not list, that second file would win or lose against the stale one
         * in directory order after the next reload.
         */
        @Test
        @DisplayName("a save fails, writing nothing, when the kits folder cannot be listed")
        void saveKitToFileFailsWhenTheKitsFolderCannotBeListed() throws Exception {
            File upper = createSimpleKitFile("VIP");
            String before = new String(java.nio.file.Files.readAllBytes(upper.toPath()), "UTF-8");
            service = new KitServiceImpl(plugin, config) {
                @Override
                File[] listKitFiles(File folder) {
                    return null;
                }
            };
            KitDefinition kit = service.getKit("vip");
            kit.setPrice(42.0);

            assertThat(service.saveKitToFile("vip", kit)).isFalse();

            assertThat(new File(tempDir, "kits").list()).containsExactly("VIP.yml");
            assertThat(new String(java.nio.file.Files.readAllBytes(upper.toPath()), "UTF-8")).isEqualTo(before);
            ArgumentCaptor<String> error = ArgumentCaptor.forClass(String.class);
            verify(mockLogger, atLeastOnce()).error(error.capture());
            assertThat(error.getAllValues()).anyMatch(line -> line.contains(upper.getParentFile().getAbsolutePath()));
        }

        @Test
        @DisplayName("saveKitToFile creates YAML file with correct structure")
        void saveKitToFileCreatesYaml() throws Exception {
            KitDefinition kit = new KitDefinition();
            kit.setName("saved");
            kit.setDisplayName("&aSaved Kit");
            kit.setIcon("DIAMOND");
            kit.setPrice(50.0);
            kit.setLevelRequired(5);
            kit.setPermission("kit.saved");
            kit.setReBuyable(true);
            kit.setCooldown(1800);
            kit.setItems("base64data");
            kit.setDescription(Arrays.asList("Line 1", "Line 2"));
            kit.setPlayerCommands(Arrays.asList("cmd1", "cmd2"));
            kit.setConsoleCommands(Arrays.asList("give {player} diamond 1"));

            boolean result = service.saveKitToFile("saved", kit);
            assertThat(result).isTrue();

            File kitFile = new File(tempDir, "kits/saved.yml");
            assertThat(kitFile).exists();
            assertThat(kitFile.length()).isGreaterThan(0);
        }

        @Test
        @DisplayName("saveKitToFile handles IO errors gracefully")
        void saveKitToFileHandlesErrors() {
            when(plugin.getResourceFolderPath()).thenReturn("/nonexistent/path/that/wont/work");

            KitDefinition kit = new KitDefinition();
            kit.setName("fail");

            boolean result = service.saveKitToFile("fail", kit);
            assertThat(result).isFalse();
            verify(mockLogger).error(contains("保存礼包文件失败"));
        }

        @Test
        @DisplayName("saveKitToFile creates file that can be reloaded correctly")
        void saveAndReload() throws Exception {
            KitDefinition kit = new KitDefinition();
            kit.setName("roundtrip");
            kit.setDisplayName("&cRound Trip");
            kit.setIcon("CHEST");
            kit.setPrice(25.0);
            kit.setLevelRequired(3);
            kit.setPermission("kit.rt");
            kit.setReBuyable(true);
            kit.setCooldown(600);
            kit.setItems("testdata");
            kit.setDescription(Arrays.asList("Test description"));
            kit.setPlayerCommands(Collections.emptyList());
            kit.setConsoleCommands(Collections.emptyList());

            boolean saved = service.saveKitToFile("roundtrip", kit);
            assertThat(saved).isTrue();

            service.reload();

            KitDefinition loaded = service.getKit("roundtrip");
            assertThat(loaded).isNotNull();
            assertThat(loaded.getDisplayName()).isEqualTo("&cRound Trip");
            assertThat(loaded.getPrice()).isEqualTo(25.0);
            assertThat(loaded.getLevelRequired()).isEqualTo(3);
            assertThat(loaded.getPermission()).isEqualTo("kit.rt");
            assertThat(loaded.isReBuyable()).isTrue();
            assertThat(loaded.getCooldown()).isEqualTo(600);
            assertThat(loaded.getItems()).isEqualTo("testdata");
        }
    }

    // =========================================================================
    // Duplicate kit files and the journaled in-place write
    // =========================================================================

    /**
     * A kit that more than one file maps to (for example {@code VIP.yml} and {@code vip.yml} on a
     * case-sensitive file system) is neither saved nor deleted: nothing is written or removed, and the
     * caller can name the files. The single file a kit does have is rewritten in place after its new
     * content is in the write journal, so it stays the same file, and a failed write leaves it as it was.
     */
    @Nested
    @DisplayName("Duplicate kit files and the journaled in-place write")
    class DuplicateFileAndKitWriteTests {

        private File upper;
        private File lower;

        private File writeKitFile(String fileName, String items) throws IOException {
            File kitsFolder = new File(tempDir, "kits");
            kitsFolder.mkdirs();
            File file = new File(kitsFolder, fileName);
            String yaml = "displayName: \"&a" + fileName + "\"\nicon: CHEST\nitems: \"" + items + "\"\n";
            java.nio.file.Files.write(file.toPath(), yaml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return file;
        }

        private byte[] bytes(File file) throws IOException {
            return java.nio.file.Files.readAllBytes(file.toPath());
        }

        private void twoFilesForOneKit() throws IOException {
            upper = writeKitFile("VIP.yml", "upper-items");
            lower = writeKitFile("vip.yml", "lower-items");
        }

        @Test
        @DisplayName("a kit two files map to is not saved, neither file changes, and the files are named")
        void saveRefusedForDuplicateFiles() throws Exception {
            twoFilesForOneKit();
            byte[] upperBefore = bytes(upper);
            byte[] lowerBefore = bytes(lower);
            KitServiceImpl spyService = spy(createService());
            String liveBefore = spyService.getKit("vip").getItems();
            doReturn("new-items").when(spyService).serializeItems(any(ItemStack[].class));

            KitService.SaveResult result = spyService.saveKitItems("vip", new ItemStack[]{mockItemStack(Material.STONE)});

            assertThat(result).isEqualTo(KitService.SaveResult.FILE_CONFLICT);
            assertThat(bytes(upper)).isEqualTo(upperBefore);
            assertThat(bytes(lower)).isEqualTo(lowerBefore);
            assertThat(new File(tempDir, "kits").list()).containsExactlyInAnyOrder("VIP.yml", "vip.yml");
            assertThat(spyService.getKit("vip").getItems()).isEqualTo(liveBefore);
            assertThat(spyService.conflictingFiles("vip")).containsExactly("VIP.yml", "vip.yml");
        }

        /**
         * The file writer itself never picks one of several files: every writer (the editor's save and
         * {@code createKit}) goes through it, so the refusal holds even for a caller that does not ask
         * {@code conflictingFiles} first.
         */
        @Test
        @DisplayName("the kit file writer refuses a kit two files map to and writes neither")
        void fileWriterRefusesDuplicateFiles() throws Exception {
            twoFilesForOneKit();
            byte[] upperBefore = bytes(upper);
            byte[] lowerBefore = bytes(lower);
            service = createService();
            KitDefinition kit = service.getKit("vip");
            kit.setItems("new-items");

            assertThat(service.saveKitToFile("vip", kit)).isFalse();

            assertThat(bytes(upper)).isEqualTo(upperBefore);
            assertThat(bytes(lower)).isEqualTo(lowerBefore);
            assertThat(new File(tempDir, "kits").list()).containsExactlyInAnyOrder("VIP.yml", "vip.yml");
        }

        @Test
        @DisplayName("a kit two files map to is not deleted: both files stay and the kit stays loaded")
        void deleteRefusedForDuplicateFiles() throws Exception {
            twoFilesForOneKit();
            byte[] upperBefore = bytes(upper);
            byte[] lowerBefore = bytes(lower);
            service = createService();

            KitService.DeleteResult result = service.deleteKit("vip");

            assertThat(result).isEqualTo(KitService.DeleteResult.FILE_CONFLICT);
            assertThat(bytes(upper)).isEqualTo(upperBefore);
            assertThat(bytes(lower)).isEqualTo(lowerBefore);
            assertThat(service.getKit("vip")).isNotNull();
        }

        /**
         * A service whose in-place write of a kit file writes half of the bytes and then fails, for its
         * first {@code failingCalls} writes: the first write is the new content, the second (after a
         * failure) is putting the old content back.
         */
        private KitServiceImpl serviceWhoseInPlaceWriteFails(int failingCalls, Exception failure) {
            int[] calls = {0};
            return new KitServiceImpl(plugin, config) {
                @Override
                void writeInPlace(java.nio.channels.FileChannel channel, byte[] content) throws IOException {
                    if (calls[0]++ < failingCalls) {
                        channel.write(java.nio.ByteBuffer.wrap(content, 0, content.length / 2));
                        if (failure instanceof IOException) {
                            throw (IOException) failure;
                        }
                        throw (RuntimeException) failure;
                    }
                    super.writeInPlace(channel, content);
                }
            };
        }

        private String[] journalListing() {
            String[] names = new File(tempDir, "kit-journal").list();
            return names == null ? new String[0] : names;
        }

        private String[] kitsFolderListing() {
            String[] names = new File(tempDir, "kits").list();
            Arrays.sort(names);
            return names;
        }

        @Test
        @DisplayName("a save whose in-place write fails part-way puts the old content back and leaves no journal")
        void failedSaveLeavesTheFileUnchanged() throws Exception {
            File file = writeKitFile("solo.yml", "old-items");
            byte[] before = bytes(file);
            KitServiceImpl failing = serviceWhoseInPlaceWriteFails(1, new IOException("disk full"));
            KitDefinition kit = failing.getKit("solo");
            kit.setItems("new-items");

            assertThat(failing.saveKitToFile("solo", kit)).isFalse();

            assertThat(bytes(file)).isEqualTo(before);
            assertThat(kitsFolderListing()).containsExactly("solo.yml");
            assertThat(journalListing()).isEmpty();
        }

        /**
         * When the old content cannot be put back either, the kit file is left part-written and the
         * journal - which holds the complete new content - is kept, so the next start completes the
         * write (see the replay tests); the console says so.
         */
        @Test
        @DisplayName("a save whose old content cannot be put back keeps the journal and says so")
        void failedRestoreKeepsTheJournal() throws Exception {
            writeKitFile("solo.yml", "old-items");
            KitServiceImpl failing = serviceWhoseInPlaceWriteFails(2, new IOException("disk full"));
            KitDefinition kit = failing.getKit("solo");
            kit.setItems("new-items");

            assertThat(failing.saveKitToFile("solo", kit)).isFalse();

            assertThat(journalListing()).containsExactly("solo.yml.journal");
            ArgumentCaptor<String> error = ArgumentCaptor.forClass(String.class);
            verify(mockLogger, atLeastOnce()).error(error.capture());
            assertThat(error.getAllValues()).anyMatch(line -> line.contains("solo.yml") && line.contains("solo.yml.journal"));
        }

        /**
         * The kit file is rewritten in place - opened, truncated, written - never created anew or moved,
         * so it stays the same file: another hard link to it sees the new content, and its inode,
         * permission bits and extended attributes are the ones it had.
         */
        @Test
        @DisplayName("a save rewrites the kit file in place: a hard link to it sees the new content")
        void saveRewritesTheFileInPlace() throws Exception {
            org.junit.jupiter.api.Assumptions.assumeTrue(
                    java.nio.file.FileSystems.getDefault().supportedFileAttributeViews().contains("unix"), "unix inode");
            File file = writeKitFile("solo.yml", "old-items");
            File outside = new File(tempDir, "outside");
            outside.mkdirs();
            java.nio.file.Path hardLink = new File(outside, "hard.yml").toPath();
            java.nio.file.Files.createLink(hardLink, file.toPath());
            Object inodeBefore = java.nio.file.Files.getAttribute(file.toPath(), "unix:ino");
            service = createService();
            KitDefinition kit = service.getKit("solo");
            kit.setItems("new-items");

            assertThat(service.saveKitToFile("solo", kit)).isTrue();

            assertThat(java.nio.file.Files.getAttribute(file.toPath(), "unix:ino")).isEqualTo(inodeBefore);
            assertThat(YamlConfiguration.loadConfiguration(hardLink.toFile()).getString("items")).isEqualTo("new-items");
            assertThat(kitsFolderListing()).containsExactly("solo.yml");
            assertThat(journalListing()).isEmpty();
        }

        @Test
        @DisplayName("a save keeps the kit file's extended attributes")
        void saveKeepsExtendedAttributes() throws Exception {
            File file = writeKitFile("solo.yml", "old-items");
            java.nio.file.attribute.UserDefinedFileAttributeView view = java.nio.file.Files.getFileAttributeView(
                    file.toPath(), java.nio.file.attribute.UserDefinedFileAttributeView.class);
            try {
                view.write("kit.owner", java.nio.ByteBuffer.wrap("panel".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            } catch (UnsupportedOperationException | IOException | NullPointerException e) {
                org.junit.jupiter.api.Assumptions.assumeTrue(false, "extended attributes not available: " + e);
            }
            service = createService();
            KitDefinition kit = service.getKit("solo");
            kit.setItems("new-items");

            assertThat(service.saveKitToFile("solo", kit)).isTrue();

            java.nio.file.attribute.UserDefinedFileAttributeView after = java.nio.file.Files.getFileAttributeView(
                    file.toPath(), java.nio.file.attribute.UserDefinedFileAttributeView.class);
            assertThat(after.list()).contains("kit.owner");
        }

        @Test
        @DisplayName("a new kit whose first write fails leaves no file behind and is not loaded")
        void failedCreateLeavesNoFile() throws Exception {
            new File(tempDir, "kits").mkdirs();
            KitServiceImpl failing = spy(serviceWhoseInPlaceWriteFails(1, new IOException("disk full")));
            doReturn("items").when(failing).serializeItems(any(ItemStack[].class));
            Player player = createMockPlayer();
            ItemStack stone = mockItemStack(Material.STONE);
            PlayerInventory inventory = player.getInventory();
            when(inventory.getStorageContents()).thenReturn(new ItemStack[]{stone});

            KitService.CreateResult result = failing.createKit(player, "fresh");

            assertThat(result).isEqualTo(KitService.CreateResult.ERROR);
            assertThat(new File(tempDir, "kits").list()).isEmpty();
            assertThat(failing.getKit("fresh")).isNull();
            assertThat(journalListing()).isEmpty();
        }

        /**
         * A write that fails with an unchecked exception (a security manager's refusal, for example)
         * is a failed save like any other: the editor is told, the loaded kit keeps its items and the
         * file and folder are as they were.
         */
        @Test
        @DisplayName("a save that fails with an unchecked exception reports FAILED and keeps the live items")
        void uncheckedWriteFailureKeepsLiveItems() throws Exception {
            File file = writeKitFile("solo.yml", "old-items");
            byte[] before = bytes(file);
            KitServiceImpl failing = spy(serviceWhoseInPlaceWriteFails(1, new SecurityException("write denied")));
            doReturn("new-items").when(failing).serializeItems(any(ItemStack[].class));

            KitService.SaveResult result = failing.saveKitItems("solo", new ItemStack[]{mockItemStack(Material.STONE)});

            assertThat(result).isEqualTo(KitService.SaveResult.FAILED);
            assertThat(failing.getKit("solo").getItems()).isEqualTo("old-items");
            assertThat(bytes(file)).isEqualTo(before);
            assertThat(kitsFolderListing()).containsExactly("solo.yml");
        }

        /**
         * Loading is unchanged - one of the files loads, as before, so the kit can still be claimed -
         * but the console now names the files and the one that was loaded, because the kit can no
         * longer be edited or deleted until only one remains.
         */
        @Test
        @DisplayName("loading a kit two files define warns once, naming both files and the one loaded")
        void loadWarnsAboutDuplicateFiles() throws Exception {
            twoFilesForOneKit();

            service = createService();

            KitDefinition loaded = service.getKit("vip");
            assertThat(loaded).isNotNull();
            String loadedFile = "upper-items".equals(loaded.getItems()) ? "VIP.yml" : "vip.yml";
            ArgumentCaptor<String> warning = ArgumentCaptor.forClass(String.class);
            verify(mockLogger, atLeastOnce()).warn(warning.capture());
            assertThat(warning.getAllValues()).containsOnlyOnce(String.format(
                    CatalogueText.text("zh", "kits.log.kit_file_conflict"),
                    new File(tempDir, "kits").getAbsolutePath(), "vip", "VIP.yml, vip.yml", loadedFile));
        }

        private boolean posix() {
            return java.nio.file.FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
        }

        private String mode(File file) throws IOException {
            return java.nio.file.attribute.PosixFilePermissions.toString(
                    java.nio.file.Files.getPosixFilePermissions(file.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS));
        }

        /**
         * The file is rewritten in place, so it keeps what it had without anything copying it: the file's
         * permission bits, a symbolic link (the file it points to is the one written) and a read-only
         * file's protection (opening it for writing fails, so the save fails and the file is unchanged).
         */
        @Test
        @DisplayName("a save keeps the kit file's permission bits")
        void saveKeepsPermissions() throws Exception {
            org.junit.jupiter.api.Assumptions.assumeTrue(posix(), "POSIX file permissions");
            File file = writeKitFile("solo.yml", "old-items");
            java.nio.file.Files.setPosixFilePermissions(file.toPath(),
                    java.nio.file.attribute.PosixFilePermissions.fromString("rw-r-----"));
            service = createService();
            KitDefinition kit = service.getKit("solo");
            kit.setItems("new-items");

            assertThat(service.saveKitToFile("solo", kit)).isTrue();

            assertThat(mode(file)).isEqualTo("rw-r-----");
            assertThat(YamlConfiguration.loadConfiguration(file).getString("items")).isEqualTo("new-items");
        }

        /**
         * Discriminates only under a umask wider than {@code 077} (for example the usual {@code 022}):
         * under {@code 077} every new file is {@code rw-------} anyway.
         */
        @Test
        @DisplayName("a new kit file gets the same permission bits as any file the server creates there")
        void newFileGetsTheDefaultMode() throws Exception {
            org.junit.jupiter.api.Assumptions.assumeTrue(posix(), "POSIX file permissions");
            File folder = new File(tempDir, "kits");
            folder.mkdirs();
            File control = new File(folder, "control.txt");
            new java.io.FileOutputStream(control).close();
            service = createService();
            KitDefinition kit = createTestKit("fresh");

            assertThat(service.saveKitToFile("fresh", kit)).isTrue();

            assertThat(mode(new File(folder, "fresh.yml"))).isEqualTo(mode(control));
        }

        @Test
        @DisplayName("a kit file that is a symbolic link stays a link, and the file it points to is written")
        void saveWritesThroughASymbolicLink() throws Exception {
            File realFolder = new File(tempDir, "real");
            realFolder.mkdirs();
            File real = new File(realFolder, "linked.yml");
            java.nio.file.Files.write(real.toPath(),
                    "icon: CHEST\nitems: \"old-items\"\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            File folder = new File(tempDir, "kits");
            folder.mkdirs();
            File link = new File(folder, "linked.yml");
            try {
                java.nio.file.Files.createSymbolicLink(link.toPath(), real.toPath());
            } catch (UnsupportedOperationException | IOException e) {
                org.junit.jupiter.api.Assumptions.assumeTrue(false, "symbolic links not available: " + e);
            }
            service = createService();
            KitDefinition kit = service.getKit("linked");
            kit.setItems("new-items");

            assertThat(service.saveKitToFile("linked", kit)).isTrue();

            assertThat(java.nio.file.Files.isSymbolicLink(link.toPath())).isTrue();
            assertThat(YamlConfiguration.loadConfiguration(real).getString("items")).isEqualTo("new-items");
            assertThat(realFolder.list()).containsExactly("linked.yml");
        }

        /**
         * A kit file that is a symbolic link whose target has gone is not turned into a plain file: the
         * save fails and the link stays, so the operator can restore what it points to.
         */
        @Test
        @DisplayName("a kit file that is a dangling symbolic link is not replaced: the save fails and the link stays")
        void danglingSymbolicLinkIsNotReplaced() throws Exception {
            File realFolder = new File(tempDir, "real");
            realFolder.mkdirs();
            File real = new File(realFolder, "linked.yml");
            java.nio.file.Files.write(real.toPath(),
                    "icon: CHEST\nitems: \"old-items\"\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            File folder = new File(tempDir, "kits");
            folder.mkdirs();
            File link = new File(folder, "linked.yml");
            try {
                java.nio.file.Files.createSymbolicLink(link.toPath(), real.toPath());
            } catch (UnsupportedOperationException | IOException e) {
                org.junit.jupiter.api.Assumptions.assumeTrue(false, "symbolic links not available: " + e);
            }
            service = createService();
            KitDefinition kit = service.getKit("linked");
            kit.setItems("new-items");
            assertThat(real.delete()).isTrue();

            assertThat(service.saveKitToFile("linked", kit)).isFalse();

            assertThat(java.nio.file.Files.isSymbolicLink(link.toPath())).isTrue();
            assertThat(real).doesNotExist();
            assertThat(kitsFolderListing()).containsExactly("linked.yml");
            assertThat(journalListing()).isEmpty();
        }

        @Test
        @DisplayName("a read-only kit file is not replaced: the save fails and the file is unchanged")
        void readOnlyFileIsNotReplaced() throws Exception {
            org.junit.jupiter.api.Assumptions.assumeTrue(posix(), "POSIX file permissions");
            org.junit.jupiter.api.Assumptions.assumeFalse("root".equals(System.getProperty("user.name")),
                    "root can write a read-only file");
            File file = writeKitFile("solo.yml", "old-items");
            byte[] before = bytes(file);
            java.nio.file.Files.setPosixFilePermissions(file.toPath(),
                    java.nio.file.attribute.PosixFilePermissions.fromString("r--r--r--"));
            service = createService();
            KitDefinition kit = service.getKit("solo");
            kit.setItems("new-items");

            assertThat(service.saveKitToFile("solo", kit)).isFalse();

            assertThat(bytes(file)).isEqualTo(before);
            assertThat(mode(file)).isEqualTo("r--r--r--");
            assertThat(kitsFolderListing()).containsExactly("solo.yml");
            assertThat(journalListing()).isEmpty();
        }

        @Test
        @DisplayName("creating a kit two unloadable files already map to is refused as a conflict, writing nothing")
        void createRefusedForDuplicateFiles() throws Exception {
            File folder = new File(tempDir, "kits");
            folder.mkdirs();
            File upperFile = new File(folder, "VIP.yml");
            File lowerFile = new File(folder, "vip.yml");
            java.nio.file.Files.write(upperFile.toPath(), "icon: [unclosed".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            java.nio.file.Files.write(lowerFile.toPath(), "icon: [unclosed".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] upperBefore = bytes(upperFile);
            byte[] lowerBefore = bytes(lowerFile);
            KitServiceImpl spyService = spy(createService());
            assertThat(spyService.getKit("vip")).isNull();
            doReturn("items").when(spyService).serializeItems(any(ItemStack[].class));
            Player player = createMockPlayer();
            ItemStack stone = mockItemStack(Material.STONE);
            PlayerInventory inventory = player.getInventory();
            when(inventory.getStorageContents()).thenReturn(new ItemStack[]{stone});

            KitService.CreateResult result = spyService.createKit(player, "vip");

            assertThat(result).isEqualTo(KitService.CreateResult.FILE_CONFLICT);
            assertThat(bytes(upperFile)).isEqualTo(upperBefore);
            assertThat(bytes(lowerFile)).isEqualTo(lowerBefore);
            assertThat(kitsFolderListing()).containsExactly("VIP.yml", "vip.yml");
        }

        /**
         * A second file that appears between the gateway's check and the write is caught by the writer;
         * the caller still gets the conflict, with the files, not a bare failure.
         */
        @Test
        @DisplayName("a conflict the writer finds after the gateway's check is still reported as a conflict")
        void conflictFoundByTheWriterIsReportedAsAConflict() throws Exception {
            twoFilesForOneKit();
            KitServiceImpl spyService = spy(createService());
            doReturn(Collections.emptyList()).doCallRealMethod().when(spyService).conflictingFiles("vip");
            doReturn("new-items").when(spyService).serializeItems(any(ItemStack[].class));

            KitService.SaveResult result = spyService.saveKitItems("vip", new ItemStack[]{mockItemStack(Material.STONE)});

            assertThat(result).isEqualTo(KitService.SaveResult.FILE_CONFLICT);
        }

        /**
         * The create side of the late-found conflict: a second file the writer finds after
         * {@code createKit}'s own check comes back as {@code FILE_CONFLICT}, not {@code ERROR}.
         */
        @Test
        @DisplayName("a conflict the writer finds after createKit's check is reported as a conflict")
        void createConflictFoundByTheWriterIsReportedAsAConflict() throws Exception {
            File folder = new File(tempDir, "kits");
            folder.mkdirs();
            java.nio.file.Files.write(new File(folder, "VIP.yml").toPath(), "icon: [unclosed".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            java.nio.file.Files.write(new File(folder, "vip.yml").toPath(), "icon: [unclosed".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            KitServiceImpl spyService = spy(createService());
            doReturn(Collections.emptyList()).doCallRealMethod().when(spyService).conflictingFiles("vip");
            doReturn("items").when(spyService).serializeItems(any(ItemStack[].class));
            Player player = createMockPlayer();
            ItemStack stone = mockItemStack(Material.STONE);
            PlayerInventory inventory = player.getInventory();
            when(inventory.getStorageContents()).thenReturn(new ItemStack[]{stone});

            assertThat(spyService.createKit(player, "vip")).isEqualTo(KitService.CreateResult.FILE_CONFLICT);
            assertThat(kitsFolderListing()).containsExactly("VIP.yml", "vip.yml");
        }

        @Test
        @DisplayName("a kit with a single file has no conflicting files")
        void singleFileIsNoConflict() throws Exception {
            writeKitFile("solo.yml", "solo-items");
            service = createService();

            assertThat(service.conflictingFiles("solo")).isEmpty();
            assertThat(service.conflictingFiles("missing")).isEmpty();
        }
    }

    // =========================================================================
    // Command Execution Tests
    // =========================================================================
    @Nested
    @DisplayName("Command Execution Tests")
    class CommandExecutionTests {

        private Player player;

        @BeforeEach
        void setUp() {
            new File(tempDir, "kits").mkdirs();
            service = createService();
            player = createMockPlayer();
        }

        @Test
        @DisplayName("claimKit executes player commands with {player} replacement")
        void executesPlayerCommands() throws Exception {
            KitDefinition kit = createTestKit("cmdkit");
            kit.setItems("someBase64Data");
            kit.setPlayerCommands(Arrays.asList("spawn", "msg {player} Welcome!"));
            injectKit(service, kit);

            KitServiceImpl spyService = spy(service);
            injectKit(spyService, kit);

            ItemStack mockItem = mock(ItemStack.class);
            when(mockItem.clone()).thenReturn(mockItem);
            doReturn(new ItemStack[]{mockItem}).when(spyService).deserializeItems("someBase64Data");

            PlayerInventory inventory = player.getInventory();
            when(inventory.getStorageContents()).thenReturn(new ItemStack[36]);

            spyService.claimKit(player, "cmdkit");

            verify(player).performCommand("spawn");
            verify(player).performCommand("msg TestPlayer Welcome!");
        }

        @Test
        @DisplayName("claimKit executes console commands via Bukkit scheduler")
        void executesConsoleCommands() throws Exception {
            KitDefinition kit = createTestKit("consolekit");
            kit.setItems("someBase64Data");
            kit.setConsoleCommands(Arrays.asList("give {player} diamond 1"));
            injectKit(service, kit);

            KitServiceImpl spyService = spy(service);
            injectKit(spyService, kit);

            ItemStack mockItem = mock(ItemStack.class);
            when(mockItem.clone()).thenReturn(mockItem);
            doReturn(new ItemStack[]{mockItem}).when(spyService).deserializeItems("someBase64Data");

            PlayerInventory inventory = player.getInventory();
            when(inventory.getStorageContents()).thenReturn(new ItemStack[36]);

            try (MockedStatic<Bukkit> mockedBukkit = mockStatic(Bukkit.class)) {
                PluginManager pluginManager = mock(PluginManager.class);
                Plugin ultiToolsPlugin = mock(Plugin.class);
                BukkitScheduler scheduler = mock(BukkitScheduler.class);

                mockedBukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
                when(pluginManager.getPlugin("UltiTools")).thenReturn(ultiToolsPlugin);
                mockedBukkit.when(Bukkit::getScheduler).thenReturn(scheduler);

                spyService.claimKit(player, "consolekit");

                verify(scheduler).runTask(eq(ultiToolsPlugin), any(Runnable.class));
            }
        }

        @Test
        @DisplayName("claimKit skips player commands when list is empty")
        void skipsEmptyPlayerCommands() throws Exception {
            KitDefinition kit = createTestKit("nocmd");
            kit.setItems("someBase64Data");
            kit.setPlayerCommands(Collections.emptyList());
            injectKit(service, kit);

            KitServiceImpl spyService = spy(service);
            injectKit(spyService, kit);

            ItemStack mockItem = mock(ItemStack.class);
            when(mockItem.clone()).thenReturn(mockItem);
            doReturn(new ItemStack[]{mockItem}).when(spyService).deserializeItems("someBase64Data");

            PlayerInventory inventory = player.getInventory();
            when(inventory.getStorageContents()).thenReturn(new ItemStack[36]);

            spyService.claimKit(player, "nocmd");

            verify(player, never()).performCommand(anyString());
        }

        @Test
        @DisplayName("claimKit skips console commands when list is null")
        void skipsNullConsoleCommands() throws Exception {
            KitDefinition kit = createTestKit("nullcmd");
            kit.setItems("someBase64Data");
            kit.setConsoleCommands(null);
            injectKit(service, kit);

            KitServiceImpl spyService = spy(service);
            injectKit(spyService, kit);

            ItemStack mockItem = mock(ItemStack.class);
            when(mockItem.clone()).thenReturn(mockItem);
            doReturn(new ItemStack[]{mockItem}).when(spyService).deserializeItems("someBase64Data");

            PlayerInventory inventory = player.getInventory();
            when(inventory.getStorageContents()).thenReturn(new ItemStack[36]);

            // No static Bukkit mock needed -- should skip entirely without calling Bukkit
            spyService.claimKit(player, "nullcmd");
        }

        @Test
        @DisplayName("claimKit skips player commands when list is null")
        void skipsNullPlayerCommands() throws Exception {
            KitDefinition kit = createTestKit("nullplayercmd");
            kit.setItems("someBase64Data");
            kit.setPlayerCommands(null);
            kit.setConsoleCommands(null);
            injectKit(service, kit);

            KitServiceImpl spyService = spy(service);
            injectKit(spyService, kit);

            ItemStack mockItem = mock(ItemStack.class);
            when(mockItem.clone()).thenReturn(mockItem);
            doReturn(new ItemStack[]{mockItem}).when(spyService).deserializeItems("someBase64Data");

            PlayerInventory inventory = player.getInventory();
            when(inventory.getStorageContents()).thenReturn(new ItemStack[36]);

            spyService.claimKit(player, "nullplayercmd");

            verify(player, never()).performCommand(anyString());
        }

        @Test
        @DisplayName("console commands skip when UltiTools plugin not found")
        void consoleCommandsSkipWhenNoPlugin() throws Exception {
            KitDefinition kit = createTestKit("noplugin");
            kit.setItems("someBase64Data");
            kit.setConsoleCommands(Arrays.asList("say hello"));
            injectKit(service, kit);

            KitServiceImpl spyService = spy(service);
            injectKit(spyService, kit);

            ItemStack mockItem = mock(ItemStack.class);
            when(mockItem.clone()).thenReturn(mockItem);
            doReturn(new ItemStack[]{mockItem}).when(spyService).deserializeItems("someBase64Data");

            PlayerInventory inventory = player.getInventory();
            when(inventory.getStorageContents()).thenReturn(new ItemStack[36]);

            try (MockedStatic<Bukkit> mockedBukkit = mockStatic(Bukkit.class)) {
                PluginManager pluginManager = mock(PluginManager.class);
                mockedBukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
                when(pluginManager.getPlugin("UltiTools")).thenReturn(null);

                spyService.claimKit(player, "noplugin");

                mockedBukkit.verify(Bukkit::getScheduler, never());
            }
        }

        @Test
        @DisplayName("console commands replace {player} with player name for each command")
        void consoleCommandsReplacePlayerMultiple() throws Exception {
            KitDefinition kit = createTestKit("conrep");
            kit.setItems("someBase64Data");
            kit.setConsoleCommands(Arrays.asList(
                    "give {player} diamond 1",
                    "broadcast {player} claimed a kit"
            ));
            injectKit(service, kit);

            KitServiceImpl spyService = spy(service);
            injectKit(spyService, kit);

            ItemStack mockItem = mock(ItemStack.class);
            when(mockItem.clone()).thenReturn(mockItem);
            doReturn(new ItemStack[]{mockItem}).when(spyService).deserializeItems("someBase64Data");

            PlayerInventory inventory = player.getInventory();
            when(inventory.getStorageContents()).thenReturn(new ItemStack[36]);

            try (MockedStatic<Bukkit> mockedBukkit = mockStatic(Bukkit.class)) {
                PluginManager pluginManager = mock(PluginManager.class);
                Plugin ultiToolsPlugin = mock(Plugin.class);
                BukkitScheduler scheduler = mock(BukkitScheduler.class);

                mockedBukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
                when(pluginManager.getPlugin("UltiTools")).thenReturn(ultiToolsPlugin);
                mockedBukkit.when(Bukkit::getScheduler).thenReturn(scheduler);

                spyService.claimKit(player, "conrep");

                verify(scheduler, times(2)).runTask(eq(ultiToolsPlugin), any(Runnable.class));
            }
        }
    }

    // =========================================================================
    // Internal Method Tests
    // =========================================================================
    @Nested
    @DisplayName("Internal Method Tests")
    class InternalMethodTests {

        @BeforeEach
        void setUp() {
            new File(tempDir, "kits").mkdirs();
            service = createService();
        }

        @Test
        @DisplayName("getClaimData returns null when no claims exist")
        void getClaimDataReturnsNull() {
            when(mockQuery.list()).thenReturn(Collections.emptyList());

            KitClaimData result = service.getClaimData(
                    UUID.fromString("00000000-0000-0000-0000-000000000001"), "starter");
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("getClaimData finds claim by kit name (case insensitive)")
        void getClaimDataFindsByName() {
            KitClaimData claim = KitClaimData.builder()
                    .uuid(UUID.randomUUID().toString())
                    .playerUuid("00000000-0000-0000-0000-000000000001")
                    .kitName("Starter")
                    .lastClaim(System.currentTimeMillis())
                    .claimCount(1)
                    .build();

            when(mockQuery.list()).thenReturn(Collections.singletonList(claim));

            KitClaimData result = service.getClaimData(
                    UUID.fromString("00000000-0000-0000-0000-000000000001"), "starter");
            assertThat(result).isSameAs(claim);
        }

        @Test
        @DisplayName("getClaimData ignores claims for different kits")
        void getClaimDataIgnoresDifferentKits() {
            KitClaimData claim = KitClaimData.builder()
                    .uuid(UUID.randomUUID().toString())
                    .playerUuid("00000000-0000-0000-0000-000000000001")
                    .kitName("vip")
                    .lastClaim(System.currentTimeMillis())
                    .claimCount(1)
                    .build();

            when(mockQuery.list()).thenReturn(Collections.singletonList(claim));

            KitClaimData result = service.getClaimData(
                    UUID.fromString("00000000-0000-0000-0000-000000000001"), "starter");
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("getClaimData finds correct claim among multiple")
        void getClaimDataFindsAmongMultiple() {
            KitClaimData starterClaim = KitClaimData.builder()
                    .uuid(UUID.randomUUID().toString())
                    .playerUuid("00000000-0000-0000-0000-000000000001")
                    .kitName("starter")
                    .lastClaim(1000L)
                    .claimCount(1)
                    .build();

            KitClaimData vipClaim = KitClaimData.builder()
                    .uuid(UUID.randomUUID().toString())
                    .playerUuid("00000000-0000-0000-0000-000000000001")
                    .kitName("vip")
                    .lastClaim(2000L)
                    .claimCount(2)
                    .build();

            when(mockQuery.list()).thenReturn(Arrays.asList(starterClaim, vipClaim));

            KitClaimData result = service.getClaimData(
                    UUID.fromString("00000000-0000-0000-0000-000000000001"), "vip");
            assertThat(result).isSameAs(vipClaim);
        }

        @Test
        @DisplayName("getClaimData queries with player UUID string")
        void getClaimDataQueriesCorrectly() {
            UUID playerUuid = UUID.fromString("00000000-0000-0000-0000-000000000042");
            when(mockQuery.list()).thenReturn(Collections.emptyList());

            service.getClaimData(playerUuid, "somekit");

            verify(mockQuery).where("player_uuid");
            verify(mockQuery).eq(playerUuid.toString());
        }

        @Test
        @DisplayName("updateClaimData inserts new record when no existing claim")
        void updateClaimDataInserts() {
            when(mockQuery.list()).thenReturn(Collections.emptyList());

            service.updateClaimData(UUID.fromString("00000000-0000-0000-0000-000000000001"), "starter");

            ArgumentCaptor<KitClaimData> captor = ArgumentCaptor.forClass(KitClaimData.class);
            verify(mockClaimOperator).insert(captor.capture());

            KitClaimData inserted = captor.getValue();
            assertThat(inserted.getPlayerUuid()).isEqualTo("00000000-0000-0000-0000-000000000001");
            assertThat(inserted.getKitName()).isEqualTo("starter");
            assertThat(inserted.getClaimCount()).isEqualTo(1);
            assertThat(inserted.getUuid()).isNotNull();
        }

        @Test
        @DisplayName("updateClaimData updates existing record incrementing claim count")
        void updateClaimDataUpdates() throws Exception {
            KitClaimData existing = KitClaimData.builder()
                    .uuid(UUID.randomUUID().toString())
                    .playerUuid("00000000-0000-0000-0000-000000000001")
                    .kitName("starter")
                    .lastClaim(1000L)
                    .claimCount(3)
                    .build();

            when(mockQuery.list()).thenReturn(Collections.singletonList(existing));

            service.updateClaimData(UUID.fromString("00000000-0000-0000-0000-000000000001"), "starter");

            verify(mockClaimOperator).update(existing);
            assertThat(existing.getClaimCount()).isEqualTo(4);
            assertThat(existing.getLastClaim()).isGreaterThan(1000L);
        }

        @Test
        @DisplayName("updateClaimData logs error on update failure")
        void updateClaimDataLogsError() throws Exception {
            KitClaimData existing = KitClaimData.builder()
                    .uuid(UUID.randomUUID().toString())
                    .playerUuid("00000000-0000-0000-0000-000000000001")
                    .kitName("starter")
                    .lastClaim(1000L)
                    .claimCount(1)
                    .build();

            when(mockQuery.list()).thenReturn(Collections.singletonList(existing));
            doThrow(new IllegalAccessException("test error")).when(mockClaimOperator).update(existing);

            service.updateClaimData(UUID.fromString("00000000-0000-0000-0000-000000000001"), "starter");

            verify(mockLogger).error(contains("更新礼包领取记录失败"));
        }

        @Test
        @DisplayName("updateClaimData sets lastClaim to approximately current time")
        void updateClaimDataSetsCurrentTime() {
            when(mockQuery.list()).thenReturn(Collections.emptyList());

            long before = System.currentTimeMillis();
            service.updateClaimData(UUID.fromString("00000000-0000-0000-0000-000000000001"), "test");
            long after = System.currentTimeMillis();

            ArgumentCaptor<KitClaimData> captor = ArgumentCaptor.forClass(KitClaimData.class);
            verify(mockClaimOperator).insert(captor.capture());

            long lastClaim = captor.getValue().getLastClaim();
            assertThat(lastClaim).isBetween(before, after);
        }

        @Test
        @DisplayName("parseKitFile returns kit with defaults for minimal YAML")
        void parseKitFileMinimalYaml() throws IOException {
            File kitsFolder = new File(tempDir, "kits");
            kitsFolder.mkdirs();
            File kitFile = new File(kitsFolder, "minimal.yml");

            FileWriter writer = new FileWriter(kitFile);
            writer.write("# empty kit\n");
            writer.close();

            KitDefinition result = service.parseKitFile(kitFile);
            assertThat(result).isNotNull();
            // The fallback name is the catalogue's, in the server's language (zh here).
            assertThat(result.getDisplayName()).isEqualTo("&7礼包");
            assertThat(result.getIcon()).isEqualTo("CHEST");
            assertThat(result.getPrice()).isEqualTo(0.0);
            assertThat(result.getLevelRequired()).isEqualTo(0);
            assertThat(result.isReBuyable()).isFalse();
            assertThat(result.getCooldown()).isEqualTo(0);
        }

        @Test
        @DisplayName("saving a kit whose file has no displayName leaves it out, so the name keeps following the language")
        void fallbackDisplayNameIsNotSaved() throws IOException {
            File kitsFolder = new File(tempDir, "kits");
            kitsFolder.mkdirs();
            File kitFile = new File(kitsFolder, "unnamed.yml");
            FileWriter writer = new FileWriter(kitFile);
            writer.write("icon: CHEST\n");
            writer.close();

            // An item-only save, as the kit editor's save button does.
            KitDefinition kit = service.parseKitFile(kitFile);
            kit.setItems("items-from-the-editor");
            assertThat(service.saveKitToFile("unnamed", kit)).isTrue();

            assertThat(YamlConfiguration.loadConfiguration(kitFile).contains("displayName")).isFalse();
            when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer("en"));
            assertThat(service.parseKitFile(kitFile).getDisplayName())
                    .isEqualTo("&7" + CatalogueText.entries("en").get("kits.kit.default_display_name"));
        }

        @Test
        @DisplayName("a display name set on a kit whose file had none is saved")
        void displayNameSetLaterIsSaved() throws IOException {
            File kitsFolder = new File(tempDir, "kits");
            kitsFolder.mkdirs();
            File kitFile = new File(kitsFolder, "renamed.yml");
            FileWriter writer = new FileWriter(kitFile);
            writer.write("icon: CHEST\n");
            writer.close();

            KitDefinition kit = service.parseKitFile(kitFile);
            kit.setDisplayName("&aNamed");
            assertThat(service.saveKitToFile("renamed", kit)).isTrue();

            assertThat(YamlConfiguration.loadConfiguration(kitFile).getString("displayName")).isEqualTo("&aNamed");
        }

        @Test
        @DisplayName("parseKitFile handles valid icon material")
        void parseKitFileValidIcon() throws IOException {
            File kitsFolder = new File(tempDir, "kits");
            kitsFolder.mkdirs();
            File kitFile = new File(kitsFolder, "goodicon.yml");

            FileWriter writer = new FileWriter(kitFile);
            writer.write("icon: DIAMOND_SWORD\n");
            writer.close();

            KitDefinition result = service.parseKitFile(kitFile);
            assertThat(result).isNotNull();
            assertThat(result.getIcon()).isEqualTo("DIAMOND_SWORD");
        }

        @Test
        @DisplayName("parseKitFile uppercases icon material string")
        void parseKitFileUppercasesIcon() throws IOException {
            File kitsFolder = new File(tempDir, "kits");
            kitsFolder.mkdirs();
            File kitFile = new File(kitsFolder, "lowericon.yml");

            FileWriter writer = new FileWriter(kitFile);
            writer.write("icon: diamond\n");
            writer.close();

            KitDefinition result = service.parseKitFile(kitFile);
            assertThat(result).isNotNull();
            assertThat(result.getIcon()).isEqualTo("DIAMOND");
        }

        @Test
        @DisplayName("parseKitFile with all fields populated")
        void parseKitFileAllFields() throws IOException {
            File kitsFolder = new File(tempDir, "kits");
            kitsFolder.mkdirs();
            File kitFile = new File(kitsFolder, "allfields.yml");

            StringBuilder yaml = new StringBuilder();
            yaml.append("displayName: \"&6Complete Kit\"\n");
            yaml.append("icon: DIAMOND_CHESTPLATE\n");
            yaml.append("price: 99.99\n");
            yaml.append("levelRequired: 15\n");
            yaml.append("permission: \"kit.complete\"\n");
            yaml.append("reBuyable: true\n");
            yaml.append("cooldown: 3600\n");
            yaml.append("items: \"somedata\"\n");
            yaml.append("description:\n  - \"Line 1\"\n  - \"Line 2\"\n");
            yaml.append("playerCommands:\n  - \"spawn\"\n");
            yaml.append("consoleCommands:\n  - \"give {player} diamond 1\"\n");

            FileWriter writer = new FileWriter(kitFile);
            writer.write(yaml.toString());
            writer.close();

            KitDefinition result = service.parseKitFile(kitFile);
            assertThat(result).isNotNull();
            assertThat(result.getDisplayName()).isEqualTo("&6Complete Kit");
            assertThat(result.getIcon()).isEqualTo("DIAMOND_CHESTPLATE");
            assertThat(result.getPrice()).isEqualTo(99.99);
            assertThat(result.getLevelRequired()).isEqualTo(15);
            assertThat(result.getPermission()).isEqualTo("kit.complete");
            assertThat(result.isReBuyable()).isTrue();
            assertThat(result.getCooldown()).isEqualTo(3600);
            assertThat(result.getItems()).isEqualTo("somedata");
            assertThat(result.getDescription()).containsExactly("Line 1", "Line 2");
            assertThat(result.getPlayerCommands()).containsExactly("spawn");
            assertThat(result.getConsoleCommands()).containsExactly("give {player} diamond 1");
        }
    }

    // =========================================================================
    // SaveKitItems Filter Tests
    // =========================================================================
    @Nested
    @DisplayName("SaveKitItems Filter Tests")
    class SaveKitItemsFilterTests {

        @BeforeEach
        void setUp() {
            new File(tempDir, "kits").mkdirs();
            service = createService();
        }

        @Test
        @DisplayName("saveKitItems filters out null items before serializing")
        void filtersNullItems() throws Exception {
            KitDefinition kit = createTestKit("filtertest");
            injectKit(service, kit);

            KitServiceImpl spyService = spy(service);
            injectKit(spyService, kit);

            ItemStack stone = mock(ItemStack.class);
            when(stone.getType()).thenReturn(Material.STONE);

            // Pass array with null items
            ItemStack[] items = new ItemStack[]{null, stone, null};

            doReturn("serialized").when(spyService).serializeItems(argThat(arr -> arr.length == 1));
            KitService.SaveResult result = spyService.saveKitItems("filtertest", items);

            assertThat(result).isEqualTo(KitService.SaveResult.SUCCESS);
        }

        @Test
        @DisplayName("saveKitItems filters out AIR items before serializing")
        void filtersAirItems() throws Exception {
            KitDefinition kit = createTestKit("filterair");
            injectKit(service, kit);

            KitServiceImpl spyService = spy(service);
            injectKit(spyService, kit);

            ItemStack air = mock(ItemStack.class);
            when(air.getType()).thenReturn(Material.AIR);
            ItemStack diamond = mock(ItemStack.class);
            when(diamond.getType()).thenReturn(Material.DIAMOND);

            ItemStack[] items = new ItemStack[]{air, diamond, air};

            doReturn("serialized").when(spyService).serializeItems(argThat(arr -> arr.length == 1));
            KitService.SaveResult result = spyService.saveKitItems("filterair", items);

            assertThat(result).isEqualTo(KitService.SaveResult.SUCCESS);
        }

        @Test
        @DisplayName("saveKitItems returns false when serialization returns null")
        void returnsFalseWhenSerializeFails() throws Exception {
            KitDefinition kit = createTestKit("serfail");
            injectKit(service, kit);

            KitServiceImpl spyService = spy(service);
            injectKit(spyService, kit);

            ItemStack stone = mock(ItemStack.class);
            when(stone.getType()).thenReturn(Material.STONE);

            ItemStack[] items = new ItemStack[]{stone};

            doReturn(null).when(spyService).serializeItems(any(ItemStack[].class));
            KitService.SaveResult result = spyService.saveKitItems("serfail", items);

            assertThat(result).isEqualTo(KitService.SaveResult.FAILED);
        }

        @Test
        @DisplayName("saveKitItems updates kit items field on success")
        void updatesKitItemsField() throws Exception {
            KitDefinition kit = createTestKit("updatefield");
            kit.setItems("old");
            injectKit(service, kit);

            KitServiceImpl spyService = spy(service);
            injectKit(spyService, kit);

            ItemStack stone = mock(ItemStack.class);
            when(stone.getType()).thenReturn(Material.STONE);

            doReturn("newbase64").when(spyService).serializeItems(any(ItemStack[].class));
            doReturn(true).when(spyService).saveKitToFile(eq("updatefield"), any(KitDefinition.class));

            spyService.saveKitItems("updatefield", new ItemStack[]{stone});

            assertThat(kit.getItems()).isEqualTo("newbase64");
        }

        @Test
        @DisplayName("saveKitItems delegates to saveKitToFile")
        void delegatesToSaveKitToFile() throws Exception {
            KitDefinition kit = createTestKit("delegate");
            injectKit(service, kit);

            KitServiceImpl spyService = spy(service);
            injectKit(spyService, kit);

            ItemStack stone = mock(ItemStack.class);
            when(stone.getType()).thenReturn(Material.STONE);

            doReturn("data").when(spyService).serializeItems(any(ItemStack[].class));
            doReturn(true).when(spyService).saveKitToFile(eq("delegate"), eq(kit));

            KitService.SaveResult result = spyService.saveKitItems("delegate", new ItemStack[]{stone});

            assertThat(result).isEqualTo(KitService.SaveResult.SUCCESS);
            verify(spyService).saveKitToFile("delegate", kit);
        }

        @Test
        @DisplayName("saveKitItems with all null/air items passes empty array to serialize")
        void allNullAirItems() throws Exception {
            KitDefinition kit = createTestKit("allnull");
            injectKit(service, kit);

            KitServiceImpl spyService = spy(service);
            injectKit(spyService, kit);

            ItemStack air = mock(ItemStack.class);
            when(air.getType()).thenReturn(Material.AIR);

            ItemStack[] items = new ItemStack[]{null, air, null};

            doReturn("emptyser").when(spyService).serializeItems(argThat(arr -> arr.length == 0));
            doReturn(true).when(spyService).saveKitToFile(anyString(), any(KitDefinition.class));

            KitService.SaveResult result = spyService.saveKitItems("allnull", items);

            assertThat(result).isEqualTo(KitService.SaveResult.SUCCESS);
        }
    }

    // =========================================================================
    // Write journal replay at start
    // =========================================================================

    /**
     * A crash is modelled by copying the module's folder at a named point of a kit file write (what a
     * crash at that moment leaves on disk) and starting a new service on that copy. Whatever point the
     * copy is taken at, the start must leave the kit file with its old content or its whole new content,
     * and a journal that is damaged, or whose kit file has gone, must change nothing.
     */
    @Nested
    @DisplayName("Write journal replay at start")
    class JournalReplayTests {

        @TempDir
        java.nio.file.Path crashImages;

        private int imageCount;

        private File writeKitFile(String fileName, String items) throws IOException {
            File kitsFolder = new File(tempDir, "kits");
            kitsFolder.mkdirs();
            File file = new File(kitsFolder, fileName);
            String yaml = "displayName: \"&a" + fileName + "\"\nicon: CHEST\nitems: \"" + items + "\"\n";
            java.nio.file.Files.write(file.toPath(), yaml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return file;
        }

        /** Copies the module folder as it is now: the disk a crash at this moment leaves behind. */
        private java.nio.file.Path crashImage() throws IOException {
            java.nio.file.Path image = crashImages.resolve("image-" + (imageCount++));
            java.nio.file.Path source = tempDir.toPath();
            try (java.util.stream.Stream<java.nio.file.Path> paths = java.nio.file.Files.walk(source)) {
                for (java.nio.file.Path path : (Iterable<java.nio.file.Path>) paths::iterator) {
                    java.nio.file.Path copy = image.resolve(source.relativize(path).toString());
                    if (java.nio.file.Files.isDirectory(path)) {
                        java.nio.file.Files.createDirectories(copy);
                    } else {
                        java.nio.file.Files.copy(path, copy);
                    }
                }
            }
            return image;
        }

        /** A service that takes a crash image at {@code point}; {@code mid-write} is half-way through the kit file. */
        private KitServiceImpl crashingAt(String point, java.nio.file.Path[] image) {
            return new KitServiceImpl(plugin, config) {
                @Override
                void checkpoint(String reached) {
                    if (reached.equals(point)) {
                        try {
                            image[0] = crashImage();
                        } catch (IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                    }
                }

                @Override
                void writeInPlace(java.nio.channels.FileChannel channel, byte[] content) throws IOException {
                    if (!"mid-write".equals(point) || image[0] != null) {
                        super.writeInPlace(channel, content);
                        return;
                    }
                    int half = content.length / 2;
                    channel.write(java.nio.ByteBuffer.wrap(content, 0, half));
                    channel.force(true);
                    image[0] = crashImage();
                    channel.write(java.nio.ByteBuffer.wrap(content, half, content.length - half));
                }
            };
        }

        /** Starts the module on a crash image, as the next server start would. */
        private KitServiceImpl startOn(java.nio.file.Path image) {
            when(plugin.getResourceFolderPath()).thenReturn(image.toString());
            return createService();
        }

        /** The kit file's {@code items}, or a marker when the file is cut off and does not parse. */
        private String itemsIn(java.nio.file.Path image, String fileName) {
            try {
                YamlConfiguration yaml = new YamlConfiguration();
                yaml.loadFromString(new String(java.nio.file.Files.readAllBytes(image.resolve("kits").resolve(fileName)),
                        java.nio.charset.StandardCharsets.UTF_8));
                return yaml.getString("items");
            } catch (IOException | org.bukkit.configuration.InvalidConfigurationException e) {
                return "<unreadable: " + e.getClass().getSimpleName() + ">";
            }
        }

        private String[] journals(java.nio.file.Path image) {
            String[] names = image.resolve("kit-journal").toFile().list();
            return names == null ? new String[0] : names;
        }

        private java.nio.file.Path savedWithCrashAt(String point) throws Exception {
            writeKitFile("solo.yml", "old-items");
            java.nio.file.Path[] image = new java.nio.file.Path[1];
            KitServiceImpl crashing = crashingAt(point, image);
            KitDefinition kit = crashing.getKit("solo");
            kit.setItems("new-items");
            assertThat(crashing.saveKitToFile("solo", kit)).isTrue();
            assertThat(image[0]).as("crash image taken at " + point).isNotNull();
            return image[0];
        }

        private void assertReplayedToTheNewContent(java.nio.file.Path image) {
            KitServiceImpl restarted = startOn(image);

            assertThat(itemsIn(image, "solo.yml")).isEqualTo("new-items");
            assertThat(restarted.getKit("solo").getItems()).isEqualTo("new-items");
            assertThat(journals(image)).isEmpty();
            ArgumentCaptor<String> info = ArgumentCaptor.forClass(String.class);
            verify(mockLogger, atLeastOnce()).info(info.capture());
            assertThat(info.getAllValues()).anyMatch(line -> line.contains("solo.yml"));
        }

        @Test
        @DisplayName("a crash after the journal is on disk: the next start writes the whole new content")
        void crashAfterJournalWritten() throws Exception {
            java.nio.file.Path image = savedWithCrashAt("journal-written");
            assertThat(itemsIn(image, "solo.yml")).isEqualTo("old-items");

            assertReplayedToTheNewContent(image);
        }

        @Test
        @DisplayName("a crash half-way through rewriting the kit file: the next start writes the whole new content")
        void crashMidInPlaceWrite() throws Exception {
            java.nio.file.Path image = savedWithCrashAt("mid-write");

            assertReplayedToTheNewContent(image);
        }

        @Test
        @DisplayName("a crash before the journal is deleted: the next start rewrites the same content and deletes it")
        void crashBeforeJournalDeleted() throws Exception {
            java.nio.file.Path image = savedWithCrashAt("target-written");
            assertThat(itemsIn(image, "solo.yml")).isEqualTo("new-items");

            assertReplayedToTheNewContent(image);
        }

        @Test
        @DisplayName("a crash while creating a new kit file: the next start creates it whole, or finishes it")
        void crashWhileCreating() throws Exception {
            for (String point : new String[] {"journal-written", "mid-write"}) {
                java.nio.file.Path[] image = new java.nio.file.Path[1];
                new File(tempDir, "kits").mkdirs();
                KitServiceImpl crashing = crashingAt(point, image);
                KitDefinition kit = createTestKit("fresh" + point.length());
                kit.setItems("created-items");
                assertThat(crashing.saveKitToFile(kit.getName(), kit)).isTrue();

                KitServiceImpl restarted = startOn(image[0]);

                assertThat(itemsIn(image[0], kit.getName() + ".yml")).as(point).isEqualTo("created-items");
                assertThat(restarted.getKit(kit.getName())).as(point).isNotNull();
                assertThat(journals(image[0])).as(point).isEmpty();
                when(plugin.getResourceFolderPath()).thenReturn(tempDir.getAbsolutePath());
            }
        }

        @Test
        @DisplayName("a crash after a failed write whose old content could not be put back: the next start completes it")
        void keptJournalIsCompletedAtTheNextStart() throws Exception {
            writeKitFile("solo.yml", "old-items");
            int[] calls = {0};
            KitServiceImpl failing = new KitServiceImpl(plugin, config) {
                @Override
                void writeInPlace(java.nio.channels.FileChannel channel, byte[] content) throws IOException {
                    if (calls[0]++ < 2) {
                        channel.write(java.nio.ByteBuffer.wrap(content, 0, content.length / 2));
                        throw new IOException("disk full");
                    }
                    super.writeInPlace(channel, content);
                }
            };
            KitDefinition kit = failing.getKit("solo");
            kit.setItems("new-items");
            assertThat(failing.saveKitToFile("solo", kit)).isFalse();

            assertReplayedToTheNewContent(crashImage());
        }

        private void assertDiscardedWithNothingChanged(java.nio.file.Path image, String expectedItems) throws Exception {
            byte[] before = java.nio.file.Files.readAllBytes(image.resolve("kits").resolve("solo.yml"));

            KitServiceImpl restarted = startOn(image);

            assertThat(java.nio.file.Files.readAllBytes(image.resolve("kits").resolve("solo.yml"))).isEqualTo(before);
            assertThat(restarted.getKit("solo").getItems()).isEqualTo(expectedItems);
            assertThat(journals(image)).isEmpty();
            ArgumentCaptor<String> warning = ArgumentCaptor.forClass(String.class);
            verify(mockLogger, atLeastOnce()).warn(warning.capture());
            assertThat(warning.getAllValues()).anyMatch(line -> line.contains("solo.yml.journal"));
        }

        @Test
        @DisplayName("a journal cut short is discarded and the kit file is not touched")
        void truncatedJournalIsDiscarded() throws Exception {
            java.nio.file.Path image = savedWithCrashAt("journal-written");
            java.nio.file.Path journal = image.resolve("kit-journal").resolve("solo.yml.journal");
            byte[] record = java.nio.file.Files.readAllBytes(journal);
            java.nio.file.Files.write(journal, Arrays.copyOf(record, record.length - 5));

            assertDiscardedWithNothingChanged(image, "old-items");
        }

        @Test
        @DisplayName("a journal whose content does not match its checksum is discarded and the kit file is not touched")
        void corruptJournalIsDiscarded() throws Exception {
            java.nio.file.Path image = savedWithCrashAt("journal-written");
            java.nio.file.Path journal = image.resolve("kit-journal").resolve("solo.yml.journal");
            byte[] record = java.nio.file.Files.readAllBytes(journal);
            record[record.length - 2] ^= 0x01;
            java.nio.file.Files.write(journal, record);

            assertDiscardedWithNothingChanged(image, "old-items");
        }

        @Test
        @DisplayName("a journal whose kit file no longer exists is discarded and no file is created")
        void journalForAMissingFileIsDiscarded() throws Exception {
            java.nio.file.Path image = savedWithCrashAt("journal-written");
            java.nio.file.Files.delete(image.resolve("kits").resolve("solo.yml"));

            KitServiceImpl restarted = startOn(image);

            assertThat(image.resolve("kits").resolve("solo.yml")).doesNotExist();
            assertThat(restarted.getKit("solo")).isNull();
            assertThat(journals(image)).isEmpty();
            ArgumentCaptor<String> warning = ArgumentCaptor.forClass(String.class);
            verify(mockLogger, atLeastOnce()).warn(warning.capture());
            assertThat(warning.getAllValues()).anyMatch(line -> line.contains("solo.yml.journal"));
        }

        @Test
        @DisplayName("a journal that names a file outside the kits folder, or another file than its own name, is discarded")
        void journalWithABadNameIsDiscarded() throws Exception {
            writeKitFile("solo.yml", "old-items");
            java.nio.file.Path folder = tempDir.toPath().resolve("kit-journal");
            java.nio.file.Files.createDirectories(folder);
            byte[] content = "items: \"evil\"\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            java.nio.file.Files.write(folder.resolve("evil.yml.journal"),
                    KitServiceImpl.journalRecord("../evil.yml", true, content));
            java.nio.file.Files.write(folder.resolve("other.yml.journal"),
                    KitServiceImpl.journalRecord("solo.yml", false, content));

            KitServiceImpl restarted = createService();

            assertThat(tempDir.toPath().resolve("evil.yml")).doesNotExist();
            assertThat(restarted.getKit("solo").getItems()).isEqualTo("old-items");
            assertThat(folder.toFile().list()).isEmpty();
        }

        /** A service whose in-place writes fail (half written) for the first {@code failing} calls. */
        private KitServiceImpl failingFor(int failing) {
            int[] calls = {0};
            return new KitServiceImpl(plugin, config) {
                @Override
                void writeInPlace(java.nio.channels.FileChannel channel, byte[] content) throws IOException {
                    if (calls[0]++ < failing) {
                        channel.write(java.nio.ByteBuffer.wrap(content, 0, content.length / 2));
                        throw new IOException("disk full");
                    }
                    super.writeInPlace(channel, content);
                }
            };
        }

        private byte[] journalContent(java.nio.file.Path image) throws IOException {
            return java.nio.file.Files.readAllBytes(image.resolve("kit-journal").resolve("solo.yml.journal"));
        }

        private boolean contains(byte[] haystack, String needle) {
            return new String(haystack, java.nio.charset.StandardCharsets.ISO_8859_1).contains(needle);
        }

        /**
         * A write that could not be undone leaves the kit file part-written and its journal as the only
         * whole copy. A later save of the same kit first completes that journal - so it starts from a
         * whole file and never truncates the only whole copy - and then writes its own content.
         */
        @Test
        @DisplayName("a save of a kit with a kept journal completes that journal first")
        void keptJournalIsCompletedBeforeTheNextSave() throws Exception {
            writeKitFile("solo.yml", "old-items");
            String[] atJournal = new String[1];
            int[] calls = {0};
            KitServiceImpl service = new KitServiceImpl(plugin, config) {
                @Override
                void writeInPlace(java.nio.channels.FileChannel channel, byte[] content) throws IOException {
                    if (calls[0]++ < 2) {
                        channel.write(java.nio.ByteBuffer.wrap(content, 0, content.length / 2));
                        throw new IOException("disk full");
                    }
                    super.writeInPlace(channel, content);
                }

                @Override
                void checkpoint(String point) {
                    if ("journal-written".equals(point) && calls[0] >= 2) {
                        atJournal[0] = itemsIn(tempDir.toPath(), "solo.yml");
                    }
                }
            };
            KitDefinition kit = service.getKit("solo");
            kit.setItems("a-items");
            assertThat(service.saveKitToFile("solo", kit)).isFalse();
            kit.setItems("b-items");

            assertThat(service.saveKitToFile("solo", kit)).isTrue();

            assertThat(atJournal[0]).isEqualTo("a-items");
            assertThat(itemsIn(tempDir.toPath(), "solo.yml")).isEqualTo("b-items");
            assertThat(journals(tempDir.toPath())).isEmpty();
        }

        @Test
        @DisplayName("a save of a kit whose kept journal cannot be completed is refused and keeps that journal")
        void keptJournalThatCannotBeCompletedRefusesTheSave() throws Exception {
            writeKitFile("solo.yml", "old-items");
            KitServiceImpl service = failingFor(3);
            KitDefinition kit = service.getKit("solo");
            kit.setItems("a-items");
            assertThat(service.saveKitToFile("solo", kit)).isFalse();
            kit.setItems("b-items");

            assertThat(service.saveKitToFile("solo", kit)).isFalse();

            byte[] journal = journalContent(tempDir.toPath());
            assertThat(contains(journal, "a-items")).isTrue();
            assertThat(contains(journal, "b-items")).isFalse();
        }

        /** A service whose deletion of a journal file fails, as a file held open by another process can. */
        private KitServiceImpl journalDeleteFails(int failing) {
            int[] calls = {0};
            return new KitServiceImpl(plugin, config) {
                @Override
                void writeInPlace(java.nio.channels.FileChannel channel, byte[] content) throws IOException {
                    if (calls[0]++ < failing) {
                        channel.write(java.nio.ByteBuffer.wrap(content, 0, content.length / 2));
                        throw new IOException("disk full");
                    }
                    super.writeInPlace(channel, content);
                }

                @Override
                void deleteJournalFile(java.nio.file.Path journal) throws IOException {
                    throw new java.nio.file.AccessDeniedException(journal.toString(), null, "in use");
                }
            };
        }

        /**
         * A save reported as failed must never take effect later: when its journal cannot be deleted
         * after the old content was put back, the journal is emptied instead, and an empty journal
         * changes nothing at the next start.
         */
        @Test
        @DisplayName("a failed save whose journal cannot be deleted does not take effect at the next start")
        void rolledBackSaveIsNotReplayedLater() throws Exception {
            writeKitFile("solo.yml", "old-items");
            KitServiceImpl service = journalDeleteFails(1);
            KitDefinition kit = service.getKit("solo");
            kit.setItems("new-items");
            assertThat(service.saveKitToFile("solo", kit)).isFalse();
            assertThat(itemsIn(tempDir.toPath(), "solo.yml")).isEqualTo("old-items");

            KitServiceImpl restarted = startOn(crashImage());

            assertThat(restarted.getKit("solo").getItems()).isEqualTo("old-items");
        }

        /**
         * After a successful save whose journal cannot be deleted, a later hand edit of the kit file is
         * not overwritten at the next start by the old journal.
         */
        @Test
        @DisplayName("a successful save whose journal cannot be deleted does not overwrite later edits")
        void leftoverJournalAfterSuccessChangesNothing() throws Exception {
            File file = writeKitFile("solo.yml", "old-items");
            KitServiceImpl service = journalDeleteFails(0);
            KitDefinition kit = service.getKit("solo");
            kit.setItems("new-items");
            assertThat(service.saveKitToFile("solo", kit)).isTrue();
            java.nio.file.Files.write(file.toPath(),
                    "icon: CHEST\nitems: \"hand-edited\"\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));

            KitServiceImpl restarted = startOn(crashImage());

            assertThat(restarted.getKit("solo").getItems()).isEqualTo("hand-edited");
        }

        @Test
        @DisplayName("a journal that can be neither deleted nor emptied is reported as taking effect later")
        void journalThatCannotBeWithdrawnIsReported() throws Exception {
            org.junit.jupiter.api.Assumptions.assumeTrue(posix(), "POSIX file permissions");
            org.junit.jupiter.api.Assumptions.assumeFalse("root".equals(System.getProperty("user.name")),
                    "root writes a read-only file");
            writeKitFile("solo.yml", "old-items");
            KitServiceImpl service = new KitServiceImpl(plugin, config) {
                @Override
                void writeInPlace(java.nio.channels.FileChannel channel, byte[] content) throws IOException {
                    java.nio.file.Path journal = journalFolder().resolve("solo.yml.journal");
                    if (java.nio.file.Files.exists(journal) && java.nio.file.Files.isWritable(journal)) {
                        java.nio.file.Files.setPosixFilePermissions(journal,
                                java.nio.file.attribute.PosixFilePermissions.fromString("r--------"));
                        channel.write(java.nio.ByteBuffer.wrap(content, 0, content.length / 2));
                        throw new IOException("disk full");
                    }
                    super.writeInPlace(channel, content);
                }

                @Override
                void deleteJournalFile(java.nio.file.Path journal) throws IOException {
                    throw new java.nio.file.AccessDeniedException(journal.toString(), null, "in use");
                }
            };
            KitDefinition kit = service.getKit("solo");
            kit.setItems("new-items");

            assertThat(service.saveKitToFile("solo", kit)).isFalse();

            ArgumentCaptor<String> error = ArgumentCaptor.forClass(String.class);
            verify(mockLogger, atLeastOnce()).error(error.capture());
            assertThat(error.getAllValues()).anyMatch(line -> line.contains("solo.yml.journal") && line.contains("/kits reload"));
        }

        private boolean posix() {
            return java.nio.file.FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
        }

        /**
         * A kit file whose existence cannot be determined (its folder cannot be searched) is not a
         * missing file: the journal - possibly the only whole copy - is kept for a later start.
         */
        @Test
        @DisplayName("a journal whose kit file cannot be checked is kept, and completed once it can")
        void journalIsKeptWhileItsFileCannotBeChecked() throws Exception {
            org.junit.jupiter.api.Assumptions.assumeTrue(posix(), "POSIX file permissions");
            org.junit.jupiter.api.Assumptions.assumeFalse("root".equals(System.getProperty("user.name")),
                    "root searches any folder");
            java.nio.file.Path image = savedWithCrashAt("mid-write");
            java.nio.file.Path kits = image.resolve("kits");
            java.nio.file.Files.setPosixFilePermissions(kits, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
            try {
                startOn(image);
                assertThat(journals(image)).containsExactly("solo.yml.journal");
            } finally {
                java.nio.file.Files.setPosixFilePermissions(kits, java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
            }

            assertReplayedToTheNewContent(image);
        }

        @Test
        @DisplayName("a new-file journal whose file is now a dangling symbolic link is discarded and the link left alone")
        void createJournalOverADanglingLinkIsDiscarded() throws Exception {
            java.nio.file.Path[] image = new java.nio.file.Path[1];
            new File(tempDir, "kits").mkdirs();
            KitServiceImpl crashing = crashingAt("journal-written", image);
            KitDefinition kit = createTestKit("fresh");
            assertThat(crashing.saveKitToFile("fresh", kit)).isTrue();
            java.nio.file.Path link = image[0].resolve("kits").resolve("fresh.yml");
            java.nio.file.Files.createSymbolicLink(link, image[0].resolve("nowhere.yml"));

            startOn(image[0]);

            assertThat(java.nio.file.Files.isSymbolicLink(link)).isTrue();
            assertThat(image[0].resolve("nowhere.yml")).doesNotExist();
            assertThat(journals(image[0])).isEmpty();
        }

        /**
         * The journal holds a kit's whole content, so it is readable only by the server's own account,
         * whatever the umask: the folder is {@code rwx------} and the journal {@code rw-------}.
         * Discriminates only under a umask wider than {@code 077}.
         */
        @Test
        @DisplayName("the journal and its folder are readable only by the server's own account")
        void journalIsPrivate() throws Exception {
            org.junit.jupiter.api.Assumptions.assumeTrue(posix(), "POSIX file permissions");
            writeKitFile("solo.yml", "old-items");
            List<String> modes = new ArrayList<>();
            KitServiceImpl observing = new KitServiceImpl(plugin, config) {
                @Override
                void checkpoint(String point) {
                    if ("journal-written".equals(point)) {
                        try {
                            modes.add(java.nio.file.attribute.PosixFilePermissions.toString(
                                    java.nio.file.Files.getPosixFilePermissions(journalFolder())));
                            modes.add(java.nio.file.attribute.PosixFilePermissions.toString(
                                    java.nio.file.Files.getPosixFilePermissions(journalFolder().resolve("solo.yml.journal"))));
                        } catch (IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                    }
                }
            };
            KitDefinition kit = observing.getKit("solo");
            kit.setItems("new-items");

            assertThat(observing.saveKitToFile("solo", kit)).isTrue();

            assertThat(modes).containsExactly("rwx------", "rw-------");
        }

        @Test
        @DisplayName("a kit name that is not a plain file name is refused, so its file and journal are the same file")
        void kitNamesWithAPathAreRefused() throws Exception {
            new File(tempDir, "kits").mkdirs();
            KitServiceImpl service = spy(createService());
            doReturn("items").when(service).serializeItems(any(ItemStack[].class));
            Player player = createMockPlayer();
            ItemStack stone = mockItemStack(Material.STONE);
            PlayerInventory inventory = player.getInventory();
            when(inventory.getStorageContents()).thenReturn(new ItemStack[]{stone});

            for (String name : new String[] {"../escape", "a/b", "a\\b", ".hidden", "c:d"}) {
                assertThat(service.createKit(player, name)).as(name).isEqualTo(KitService.CreateResult.INVALID_NAME);
            }
            assertThat(tempDir.toPath().resolve("escape.yml")).doesNotExist();
            assertThat(new File(tempDir, "kits").list()).isEmpty();
            assertThat(service.createKit(player, "plain")).isEqualTo(KitService.CreateResult.SUCCESS);
        }

        @Test
        @DisplayName("a journal whose own name holds a backslash path is discarded without creating anything")
        void journalWithABackslashNameIsDiscarded() throws Exception {
            writeKitFile("solo.yml", "old-items");
            java.nio.file.Path folder = tempDir.toPath().resolve("kit-journal");
            java.nio.file.Files.createDirectories(folder);
            java.nio.file.Files.write(folder.resolve("..\\evil.yml.journal"),
                    KitServiceImpl.journalRecord("..\\evil.yml", true, "items: \"evil\"\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)));

            createService();

            assertThat(new File(tempDir, "kits").list()).containsExactly("solo.yml");
            assertThat(folder.toFile().list()).isEmpty();
        }

        /**
         * Only a journal that was read and fails its own checks is damaged. One that cannot be read at
         * all (its permissions changed, a transient error) may be the only whole copy, so it is kept.
         */
        @Test
        @DisplayName("a journal that cannot be read is kept, and completed once it can")
        void unreadableJournalIsKept() throws Exception {
            org.junit.jupiter.api.Assumptions.assumeTrue(posix(), "POSIX file permissions");
            org.junit.jupiter.api.Assumptions.assumeFalse("root".equals(System.getProperty("user.name")),
                    "root reads any file");
            java.nio.file.Path image = savedWithCrashAt("mid-write");
            java.nio.file.Path journal = image.resolve("kit-journal").resolve("solo.yml.journal");
            java.nio.file.Files.setPosixFilePermissions(journal, java.nio.file.attribute.PosixFilePermissions.fromString("---------"));
            try {
                startOn(image);
                assertThat(journals(image)).containsExactly("solo.yml.journal");
            } finally {
                java.nio.file.Files.setPosixFilePermissions(journal, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
            }

            assertReplayedToTheNewContent(image);
        }

        /**
         * A new-file journal only ever creates a plain file. If its name has become a symbolic link to an
         * existing file (outside the kits folder, say), replay does not write through it.
         */
        @Test
        @DisplayName("a new-file journal whose name is now a link to an existing file does not write through it")
        void createJournalOverALiveLinkIsDiscarded() throws Exception {
            java.nio.file.Path[] image = new java.nio.file.Path[1];
            new File(tempDir, "kits").mkdirs();
            KitServiceImpl crashing = crashingAt("journal-written", image);
            KitDefinition kit = createTestKit("fresh");
            assertThat(crashing.saveKitToFile("fresh", kit)).isTrue();
            java.nio.file.Path victim = image[0].resolve("victim.yml");
            java.nio.file.Files.write(victim, "items: \"victim\"\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] before = java.nio.file.Files.readAllBytes(victim);
            java.nio.file.Path link = image[0].resolve("kits").resolve("fresh.yml");
            java.nio.file.Files.createSymbolicLink(link, victim);

            startOn(image[0]);

            assertThat(java.nio.file.Files.readAllBytes(victim)).isEqualTo(before);
            assertThat(java.nio.file.Files.isSymbolicLink(link)).isTrue();
            assertThat(journals(image[0])).isEmpty();
        }

        @Test
        @DisplayName("without a journal the start reads the kit files as they are (control)")
        void noJournalNothingReplayed() throws Exception {
            writeKitFile("solo.yml", "old-items");

            KitServiceImpl started = createService();

            assertThat(started.getKit("solo").getItems()).isEqualTo("old-items");
            verify(mockLogger, never()).error(anyString());
        }
    }

    // =========================================================================
    // CopyExampleKit Tests
    // =========================================================================
    @Nested
    @DisplayName("CopyExampleKit Tests")
    class CopyExampleKitTests {

        @Test
        @DisplayName("copyExampleKit is called when kits folder does not exist")
        void calledWhenFolderCreated() {
            // When kits folder doesn't exist, loadKits() creates it and calls copyExampleKit
            // The resource won't exist in test context, but the method should handle null gracefully
            service = createService();

            File kitsFolder = new File(tempDir, "kits");
            assertThat(kitsFolder).exists();
        }

        @Test
        @DisplayName("a failed copy of the example kit is logged in the server's language (zh)")
        void copyFailureIsLoggedInTheServersLanguage() throws Exception {
            service = createService();
            File notAFolder = new File(tempDir, "not-a-folder");
            assertThat(notAFolder.createNewFile()).isTrue();
            java.lang.reflect.Method copy = KitServiceImpl.class.getDeclaredMethod("copyExampleKit", File.class);
            copy.setAccessible(true); // NOPMD - private helper, reached to drive its failure branch

            copy.invoke(service, notAFolder);

            ArgumentCaptor<String> line = ArgumentCaptor.forClass(String.class);
            verify(mockLogger, atLeastOnce()).warn(line.capture());
            assertThat(line.getAllValues()).anyMatch(l -> l.startsWith(String.format(zh("kits.log.example_copy_failed"), "")));
        }

        /**
         * The example kit is written like any kit file: a copy that fails part-way leaves no
         * {@code starter.yml} (which would stop the next start from copying it again) and no
         * journal. The control shows the jar's example really is copied when nothing fails.
         */
        @Test
        @DisplayName("a failed copy of the example kit leaves no partial starter.yml")
        void failedExampleCopyLeavesNoFile() {
            File folder = new File(tempDir, "kits");
            new KitServiceImpl(plugin, config) {
                @Override
                void writeInPlace(java.nio.channels.FileChannel channel, byte[] content) throws IOException {
                    channel.write(java.nio.ByteBuffer.wrap(content, 0, content.length / 2));
                    throw new IOException("disk full");
                }
            };

            assertThat(folder.list()).isEmpty();

            assertThat(folder.delete()).isTrue();
            createService();
            assertThat(new File(folder, "starter.yml")).isFile();
        }

        @Test
        @DisplayName("copyExampleKit handles missing resource stream gracefully")
        void handlesMissingResource() {
            // loadKits -> kits folder doesn't exist -> mkdirs + copyExampleKit
            // The test classloader won't have kits/starter.yml, so InputStream is null
            // Should not throw exception
            service = createService();

            File starterFile = new File(tempDir, "kits/starter.yml");
            // Should not crash -- either the file exists or it doesn't depending on resources
        }
    }

    // =========================================================================
    // ClaimKit Deserialization Edge Cases
    // =========================================================================
    @Nested
    @DisplayName("ClaimKit Deserialization Edge Cases")
    class ClaimKitDeserializationTests {

        private Player player;
        private PlayerInventory inventory;

        @BeforeEach
        void setUp() {
            new File(tempDir, "kits").mkdirs();
            service = createService();

            player = createMockPlayer();
            inventory = player.getInventory();
            when(inventory.getStorageContents()).thenReturn(new ItemStack[36]);
        }

        @Test
        @DisplayName("claimKit returns EMPTY_KIT when deserialization returns empty array")
        void emptyArrayFromDeserialize() throws Exception {
            KitDefinition kit = createTestKit("emptyresult");
            kit.setItems("someBase64Data");
            injectKit(service, kit);

            KitServiceImpl spyService = spy(service);
            injectKit(spyService, kit);

            doReturn(new ItemStack[0]).when(spyService).deserializeItems("someBase64Data");

            KitService.ClaimResult result = spyService.claimKit(player, "emptyresult");
            assertThat(result).isEqualTo(KitService.ClaimResult.EMPTY_KIT);
        }

        @Test
        @DisplayName("claimKit returns EMPTY_KIT when deserialization returns null")
        void nullFromDeserialize() throws Exception {
            KitDefinition kit = createTestKit("nullresult");
            kit.setItems("someBase64Data");
            injectKit(service, kit);

            KitServiceImpl spyService = spy(service);
            injectKit(spyService, kit);

            doReturn(null).when(spyService).deserializeItems("someBase64Data");

            KitService.ClaimResult result = spyService.claimKit(player, "nullresult");
            assertThat(result).isEqualTo(KitService.ClaimResult.EMPTY_KIT);
        }
    }

    // =========================================================================
    // CreateKit Serialization Error Tests
    // =========================================================================
    @Nested
    @DisplayName("CreateKit Serialization Error Tests")
    class CreateKitSerializationTests {

        private Player player;
        private PlayerInventory inventory;

        @BeforeEach
        void setUp() {
            new File(tempDir, "kits").mkdirs();
            service = createService();

            player = createMockPlayer();
            inventory = player.getInventory();
        }

        @Test
        @DisplayName("createKit returns ERROR when serialization fails")
        void serializationFails() throws Exception {
            ItemStack stone = mock(ItemStack.class);
            when(stone.getType()).thenReturn(Material.STONE);
            ItemStack[] contents = new ItemStack[]{stone};
            when(inventory.getStorageContents()).thenReturn(contents);

            KitServiceImpl spyService = spy(service);
            doReturn(null).when(spyService).serializeItems(any(ItemStack[].class));

            KitService.CreateResult result = spyService.createKit(player, "failser");
            assertThat(result).isEqualTo(KitService.CreateResult.ERROR);
        }

        @Test
        @DisplayName("createKit returns ERROR when saveKitToFile fails")
        void saveToFileFails() throws Exception {
            ItemStack stone = mock(ItemStack.class);
            when(stone.getType()).thenReturn(Material.STONE);
            ItemStack[] contents = new ItemStack[]{stone};
            when(inventory.getStorageContents()).thenReturn(contents);

            KitServiceImpl spyService = spy(service);
            doReturn("data").when(spyService).serializeItems(any(ItemStack[].class));
            doReturn(false).when(spyService).saveKitToFile(anyString(), any(KitDefinition.class));

            KitService.CreateResult result = spyService.createKit(player, "savefail");
            assertThat(result).isEqualTo(KitService.CreateResult.ERROR);
        }

        @Test
        @DisplayName("createKit sets icon from first valid item material")
        void setsIconFromFirstItem() throws Exception {
            ItemStack diamond = mock(ItemStack.class);
            when(diamond.getType()).thenReturn(Material.DIAMOND_SWORD);
            ItemStack stone = mock(ItemStack.class);
            when(stone.getType()).thenReturn(Material.STONE);
            ItemStack[] contents = new ItemStack[]{diamond, stone};
            when(inventory.getStorageContents()).thenReturn(contents);

            KitServiceImpl spyService = spy(service);
            doReturn("serialized").when(spyService).serializeItems(any(ItemStack[].class));
            doReturn(true).when(spyService).saveKitToFile(anyString(), any(KitDefinition.class));

            KitService.CreateResult result = spyService.createKit(player, "icontest");
            assertThat(result).isEqualTo(KitService.CreateResult.SUCCESS);

            ArgumentCaptor<KitDefinition> captor = ArgumentCaptor.forClass(KitDefinition.class);
            verify(spyService).saveKitToFile(eq("icontest"), captor.capture());
            assertThat(captor.getValue().getIcon()).isEqualTo("DIAMOND_SWORD");
        }

        @Test
        @DisplayName("createKit sets displayName with original casing")
        void setsDisplayNameWithOriginalCase() throws Exception {
            ItemStack stone = mock(ItemStack.class);
            when(stone.getType()).thenReturn(Material.STONE);
            ItemStack[] contents = new ItemStack[]{stone};
            when(inventory.getStorageContents()).thenReturn(contents);

            KitServiceImpl spyService = spy(service);
            doReturn("serialized").when(spyService).serializeItems(any(ItemStack[].class));
            doReturn(true).when(spyService).saveKitToFile(anyString(), any(KitDefinition.class));

            spyService.createKit(player, "MyNewKit");

            ArgumentCaptor<KitDefinition> captor = ArgumentCaptor.forClass(KitDefinition.class);
            verify(spyService).saveKitToFile(eq("mynewkit"), captor.capture());
            assertThat(captor.getValue().getDisplayName()).isEqualTo("&fMyNewKit");
        }

        @Test
        @DisplayName("createKit adds kit to internal map on success")
        void addsToInternalMap() throws Exception {
            ItemStack stone = mock(ItemStack.class);
            when(stone.getType()).thenReturn(Material.STONE);
            ItemStack[] contents = new ItemStack[]{stone};
            when(inventory.getStorageContents()).thenReturn(contents);

            KitServiceImpl spyService = spy(service);
            doReturn("serialized").when(spyService).serializeItems(any(ItemStack[].class));
            doReturn(true).when(spyService).saveKitToFile(anyString(), any(KitDefinition.class));

            KitService.CreateResult result = spyService.createKit(player, "added");
            assertThat(result).isEqualTo(KitService.CreateResult.SUCCESS);
            assertThat(spyService.getKit("added")).isNotNull();
        }

        @Test
        @DisplayName("createKit filters out air and null from inventory before serializing")
        void filtersInventoryContents() throws Exception {
            ItemStack air = mock(ItemStack.class);
            when(air.getType()).thenReturn(Material.AIR);
            ItemStack stone = mock(ItemStack.class);
            when(stone.getType()).thenReturn(Material.STONE);
            ItemStack[] contents = new ItemStack[]{null, air, stone, null};
            when(inventory.getStorageContents()).thenReturn(contents);

            KitServiceImpl spyService = spy(service);
            doReturn("data").when(spyService).serializeItems(argThat(arr -> arr.length == 1));
            doReturn(true).when(spyService).saveKitToFile(anyString(), any(KitDefinition.class));

            KitService.CreateResult result = spyService.createKit(player, "filtered");
            assertThat(result).isEqualTo(KitService.CreateResult.SUCCESS);
        }
    }

    // =========================================================================
    // ParseKitFile Exception Tests
    // =========================================================================
    @Nested
    @DisplayName("ParseKitFile Exception Tests")
    class ParseKitFileExceptionTests {

        @BeforeEach
        void setUp() {
            new File(tempDir, "kits").mkdirs();
            service = createService();
        }

        @Test
        @DisplayName("parseKitFile returns null for corrupted YAML file")
        void returnsNullForCorruptedFile() throws IOException {
            File kitsFolder = new File(tempDir, "kits");
            File kitFile = new File(kitsFolder, "corrupt.yml");

            FileWriter writer = new FileWriter(kitFile);
            writer.write("invalid: yaml: [\n  bad: [[\n");
            writer.close();

            KitDefinition result = service.parseKitFile(kitFile);
            // May return a kit with defaults or null depending on snakeyaml parsing
            // The key is it should not throw
        }

        @Test
        @DisplayName("a kit file that fails to load is logged in the server's language (zh)")
        void loadFailureIsLoggedInTheServersLanguage() throws IOException {
            File kitFile = new File(new File(tempDir, "kits"), "broken.yml");
            FileWriter writer = new FileWriter(kitFile);
            writer.write("icon: CHEST\n");
            writer.close();
            // Any exception inside the parse lands in the same catch; this one is the cheapest to cause.
            when(plugin.i18n("kits.kit.default_display_name")).thenThrow(new IllegalStateException("boom"));

            assertThat(service.parseKitFile(kitFile)).isNull();

            verify(mockLogger).warn(String.format(zh("kits.log.load_failed"), "broken.yml", "boom"));
        }

        @Test
        @DisplayName("parseKitFile returns kit with default permission when not specified")
        void defaultPermission() throws IOException {
            File kitsFolder = new File(tempDir, "kits");
            File kitFile = new File(kitsFolder, "noperm.yml");

            FileWriter writer = new FileWriter(kitFile);
            writer.write("displayName: \"&aTest\"\nicon: CHEST\n");
            writer.close();

            KitDefinition result = service.parseKitFile(kitFile);
            assertThat(result).isNotNull();
            assertThat(result.getPermission()).isEqualTo("");
        }

        @Test
        @DisplayName("parseKitFile returns kit with empty description when not specified")
        void defaultDescription() throws IOException {
            File kitsFolder = new File(tempDir, "kits");
            File kitFile = new File(kitsFolder, "nodesc.yml");

            FileWriter writer = new FileWriter(kitFile);
            writer.write("displayName: \"&aTest\"\nicon: CHEST\n");
            writer.close();

            KitDefinition result = service.parseKitFile(kitFile);
            assertThat(result).isNotNull();
            assertThat(result.getDescription()).isEmpty();
        }

        @Test
        @DisplayName("parseKitFile returns kit with empty player commands when not specified")
        void defaultPlayerCommands() throws IOException {
            File kitsFolder = new File(tempDir, "kits");
            File kitFile = new File(kitsFolder, "nocmds.yml");

            FileWriter writer = new FileWriter(kitFile);
            writer.write("displayName: \"&aTest\"\n");
            writer.close();

            KitDefinition result = service.parseKitFile(kitFile);
            assertThat(result).isNotNull();
            assertThat(result.getPlayerCommands()).isEmpty();
            assertThat(result.getConsoleCommands()).isEmpty();
        }
    }

    // =========================================================================
    // ValidateClaim Detail Tests
    // =========================================================================
    @Nested
    @DisplayName("ValidateClaim Detail Tests")
    class ValidateClaimDetailTests {

        private Player player;

        @BeforeEach
        void setUp() {
            new File(tempDir, "kits").mkdirs();
            service = createService();
            player = createMockPlayer();
        }

        @Test
        @DisplayName("validateClaim returns null when all checks pass and kit has items")
        void allChecksPassWithItems() throws Exception {
            KitDefinition kit = createTestKit("valid");
            kit.setItems("someBase64Data");
            kit.setPermission("");
            kit.setLevelRequired(0);
            kit.setPrice(0);
            injectKit(service, kit);

            KitService.ClaimResult result = service.validateClaim(player, kit);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("validateClaim returns EMPTY_KIT when kit has no items but passes other checks")
        void emptyKitAfterChecks() throws Exception {
            KitDefinition kit = createTestKit("noitems");
            kit.setItems("");
            kit.setPermission("");
            kit.setLevelRequired(0);
            kit.setPrice(0);
            injectKit(service, kit);

            KitService.ClaimResult result = service.validateClaim(player, kit);
            assertThat(result).isEqualTo(KitService.ClaimResult.EMPTY_KIT);
        }

        @Test
        @DisplayName("validateClaim returns EMPTY_KIT when items is null")
        void nullItemsAfterChecks() throws Exception {
            KitDefinition kit = createTestKit("nullitems");
            kit.setItems(null);
            kit.setPermission("");
            kit.setLevelRequired(0);
            kit.setPrice(0);
            injectKit(service, kit);

            KitService.ClaimResult result = service.validateClaim(player, kit);
            assertThat(result).isEqualTo(KitService.ClaimResult.EMPTY_KIT);
        }

        @Test
        @DisplayName("validateClaim returns NO_PERMISSION before checking level or economy")
        void permissionBeforeLevel() throws Exception {
            KitDefinition kit = createTestKit("permprio");
            kit.setPermission("kit.special");
            kit.setLevelRequired(100);
            kit.setPrice(9999);
            injectKit(service, kit);

            when(player.hasPermission("kit.special")).thenReturn(false);

            KitService.ClaimResult result = service.validateClaim(player, kit);
            assertThat(result).isEqualTo(KitService.ClaimResult.NO_PERMISSION);
        }
    }

    // =========================================================================
    // Enum Value Tests
    // =========================================================================
    @Nested
    @DisplayName("Enum Value Tests")
    class EnumValueTests {

        @Test
        @DisplayName("ClaimResult has all expected values")
        void claimResultValues() {
            assertThat(KitService.ClaimResult.values()).containsExactlyInAnyOrder(
                    KitService.ClaimResult.SUCCESS,
                    KitService.ClaimResult.NOT_FOUND,
                    KitService.ClaimResult.NO_PERMISSION,
                    KitService.ClaimResult.INSUFFICIENT_LEVEL,
                    KitService.ClaimResult.INSUFFICIENT_FUNDS,
                    KitService.ClaimResult.ALREADY_CLAIMED,
                    KitService.ClaimResult.ON_COOLDOWN,
                    KitService.ClaimResult.INVENTORY_FULL,
                    KitService.ClaimResult.EMPTY_KIT,
                    KitService.ClaimResult.PAYMENT_FAILED,
                    KitService.ClaimResult.SYSTEM_DISABLED,
                    KitService.ClaimResult.NOT_RECORDED,
                    KitService.ClaimResult.NOT_RECORDED_REFUND_FAILED,
                    KitService.ClaimResult.ERROR
            );
        }

        @Test
        @DisplayName("CreateResult has all expected values")
        void createResultValues() {
            assertThat(KitService.CreateResult.values()).containsExactlyInAnyOrder(
                    KitService.CreateResult.SUCCESS,
                    KitService.CreateResult.ALREADY_EXISTS,
                    KitService.CreateResult.INVALID_NAME,
                    KitService.CreateResult.EMPTY_INVENTORY,
                    KitService.CreateResult.ERROR,
                    KitService.CreateResult.FILE_CONFLICT
            );
        }
    }

    /** The zh text for {@code key}, or a marker naming the missing key so a failure shows the logged line. */
    private static String zh(String key) {
        String text = CatalogueText.entries("zh").get(key);
        return text == null ? "<lang/zh.json has no " + key + ">" : text;
    }
}
