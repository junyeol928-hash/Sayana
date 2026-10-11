package com.sayana.dojo.exec;

import java.util.List;

/**
 * 演習の採点結果。
 */
public record GradeResult(Status status, List<TestCase> tests, List<CodeDiagnostic> compileErrors, String message,
                          String output, long elapsedMillis) {

    public enum Status {
        /** 全テスト合格 */
        PASSED,
        /** 1つ以上のテストが不合格 */
        FAILED,
        /** コンパイルエラー */
        COMPILE_ERROR,
        /** 時間切れ */
        TIMEOUT,
        /** 混雑中・内部エラーなど */
        ERROR
    }

    /** テスト1件の結果。 */
    public record TestCase(String name, boolean passed, String message) {
    }

    public boolean passed() {
        return status == Status.PASSED;
    }

    public long passedCount() {
        return tests.stream().filter(TestCase::passed).count();
    }

    public int totalCount() {
        return tests.size();
    }

    public String statusLabel() {
        return switch (status) {
            case PASSED -> "合格";
            case FAILED -> "不合格";
            case COMPILE_ERROR -> "コンパイルエラー";
            case TIMEOUT -> "タイムアウト";
            case ERROR -> "エラー";
        };
    }

    /** AI のエラー翻訳・レビューに渡す要約テキスト。 */
    public String summaryText() {
        StringBuilder sb = new StringBuilder();
        sb.append("結果: ").append(statusLabel());
        if (!tests.isEmpty()) {
            sb.append("(").append(passedCount()).append("/").append(totalCount()).append(" 合格)");
        }
        sb.append('\n');
        if (message != null && !message.isBlank()) {
            sb.append(message).append('\n');
        }
        compileErrors.forEach(d -> {
            sb.append("- ").append(d.format()).append('\n');
            if (d.codeLine() != null && !d.codeLine().isBlank()) {
                sb.append("    > ").append(d.codeLine().strip()).append('\n');
            }
        });
        tests.stream().filter(t -> !t.passed())
                .forEach(t -> sb.append("- [不合格] ").append(t.name()).append(": ").append(t.message()).append('\n'));
        return sb.toString().strip();
    }
}
