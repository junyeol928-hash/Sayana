package com.sayana.dojo.exec;

/**
 * コンパイルエラー1件。line は 1 始まり(不明なら 0)。
 *
 * @param source どのファイル由来か(提出コード / 採点テスト など)
 */
public record CodeDiagnostic(String source, long line, long column, String message, String codeLine) {

    public String format() {
        String where = line > 0 ? source + " " + line + "行目" : source;
        return where + ": " + message;
    }
}
