import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

record Stock(String code, String name, int quantity) {
}

@RestController
@RequestMapping("/api/stocks")
public class StockApiController {

    /** 在庫データ(商品コード → 在庫)。本物のアプリなら DB */
    private final Map<String, Stock> stocks = new HashMap<>();

    public StockApiController() {
        stocks.put("A001", new Stock("A001", "ボールペン", 12));
        stocks.put("B002", new Stock("B002", "ノート", 0));
        stocks.put("C003", new Stock("C003", "消しゴム", 5));
    }

    @GetMapping("/{code}")
    public ResponseEntity<Stock> find(@PathVariable String code) {
        Stock stock = stocks.get(code);
        if (stock == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(stock);
    }

    @PostMapping("/{code}/ship")
    public ResponseEntity<Stock> ship(@PathVariable String code, @RequestParam int qty) {
        Stock stock = stocks.get(code);
        if (stock == null) {
            return ResponseEntity.notFound().build();
        }
        if (qty <= 0) {
            return ResponseEntity.badRequest().build();
        }
        if (stock.quantity() < qty) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        Stock shipped = new Stock(code, stock.name(), stock.quantity() - qty);
        stocks.put(code, shipped);
        return ResponseEntity.ok(shipped);
    }
}
