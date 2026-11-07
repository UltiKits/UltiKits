package com.ultikits.plugins.kits;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure start-up compatibility decision, independent of the server API. */
final class StartupCompatibility {
    enum Result { ALLOW, REFUSE, UNKNOWN }

    private static final Pattern MINECRAFT = Pattern.compile(
            "^(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:-[A-Za-z0-9][A-Za-z0-9.-]*)?$");
    private static final Pattern FRAMEWORK = Pattern.compile(
            "^(\\d+)\\.(\\d+)\\.(\\d+)(?:-[A-Za-z0-9][A-Za-z0-9.-]*)?$");

    private StartupCompatibility() {
    }

    static Result check(String minecraftVersion, String frameworkVersion) {
        int[] minecraft = parse(minecraftVersion, MINECRAFT);
        int[] framework = parse(frameworkVersion, FRAMEWORK);
        if (minecraft == null || framework == null) {
            return Result.UNKNOWN;
        }
        return below(minecraft, 1, 21, 0) && below(framework, 6, 4, 0)
                ? Result.REFUSE : Result.ALLOW;
    }

    private static int[] parse(String version, Pattern pattern) {
        if (version == null) {
            return null;
        }
        Matcher match = pattern.matcher(version.trim());
        if (!match.matches()) {
            return null;
        }
        try {
            return new int[]{Integer.parseInt(match.group(1)), Integer.parseInt(match.group(2)),
                    match.group(3) == null ? 0 : Integer.parseInt(match.group(3))};
        } catch (NumberFormatException overflow) {
            return null;
        }
    }

    private static boolean below(int[] version, int major, int minor, int patch) {
        if (version[0] != major) {
            return version[0] < major;
        }
        if (version[1] != minor) {
            return version[1] < minor;
        }
        return version[2] < patch;
    }
}
