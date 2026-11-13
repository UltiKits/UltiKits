package com.ultikits.plugins.kits;

import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.UltiToolsModule;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;
import java.util.List;

@UltiToolsModule
public class UltiKits extends UltiToolsPlugin {
    @Override
    public boolean registerSelf() {
        String minecraftVersion = Bukkit.getBukkitVersion();
        Plugin core = Bukkit.getPluginManager().getPlugin("UltiTools");
        String frameworkVersion = core == null ? null : core.getDescription().getVersion();
        StartupCompatibility.Result compatibility = StartupCompatibility.check(minecraftVersion, frameworkVersion);
        // The api-version: 640 descriptor rejects these versions before normal registration.
        // Retain this guard for direct registerSelf() callers, which do not pass through the descriptor gate;
        // it does not add another refusal on any supported framework. UNKNOWN still warns as before.
        if (compatibility == StartupCompatibility.Result.REFUSE) {
            getLogger().error(String.format(i18n("kits.startup.framework_required"),
                    minecraftVersion, frameworkVersion));
            return false;
        }
        if (compatibility == StartupCompatibility.Result.UNKNOWN) {
            getLogger().warn(String.format(i18n("kits.startup.version_unknown"),
                    minecraftVersion, frameworkVersion));
        }
        return true;
    }

    @Override
    public List<String> supported() {
        return Arrays.asList("zh", "en");
    }
}
