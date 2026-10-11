import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FizzBuzzTest {

    @Test
    @DisplayName("n = 1 のときは \"1\" だけを返す")
    void one() {
        assertEquals("1", FizzBuzz.fizzBuzz(1),
                "n = 1 なら \"1\" だけになるはずです。ループの開始値(1から)と条件式(<= n)を確認しましょう");
    }

    @Test
    @DisplayName("n = 5 で Fizz と Buzz が正しい位置に出る")
    void five() {
        assertEquals("1\n2\nFizz\n4\nBuzz", FizzBuzz.fizzBuzz(5),
                "3 は Fizz、5 は Buzz、それ以外は数字、行は \\n 区切りです。最後の 5 まで含まれているかも確認しましょう");
    }

    @Test
    @DisplayName("15 の倍数は FizzBuzz になる")
    void fifteen() {
        String expected = "1\n2\nFizz\n4\nBuzz\nFizz\n7\n8\nFizz\nBuzz\n11\nFizz\n13\n14\nFizzBuzz";
        assertEquals(expected, FizzBuzz.fizzBuzz(15),
                "15 が \"Fizz\" になっていませんか? 15 の倍数の判定を、3 の倍数の判定より先に書きましょう");
    }

    @Test
    @DisplayName("最後の行の後ろに改行が付かない")
    void noTrailingNewline() {
        String result = FizzBuzz.fizzBuzz(30);
        assertFalse(result.endsWith("\n"),
                "末尾に改行が付いています。区切りの \\n は「2行目以降の前に」付けましょう");
        assertTrue(result.endsWith("29\nFizzBuzz"),
                "n = 30 の最後は \"29\" → \"FizzBuzz\" で終わるはずです");
        assertEquals(30, result.split("\n").length,
                "n = 30 なら 30 行になるはずです。ループ回数(off-by-one)を確認しましょう");
    }

    @Test
    @DisplayName("n が 0 以下なら空文字列を返す")
    void zeroOrNegative() {
        assertEquals("", FizzBuzz.fizzBuzz(0), "n = 0 のときは空文字列 \"\" を返してください");
        assertEquals("", FizzBuzz.fizzBuzz(-3), "n が負のときも空文字列 \"\" を返してください");
    }
}
