import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShopConfigTest {

    /** 設定値(application.yml の代わり)を Environment に足してから ShopConfig を登録する */
    private static ShippingPolicy load(Map<String, Object> settings) {
        try (var ctx = new AnnotationConfigApplicationContext()) {
            ctx.getEnvironment().getPropertySources()
                    .addFirst(new MapPropertySource("test-settings", settings));
            ctx.register(ShopConfig.class);
            ctx.refresh();
            return ctx.getBean(ShippingPolicy.class);
        }
    }

    @Test
    @DisplayName("設定が何も無いときは既定値(Java道場ストア / 5000円 / 500円)になる")
    void defaults() {
        ShippingPolicy p = load(Map.of());
        assertEquals("Java道場ストア", p.shopName(),
                "shop.name が無いときは既定値「Java道場ストア」です。@Value(\"${shop.name:Java道場ストア}\") を付けましたか?");
        assertEquals(5000, p.freeThreshold(),
                "shop.shipping.free-threshold が無いときは既定値 5000 です。プレースホルダの「:既定値」を確認してください");
        assertEquals(500, p.fee(),
                "shop.shipping.fee が無いときは既定値 500 です。プレースホルダの「:既定値」を確認してください");
    }

    @Test
    @DisplayName("設定ファイルの値が既定値より優先される(文字列の \"3000\" も int に変換される)")
    void overridden() {
        ShippingPolicy p = load(Map.of(
                "shop.name", "モカ珈琲店",
                "shop.shipping.free-threshold", "3000",
                "shop.shipping.fee", "300"));
        assertEquals("モカ珈琲店", p.shopName(), "shop.name の設定値が受け取れていません。キーの綴りを確認してください");
        assertEquals(3000, p.freeThreshold(),
                "shop.shipping.free-threshold の設定値が受け取れていません。キーは free-threshold(ハイフン区切り)です");
        assertEquals(300, p.fee(), "shop.shipping.fee の設定値が受け取れていません。キーの綴りを確認してください");
    }

    @Test
    @DisplayName("一部のキーだけ設定したら、残りは既定値のまま")
    void partial() {
        ShippingPolicy p = load(Map.of("shop.shipping.fee", "800"));
        assertEquals(800, p.fee(), "shop.shipping.fee=800 を設定したので送料は 800 です");
        assertEquals(5000, p.freeThreshold(), "free-threshold は設定していないので既定値 5000 のはずです");
        assertEquals("Java道場ストア", p.shopName(), "shop.name は設定していないので既定値のはずです");
    }

    @Test
    @DisplayName("feeFor:無料ラインちょうどは送料 0、1円足りなければ送料がかかる(境界値)")
    void feeForBoundary() {
        ShippingPolicy p = load(Map.of("shop.shipping.free-threshold", "3000", "shop.shipping.fee", "300"));
        assertEquals(300, p.feeFor(2999), "無料ライン 3000 円に 1 円足りないので送料 300 円です");
        assertEquals(0, p.feeFor(3000), "無料ライン「以上」は送料 0 円です。>= になっているか確認してください");
        assertEquals(0, p.feeFor(10000), "無料ラインを超えていれば送料 0 円です");
    }
}
