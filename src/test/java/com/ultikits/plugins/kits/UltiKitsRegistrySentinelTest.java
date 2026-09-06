package com.ultikits.plugins.kits;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Reopen guard for the test-time server bootstrap. Every assertion here depends on a live
 * server-backed value, never a bare registry constant -- a bare constant resolves via
 * ServiceLoader from the classpath alone and would stay green even if the bootstrap were
 * silently deleted from this module (see 14-VALIDATION.md's Sentinel Design Constraint).
 * <p>
 * Deliberately bootstraps through {@link MockBukkitSupport#bootstrap()} /
 * {@link MockBukkitSupport#shutdown()} -- the same shared entry point every other bootstrapped
 * test class in this module uses -- rather than calling {@code MockBukkit.mock()} itself. A
 * sentinel that mocks its own unrelated live server stays green even after the shared bootstrap is
 * silently removed or broken, which defeats the reopen guard this class exists to provide.
 */
public class UltiKitsRegistrySentinelTest {

    @BeforeEach
    void setUp() {
        MockBukkitSupport.bootstrap();
    }

    @AfterEach
    void tearDown() {
        MockBukkitSupport.shutdown();
    }

    @Test
    void liveServerIsBootstrapped() {
        assertNotNull(Bukkit.getServer(), "live server bootstrap must be present");
    }

    @Test
    void unsafeValuesResolves() {
        assertNotNull(Bukkit.getUnsafe(), "UnsafeValues must resolve on a live server");
    }

    @Test
    void createProfileDoesNotSilentlyReturnNull() {
        Object profile = Bukkit.createProfile(UUID.randomUUID(), "SentinelPlayer");
        assertNotNull(profile, "createProfile must not silently return null");
    }

    @Test
    void itemStackConstructionResolvesRegistry() {
        ItemStack stack = new ItemStack(Material.DIAMOND);
        assertNotNull(stack);
        assertEquals(Material.DIAMOND, stack.getType());
    }
}
