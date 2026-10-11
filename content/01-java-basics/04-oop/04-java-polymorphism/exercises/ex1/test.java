import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AreaCalculatorTest {

    @Test
    @DisplayName("長方形と三角形の総面積")
    void totalOfMixed() {
        Shape[] shapes = { new Rectangle(3, 4), new Triangle(4, 5) };
        assertEquals(22.0, AreaCalculator.totalArea(shapes), 1e-9,
                "長方形 3×4(12.0)と三角形 4×5(10.0)の合計は 22.0 です。各図形の area() を足していますか?");
    }

    @Test
    @DisplayName("円を含む3種類の総面積")
    void totalWithCircle() {
        Shape[] shapes = { new Circle(1), new Rectangle(2, 2), new Triangle(2, 3) };
        assertEquals(Math.PI + 4 + 3, AreaCalculator.totalArea(shapes), 1e-9,
                "半径1の円(π)+ 長方形 2×2(4.0)+ 三角形 2×3(3.0)の合計になるはずです");
    }

    @Test
    @DisplayName("空の配列なら総面積は 0.0")
    void totalOfEmpty() {
        assertEquals(0.0, AreaCalculator.totalArea(new Shape[0]), 1e-9,
                "図形が1つもなければ合計は 0.0 です");
    }

    @Test
    @DisplayName("面積が limit より大きい図形だけを数える")
    void countLarger() {
        Shape[] shapes = { new Rectangle(3, 4), new Triangle(4, 5), new Circle(1) };
        assertEquals(1, AreaCalculator.countLargerThan(shapes, 10.0),
                "面積 12.0 / 10.0 / 3.14… のうち 10.0 より大きいのは 1 個です");
        assertEquals(3, AreaCalculator.countLargerThan(shapes, 0.0),
                "limit が 0.0 なら 3 個すべてが該当します");
    }

    @Test
    @DisplayName("面積がちょうど limit の図形は数えない(境界値)")
    void countBoundary() {
        Shape[] shapes = { new Rectangle(2, 5), new Triangle(4, 5) };
        assertEquals(0, AreaCalculator.countLargerThan(shapes, 10.0),
                "面積がちょうど 10.0 の図形は「より大きい」に含みません。>= ではなく > で比べてください");
    }
}
