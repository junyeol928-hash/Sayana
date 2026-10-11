import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ShopConfig {

    @Bean
    ShippingPolicy shippingPolicy(
            @Value("${shop.name:Java道場ストア}") String name,
            @Value("${shop.shipping.free-threshold:5000}") int freeThreshold,
            @Value("${shop.shipping.fee:500}") int fee) {
        return new ShippingPolicy(name, freeThreshold, fee);
    }
}

/** 送料ルール(STS では com.example.shop.domain パッケージの別ファイルに置く) */
record ShippingPolicy(String shopName, int freeThreshold, int fee) {

    int feeFor(int amount) {
        return amount >= freeThreshold ? 0 : fee;
    }
}
