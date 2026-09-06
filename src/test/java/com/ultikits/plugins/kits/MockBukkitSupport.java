package com.ultikits.plugins.kits;

import java.lang.reflect.Field;

import org.bukkit.Bukkit;

import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Shared test-time server bootstrap entry point for this module (Phase 14).
 * <p>
 * Every test class in this module that needs a live Bukkit server -- the registry-reachability
 * sentinel included -- must call {@link #bootstrap()} in its {@code @BeforeEach} and
 * {@link #shutdown()} in its {@code @AfterEach} rather than calling {@code MockBukkit.mock()} /
 * {@code MockBukkit.unmock()} directly. Routing every caller through this one method is what makes
 * the sentinel's guard real: if this bootstrap is ever silently removed or broken, every class that
 * depends on it -- sentinel included -- fails together, instead of the sentinel quietly creating its
 * own unrelated live server and staying green.
 * <p>
 * The defensive cleanup logic is copied (logic only, per Phase 14's "no shared artifact" decision --
 * never add a dependency on the framework's copy) from
 * {@code com.ultikits.ultitools.utils.MockBukkitHelper} in the framework repository. It reconciles
 * the fact that {@link MockBukkit#unmock()} alone can leave a stale {@code Bukkit.server} /
 * {@code MockBukkit.mocked} singleton behind between test classes reused in the same Surefire fork.
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
     * Defensively clear the MockBukkit and Bukkit singleton state.
     * Call at the start of every test's {@code @BeforeEach}, before {@link MockBukkit#mock()}.
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
            Field mockedField = MockBukkit.class.getDeclaredField("mocked");
            mockedField.setAccessible(true);
            mockedField.setBoolean(null, false);
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
