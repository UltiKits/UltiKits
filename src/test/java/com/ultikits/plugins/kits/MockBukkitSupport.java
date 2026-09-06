package com.ultikits.plugins.kits;

import java.lang.reflect.Field;

import org.bukkit.Bukkit;

import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Shared test-time server bootstrap entry point for this module.
 * <p>
 * Every test class in this module that needs a live Bukkit server -- the registry-reachability
 * sentinel included -- must call {@link #bootstrap()} in its {@code @BeforeEach} and
 * {@link #shutdown()} in its {@code @AfterEach} rather than calling {@code MockBukkit.mock()} /
 * {@code MockBukkit.unmock()} directly. Routing every caller through this one method is what makes
 * the sentinel's guard real: if this bootstrap is ever silently removed or broken, every class that
 * depends on it -- sentinel included -- fails together, instead of the sentinel quietly creating its
 * own unrelated live server and staying green.
 * <p>
 * The defensive cleanup logic is duplicated here rather than shared. This module deliberately keeps
 * no dependency on the framework repository's test tree, so an equivalent helper there cannot be
 * imported and the logic is copied instead.
 * <p>
 * That cleanup guards {@link MockBukkit#unmock()}'s failure path, not its normal one. On a clean
 * shutdown {@code unmock()} reaches {@code setServerInstanceToNull()}, which nulls both
 * {@code Bukkit.server} and MockBukkit's own {@code mock} singleton, leaving
 * {@link #ensureCleanState()} nothing to do. But {@code unmock()} wraps only its scheduler
 * shutdown in a {@code finally}; a throw out of {@code disablePlugins()}, {@code unload()} or the
 * lifecycle-runner reset skips that final cleanup and leaves both singletons set. The next
 * {@code MockBukkit.mock()} in the same Surefire fork would then fail with an
 * {@code IllegalStateException("Already mocking")}, and every later test class in that fork with
 * it.
 */
@SuppressWarnings("PMD.AvoidAccessibilityAlteration") // Test helper requires reflection for singleton cleanup
public final class MockBukkitSupport {

    private MockBukkitSupport() {
        // utility class, no instances
    }

    /**
     * Bootstrap the module's shared live test-time server. Call at the start of every test's
     * {@code @BeforeEach} that needs a live Bukkit server.
     */
    public static void bootstrap() {
        ensureCleanState();
        MockBukkit.mock();
    }

    /**
     * Shut down the module's shared live test-time server. Call at the end of every test's
     * {@code @AfterEach} that called {@link #bootstrap()}.
     */
    public static void shutdown() {
        try {
            MockBukkit.unmock();
        } catch (Exception ignored) {
            // best-effort cleanup only
        }
        ensureCleanState();
    }

    /**
     * Defensively clear MockBukkit's and Bukkit's server singletons.
     * Call at the start of every test's {@code @BeforeEach}, before {@link MockBukkit#mock()}.
     * <p>
     * Every step is best-effort and may fail silently, because on the normal path
     * {@link MockBukkit#unmock()} has already done this work. The reflection targets MockBukkit's
     * sole declared field, {@code private static ServerMock mock} -- there is no boolean
     * {@code mocked} flag, and {@link MockBukkit#isMocked()} is itself just {@code mock != null}.
     */
    public static void ensureCleanState() {
        try {
            if (MockBukkit.isMocked()) {
                MockBukkit.unmock();
            }
        } catch (Exception ignored) {
            // best-effort cleanup only
        }

        try {
            Field mockField = MockBukkit.class.getDeclaredField("mock");
            mockField.setAccessible(true);
            mockField.set(null, null);
        } catch (Exception ignored) {
            // best-effort cleanup only
        }

        if (Bukkit.getServer() != null) {
            try {
                Field serverField = Bukkit.class.getDeclaredField("server");
                serverField.setAccessible(true);
                serverField.set(null, null);
            } catch (Exception ignored) {
                // best-effort cleanup only
            }
        }
    }
}
