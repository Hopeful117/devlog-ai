package com.hopeful117.devlogai.aicredentialbinding.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class AiEngineBindingRequestSignerTest {
    @Test
    void signsCanonicalRequestWithDedicatedHeaders() {
        var signer = new AiEngineBindingRequestSigner("binding-secret");

        Map<String, String> headers = signer.sign(
                "POST", "/internal/ai/bindings", "{}".getBytes(StandardCharsets.UTF_8),
                1000, "nonce-1", "request-1");

        assertThat(headers).containsKeys(
                AiEngineBindingRequestSigner.SIGNATURE_HEADER,
                AiEngineBindingRequestSigner.TIMESTAMP_HEADER,
                AiEngineBindingRequestSigner.NONCE_HEADER,
                AiEngineBindingRequestSigner.REQUEST_ID_HEADER);
        assertThat(headers.get(AiEngineBindingRequestSigner.SIGNATURE_HEADER))
                .matches("sha256=[0-9a-f]{64}");
    }

    @Test
    void refusesBlankSecret() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AiEngineBindingRequestSigner(" "));
    }
}
