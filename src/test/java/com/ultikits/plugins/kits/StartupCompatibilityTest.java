package com.ultikits.plugins.kits;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class StartupCompatibilityTest {
    @ParameterizedTest
    @CsvSource({
            "1.19.2-R0.1-SNAPSHOT, 6.3.0, REFUSE",
            "1.20.6-R0.1-SNAPSHOT, 6.3.1-SNAPSHOT, REFUSE",
            "1.20.6, 6.4.0, ALLOW",
            "1.20.6, 6.4.0-SNAPSHOT, ALLOW",
            "1.21, 6.3.0, ALLOW",
            "1.21.11-R0.1-SNAPSHOT, 6.3.0, ALLOW",
            "1.20.6, 6.4.1, ALLOW",
            "1.20.6, 6.10.0, ALLOW",
            "1.20.6, 7.0.0, ALLOW",
            "1.20.6, 6.2.99, REFUSE",
            "1.9.4, 6.3.0, REFUSE",
            "2.0, 6.3.0, ALLOW"
    })
    void decidesNumericVersionBoundaries(String minecraft, String framework,
                                        StartupCompatibility.Result expected) {
        assertThat(StartupCompatibility.check(minecraft, framework)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"garbage", "1.20.x", "1.20.6garbage", "1.20.6.1", "2147483648.1"})
    void unknownMinecraftIsNotARefusal(String version) {
        assertThat(StartupCompatibility.check(version, "6.3.0"))
                .isEqualTo(StartupCompatibility.Result.UNKNOWN);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"garbage", "6.4", "6.4.0garbage", "6.4.0.1", "6.2147483648.0"})
    void unknownFrameworkIsNotARefusal(String version) {
        assertThat(StartupCompatibility.check("1.20.6", version))
                .isEqualTo(StartupCompatibility.Result.UNKNOWN);
    }
}
