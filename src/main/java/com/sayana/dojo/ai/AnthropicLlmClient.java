package com.sayana.dojo.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.BadRequestException;
import com.anthropic.errors.InternalServerException;
import com.anthropic.errors.NotFoundException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlockParam;
import com.sayana.dojo.config.DojoProperties;

/**
 * 公式 Java SDK(com.anthropic:anthropic-java)で Claude API を呼ぶ。
 * APIキーは環境変数 ANTHROPIC_API_KEY から読む。未設定なら {@link #isAvailable()} が false になる。
 */
@Component
public class AnthropicLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(AnthropicLlmClient.class);
    private static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";

    private final DojoProperties.Ai config;
    private final String apiKey;
    private volatile AnthropicClient client;

    public AnthropicLlmClient(DojoProperties props) {
        this.config = props.getAi();
        String key = System.getenv("ANTHROPIC_API_KEY");
        this.apiKey = key == null || key.isBlank() ? null : key.strip();
        log.info(apiKey == null ? "ANTHROPIC_API_KEY が未設定なので AI 機能は無効です(それ以外はすべて使えます)"
                : "AI 機能が有効です");
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null;
    }

    private AnthropicClient client() {
        AnthropicClient c = client;
        if (c == null) {
            synchronized (this) {
                if (client == null) {
                    client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
                }
                c = client;
            }
        }
        return c;
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        if (!isAvailable()) {
            throw new AiException("ANTHROPIC_API_KEY が設定されていないので AI 機能は使えません。README の手順で設定してください。");
        }
        List<TextBlockParam> system = new ArrayList<>();
        for (SystemBlock b : request.system()) {
            TextBlockParam.Builder tb = TextBlockParam.builder().text(b.text());
            if (b.cache()) {
                // ここまで(=レッスン本文)をキャッシュして、同じレッスンでの2回目以降の入力コストを下げる
                tb.cacheControl(CacheControlEphemeral.builder().build());
            }
            system.add(tb.build());
        }
        MessageCreateParams.Builder params = MessageCreateParams.builder()
                .model(request.model())
                .maxTokens(request.maxTokens())
                .systemOfTextBlockParams(system)
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.of(request.effort())).build());
        for (Turn t : request.messages()) {
            if ("assistant".equals(t.role())) {
                params.addAssistantMessage(t.text());
            } else {
                params.addUserMessage(t.text());
            }
        }
        if (config.isRefusalFallback() && request.model().startsWith("claude-sonnet")) {
            // 安全分類器に誤って拒否されたとき、サーバー側で別モデルに回してもらう
            params.putAdditionalHeader("anthropic-beta", FALLBACK_BETA);
            params.putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
        }
        try {
            Message message = client().messages().create(params.build());
            String text = message.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(t -> t.text())
                    .collect(Collectors.joining("\n"))
                    .strip();
            String stop = message.stopReason().map(StopReason::asString).orElse("");
            if (message.stopReason().filter(StopReason.REFUSAL::equals).isPresent() && text.isEmpty()) {
                text = "(この内容には回答できませんでした。質問の言い回しを変えてもう一度試してください)";
            } else if (message.stopReason().filter(StopReason.MAX_TOKENS::equals).isPresent()) {
                text = text + "\n\n(長くなったので途中で切れました)";
            }
            var usage = message.usage();
            log.debug("Claude {}: in={} out={} cacheRead={} cacheWrite={}", request.model(), usage.inputTokens(),
                    usage.outputTokens(), usage.cacheReadInputTokens().orElse(0L),
                    usage.cacheCreationInputTokens().orElse(0L));
            return new LlmResponse(text, stop, usage.inputTokens(), usage.outputTokens(),
                    usage.cacheReadInputTokens().orElse(0L), usage.cacheCreationInputTokens().orElse(0L));
        } catch (UnauthorizedException e) {
            throw new AiException("APIキーが無効です(401)。ANTHROPIC_API_KEY を確認してください。", e);
        } catch (PermissionDeniedException e) {
            throw new AiException("このAPIキーではこの機能を使えません(403)。", e);
        } catch (NotFoundException e) {
            throw new AiException("モデル「" + request.model() + "」が見つかりません。application.yml の dojo.ai.models を確認してください。", e);
        } catch (RateLimitException e) {
            throw new AiException("APIの利用上限に達しました(429)。少し待ってから試してください。", e);
        } catch (BadRequestException e) {
            throw new AiException("リクエストが不正です(400): " + e.getMessage(), e);
        } catch (InternalServerException e) {
            throw new AiException("Claude API 側で一時的なエラーが起きました。少し待ってから試してください。", e);
        } catch (AnthropicServiceException e) {
            throw new AiException("Claude API がエラーを返しました(" + e.statusCode() + ")。", e);
        } catch (AnthropicIoException e) {
            throw new AiException("Claude API に接続できませんでした。ネットワークを確認してください。", e);
        }
    }
}
