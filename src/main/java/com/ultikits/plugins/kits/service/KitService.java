package com.ultikits.plugins.kits.service;

import com.ultikits.plugins.kits.model.KitDefinition;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;

/**
 * Service for managing gift package (kit) system.
 * 管理礼包系统的服务接口。
 */
public interface KitService {

    enum ClaimResult {
        SUCCESS, NOT_FOUND, NO_PERMISSION, INSUFFICIENT_LEVEL,
        INSUFFICIENT_FUNDS, ALREADY_CLAIMED, ON_COOLDOWN,
        INVENTORY_FULL, EMPTY_KIT,
        /**
         * The kit's price could not be withdrawn even though the earlier affordability check
         * passed - the balance was spent between the two calls, or the economy rejected the
         * transaction. Distinct from {@link #INSUFFICIENT_FUNDS}, which is that earlier check
         * failing, because telling a player "insufficient balance" when the economy refused a
         * transaction they could afford would be a false statement (UltiKits/UltiKits#20).
         * 扣款失败：余额检查通过后扣款仍未成功。
         */
        PAYMENT_FAILED,
        /**
         * The kit system's master switch ({@code config.yml: enabled}) is off, so no kit may be
         * handed out. Returned by {@link #claimKit(Player, String)} itself rather than checked by
         * each caller: {@code claimKit} is the only gateway to a kit, so a guard there holds for
         * every caller that exists now and every one added later, while a guard repeated at the
         * callers holds only for the callers someone remembered (UltiKits/UltiKits#13).
         * 礼包系统总开关已关闭。该结果由 claimKit 自身返回，而不是由每个调用方各自检查。
         */
        SYSTEM_DISABLED, ERROR
    }

    enum CreateResult {
        SUCCESS, ALREADY_EXISTS, INVALID_NAME, EMPTY_INVENTORY, ERROR
    }

    /**
     * Outcome of writing a kit's item contents.
     * <p>
     * Typed rather than a boolean for the same reason {@link ClaimResult#SYSTEM_DISABLED} exists:
     * {@link #saveKitItems(String, ItemStack[])} is the only way kit contents are written, so the
     * master switch is enforced there and not at its caller - and a caller cannot tell a refused
     * save from a failed one, nor answer the player correctly, unless the result says which it was.
     * 保存礼包内容的结果。总开关在保存入口处执行，因此调用方需要知道是被拒绝还是真的失败了。
     */
    enum SaveResult {
        SUCCESS,
        /** The kit does not exist, its items could not be serialized, or the file write failed. */
        FAILED,
        /** The kit system's master switch ({@code config.yml: enabled}) is off. */
        SYSTEM_DISABLED
    }

    /**
     * Outcome of deleting a kit.
     * <p>
     * Typed rather than a boolean because a boolean folded "the kit does not exist" and "the kit's
     * file could not be removed" into one value, and the command then reported a deletion that had
     * not happened on disk: the surviving file came back on the next {@code /kits reload} or restart
     * (UltiKits/UltiKits#23).
     * 删除礼包的结果：区分「礼包不存在」和「礼包文件无法删除」。
     */
    enum DeleteResult {
        /** The kit's file is gone (or was already absent) and the kit is no longer loaded. */
        DELETED,
        /** No kit with this name is loaded. */
        NOT_FOUND,
        /**
         * The kit's file exists and could not be deleted, so the kit stays loaded - the catalogue
         * keeps matching what {@code /kits reload} would read back from disk.
         */
        FILE_NOT_DELETED
    }

    void loadKits();

    void reload();

    @Nullable
    KitDefinition getKit(String name);

    Collection<KitDefinition> getAllKits();

    List<KitDefinition> getAvailableKits(Player player);

    List<String> getKitNames();

    CreateResult createKit(Player player, String name);

    DeleteResult deleteKit(String name);

    SaveResult saveKitItems(String kitName, ItemStack[] items);

    ClaimResult claimKit(Player player, String kitName);

    long getRemainingCooldown(Player player, KitDefinition kit);

    String formatCooldown(long millis);

    String serializeItems(ItemStack[] items);

    @Nullable
    ItemStack[] deserializeItems(String data);
}
