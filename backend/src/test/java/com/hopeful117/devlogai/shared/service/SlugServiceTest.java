package com.hopeful117.devlogai.shared.service;


import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SlugServiceTest {
    private final SlugService slugService = new SlugServiceImpl();

    @Test
    void shouldGenerateValidSlug() {

        String result = slugService.generate("DevLog AI Architecture");

        assertEquals("devlog-ai-architecture", result);
    }


    @Test
    void shouldRemoveAccents() {

        String result = slugService.generate("Documentation complète");

        assertEquals("documentation-complete", result);
    }


    @Test
    void shouldRemoveSpecialCharacters() {

        String result = slugService.generate("Trading OS - V1!");

        assertEquals("trading-os-v1", result);
    }


    @Test
    void shouldNormalizeMultipleSpaces() {

        String result = slugService.generate("My    New    Project");

        assertEquals("my-new-project", result);
    }


    @Test
    void shouldRejectEmptyValue() {

        assertThrows(
                IllegalArgumentException.class,
                () -> slugService.generate("")
        );
    }
}
