import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ShopConfig {

    @Bean
    ShippingPolicy shippingPolicy(
            // TODO: @Value("${shop.name:Java道場ストア}") のように、3つの引数に @Value を付ける
            String name,
            int freeThreshold,
            int fee) {
        // TODO: 受け取った値で ShippingPolicy を作って返す
        return new ShippingPolicy("", 0, 0); // ← ダミー。書き換えてください
    }
}

/** 送料ルール(STS では com.example.shop.domain パッケージの別ファイルに置く) */
record ShippingPolicy(String shopName, int freeThreshold, int fee) {

    int feeFor(int amount) {
        // TODO: amount が freeThreshold 以上なら 0、未満なら fee を返す
        return -1; // ← ダミー。書き換えてください
    }
}
