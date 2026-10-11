package com.sayana.dojo.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sayana.dojo.config.DojoProperties;

class AnthropicLlmClientTest {

    @Test
    void APIキーがなければ無効で呼び出しは分かりやすいエラーになる() {
        String key = System.getenv("ANTHROPIC_API_KEY");
        assumeTrue(key == null || key.isBlank(), "APIキーが設定された環境では実行しない");
        AnthropicLlmClient client = new AnthropicLlmClient(new DojoProperties());
        assertThat(client.isAvailable()).isFalse();
        assertThatThrownBy(() -> client.complete(new LlmClient.LlmRequest("claude-haiku-5-5", "low",
                List.of(new LlmClient.SystemBlock("sys", true)), List.of(LlmClient.Turn.user("hi")), 100)))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("ANTHROPIC_API_KEY");
    }
}
