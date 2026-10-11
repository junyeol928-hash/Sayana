package com.sayana.dojo.ai;

/** AI 呼び出しの失敗。メッセージはそのまま画面に出せる日本語にする。 */
public class AiException extends RuntimeException {

    public AiException(String message) {
        super(message);
    }

    public AiException(String message, Throwable cause) {
        super(message, cause);
    }
}
