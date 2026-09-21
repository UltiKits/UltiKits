package com.ultikits.plugins.kits;

import com.ultikits.ultitools.annotations.UltiToolsModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

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

    @Nested
    @DisplayName("Lifecycle template methods (UltiKits/UltiKits#18)")
    class LifecycleTemplateMethodTests {

        /**
         * UltiTools 6.3.0 makes {@code unregisterSelf()} and {@code reloadSelf()} final template
         * methods that always run the framework's own steps (on unload: this module's
         * {@code onUnregister()} hook, then command and listener unregistration). This module's
         * former {@code unregisterSelf()} override was empty and never called {@code super}, so
         * those framework steps were skipped (UltiKits/UltiKits#18). It is deleted outright; this
         * test pins that neither template method is declared again.
         */
        @Test
        @DisplayName("UltiKits declares neither framework template method")
        void declaresNeitherTemplateMethod() {
            List<String> declared = new ArrayList<>();
            for (Method method : UltiKits.class.getDeclaredMethods()) {
                declared.add(method.getName());
            }

            assertThat(declared).doesNotContain("unregisterSelf", "reloadSelf");
        }
    }
}
