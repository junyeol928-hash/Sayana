package com.sayana.dojo.exec;

import java.util.List;

/**
 * JShell でのサンプル実行結果。
 *
 * @param status  結果の種類
 * @param output  標準出力・標準エラー出力(混在、上限で切り詰め)
 * @param values  式の評価結果(JShell の {@code $1 ==> 3} に相当)
 * @param errors  コンパイルエラーなど
 * @param exception 実行時例外の要約(無ければ null)。行は編集欄の行番号で、JShell が包むクラスの名前は外してある
 *                  ({@link JShellRunner#describe})
 */
public record RunResult(Status status, String output, List<String> values, List<CodeDiagnostic> errors,
                        String exception, long elapsedMillis, boolean truncated) {

    public enum Status {
        OK, COMPILE_ERROR, EXCEPTION, TIMEOUT, BUSY, ERROR
    }

    public boolean ok() {
        return status == Status.OK;
    }

    /**
     * 例外の文の 1 行目(例外の名前と理由)。画面はここだけ朱にする。CSS の {@code ::first-line} は折り返した見た目の
     * 1 行目にしか効かず、理由の途中で色が切れた(30 回目の審査)。例外が無ければ空
     */
    public String exceptionHead() {
        if (exception == null) {
            return "";
        }
        int nl = exception.indexOf('\n');
        return nl < 0 ? exception : exception.substring(0, nl);
    }

    /** 例外の文の 2 行目から(頭の改行を含む。スタックトレースの行)。無ければ空 */
    public String exceptionRest() {
        if (exception == null) {
            return "";
        }
        int nl = exception.indexOf('\n');
        return nl < 0 ? "" : exception.substring(nl);
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
