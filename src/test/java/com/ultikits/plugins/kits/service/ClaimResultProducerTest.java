package com.ultikits.plugins.kits.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every claim outcome a caller has to render is one the claim path can actually return. A constant with
 * no producer makes its renderings and its message unreachable while the tests that construct it by
 * hand keep them looking covered; {@code ClaimResult.ERROR} was one (UltiKits/UltiKits#27).
 * <p>
 * {@link KitServiceImpl} is the only class that returns a {@code ClaimResult} ({@code claimKit} is the
 * module's single claim gateway), so a producer is a reference to the constant in its source.
 */
@DisplayName("every ClaimResult has a producer in the claim path")
class ClaimResultProducerTest {

    private static final Path SERVICE_SOURCE =
            Paths.get("src/main/java/com/ultikits/plugins/kits/service/KitServiceImpl.java");

    @Test
    @DisplayName("KitServiceImpl returns every ClaimResult constant somewhere")
    void everyConstantIsProduced() throws IOException {
        String source = new String(Files.readAllBytes(SERVICE_SOURCE), StandardCharsets.UTF_8);
        assertThat(source).as("positive control: the scan reads the claim gateway")
                .contains("public ClaimResult claimKit(");

        List<String> unproduced = new ArrayList<>();
        for (KitService.ClaimResult result : KitService.ClaimResult.values()) {
            if (!source.contains("ClaimResult." + result.name())) {
                unproduced.add(result.name());
            }
        }

        assertThat(unproduced).as("ClaimResult constants nothing returns").isEmpty();
    }
}
