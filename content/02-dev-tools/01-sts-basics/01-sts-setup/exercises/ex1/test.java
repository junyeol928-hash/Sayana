import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EncodingDoctorTest {

    private static final Charset MS932 = Charset.forName("MS932");

    @Test
    @DisplayName("UTF-8 で保存された日本語は UTF-8 と判定し、そのまま読める")
    void utf8Japanese() {
        byte[] data = "勤怠,出勤\n山田,09:00".getBytes(StandardCharsets.UTF_8);
        assertTrue(EncodingDoctor.isUtf8(data),
                "UTF-8 のバイト列なのに isUtf8 が false です。デコードに成功したら true を返していますか");
        assertEquals("勤怠,出勤\n山田,09:00", EncodingDoctor.decode(data),
                "UTF-8 のバイト列は UTF-8 で文字列にしてください");
    }

    @Test
    @DisplayName("MS932 で保存された日本語は UTF-8 ではないと判定し、MS932 で読める")
    void ms932Japanese() {
        byte[] data = "勤怠,出勤\n山田,09:00".getBytes(MS932);
        assertFalse(EncodingDoctor.isUtf8(data),
                "MS932 のバイト列は UTF-8 として不正です。new String(..., UTF_8) ではなく strictUtf8() で判定していますか");
        assertEquals("勤怠,出勤\n山田,09:00", EncodingDoctor.decode(data),
                "UTF-8 で読めないときは Charset.forName(\"MS932\") で文字列にしてください");
    }

    @Test
    @DisplayName("英数字だけ(ASCII)のデータは UTF-8 として正しい")
    void asciiOnly() {
        byte[] data = "id,name,minutes".getBytes(StandardCharsets.US_ASCII);
        assertTrue(EncodingDoctor.isUtf8(data),
                "ASCII の範囲の文字は UTF-8 でも同じバイトなので、isUtf8 は true になるはずです");
        assertEquals("id,name,minutes", EncodingDoctor.decode(data),
                "ASCII だけのデータはそのまま読めるはずです");
    }

    @Test
    @DisplayName("空のバイト列は UTF-8 として正しく、空文字列になる(境界値)")
    void empty() {
        assertTrue(EncodingDoctor.isUtf8(new byte[0]),
                "空のバイト列には不正なバイトが無いので true を返してください");
        assertEquals("", EncodingDoctor.decode(new byte[0]),
                "空のバイト列は空文字列 \"\" になるはずです");
    }

    @Test
    @DisplayName("MS932 の機種依存文字(①・髙)も正しく読める")
    void machineDependentChars() {
        byte[] data = "①髙橋,早退".getBytes(MS932);
        assertEquals("①髙橋,早退", EncodingDoctor.decode(data),
                "① や 髙 が化けています。Shift_JIS ではなく MS932 を使っていますか");
    }
}
