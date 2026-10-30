package com.hopeful117.devlogai.aicredentialbinding.model;

/** Explicit boundary between the product catalog id and the AI Engine id. */
public final class AiProviderIdentity {
    public static final String PRODUCT_OPENAI_CHATGPT = "openai_chatgpt";
    public static final String ENGINE_CHATGPT = "chatgpt";

    private AiProviderIdentity() {
    }

    public static String engineProvider(String provider) {
        if (PRODUCT_OPENAI_CHATGPT.equals(provider) || ENGINE_CHATGPT.equals(provider)) {
            return ENGINE_CHATGPT;
        }
        return provider;
    }

    public static boolean isChatGpt(String provider) {
        return PRODUCT_OPENAI_CHATGPT.equals(provider) || ENGINE_CHATGPT.equals(provider);
    }
}
