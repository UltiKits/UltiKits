package com.ultikits.plugins.kits.i18n;

import com.ultikits.ultitools.entities.Language;
import com.ultikits.ultitools.annotations.command.CmdExecutor;
import com.ultikits.plugins.kits.MockBukkitSupport;
import com.ultikits.plugins.kits.commands.KitCommands;
import com.ultikits.plugins.kits.config.KitsConfig;
import com.ultikits.plugins.kits.gui.KitEditorGui;
import com.ultikits.plugins.kits.model.KitDefinition;
import com.ultikits.plugins.kits.service.KitService;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Text that used to be fixed English now follows the framework's {@code language} setting.
 * <p>
 * {@code i18n} answers from the real shipped {@code zh} catalogue ({@link CatalogueText}). Before the
 * language sweep the help lines and the editor's save hint were English literals, so these tests fail
 * by showing the English text that was actually produced.
 */
@DisplayName("Kit help and editor text follow the language setting")
class KitsLanguageTest {

    private UltiToolsPlugin plugin;
    private KitService kitService;

    @BeforeEach
    void setUp() {
        MockBukkitSupport.bootstrap();
        plugin = mock(UltiToolsPlugin.class);
        kitService = mock(KitService.class);
        lenient().when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer("zh"));
    }

    @AfterEach
    void tearDown() {
        MockBukkitSupport.shutdown();
    }

    /** The zh text for {@code key}, or a marker naming the missing key so the failure shows what was sent. */
    private static String zh(String key) {
        String text = CatalogueText.entries("zh").get(key);
        return text == null ? "<lang/zh.json has no " + key + ">" : text;
    }

    private static String line(String command, String key) {
        return ChatColor.YELLOW + command + ChatColor.GRAY + " - " + zh(key);
    }

    @Test
    @DisplayName("/kits help: every line is in the server's language, and claim says what it does")
    void helpLines() throws Exception {
        CommandSender sender = mock(CommandSender.class);
        KitCommands commands = new KitCommands(plugin, kitService, new KitsConfig("config/config.yml"));

        Method help = KitCommands.class.getDeclaredMethod("handleHelp", CommandSender.class);
        help.setAccessible(true); // NOPMD - handleHelp is the framework's protected help hook
        help.invoke(commands, sender);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(sender, atLeastOnce()).sendMessage(captor.capture());
        assertThat(captor.getAllValues()).containsExactly(
                ChatColor.GOLD + "=== UltiKits ===",
                line("/kits", "kits.help.open"),
                line("/kits claim <name>", "kits.help.claim"),
                line("/kits list", "kits.help.list"),
                line("/kits edit <name>", "kits.help.edit"),
                line("/kits create <name>", "kits.help.create"),
                line("/kits delete <name>", "kits.help.delete"),
                line("/kits reload", "kits.help.reload"));
    }

    @Test
    @DisplayName("the /kits command description is in the server's language (its key was missing from both catalogues)")
    void commandDescription() {
        String key = KitCommands.class.getAnnotation(CmdExecutor.class).description();
        assertThat(new Language(CatalogueText.entries("en")).getLocalizedText(key)).isEqualTo("Kit management command");
        assertThat(new Language(CatalogueText.entries("zh")).getLocalizedText(key)).isEqualTo("\u793c\u5305\u7ba1\u7406\u547d\u4ee4");
    }

    @Test
    @DisplayName("kit editor: the save button's hint is in the server's language")
    void editorSaveHint() throws Exception {
        Player player = mock(Player.class);
        KitDefinition kit = new KitDefinition();
        kit.setName("testkit");
        kit.setDisplayName("&aTest Kit");
        KitEditorGui gui = new KitEditorGui(player, plugin, kitService, kit);
        Inventory guiInventory = mock(Inventory.class);
        when(guiInventory.getSize()).thenReturn(54);
        for (Class<?> c = gui.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField("inventory");
                f.setAccessible(true); // NOPMD - obliviate-invs keeps its inventory private
                f.set(gui, guiInventory);
                break;
            } catch (NoSuchFieldException e) {
                // keep walking up
            }
        }
        InventoryOpenEvent event = mock(InventoryOpenEvent.class);
        lenient().when(event.getInventory()).thenReturn(mock(Inventory.class));

        gui.onOpen(event);

        ArgumentCaptor<ItemStack> saved = ArgumentCaptor.forClass(ItemStack.class);
        verify(guiInventory).setItem(eq(45), saved.capture());
        assertThat(saved.getValue().getItemMeta().getLore())
                .isEqualTo(Arrays.asList(ChatColor.GRAY + zh("kits.editor.save_hint")));
    }
}
