import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public class EncodingDoctor {

    /** UTF-8 として不正なバイトを見つけたら例外を投げるデコーダ */
    static CharsetDecoder strictUtf8() {
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
    }

    public static boolean isUtf8(byte[] data) {
        // TODO: strictUtf8().decode(ByteBuffer.wrap(data)) を試し、
        //       成功したら true、CharacterCodingException が出たら false を返す
        return false; // ← ダミー。書き換えてください
    }

    public static String decode(byte[] data) {
        // TODO: isUtf8 なら UTF-8 で、そうでなければ MS932 で文字列にする
        return ""; // ← ダミー。書き換えてください
    }

    public static void main(String[] args) {
        byte[] utf8 = "勤怠,出勤".getBytes(StandardCharsets.UTF_8);
        byte[] sjis = "勤怠,出勤".getBytes(Charset.forName("MS932"));
        System.out.println(isUtf8(utf8) + " " + decode(utf8)); // true 勤怠,出勤
        System.out.println(isUtf8(sjis) + " " + decode(sjis)); // false 勤怠,出勤
    }
}
