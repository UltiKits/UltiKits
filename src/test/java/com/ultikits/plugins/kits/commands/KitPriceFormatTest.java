package com.ultikits.plugins.kits.commands;

import com.ultikits.plugins.kits.MockBukkitSupport;
import com.ultikits.plugins.kits.config.KitsConfig;
import com.ultikits.plugins.kits.i18n.CatalogueText;
import com.ultikits.plugins.kits.model.KitDefinition;
import com.ultikits.plugins.kits.service.KitService;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.utils.EconomyUtils;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.ArgumentCaptor;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A kit's price in chat is formatted by the server's economy, as the kit browser already formats it:
 * {@code /kits list} and the insufficient-balance reply printed a hard-coded {@code $} and the raw
 * double, so a server whose currency is not dollars showed two different prices (UltiKits/UltiKits#34).
 */
@DisplayName("kit prices in chat use the economy's format")
class KitPriceFormatTest {

    private UltiToolsPlugin plugin;
    private KitService kitService;
    private KitCommands commands;
    private Player player;

    @BeforeEach
    void setUp() {
        MockBukkitSupport.bootstrap();
        Economy economy = mock(Economy.class);
        when(economy.format(250.0)).thenReturn("250 Coins");
        Bukkit.getServicesManager().register(Economy.class, economy, MockBukkit.createMockPlugin("Vault"),
                ServicePriority.Normal);
        EconomyUtils.reset();
        assertThat(EconomyUtils.isAvailable()).isTrue();

        plugin = mock(UltiToolsPlugin.class);
        when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer("en"));
        kitService = mock(KitService.class);
        commands = new KitCommands(plugin, kitService, new KitsConfig("config/config.yml"));
        player = mock(Player.class);
    }

    @AfterEach
    void tearDown() {
        EconomyUtils.reset();
        MockBukkitSupport.shutdown();
    }

    private KitDefinition paidKit() {
        KitDefinition kit = new KitDefinition();
        kit.setName("vip");
        kit.setDisplayName("&6VIP Kit");
        kit.setPrice(250.0);
        return kit;
    }

    @Test
    @DisplayName("/kits list shows the economy-formatted price, not a hard-coded dollar sign")
    void listUsesTheEconomyFormat() {
        when(kitService.getAvailableKits(player)).thenReturn(Collections.singletonList(paidKit()));

        commands.onList(player);

        ArgumentCaptor<String> lines = ArgumentCaptor.forClass(String.class);
        verify(player, atLeastOnce()).sendMessage(lines.capture());
        String kitLine = lines.getAllValues().get(lines.getAllValues().size() - 1);
        assertThat(kitLine).contains("vip").contains("(250 Coins)").doesNotContain("$");
    }

    @Test
    @DisplayName("the insufficient-balance reply names the economy-formatted price")
    void insufficientBalanceUsesTheEconomyFormat() {
        when(kitService.getKit("vip")).thenReturn(paidKit());
        when(kitService.claimKit(player, "vip")).thenReturn(KitService.ClaimResult.INSUFFICIENT_FUNDS);

        commands.onClaim(player, "vip");

        ArgumentCaptor<String> line = ArgumentCaptor.forClass(String.class);
        verify(player).sendMessage(line.capture());
        assertThat(line.getValue()).isEqualTo(ChatColor.RED
                + String.format(CatalogueText.text("en", "kits.claim.insufficient_funds_price"), "250 Coins"));
    }
}
