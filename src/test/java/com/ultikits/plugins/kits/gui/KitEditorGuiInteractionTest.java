package com.ultikits.plugins.kits.gui;

import com.ultikits.plugins.kits.MockBukkitSupport;
import com.ultikits.plugins.kits.config.KitsConfig;
import com.ultikits.plugins.kits.entity.KitClaimData;
import com.ultikits.plugins.kits.i18n.CatalogueText;
import com.ultikits.plugins.kits.model.KitDefinition;
import com.ultikits.plugins.kits.service.KitService;
import com.ultikits.plugins.kits.service.KitServiceImpl;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.interfaces.DataOperator;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;
import mc.obliviate.inventory.InventoryAPI;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.DragType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The kit editor driven through the GUI library's own listener, the way a server drives it: a click or
 * drag is an event the library's {@code InvListener} passes to {@link KitEditorGui#onClick} /
 * {@link KitEditorGui#onDrag} and cancels or lets through on their answer. MockBukkit fires the event but
 * does not move items, so each test applies an uncancelled move itself, as the server would, and then
 * presses Save through the same listener (UltiKits/UltiKits#16).
 */
@DisplayName("KitEditorGui through the GUI library's listener")
@SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert") // assertions live in the shared helpers below
class KitEditorGuiInteractionTest {

    private static final int SAVE_SLOT = 45;
    private static final int CANCEL_SLOT = 53;
    private static final int GLASS_SLOT = 47;
    /** Raw slot of the player's first main-inventory slot below a six-row chest (inventory slot 9). */
    private static final int FIRST_OWN_RAW_SLOT = 54;

    @TempDir
    File tempDir;

    private ServerMock server;
    private UltiToolsPlugin plugin;
    private KitServiceImpl service;
    private PlayerMock admin;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        MockBukkitSupport.bootstrap();
        server = MockBukkit.getMock();
        new InventoryAPI(MockBukkit.createMockPlugin()).init();

        plugin = mock(UltiToolsPlugin.class);
        when(plugin.getLogger()).thenReturn(mock(PluginLogger.class));
        when(plugin.getResourceFolderPath()).thenReturn(tempDir.getAbsolutePath());
        when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer("en"));
        when(plugin.getDataOperator(KitClaimData.class)).thenReturn(mock(DataOperator.class));
        service = new KitServiceImpl(plugin, new KitsConfig("config/config.yml"));

        admin = server.addPlayer();
        admin.getInventory().setItem(0, new ItemStack(Material.DIAMOND_SWORD));
        assertThat(service.createKit(admin, "edited")).isEqualTo(KitService.CreateResult.SUCCESS);
        admin.getInventory().clear();
    }

    @AfterEach
    void tearDown() {
        // InventoryAPI#unload asks UniversalScheduler for a scheduler MockBukkit does not implement;
        // unmocking the server drops the listener it registered.
        MockBukkitSupport.shutdown();
    }

    private KitEditorGui openEditor() {
        KitDefinition kit = service.getKit("edited");
        KitEditorGui gui = new KitEditorGui(admin, plugin, service, kit);
        gui.open();
        assertThat(admin.getOpenInventory().getTopInventory().getItem(0))
                .as("the editor opened pre-filled with the kit's item")
                .isNotNull();
        return gui;
    }

    private InventoryClickEvent click(int rawSlot, ClickType type, InventoryAction action) {
        return click(rawSlot, type, action, null);
    }

    /**
     * Fires a click. {@code current}, when given, is the clicked item: MockBukkit's view maps a raw slot in
     * the player's inventory to a different slot than a server does, so a test naming the item states it.
     */
    private InventoryClickEvent click(int rawSlot, ClickType type, InventoryAction action, ItemStack current) {
        InventoryView view = admin.getOpenInventory();
        InventoryType.SlotType slotType = rawSlot < view.getTopInventory().getSize()
                ? InventoryType.SlotType.CONTAINER : InventoryType.SlotType.QUICKBAR;
        InventoryClickEvent event = new InventoryClickEvent(view, slotType, rawSlot, type, action);
        if (current != null) {
            event.setCurrentItem(current);
        }
        server.getPluginManager().callEvent(event);
        return event;
    }

    private InventoryDragEvent drag(ItemStack placed, Integer... rawSlots) {
        InventoryView view = admin.getOpenInventory();
        Map<Integer, ItemStack> slots = new HashMap<>();
        for (Integer slot : rawSlots) {
            slots.put(slot, placed.clone());
        }
        InventoryDragEvent event = new InventoryDragEvent(view, null, placed.clone(), false, slots);
        server.getPluginManager().callEvent(event);
        return event;
    }

    private Inventory grid() {
        return admin.getOpenInventory().getTopInventory();
    }

    private void pressSave() {
        click(SAVE_SLOT, ClickType.LEFT, InventoryAction.PICKUP_ALL);
    }

    private List<Material> savedMaterials() {
        ItemStack[] items = service.deserializeItems(service.getKit("edited").getItems());
        assertThat(items).isNotNull();
        return Arrays.stream(items).map(ItemStack::getType).collect(java.util.stream.Collectors.toList());
    }

    @Nested
    @DisplayName("the grid (slots 0-44) accepts the player's moves")
    class GridMoves {

        @Test
        @DisplayName("picking up an item in the grid is not cancelled, and a moved item is saved")
        void moveWithinTheGridSticksAndSaves() {
            openEditor();
            assertThat(click(0, ClickType.LEFT, InventoryAction.PICKUP_ALL).isCancelled())
                    .as("the pick-up in grid slot 0").isFalse();
            assertThat(click(20, ClickType.LEFT, InventoryAction.PLACE_ALL).isCancelled())
                    .as("the put-down in grid slot 20").isFalse();
            // The server applies the two uncancelled clicks: slot 0's item now sits in slot 20.
            ItemStack sword = grid().getItem(0);
            grid().setItem(0, null);
            grid().setItem(20, sword);

            pressSave();

            assertThat(savedMaterials()).containsExactly(Material.DIAMOND_SWORD);
            KitEditorGui reopened = openEditor();
            assertThat(reopened).isNotNull();
            assertThat(grid().getItem(0).getType()).isEqualTo(Material.DIAMOND_SWORD);
        }

        @Test
        @DisplayName("an item added from the player's own inventory is saved into the kit")
        void additionFromOwnInventorySticksAndSaves() {
            admin.getInventory().setItem(9, new ItemStack(Material.GOLDEN_APPLE, 3));
            openEditor();
            assertThat(click(FIRST_OWN_RAW_SLOT, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY,
                    new ItemStack(Material.GOLDEN_APPLE, 3)).isCancelled())
                    .as("the shift-click from the player's own inventory").isFalse();
            // The server applies it: the apples leave the player's slot 9 for the first empty grid slot.
            admin.getInventory().setItem(9, null);
            grid().setItem(1, new ItemStack(Material.GOLDEN_APPLE, 3));

            pressSave();

            assertThat(savedMaterials()).containsExactly(Material.DIAMOND_SWORD, Material.GOLDEN_APPLE);
        }

        @Test
        @DisplayName("a drag that stays inside the grid is not cancelled")
        void dragInsideTheGridIsAllowed() {
            openEditor();
            assertThat(drag(new ItemStack(Material.STONE, 2), 3, 4).isCancelled()).isFalse();
        }

        @Test
        @DisplayName("a drag that also covers the player's own inventory is not cancelled")
        void dragAcrossGridAndOwnInventoryIsAllowed() {
            openEditor();
            assertThat(drag(new ItemStack(Material.STONE, 2), 3, FIRST_OWN_RAW_SLOT + 1).isCancelled()).isFalse();
        }
    }

    @Nested
    @DisplayName("the control row (slots 45-53) stays protected")
    class ControlRow {

        @Test
        @DisplayName("a click on a control-row pane is cancelled")
        void paneClickIsCancelled() {
            openEditor();
            assertThat(click(GLASS_SLOT, ClickType.LEFT, InventoryAction.PICKUP_ALL).isCancelled()).isTrue();
        }

        @Test
        @DisplayName("a drag that touches the control row is cancelled")
        void dragIntoControlRowIsCancelled() {
            openEditor();
            assertThat(drag(new ItemStack(Material.STONE, 2), 3, GLASS_SLOT).isCancelled()).isTrue();
        }

        @Test
        @DisplayName("a shift-click that would stack onto a control-row item is cancelled")
        void shiftClickOntoAControlItemIsCancelled() {
            openEditor();
            ItemStack pane = grid().getItem(GLASS_SLOT).clone();
            admin.getInventory().setItem(9, pane);
            assertThat(click(FIRST_OWN_RAW_SLOT, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY, pane)
                    .isCancelled()).isTrue();
        }

        @Test
        @DisplayName("a double-click that would collect a control-row item onto the cursor is cancelled")
        void collectOfAControlItemIsCancelled() {
            openEditor();
            admin.getOpenInventory().setCursor(grid().getItem(GLASS_SLOT).clone());
            assertThat(click(5, ClickType.DOUBLE_CLICK, InventoryAction.COLLECT_TO_CURSOR).isCancelled()).isTrue();
        }

        @Test
        @DisplayName("a plain click in the player's own inventory is not cancelled")
        void ownInventoryClickIsAllowed() {
            admin.getInventory().setItem(9, new ItemStack(Material.STONE));
            openEditor();
            assertThat(click(FIRST_OWN_RAW_SLOT, ClickType.LEFT, InventoryAction.PICKUP_ALL).isCancelled()).isFalse();
        }
    }

    @Nested
    @DisplayName("closing without a successful save")
    class CloseWithoutSave {

        @Test
        @DisplayName("Cancel gives back what the player put into the grid, and the kit is unchanged")
        void cancelReturnsAddedItems() {
            admin.getInventory().setItem(9, new ItemStack(Material.GOLDEN_APPLE, 3));
            openEditor();
            admin.getInventory().setItem(9, null);
            grid().setItem(1, new ItemStack(Material.GOLDEN_APPLE, 3));
            String before = service.getKit("edited").getItems();

            click(CANCEL_SLOT, ClickType.LEFT, InventoryAction.PICKUP_ALL);

            assertThat(admin.getInventory().containsAtLeast(new ItemStack(Material.GOLDEN_APPLE), 3)).isTrue();
            assertThat(admin.getInventory().contains(Material.DIAMOND_SWORD))
                    .as("the kit's own item is not handed out").isFalse();
            assertThat(service.getKit("edited").getItems()).isEqualTo(before);
        }

        @Test
        @DisplayName("after a successful save nothing is given back: the items went into the kit")
        void successfulSaveReturnsNothing() {
            admin.getInventory().setItem(9, new ItemStack(Material.GOLDEN_APPLE, 3));
            openEditor();
            admin.getInventory().setItem(9, null);
            grid().setItem(1, new ItemStack(Material.GOLDEN_APPLE, 3));

            pressSave();

            assertThat(admin.getInventory().contains(Material.GOLDEN_APPLE)).isFalse();
            assertThat(savedMaterials()).containsExactly(Material.DIAMOND_SWORD, Material.GOLDEN_APPLE);
        }

        @Test
        @DisplayName("a refused save gives back what the player put into the grid")
        void refusedSaveReturnsAddedItems() throws Exception {
            admin.getInventory().setItem(9, new ItemStack(Material.GOLDEN_APPLE, 3));
            openEditor();
            admin.getInventory().setItem(9, null);
            grid().setItem(1, new ItemStack(Material.GOLDEN_APPLE, 3));
            // A second file now defines the kit, so the save gateway refuses the write.
            assertThat(new File(tempDir, "kits/EDITED.yml").createNewFile()).isTrue();

            pressSave();

            assertThat(admin.getInventory().containsAtLeast(new ItemStack(Material.GOLDEN_APPLE), 3)).isTrue();
        }

        @Test
        @DisplayName("an item moved within the grid is not given back")
        void movedKitItemIsNotReturned() {
            openEditor();
            ItemStack sword = grid().getItem(0);
            grid().setItem(0, null);
            grid().setItem(20, sword);

            admin.closeInventory();

            assertThat(admin.getInventory().contains(Material.DIAMOND_SWORD)).isFalse();
            assertThat(Collections.singletonList(savedMaterials().get(0))).containsExactly(Material.DIAMOND_SWORD);
        }
    }
}
