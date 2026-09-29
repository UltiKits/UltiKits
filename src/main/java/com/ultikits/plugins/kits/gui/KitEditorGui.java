package com.ultikits.plugins.kits.gui;

import com.ultikits.plugins.kits.model.KitDefinition;
import com.ultikits.plugins.kits.service.KitService;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import mc.obliviate.inventory.Gui;
import mc.obliviate.inventory.Icon;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * GUI for editing kit contents: slots 0-44 are a grid of real items the player moves, adds and removes
 * like a chest, and the bottom row holds the Save, info and Cancel buttons.
 * <p>
 * The GUI library cancels every click and drag in the top inventory unless {@link #onClick} /
 * {@link #onDrag} answer {@code true}, and its defaults answer {@code false}; without the two overrides
 * below no item could be moved into, out of or within the grid, and Save re-saved the items the editor
 * opened with (UltiKits/UltiKits#16). The overrides let the grid and the player's own inventory through
 * and keep the bottom row fixed.
 * <p>
 * An item the player puts into the grid leaves their inventory, as it would into a chest. It becomes part
 * of the kit when Save succeeds; when the editor closes any other way - Cancel, Esc, a refused save - the
 * items the grid holds beyond what it opened with go back to the player ({@link #onClose}), so a cancelled
 * edit costs nothing. The grid's own items are copies of the kit's, so one taken out stays with the player.
 * <p>
 * 礼包内容编辑界面：0-44 格可像箱子一样移动、放入、取出物品；未成功保存就关闭时，玩家放入的物品会退还。
 */
public class KitEditorGui extends Gui {

    private static final int SAVE_SLOT = 45;
    private static final int INFO_SLOT = 49;
    private static final int CANCEL_SLOT = 53;
    private static final int ITEM_SLOTS = 45; // Slots 0-44 for items

    private final Player player;
    private final UltiToolsPlugin plugin;
    private final KitService kitService;
    private final KitDefinition kit;
    /** Copies of the items the grid was filled with on open; what the grid holds beyond them was added. */
    private final List<ItemStack> openedWith = new ArrayList<>();
    /** Whether Save wrote the grid into the kit; a close after that gives nothing back. */
    private boolean saved;
    /** Whether the close has been handled, so a second close event cannot give items back twice. */
    private boolean settled;

    public KitEditorGui(Player player, UltiToolsPlugin plugin, KitService kitService, KitDefinition kit) {
        super(player, "kit_editor_" + kit.getName(),
                ChatColor.translateAlternateColorCodes('&', "&6&l" + ChatColor.stripColor(
                        ChatColor.translateAlternateColorCodes('&', kit.getDisplayName()))),
                6);
        this.player = player;
        this.plugin = plugin;
        this.kitService = kitService;
        this.kit = kit;
    }

    @Override
    public void onOpen(InventoryOpenEvent event) {
        // Pre-fill with existing kit items
        if (kit.hasItems()) {
            ItemStack[] existingItems = kitService.deserializeItems(kit.getItems());
            if (existingItems != null) {
                for (int i = 0; i < existingItems.length && i < ITEM_SLOTS; i++) {
                    if (existingItems[i] != null && existingItems[i].getType() != Material.AIR) {
                        // Place directly in inventory without Icon wrapper so items are moveable
                        event.getInventory().setItem(i, existingItems[i].clone());
                        openedWith.add(existingItems[i].clone());
                    }
                }
            }
        }

        // Row 6 control buttons

        // Save button (slot 45)
        ItemStack saveItem = new ItemStack(Material.EMERALD);
        ItemMeta saveMeta = saveItem.getItemMeta();
        if (saveMeta != null) {
            saveMeta.setDisplayName(ChatColor.GREEN + plugin.i18n("kits.editor.save"));
            saveMeta.setLore(Collections.singletonList(ChatColor.GRAY + plugin.i18n("kits.editor.save_hint")));
            saveItem.setItemMeta(saveMeta);
        }
        Icon saveIcon = new Icon(saveItem);
        saveIcon.onClick(e -> {
            e.setCancelled(true);
            handleSave();
        });
        addItem(SAVE_SLOT, saveIcon);

        // Info button (slot 49)
        ItemStack infoItem = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = infoItem.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName(ChatColor.GOLD + kit.getName());
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.translateAlternateColorCodes('&', kit.getDisplayName()));
            for (String desc : kit.getDescription()) {
                lore.add(ChatColor.translateAlternateColorCodes('&', desc));
            }
            infoMeta.setLore(lore);
            infoItem.setItemMeta(infoMeta);
        }
        Icon infoIcon = new Icon(infoItem);
        infoIcon.onClick(e -> e.setCancelled(true));
        addItem(INFO_SLOT, infoIcon);

        // Cancel button (slot 53)
        ItemStack cancelItem = new ItemStack(Material.BARRIER);
        ItemMeta cancelMeta = cancelItem.getItemMeta();
        if (cancelMeta != null) {
            cancelMeta.setDisplayName(ChatColor.RED + plugin.i18n("kits.editor.cancel"));
            cancelItem.setItemMeta(cancelMeta);
        }
        Icon cancelIcon = new Icon(cancelItem);
        cancelIcon.onClick(e -> {
            e.setCancelled(true);
            player.closeInventory();
        });
        addItem(CANCEL_SLOT, cancelIcon);

        // Fill remaining control row slots with glass
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        if (glassMeta != null) {
            glassMeta.setDisplayName(" ");
            glass.setItemMeta(glassMeta);
        }
        for (int i = 45; i <= 53; i++) {
            if (i != SAVE_SLOT && i != INFO_SLOT && i != CANCEL_SLOT) {
                Icon glassIcon = new Icon(glass);
                glassIcon.onClick(e -> e.setCancelled(true));
                addItem(i, glassIcon);
            }
        }
    }

    /**
     * Lets a click through in the grid and in the player's own inventory, and refuses it on the bottom
     * row. Three actions reach outside the slot they were clicked on and are refused when they would
     * leave the grid a way {@link #onClose}'s reconciliation cannot see: a shift-click from the player's
     * inventory that would stack onto a matching top-inventory item, a double-click that collects
     * matching items from the whole view onto the cursor, and Q (or Ctrl+Q) over a grid slot, which drops
     * that slot's item into the world rather than moving it anywhere {@link #onClose} still accounts for
     * -- reconciliation matches the grid, the player's inventory and the cursor (UltiKits/UltiKits#39
     * review), but a dropped item is gone from all three, and closing without saving would then leave a
     * free duplicate of a kit item lying in the world with the kit file unchanged. The first two would
     * also pull a button or pane out of place, or push the player's item into a slot that is thrown away
     * on close.
     * <p>
     * A fourth, {@code CLONE_STACK} (a Creative-mode middle-click), is refused for the grid the same way:
     * unlike the other three, it does not even remove the original from its slot, so reconciliation never
     * sees anything missing to reclaim from the free copy Bukkit places on the cursor.
     * <p>
     * All four are refused for the grid, where the GUI library this class builds on
     * ({@code mc.obliviate.inventory.InvListener}, bundled inside {@code UltiTools-API}) always honours a
     * {@code Gui#onClick} refusal, regardless of the action. Q over the PLAYER'S OWN inventory is
     * deliberately NOT refused here, even though a pre-filled item taken out of the grid first (an
     * ordinary, otherwise-allowed move) can reach the grid-drop or grid-clone outcome in two steps instead
     * of one: that library only honours a refusal for an own-inventory click when the action is
     * {@code MOVE_TO_OTHER_INVENTORY}, {@code COLLECT_TO_CURSOR} or {@code UNKNOWN} -- confirmed by
     * decompiling its shaded classes and by an actual test asserting a {@code DROP_ALL_SLOT} refusal on an
     * own-inventory slot, which the library silently does not apply; the same applies to {@code
     * CLONE_STACK} there. Closing that route needs either a second, independent listener that does not go
     * through this method, or accepting it as a residual risk on top of the same "moved on again" ceiling
     * {@link #reclaimFromPlayer} already documents; that is a decision for the maintainer, not something
     * this method can deliver on its own (UltiKits/UltiKits#39 review).
     *
     * @return {@code true} to let the click happen / 允许点击时为 {@code true}
     */
    @Override
    public boolean onClick(InventoryClickEvent event) {
        int rawSlot = event.getRawSlot();
        Inventory top = event.getView().getTopInventory();
        boolean inGrid = rawSlot >= 0 && rawSlot < ITEM_SLOTS;
        boolean inOwnInventory = rawSlot >= top.getSize();
        if (!inGrid && !inOwnInventory) {
            return false;
        }
        if (inGrid && (event.getAction() == InventoryAction.DROP_ONE_SLOT
                || event.getAction() == InventoryAction.DROP_ALL_SLOT
                || event.getAction() == InventoryAction.CLONE_STACK)) {
            return false;
        }
        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR && matchesControlRow(top, event.getCursor())) {
            return false;
        }
        return !(inOwnInventory && event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                && matchesControlRow(top, event.getCurrentItem()));
    }

    /**
     * Lets a drag through when none of its slots is on the bottom row.
     *
     * @return {@code true} to let the drag happen / 允许拖动时为 {@code true}
     */
    @Override
    public boolean onDrag(InventoryDragEvent event) {
        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= ITEM_SLOTS && rawSlot < topSize) {
                return false;
            }
        }
        return true;
    }

    /**
     * Gives the player back what they put into the grid, unless Save wrote it into the kit: every item
     * the grid holds beyond the copies it opened with, into their inventory or at their feet when it is
     * full. The library's own {@code onClose} only stops this GUI's scheduled tasks, which is done here too.
     * <p>
     * The reverse also has to be reclaimed: {@link #onClick}/{@link #onDrag} let a pre-filled item move
     * out of the grid into the player's own inventory the same as any other item in this chest-like
     * editor (removing an item from the grid is how an admin shrinks a kit, and it is meant to stay with
     * them once Save writes the smaller grid back to the kit). Cancelling never runs Save, so the kit's
     * stored items are exactly as before -- but the copy already sitting in the player's inventory from
     * having taken it out mid-edit is not; left alone, it is a free duplicate of an item the kit still
     * has (UltiKits/UltiKits#38 review of #39). Whatever of {@code openedWith} the grid no longer
     * accounts for is therefore reclaimed below, the same way an added excess is given back above, so a
     * cancelled edit costs nothing in either direction.
     * <p>
     * A picked-up item can be in exactly one of three places when this fires, because {@link #onClick}/
     * {@link #onDrag} refuse anything that would put it anywhere else: still in the grid (accounted for
     * above), in the player's own inventory (the common case, reclaimed from there below), or on the
     * cursor -- picked up but not yet placed anywhere when Esc closed the window mid-drag, which Bukkit
     * hands back to the player's inventory once this handler returns. Left unreclaimed, that third place
     * duplicates the item exactly as the second one did, so it is matched against {@code unmatched}
     * first, before anything is taken from the inventory itself.
     * <p>
     * Reclaiming runs before giving anything back, not after: a full inventory that swapped a personal
     * item into the grid for a pre-filled one has no free slot for that personal item until the kit's own
     * copy is removed from wherever it ended up. Handing the personal item back first would drop it at
     * the player's feet -- exposed to despawning or another player picking it up -- for want of the exact
     * slot the reclaim below is about to free, so every excess item the grid holds is collected into
     * {@code toReturn} and not handed over until after both reclaims have run (UltiKits/UltiKits#39
     * review).
     */
    @Override
    public void onClose(InventoryCloseEvent event) {
        stopAllTasks();
        if (saved || settled) {
            return;
        }
        settled = true;
        Inventory grid = event.getInventory();
        List<ItemStack> unmatched = new ArrayList<>();
        for (ItemStack original : openedWith) {
            unmatched.add(original.clone());
        }
        List<ItemStack> toReturn = new ArrayList<>();
        for (int i = 0; i < ITEM_SLOTS && i < grid.getSize(); i++) {
            ItemStack item = grid.getItem(i);
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            int added = item.getAmount();
            for (ItemStack original : unmatched) {
                if (added == 0) {
                    break;
                }
                if (original.getAmount() > 0 && original.isSimilar(item)) {
                    int matched = Math.min(added, original.getAmount());
                    original.setAmount(original.getAmount() - matched);
                    added -= matched;
                }
            }
            if (added > 0) {
                ItemStack returned = item.clone();
                returned.setAmount(added);
                toReturn.add(returned);
            }
        }
        reclaimFromCursor(event, unmatched);
        for (ItemStack original : unmatched) {
            if (original.getAmount() > 0) {
                reclaimFromPlayer(original);
            }
        }
        for (ItemStack returned : toReturn) {
            giveOrDrop(returned);
        }
    }

    /**
     * Matches {@code unmatched} against whatever the player is still holding on the cursor, and strips
     * out whatever matches before Bukkit hands the cursor back to the player's inventory once this
     * handler returns -- the one place a picked-up pre-filled item can be that is not the grid or the
     * player's already-settled inventory (see {@link #onClose}'s own note on the three places it can be).
     * A cursor holding something the player added themselves, or only partly a reclaimed item, keeps
     * whatever remains after the match.
     */
    private void reclaimFromCursor(InventoryCloseEvent event, List<ItemStack> unmatched) {
        ItemStack cursor = event.getView().getCursor();
        if (cursor == null || cursor.getType() == Material.AIR) {
            return;
        }
        int remaining = cursor.getAmount();
        for (ItemStack original : unmatched) {
            if (remaining == 0) {
                break;
            }
            if (original.getAmount() > 0 && original.isSimilar(cursor)) {
                int matched = Math.min(remaining, original.getAmount());
                original.setAmount(original.getAmount() - matched);
                remaining -= matched;
            }
        }
        if (remaining == cursor.getAmount()) {
            return;
        }
        if (remaining <= 0) {
            event.getView().setCursor(null);
        } else {
            ItemStack updatedCursor = cursor.clone();
            updatedCursor.setAmount(remaining);
            event.getView().setCursor(updatedCursor);
        }
    }

    /**
     * Removes {@code missing} from the player's own inventory: a pre-filled quantity that
     * {@link #reclaimFromCursor} did not already account for on the cursor, which a click or drag
     * {@link #onClick}/{@link #onDrag} let through can only have put in the player's own inventory, so
     * that is where the duplicate copy this cancelled edit must not leave behind is taken back from.
     * Silent, and does not drop or substitute anything, if the player no longer holds enough of it --
     * moved on again (dropped, deposited elsewhere, traded) before the editor closed -- since nothing
     * further can be reclaimed once an item has left the two places this method and
     * {@link #reclaimFromCursor} are able to look.
     */
    private void reclaimFromPlayer(ItemStack missing) {
        player.getInventory().removeItem(missing);
    }

    private void giveOrDrop(ItemStack item) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    /** Whether {@code item} would stack with a button or pane on the bottom row. */
    private static boolean matchesControlRow(Inventory top, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        for (int slot = ITEM_SLOTS; slot < top.getSize(); slot++) {
            ItemStack control = top.getItem(slot);
            if (control != null && control.isSimilar(item)) {
                return true;
            }
        }
        return false;
    }

    void handleSave() {
        // Collect items from slots 0-44
        List<ItemStack> collectedItems = new ArrayList<>();
        for (int i = 0; i < ITEM_SLOTS; i++) {
            ItemStack item = player.getOpenInventory().getTopInventory().getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                collectedItems.add(item);
            }
        }

        ItemStack[] itemsArray = collectedItems.toArray(new ItemStack[0]);
        KitService.SaveResult result = kitService.saveKitItems(kit.getName(), itemsArray);
        saved = result == KitService.SaveResult.SUCCESS;

        switch (result) {
            case SUCCESS:
                player.sendMessage(ChatColor.GREEN + String.format(plugin.i18n("kits.editor.saved"), kit.getName()));
                break;
            case SYSTEM_DISABLED:
                // This editor outlives the /kits edit command that opened it, so an operator can
                // switch the kit system off while it is on screen. The refusal comes from the save
                // gateway, which is where the switch is enforced; this only renders it, and it says
                // the same sentence every other surface says.
                player.sendMessage(ChatColor.RED + plugin.i18n("kits.disabled"));
                break;
            case FILE_CONFLICT:
                player.sendMessage(ChatColor.RED + String.format(plugin.i18n("kits.conflict.not_changed"),
                        kit.getName(), String.join(", ", kitService.conflictingFiles(kit.getName()))));
                break;
            default:
                player.sendMessage(ChatColor.RED + plugin.i18n("kits.editor.save_error"));
                break;
        }

        player.closeInventory();
    }
}
