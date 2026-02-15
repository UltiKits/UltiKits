package com.ultikits.plugins.kits;

import com.ultikits.ultitools.annotations.UltiToolsModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UltiKits Module")
class UltiKitsTest {

    @Nested
    @DisplayName("Annotations")
    class AnnotationTests {

        @Test
        @DisplayName("class has @UltiToolsModule annotation")
        void hasUltiToolsModuleAnnotation() {
            UltiToolsModule annotation = UltiKits.class.getAnnotation(UltiToolsModule.class);
            assertThat(annotation).isNotNull();
        }
    }
}
