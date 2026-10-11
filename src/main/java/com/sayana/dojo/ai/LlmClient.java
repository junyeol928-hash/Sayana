package com.sayana.dojo.ai;

import java.util.List;

/**
 * LLM 呼び出しの最小インターフェース。本番は {@link AnthropicLlmClient}、テストではモックに差し替える。
 */
public interface LlmClient {

    /** APIキーが設定されていて呼び出せる状態か。 */
    boolean isAvailable();

    LlmResponse complete(LlmRequest request);

    /**
     * @param model    モデル名
     * @param effort   low / medium / high(思考の深さ)
     * @param system   システムプロンプト(先頭から順に連結される)
     * @param messages 会話(user / assistant 交互)
     */
    record LlmRequest(String model, String effort, List<SystemBlock> system, List<Turn> messages, long maxTokens) {
    }

    /** システムプロンプトの1ブロック。cache=true ならここまでをプロンプトキャッシュの対象にする。 */
    record SystemBlock(String text, boolean cache) {
    }

    record Turn(String role, String text) {

        public static Turn user(String text) {
            return new Turn("user", text);
        }

        public static Turn assistant(String text) {
            return new Turn("assistant", text);
        }
    }

    record LlmResponse(String text, String stopReason, long inputTokens, long outputTokens, long cacheReadTokens,
                       long cacheWriteTokens) {
    }
}
