package com.sayana.dojo.exec;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 上限付きの出力バッファ。無限ループで println し続けてもメモリを食い潰さない。
 */
final class BoundedOutput extends OutputStream {

    private final ByteArrayOutputStream buf = new ByteArrayOutputStream();
    private final int maxBytes;
    private volatile boolean truncated;

    BoundedOutput(int maxChars) {
        // UTF-8 の日本語は1文字3バイト
        this.maxBytes = Math.max(1024, maxChars * 3);
    }

    @Override
    public synchronized void write(int b) {
        if (buf.size() < maxBytes) {
            buf.write(b);
        } else {
            truncated = true;
        }
    }

    @Override
    public synchronized void write(byte[] b, int off, int len) {
        int room = maxBytes - buf.size();
        if (room <= 0) {
            truncated = true;
            return;
        }
        if (len > room) {
            truncated = true;
            len = room;
        }
        buf.write(b, off, len);
    }

    boolean truncated() {
        return truncated;
    }

    synchronized String text() {
        return buf.toString(StandardCharsets.UTF_8);
    }
}
