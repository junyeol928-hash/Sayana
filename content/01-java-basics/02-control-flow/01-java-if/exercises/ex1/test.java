import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GradeJudgeTest {

    @Test
    @DisplayName("満点の100点と、ちょうど90点は S")
    void topBoundary() {
        assertEquals("S", GradeJudge.grade(100), "100点は S になるはずです。100 を範囲外として扱っていないか確認してください");
        assertEquals("S", GradeJudge.grade(90), "90点は S になるはずです。条件が score > 90 になっていないか確認してください(>= を使います)");
    }

    @Test
    @DisplayName("89点は A、80点も A(境界値)")
    void aBoundary() {
        assertEquals("A", GradeJudge.grade(89), "89点は A になるはずです");
        assertEquals("A", GradeJudge.grade(80), "80点は A になるはずです。境界の 80 を含めているか(>=)確認してください");
    }

    @Test
    @DisplayName("75点は B、60点は C")
    void middle() {
        assertEquals("B", GradeJudge.grade(75), "75点は B になるはずです");
        assertEquals("C", GradeJudge.grade(60), "60点は C になるはずです。境界の 60 を含めているか確認してください");
    }

    @Test
    @DisplayName("59点と0点は D")
    void lowest() {
        assertEquals("D", GradeJudge.grade(59), "59点は D になるはずです");
        assertEquals("D", GradeJudge.grade(0), "0点は範囲内なので D になるはずです。0 を ERROR にしていないか確認してください");
    }

    @Test
    @DisplayName("範囲外(-1点、101点)は ERROR")
    void outOfRange() {
        assertEquals("ERROR", GradeJudge.grade(-1), "-1点は範囲外なので \"ERROR\" を返してください");
        assertEquals("ERROR", GradeJudge.grade(101), "101点は範囲外なので \"ERROR\" を返してください。S と判定していないか確認してください");
    }
}
