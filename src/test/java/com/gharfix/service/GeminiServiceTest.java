package com.gharfix.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeminiServiceTest {

    @Test
    void testDefaultModel() {
        try (GeminiService service = new GeminiService()) {
            assertEquals("gemini-3.6-flash", service.getModelName());
        }
    }

    @Test
    void testCustomModel() {
        try (GeminiService service = new GeminiService("", "gemini-2.0-flash")) {
            assertEquals("gemini-2.0-flash", service.getModelName());
        }
    }

    @Test
    void testMissingApiKey_GetClient_ThrowsClearConfigurationError() {
        try (GeminiService service = new GeminiService("", "gemini-2.5-flash")) {
            IllegalStateException ex = assertThrows(IllegalStateException.class, service::getClient);
            assertTrue(ex.getMessage().contains("GEMINI_API_KEY"), 
                    "Exception message must clearly reference GEMINI_API_KEY");
            assertTrue(ex.getMessage().contains("environment variable"),
                    "Exception message must explain how to configure it");
        }
    }

    @Test
    void testMissingApiKey_GenerateContent_ThrowsClearConfigurationError() {
        try (GeminiService service = new GeminiService("", "gemini-2.5-flash")) {
            IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.generateContent("Help me fix my tap"));
            assertTrue(ex.getMessage().contains("GEMINI_API_KEY"),
                    "Exception message must clearly reference GEMINI_API_KEY");
        }
    }

    @Test
    void testNullOrBlankPrompt_ThrowsIllegalArgumentException() {
        try (GeminiService service = new GeminiService("", "gemini-2.5-flash")) {
            assertThrows(IllegalArgumentException.class, () -> service.generateContent(null));
            assertThrows(IllegalArgumentException.class, () -> service.generateContent("   "));
            assertThrows(IllegalArgumentException.class, () -> service.generateContent(null, "system instructions"));
        }
    }

    @Test
    void testIsConfigured_WithoutKey_ReturnsFalse() {
        try (GeminiService service = new GeminiService("", "gemini-2.5-flash")) {
            // Unless user has GEMINI_API_KEY exported in their host environment, should be false
            if (System.getenv("GEMINI_API_KEY") == null && System.getProperty("GEMINI_API_KEY") == null) {
                assertFalse(service.isConfigured());
            }
        }
    }
}
