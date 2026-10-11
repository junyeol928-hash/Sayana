import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShapeTest {

    @Test
    @DisplayName("長方形の面積は 幅 × 高さ")
    void rectangleArea() {
        assertEquals(12.0, new Rectangle(3, 4).area(), 1e-9,
                "Rectangle(3, 4) の面積は 12.0 です。Rectangle で area() をオーバーライドしましたか?");
    }

    @Test
    @DisplayName("三角形の面積は 底辺 × 高さ ÷ 2(小数になるケース)")
    void triangleArea() {
        assertEquals(7.5, new Triangle(3, 5).area(), 1e-9,
                "Triangle(3, 5) の面積は 7.5 です。7.0 になる場合は int 同士の割り算で小数が切り捨てられています(2.0 で割りましょう)");
        assertEquals(6.0, new Triangle(3, 4).area(), 1e-9,
                "Triangle(3, 4) の面積は 6.0 です");
    }

    @Test
    @DisplayName("名前を親のコンストラクタに渡している")
    void names() {
        assertEquals("長方形", new Rectangle(1, 1).getName(),
                "Rectangle の名前は \"長方形\" です。super(\"長方形\") で親に渡してください");
        assertEquals("三角形", new Triangle(1, 1).getName(),
                "Triangle の名前は \"三角形\" です。super(\"三角形\") で親に渡してください");
    }

    @Test
    @DisplayName("親の describe() から子の area() が呼ばれる")
    void describe() {
        assertEquals("長方形の面積: 12.0", new Rectangle(3, 4).describe(),
                "describe() は親のメソッドのままで、子でオーバーライドした area() が使われるはずです");
        assertEquals("三角形の面積: 7.5", new Triangle(3, 5).describe(),
                "Triangle(3, 5) の describe() は \"三角形の面積: 7.5\" です");
    }

    @Test
    @DisplayName("辺が 0 なら面積は 0(境界値)")
    void zero() {
        assertEquals(0.0, new Rectangle(0, 5).area(), 1e-9,
                "幅が 0 の長方形の面積は 0.0 です");
        assertEquals(0.0, new Triangle(4, 0).area(), 1e-9,
                "高さが 0 の三角形の面積は 0.0 です");
    }
}
