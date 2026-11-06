package com.ultikits.plugins.kits;

import com.ultikits.plugins.kits.i18n.CatalogueText;
import com.ultikits.ultitools.UltiTools;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.context.ComponentScanner;
import com.ultikits.ultitools.context.SimpleContainer;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;
import com.ultikits.ultitools.manager.CommandManager;
import com.ultikits.ultitools.manager.ListenerManager;
import com.ultikits.ultitools.manager.PluginManager;
import com.ultikits.ultitools.manager.TaskManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import javax.tools.ToolProvider;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real released framework registration; external services and container assembly are test doubles. */
class FrameworkStartupRefusalTest {
    @TempDir Path temp;

    @Test
    void publicFrameworkEntryRefusesWithoutEnablingOrRegistering() throws Exception {
        assertRefused(UltiKits.class, UltiKits.class.getClassLoader(), false);
    }

    @Test
    void realFrameworkScanLoadsEveryModuleClassWithClassShapedInventoryViewBeforeRefusal() throws Exception {
        Path source = temp.resolve("org/bukkit/inventory/InventoryView.java");
        Files.createDirectories(source.getParent());
        Files.write(source, "package org.bukkit.inventory; public abstract class InventoryView {}"
                .getBytes(StandardCharsets.UTF_8));
        assertThat(ToolProvider.getSystemJavaCompiler()).as("JDK compiler for isolated API stub").isNotNull();
        assertThat(ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-d", temp.toString(), source.toString())).isZero();
        Path classes = Paths.get(UltiKits.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Set<String> expected;
        try (Stream<Path> paths = Files.walk(classes.resolve("com/ultikits/plugins/kits"))) {
            expected = paths.filter(p -> p.toString().endsWith(".class"))
                    .map(p -> classes.relativize(p).toString().replace('/', '.').replace(".class", ""))
                    .collect(Collectors.toSet());
        }
        assertThat(expected).contains("com.ultikits.plugins.kits.gui.KitEditorGui",
                "com.ultikits.plugins.kits.commands.KitCommands");
        try (StubApiLoader loader = new StubApiLoader(new URL[]{temp.toUri().toURL(), classes.toUri().toURL()})) {
            Class<?> inventoryView = loader.loadClass("org.bukkit.inventory.InventoryView");
            assertThat(inventoryView.isInterface()).isFalse();
            assertThat(inventoryView.getClassLoader()).isSameAs(loader);
            Class<? extends UltiToolsPlugin> moduleType = loader.loadClass(UltiKits.class.getName())
                    .asSubclass(UltiToolsPlugin.class);
            assertRefused(moduleType, loader, true);
            assertThat(loader.scanned).containsAll(expected);
            assertThat(loader.failures).isEmpty();
        }
    }

    private void assertRefused(Class<? extends UltiToolsPlugin> moduleType, ClassLoader loader,
                               boolean runRealScanner) throws Exception {
        UltiToolsPlugin module = mock(moduleType, CALLS_REAL_METHODS);
        PluginLogger logger = mock(PluginLogger.class);
        doReturn(logger).when(module).getLogger();
        doAnswer(CatalogueText.answer("en")).when(module).i18n(anyString());
        doReturn("UltiTools-Kits").when(module).getPluginName();
        doReturn("2.0.0").when(module).getVersion();
        doReturn(UltiKits.class.getName()).when(module).getMainClass();
        doReturn(630).when(module).getMinUltiToolsVersion();
        doNothing().when(module).commitLanguageProvenance();
        UltiTools core = mock(UltiTools.class, RETURNS_DEEP_STUBS);
        when(core.getDataFolder()).thenReturn(temp.toFile());
        when(core.getConfigManager().getAllConfigEntities(module)).thenReturn(Collections.emptyMap());
        CommandManager commands = mock(CommandManager.class);
        ListenerManager listeners = mock(ListenerManager.class);
        when(core.getCommandManager()).thenReturn(commands);
        when(core.getListenerManager()).thenReturn(listeners);
        org.bukkit.plugin.PluginManager bukkitManager = mock(org.bukkit.plugin.PluginManager.class);
        Plugin host = mock(Plugin.class);
        when(host.getDescription()).thenReturn(new PluginDescriptionFile("UltiTools", "6.3.0", "test.Core"));
        when(bukkitManager.getPlugin("UltiTools")).thenReturn(host);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        PluginManager framework = new PluginManager();
        TaskManager tasks = mock(TaskManager.class);
        setField(framework, "taskManager", tasks);
        setField(framework, "classLoader", loader);
        List<LogRecord> scanRecords = new ArrayList<>();
        Handler handler = new Handler() {
            @Override public void publish(LogRecord record) { scanRecords.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        Logger scanLogger = Logger.getLogger(ComponentScanner.class.getName());
        scanLogger.addHandler(handler);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<UltiTools> frameworkStatic = mockStatic(UltiTools.class);
             MockedConstruction<SimpleContainer> containers = mockConstruction(SimpleContainer.class,
                     withSettings().defaultAnswer(RETURNS_DEEP_STUBS), (context, ignored) -> {
                         when(context.getClassLoader()).thenReturn(loader);
                         when(context.getBeanNamesForType(any())).thenReturn(new String[0]);
                         when(context.getSingletonValues()).thenReturn(Collections.emptyList());
                         if (runRealScanner) {
                             doAnswer(call -> {
                                 StubApiLoader apiLoader = (StubApiLoader) loader;
                                 apiLoader.loaded.clear();
                                 new ComponentScanner(context).scanPackages("com.ultikits.plugins.kits");
                                 apiLoader.scanned.addAll(apiLoader.loaded);
                                 return null;
                             }).when(context).scanComponents(any(String[].class));
                         }
                     })) {
            bukkit.when(Bukkit::getBukkitVersion).thenReturn("1.20.6-R0.1-SNAPSHOT");
            bukkit.when(Bukkit::getPluginManager).thenReturn(bukkitManager);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(Bukkit::getLogger).thenReturn(Logger.getLogger("FrameworkStartupRefusalTest"));
            frameworkStatic.when(UltiTools::getInstance).thenReturn(core);
            frameworkStatic.when(UltiTools::getPluginVersion).thenReturn(630);

            // Public entry, not a locally reimplemented if(registerSelf()) simulation.
            assertThat(framework.register(module)).isFalse();
            verify(module).registerSelf(); // excludes an earlier unrelated framework-gate failure
            verify(logger).error("UltiKits needs UltiTools 6.4.0 or later on Minecraft versions below 1.21 "
                    + "(server: 1.20.6-R0.1-SNAPSHOT, UltiTools: 6.3.0). Please update UltiTools.");
            assertThat(framework.getPluginList()).doesNotContain(module).isEmpty();
            assertThat(containers.constructed()).hasSize(1);
            verify(containers.constructed().get(0)).refresh();
            verify(containers.constructed().get(0)).close();
            verifyNoInteractions(commands, listeners, tasks, scheduler);
            verify(bukkitManager, never()).registerEvents(any(), any());
            verify(bukkitManager, never()).disablePlugin(any());
            assertThat(scanRecords).noneMatch(r -> r.getThrown() != null
                    || r.getLevel().intValue() >= java.util.logging.Level.WARNING.intValue());
        } finally {
            scanLogger.removeHandler(handler);
        }
    }

    private static void setField(PluginManager manager, String name, Object value) throws Exception {
        Field field = PluginManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(manager, value);
    }

    /** Child-first only for this module and one intentionally old-shaped API type. */
    private static final class StubApiLoader extends URLClassLoader {
        final Set<String> loaded = new HashSet<>();
        final Set<String> scanned = new HashSet<>();
        final List<Throwable> failures = new ArrayList<>();

        StubApiLoader(URL[] urls) { super(urls, UltiKits.class.getClassLoader()); }

        @Override public URL getResource(String name) {
            // Surefire's parent otherwise finds target/test-classes for the same package.
            if (name.startsWith("com/ultikits/plugins/kits")) { return findResource(name); }
            return super.getResource(name);
        }

        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith("com.ultikits.plugins.kits.")
                    && !name.equals("org.bukkit.inventory.InventoryView")) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                try {
                    Class<?> type = findLoadedClass(name);
                    if (type == null) { type = findClass(name); }
                    if (resolve) { resolveClass(type); }
                    loaded.add(name);
                    return type;
                } catch (ClassNotFoundException | LinkageError failure) {
                    failures.add(failure);
                    throw failure;
                }
            }
        }
    }
}
