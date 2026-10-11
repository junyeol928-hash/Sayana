import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShippingLabelTest {

    @Test
    @DisplayName("住所がそろっていれば、前後の空白を除いた郵便番号と市区町村")
    void complete() {
        Customer c = new Customer("佐藤", new Address(" 100-0001 ", "  千代田区 "));
        assertEquals("100-0001", ShippingLabel.zipOf(c),
                "郵便番号の前後の空白は strip で取り除いてください");
        assertEquals(Optional.of("千代田区"), ShippingLabel.cityOf(c),
                "市区町村も前後の空白を除いて Optional に入れて返してください");
    }

    @Test
    @DisplayName("顧客そのものが null でも例外にならない")
    void nullCustomer() {
        assertEquals("未登録", ShippingLabel.zipOf(null),
                "顧客が null なら \"未登録\" です。Optional.ofNullable(c) から始めていますか?");
        Optional<String> city = ShippingLabel.cityOf(null);
        assertNotNull(city, "cityOf は null ではなく空の Optional を返してください");
        assertTrue(city.isEmpty(), "顧客が null なら市区町村は空の Optional です");
    }

    @Test
    @DisplayName("住所が null なら郵便番号は \"未登録\"、市区町村は空")
    void nullAddress() {
        Customer c = new Customer("鈴木", null);
        assertEquals("未登録", ShippingLabel.zipOf(c),
                "住所が null のときは \"未登録\" を返してください(NullPointerException になっていませんか?)");
        assertTrue(ShippingLabel.cityOf(c).isEmpty(), "住所が null なら市区町村は空の Optional です");
    }

    @Test
    @DisplayName("郵便番号だけ null なら \"未登録\"、市区町村は取れる")
    void nullZip() {
        Customer c = new Customer("田中", new Address(null, "大阪市"));
        assertEquals("未登録", ShippingLabel.zipOf(c), "郵便番号が null なら \"未登録\" です");
        assertEquals(Optional.of("大阪市"), ShippingLabel.cityOf(c),
                "郵便番号が null でも、市区町村は取り出せるはずです");
    }

    @Test
    @DisplayName("空白だけ・空文字は未入力扱い(境界値)")
    void blank() {
        Customer spaces = new Customer("伊藤", new Address("   ", "   "));
        assertEquals("未登録", ShippingLabel.zipOf(spaces),
                "空白だけの郵便番号は未入力扱いです。strip のあと filter で空文字を落としていますか?");
        assertTrue(ShippingLabel.cityOf(spaces).isEmpty(), "空白だけの市区町村は空の Optional にしてください");
        Customer empty = new Customer("渡辺", new Address("", ""));
        assertEquals("未登録", ShippingLabel.zipOf(empty), "空文字の郵便番号も \"未登録\" です");
    }
}
