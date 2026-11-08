package com.ultikits.plugins.kits;

import com.ultikits.plugins.kits.i18n.CatalogueText;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class UltiKitsStartupTest {
    @ParameterizedTest
    @CsvSource({
            "en, 'UltiKits needs UltiTools 6.4.0 or later on Minecraft versions below 1.21 (server: 1.20.6-R0.1-SNAPSHOT, UltiTools: 6.3.1-SNAPSHOT). Please update UltiTools.'",
            "zh, 'Minecraft 低于 1.21 时，UltiKits 需要 UltiTools 6.4.0 或更高版本（服务器：1.20.6-R0.1-SNAPSHOT，UltiTools：6.3.1-SNAPSHOT）。请更新 UltiTools。'"
    })
    void refusesBeforeAccessingOrRegisteringAnyModuleResource(String language, String message) {
        UltiKits module = mock(UltiKits.class, CALLS_REAL_METHODS);
        PluginLogger logger = mock(PluginLogger.class);
        doReturn(logger).when(module).getLogger();
        doAnswer(CatalogueText.answer(language)).when(module).i18n(anyString());
        PluginManager manager = mock(PluginManager.class);
        Plugin core = core("6.3.1-SNAPSHOT");
        when(manager.getPlugin("UltiTools")).thenReturn(core);
        clearInvocations(module, manager, core);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getBukkitVersion).thenReturn("1.20.6-R0.1-SNAPSHOT");
            bukkit.when(Bukkit::getPluginManager).thenReturn(manager);

            assertThat(module.registerSelf()).isFalse();

            verify(logger).error(message);
            verifyNoMoreInteractions(logger);
            verify(module).registerSelf();
            verify(module).getLogger();
            verify(module).i18n("kits.startup.framework_required");
            verifyNoMoreInteractions(module);
            verify(manager).getPlugin("UltiTools");
            verifyNoMoreInteractions(manager);
            verify(core).getDescription();
            verifyNoMoreInteractions(core);
            bukkit.verify(Bukkit::getBukkitVersion);
            bukkit.verify(Bukkit::getPluginManager);
            bukkit.verifyNoMoreInteractions();
        }
    }

    @ParameterizedTest
    @CsvSource({"1.20.6, 6.4.0", "1.20.6, 6.4.0-SNAPSHOT", "1.21.11, 6.3.0"})
    void supportedPairContinuesWithoutWarning(String minecraft, String framework) {
        UltiKits module = mock(UltiKits.class, CALLS_REAL_METHODS);
        PluginManager manager = mock(PluginManager.class);
        Plugin core = core(framework);
        when(manager.getPlugin("UltiTools")).thenReturn(core);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getBukkitVersion).thenReturn(minecraft);
            bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
            assertThat(module.registerSelf()).isTrue();
            verify(module, never()).getLogger();
            verify(module, never()).getContext();
        }
    }

    @ParameterizedTest
    @CsvSource({"garbage, 6.3.0", "1.20.6, garbage", "1.21.11, garbage"})
    void unknownPairWarnsOnceAndContinues(String minecraft, String framework) {
        assertUnknownContinues(minecraft, core(framework), framework);
    }

    @Test
    void missingCoreVersionWarnsAndContinues() {
        assertUnknownContinues("1.20.6", null, "null");
    }

    private void assertUnknownContinues(String minecraft, Plugin core, String framework) {
        UltiKits module = mock(UltiKits.class, CALLS_REAL_METHODS);
        PluginLogger logger = mock(PluginLogger.class);
        doReturn(logger).when(module).getLogger();
        doAnswer(CatalogueText.answer("en")).when(module).i18n(anyString());
        PluginManager manager = mock(PluginManager.class);
        when(manager.getPlugin("UltiTools")).thenReturn(core);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getBukkitVersion).thenReturn(minecraft);
            bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
            assertThat(module.registerSelf()).isTrue();
            verify(logger).warn("UltiKits could not check start-up compatibility (server: " + minecraft
                    + ", UltiTools: " + framework + "). Continuing without a version-based refusal.");
            verifyNoMoreInteractions(logger);
            verify(module, never()).getContext();
        }
    }

    private Plugin core(String version) {
        Plugin core = mock(Plugin.class);
        when(core.getDescription()).thenReturn(new PluginDescriptionFile("UltiTools", version, "test.Core"));
        return core;
    }
}
