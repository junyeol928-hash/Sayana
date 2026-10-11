import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

public class InventoryApp {

    /** 3層を登録した DI コンテナを作って返す */
    public static AnnotationConfigApplicationContext createContext() {
        return new AnnotationConfigApplicationContext(
                InventoryController.class,
                InventoryService.class,
                InMemoryStockRepository.class);
    }
}

@Controller
class InventoryController {

    private final InventoryService service;   // Repository は知らない

    InventoryController(InventoryService service) {
        this.service = service;
    }

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

@Service
class InventoryService {

    private final StockRepository repository;   // インターフェースに依存する

    InventoryService(StockRepository repository) {
        this.repository = repository;
    }

    int receive(String sku, int quantity) {
        checkQuantity(quantity);
        int after = repository.findQuantity(sku) + quantity;
        repository.saveQuantity(sku, after);
        return after;
    }

    int ship(String sku, int quantity) {
        checkQuantity(quantity);
        int current = repository.findQuantity(sku);
        if (current < quantity) {
            throw new IllegalStateException(sku + " の在庫が足りません(在庫 " + current + " 個)");
        }
        int after = current - quantity;
        repository.saveQuantity(sku, after);
        return after;
    }

    private static void checkQuantity(int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("数量は1以上にしてください");
        }
    }
}

/** 在庫の保存先(Repository 層) */
interface StockRepository {
    /** 在庫数を返す。登録の無い SKU は 0 */
    int findQuantity(String sku);

    void saveQuantity(String sku, int quantity);
}

@Repository
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
