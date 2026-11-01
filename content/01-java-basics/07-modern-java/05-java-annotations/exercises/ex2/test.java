import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LogMaskerTest {

    static class Member {
        private String name;
        @Mask private String email;
        @Mask(keep = 3) private String phone;
        private Integer points;

        Member(String name, String email, String phone, Integer points) {
            this.name = name;
            this.email = email;
            this.phone = phone;
            this.points = points;
        }
    }

    static class Plain {
        private int id = 7;
        private String title = "見積書";
    }

    @Test
    @DisplayName("mask:keep が 0 なら長さに関係なく ***")
    void maskAll() {
        assertEquals("***", LogMasker.mask("s3cret!", 0),
                "keep が 0 のときは、値の長さに関係なく *** (アスタリスク3つ)を返してください。長さが分かると推測のヒントになるためです");
        assertEquals("***", LogMasker.mask("a", 0), "1文字でも keep が 0 なら *** です");
    }

    @Test
    @DisplayName("mask:末尾 keep 文字を残して、前を1文字ずつ * にする")
    void maskKeep() {
        assertEquals("************4444", LogMasker.mask("4111222233334444", 4),
                "16文字のうち前の12文字を *、末尾4文字はそのまま残します");
        assertEquals("********678", LogMasker.mask("09012345678", 3),
                "11文字で keep=3 なら、* が8個と末尾の 678 です");
    }

    @Test
    @DisplayName("mask:長さが keep 以下なら全部 *、null は \"null\"")
    void maskShortAndNull() {
        assertEquals("***", LogMasker.mask("123", 4),
                "値の長さが keep 以下なら、全部を * にします(\"123\" → \"***\")。短い値が丸見えにならないようにするためです");
        assertEquals("****", LogMasker.mask("1234", 4),
                "長さがちょうど keep と同じときも全部 * です");
        assertEquals("null", LogMasker.mask(null, 4), "value が null なら文字列の \"null\" を返してください");
    }

    @Test
    @DisplayName("toLog:問題文の LoginForm")
    void loginForm() {
        assertEquals("LoginForm{cardNo=************4444, password=***, retry=2, userId=haru}",
                LogMasker.toLog(new LoginForm()),
                "フィールド名の昇順に「名前=値」を「, 」でつなぎます。@Mask の付いた password と cardNo は伏せ字にしてください");
    }

    @Test
    @DisplayName("toLog:目印のあるフィールドだけ伏せる。null もそのまま出す")
    void member() {
        assertEquals("Member{email=***, name=ハル, phone=********678, points=120}",
                LogMasker.toLog(new Member("ハル", "haru@example.com", "09012345678", 120)),
                "@Mask の付いた email と phone だけを伏せ字にします。name と points はそのままです");
        assertEquals("Member{email=null, name=null, phone=null, points=null}",
                LogMasker.toLog(new Member(null, null, null, null)),
                "値が null のフィールドは、@Mask の有無に関係なく null と出してください");
    }

    @Test
    @DisplayName("toLog:@Mask が1つもないクラスはそのまま出る")
    void plain() {
        assertEquals("Plain{id=7, title=見積書}", LogMasker.toLog(new Plain()),
                "@Mask が無ければ、全フィールドを名前順にそのまま出します");
    }
}
