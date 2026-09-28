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
import org.bukkit.event.inventory.InventoryCloseEvent;
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
import java.lang.reflect.Field;
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
    @SuppressWarnings("PMD.AvoidAccessibilityAlteration") // the library keeps its singleton private
    void tearDown() throws Exception {
        // InventoryAPI#unload asks UniversalScheduler for a scheduler MockBukkit does not implement;
        // unmocking the server drops the listener it registered. The library's static instance is
        // cleared by hand so it does not outlive this test's server: other test classes rely on
        // opening a GUI failing with "Inventory API is not initialized".
        Field instance = InventoryAPI.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, null);
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

        @Test
        @DisplayName("Q over a pre-filled grid slot is cancelled: it would drop the item into the world, a place reconciliation cannot reach (UltiKits/UltiKits#39 review)")
        void dropFromTheGridIsCancelled() {
            openEditor();
            assertThat(click(0, ClickType.DROP, InventoryAction.DROP_ALL_SLOT, grid().getItem(0)).isCancelled())
                    .as("Ctrl+Q over the pre-filled item").isTrue();
            assertThat(click(0, ClickType.DROP, InventoryAction.DROP_ONE_SLOT, grid().getItem(0)).isCancelled())
                    .as("Q over the pre-filled item").isTrue();
            // The kit item is still exactly where it was: neither click was applied.
            assertThat(grid().getItem(0).getType()).isEqualTo(Material.DIAMOND_SWORD);
        }

        /**
         * Not a bug in this test, and not something {@link KitEditorGui#onClick} can fix on its own:
         * {@code mc.obliviate.inventory.InvListener} (the GUI library, bundled inside {@code UltiTools-API})
         * only enforces a {@code Gui#onClick} refusal for an own-inventory click when the action is
         * {@code MOVE_TO_OTHER_INVENTORY}, {@code COLLECT_TO_CURSOR} or {@code UNKNOWN} -- confirmed by
         * decompiling its shaded classes. {@code onClick} returning {@code false} for {@code DROP_ALL_SLOT}
         * here is simply not honoured by the library for a slot in the player's own inventory, so a
         * pre-filled item taken out of the grid first (an ordinary move) and then Q-dropped from there
         * still reaches the world uncancelled. This is pinned as a known, reported gap
         * (UltiKits/UltiKits#39 review) rather than silently left unasserted.
         */
        @Test
        @DisplayName("Q over the player's own inventory is NOT cancelled by this GUI's onClick -- a library limitation, not fixed here (UltiKits/UltiKits#39 review)")
        void dropFromOwnInventoryIsNotCancelledByOnClickAloneLibraryLimitation() {
            admin.getInventory().setItem(9, new ItemStack(Material.STONE));
            openEditor();
            assertThat(click(FIRST_OWN_RAW_SLOT, ClickType.DROP, InventoryAction.DROP_ALL_SLOT,
                    new ItemStack(Material.STONE)).isCancelled())
                    .as("the GUI library does not apply onClick's refusal to a DROP_*_SLOT action on an "
                            + "own-inventory slot; only MOVE_TO_OTHER_INVENTORY, COLLECT_TO_CURSOR and "
                            + "UNKNOWN are enforced there")
                    .isFalse();
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
        @DisplayName("a click on each button is cancelled and still runs the button")
        void buttonClicksAreCancelledAndRouted() {
            openEditor();
            assertThat(click(49, ClickType.LEFT, InventoryAction.PICKUP_ALL).isCancelled()).as("info").isTrue();
            assertThat(admin.getOpenInventory().getTopInventory().getItem(49)).isNotNull();

            assertThat(click(SAVE_SLOT, ClickType.LEFT, InventoryAction.PICKUP_ALL).isCancelled()).as("save").isTrue();
            assertThat(admin.nextMessage()).contains("Saved kit: edited");
        }

        @Test
        @DisplayName("Cancel is cancelled as a click and closes the editor without a message")
        void cancelButtonClosesWithoutMessage() {
            openEditor();
            assertThat(click(CANCEL_SLOT, ClickType.LEFT, InventoryAction.PICKUP_ALL).isCancelled()).isTrue();
            assertThat(admin.nextMessage()).isNull();
            assertThat(admin.getOpenInventory().getTopInventory()).as("the editor is closed").isNull();
        }

        @Test
        @DisplayName("a click outside the window is cancelled, as before, so the cursor's item is not dropped")
        void clickOutsideIsCancelled() {
            openEditor();
            InventoryView view = admin.getOpenInventory();
            InventoryClickEvent event = new InventoryClickEvent(view, InventoryType.SlotType.OUTSIDE,
                    InventoryView.OUTSIDE, ClickType.LEFT, InventoryAction.DROP_ALL_CURSOR);
            server.getPluginManager().callEvent(event);

            assertThat(event.isCancelled()).isTrue();
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

        @Test
        @DisplayName("Cancel after taking a pre-filled kit item into the player's own inventory reclaims it: the item is not duplicated (UltiKits/UltiKits#38 review of #39)")
        void cancelAfterTakingAPreFilledItemOutDoesNotDuplicateIt() {
            openEditor();
            // A plain pickup-then-place, as onClick already lets through both in the grid and in the
            // player's own inventory: the pre-filled sword leaves the grid for the player's own inventory.
            assertThat(click(0, ClickType.LEFT, InventoryAction.PICKUP_ALL).isCancelled())
                    .as("picking up the pre-filled item").isFalse();
            assertThat(click(FIRST_OWN_RAW_SLOT, ClickType.LEFT, InventoryAction.PLACE_ALL).isCancelled())
                    .as("placing it in the player's own inventory").isFalse();
            // The server applies the two uncancelled clicks.
            grid().setItem(0, null);
            admin.getInventory().setItem(9, new ItemStack(Material.DIAMOND_SWORD));
            String before = service.getKit("edited").getItems();

            click(CANCEL_SLOT, ClickType.LEFT, InventoryAction.PICKUP_ALL);

            assertThat(admin.getInventory().contains(Material.DIAMOND_SWORD))
                    .as("the copy taken out mid-edit is reclaimed, not left with the player as a free duplicate")
                    .isFalse();
            assertThat(service.getKit("edited").getItems())
                    .as("the kit itself is unaffected by a cancelled edit -- it still has its own copy")
                    .isEqualTo(before);
        }

        @Test
        @DisplayName("Closing while a pre-filled item is still on the cursor (picked up but not yet placed) reclaims it too, not only from a settled inventory slot (UltiKits/UltiKits#39 review)")
        void closingWhileAPreFilledItemIsStillOnTheCursorDoesNotDuplicateIt() {
            openEditor();
            assertThat(click(0, ClickType.LEFT, InventoryAction.PICKUP_ALL).isCancelled())
                    .as("picking up the pre-filled item onto the cursor").isFalse();
            // The server applies the pickup: the sword leaves grid slot 0 for the cursor, and stays
            // there -- Esc (or any other non-save close) can happen mid-drag, before any placement click.
            grid().setItem(0, null);
            InventoryView view = admin.getOpenInventory();
            view.setCursor(new ItemStack(Material.DIAMOND_SWORD));
            String before = service.getKit("edited").getItems();

            // Fired directly, as click() and drag() above do, rather than through
            // PlayerMock#closeInventory(): that method unconditionally nulls the player's cursor field
            // right after the event, which is MockBukkit's own simplified close simulation (the real
            // server instead deposits it into the inventory) and would erase the very evidence this row
            // checks regardless of whether onClose reclaimed anything.
            InventoryCloseEvent event = new InventoryCloseEvent(view, InventoryCloseEvent.Reason.PLAYER);
            server.getPluginManager().callEvent(event);

            ItemStack cursorAfterClose = view.getCursor();
            boolean cursorIsEmpty = cursorAfterClose == null || cursorAfterClose.getType() == Material.AIR;
            assertThat(cursorIsEmpty)
                    .as("the copy still on the cursor when the window closed is reclaimed, not left on it "
                            + "for the real server to hand back to the player")
                    .isTrue();
            assertThat(service.getKit("edited").getItems())
                    .as("the kit itself is unaffected by a cancelled edit")
                    .isEqualTo(before);
        }

        @Test
        @DisplayName("Cancel after taking part of a pre-filled stack out reclaims only that part")
        void cancelAfterTakingPartOfAPreFilledStackOutReclaimsOnlyThatPart() {
            admin.getInventory().clear();
            admin.getInventory().setItem(0, new ItemStack(Material.ARROW, 10));
            assertThat(service.createKit(admin, "arrows")).isEqualTo(KitService.CreateResult.SUCCESS);
            admin.getInventory().clear();
            new KitEditorGui(admin, plugin, service, service.getKit("arrows")).open();

            // As if 6 of the pre-filled 10 arrows were taken into the player's own inventory, leaving 4
            // in the grid -- the server applying an uncancelled click or drag the same as elsewhere in
            // this class.
            grid().setItem(0, new ItemStack(Material.ARROW, 4));
            admin.getInventory().setItem(9, new ItemStack(Material.ARROW, 6));

            click(CANCEL_SLOT, ClickType.LEFT, InventoryAction.PICKUP_ALL);

            assertThat(admin.getInventory().all(Material.ARROW).values().stream()
                    .mapToInt(ItemStack::getAmount).sum())
                    .as("only the 6 taken out of the stack of 10 are reclaimed, none of the 4 left in the grid")
                    .isZero();
        }
    }
}
