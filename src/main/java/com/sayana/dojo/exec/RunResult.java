package com.sayana.dojo.exec;

import java.util.List;

/**
 * JShell でのサンプル実行結果。
 *
 * @param status  結果の種類
 * @param output  標準出力・標準エラー出力(混在、上限で切り詰め)
 * @param values  式の評価結果(JShell の {@code $1 ==> 3} に相当)
 * @param errors  コンパイルエラーなど
 * @param exception 実行時例外の要約(無ければ null)
 */
public record RunResult(Status status, String output, List<String> values, List<CodeDiagnostic> errors,
                        String exception, long elapsedMillis, boolean truncated) {

    public enum Status {
        OK, COMPILE_ERROR, EXCEPTION, TIMEOUT, BUSY, ERROR
    }

    public boolean ok() {
        return status == Status.OK;
    }

    /** AI のエラー翻訳に渡すためのテキスト。 */
    public String errorText() {
        StringBuilder sb = new StringBuilder();
        errors.forEach(d -> sb.append(d.format()).append('\n'));
        if (exception != null) {
            sb.append(exception).append('\n');
        }
        if (status == Status.TIMEOUT) {
            sb.append("タイムアウト(無限ループの可能性)\n");
        }
        return sb.toString().strip();
    }
}
