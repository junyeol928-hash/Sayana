import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

public class InventoryApp {

    /** 3層を登録した DI コンテナを作って返す */
    public static AnnotationConfigApplicationContext createContext() {
        // TODO: InventoryController・InventoryService・InMemoryStockRepository を登録したコンテナを返す
        return new AnnotationConfigApplicationContext();
    }
}

// TODO: Controller 層の注釈を付ける
class InventoryController {

    // TODO: final にして、コンストラクタで受け取る(コンストラクタインジェクション)
    private InventoryService service;

    String receive(String sku, int quantity) {
        try {
            return "入荷:" + sku + " 在庫 " + service.receive(sku, quantity) + " 個";
        } catch (IllegalArgumentException e) {
            return "エラー:" + e.getMessage();
        }
    }

    String ship(String sku, int quantity) {
        try {
            return "出荷:" + sku + " 残り " + service.ship(sku, quantity) + " 個";
        } catch (IllegalArgumentException | IllegalStateException e) {
            return "エラー:" + e.getMessage();
        }
    }
}

// TODO: Service 層の注釈を付ける
class InventoryService {

    // TODO: StockRepository(インターフェース)型の final フィールドを作り、コンストラクタで受け取る

    int receive(String sku, int quantity) {
        // TODO: 数量チェック → 今の在庫 + quantity を保存 → 入荷後の在庫数を返す
        return 0;
    }

    int ship(String sku, int quantity) {
        // TODO: 数量チェック → 在庫不足なら IllegalStateException → 減らして保存 → 出荷後の在庫数を返す
        return 0;
    }
}

/** 在庫の保存先(Repository 層) */
interface StockRepository {
    /** 在庫数を返す。登録の無い SKU は 0 */
    int findQuantity(String sku);

    void saveQuantity(String sku, int quantity);
}

// TODO: Repository 層の注釈を付ける
class InMemoryStockRepository implements StockRepository {

    private final Map<String, Integer> stocks = new HashMap<>();

    @Override
    public int findQuantity(String sku) {
        return stocks.getOrDefault(sku, 0);
    }

    @Override
    public void saveQuantity(String sku, int quantity) {
        stocks.put(sku, quantity);
    }
}
