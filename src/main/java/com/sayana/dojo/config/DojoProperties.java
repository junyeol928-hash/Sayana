package com.sayana.dojo.config;

import java.nio.file.Path;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml の {@code dojo.*} をまとめた設定。
 */
@ConfigurationProperties("dojo")
public class DojoProperties {

    /** 教材ディレクトリ(部→章→レッスン)。 */
    private Path contentDir = Path.of("content");

    private final Exec exec = new Exec();
    private final Ai ai = new Ai();
    private final Lan lan = new Lan();

    public Path getContentDir() { return contentDir; }
    public void setContentDir(Path contentDir) { this.contentDir = contentDir; }
    public Exec getExec() { return exec; }
    public Ai getAi() { return ai; }
    public Lan getLan() { return lan; }

    /**
     * LANモード(同じWi-Fiのスマホから開く)。lan プロファイルで有効になる。
     * PC以外からのアクセスには合言葉(キー)を求める。
     */
    public static class Lan {
        private boolean enabled = false;
        /** 合言葉。空なら data/lan-key.txt に自動生成して使い回す。 */
        private String key = "";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
    }

    /** コード実行・採点の制限。 */
    public static class Exec {
        /** 1回の実行・採点のタイムアウト。 */
        private Duration timeout = Duration.ofSeconds(10);
        /** 子JVMの最大ヒープ(-Xmx)。 */
        private String maxHeap = "256m";
        /** 画面に返す出力の最大文字数。 */
        private int maxOutputChars = 20_000;
        /** 同時に動かす子JVMの数。 */
        private int maxConcurrent = 2;

        public Duration getTimeout() { return timeout; }
        public void setTimeout(Duration timeout) { this.timeout = timeout; }
        public String getMaxHeap() { return maxHeap; }
        public void setMaxHeap(String maxHeap) { this.maxHeap = maxHeap; }
        public int getMaxOutputChars() { return maxOutputChars; }
        public void setMaxOutputChars(int maxOutputChars) { this.maxOutputChars = maxOutputChars; }
        public int getMaxConcurrent() { return maxConcurrent; }
        public void setMaxConcurrent(int maxConcurrent) { this.maxConcurrent = maxConcurrent; }
    }

    /** Claude API まわり。APIキーは環境変数 ANTHROPIC_API_KEY から読む(ここには書かない)。 */
    public static class Ai {
        private final Models models = new Models();
        /** Sonnet 5.5 / Opus 5.5 系モデルで、拒否(refusal)時のサーバー側フォールバックを使うか。 */
        private boolean refusalFallback = true;
        /** チューターに渡す過去の会話の最大件数。 */
        private int chatHistoryLimit = 20;
        /** 1回の応答の最大トークン数。 */
        private long maxTokens = 8000;

        public Models getModels() { return models; }
        public boolean isRefusalFallback() { return refusalFallback; }
        public void setRefusalFallback(boolean refusalFallback) { this.refusalFallback = refusalFallback; }
        public int getChatHistoryLimit() { return chatHistoryLimit; }
        public void setChatHistoryLimit(int chatHistoryLimit) { this.chatHistoryLimit = chatHistoryLimit; }
        public long getMaxTokens() { return maxTokens; }
        public void setMaxTokens(long maxTokens) { this.maxTokens = maxTokens; }
    }

    /** 機能ごとのモデル名。 */
    public static class Models {
        private String tutor = "claude-sonnet-5-5";
        private String review = "claude-opus-5-5";
        private String explain = "claude-sonnet-5-5";
        private String hint = "claude-sonnet-5-5";
        private String generate = "claude-haiku-5-5";

        public String getTutor() { return tutor; }
        public void setTutor(String tutor) { this.tutor = tutor; }
        public String getReview() { return review; }
        public void setReview(String review) { this.review = review; }
        public String getExplain() { return explain; }
        public void setExplain(String explain) { this.explain = explain; }
        public String getHint() { return hint; }
        public void setHint(String hint) { this.hint = hint; }
        public String getGenerate() { return generate; }
        public void setGenerate(String generate) { this.generate = generate; }
    }
}
