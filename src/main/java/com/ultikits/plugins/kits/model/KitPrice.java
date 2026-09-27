package com.ultikits.plugins.kits.model;

import com.ultikits.ultitools.utils.EconomyUtils;

/**
 * The one way a kit's price is written for a player: through the server economy's own format when an
 * economy is available ({@code 250 Coins}, {@code $250.00}, whatever the economy uses), and as the plain
 * number when none is. The kit browser, {@code /kits list} and the insufficient-balance reply all read it,
 * so they cannot show one price two ways; the list and the reply used to print a hard-coded {@code $} and
 * the raw double (UltiKits/UltiKits#34).
 * <p>
 * 礼包价格的唯一显示方式：有经济系统时使用其格式，否则显示数字。
 */
public final class KitPrice {

    private KitPrice() {
        // utility class, no instances
    }

    /**
     * Formats a price for display.
     *
     * @param price the kit's price / 礼包价格
     * @return the price as the economy writes it, or the plain number with no economy / 格式化后的价格
     */
    public static String format(double price) {
        return EconomyUtils.isAvailable() ? EconomyUtils.format(price) : String.valueOf(price);
    }
}
