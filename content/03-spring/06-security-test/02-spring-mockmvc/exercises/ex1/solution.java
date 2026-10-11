import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/stocks")
public class StockController {

    // 本物のアプリでは Service / Repository の先の DB。ここでは簡単のためメモリ上に持つ
    private final Map<String, Stock> stocks = new LinkedHashMap<>();

    public StockController() {
        stocks.put("A001", new Stock("A001", "ボールペン", 12));
        stocks.put("B002", new Stock("B002", "ノート", 0));
    }

    @GetMapping("/{code}")
    public ResponseEntity<Stock> find(@PathVariable("code") String code) {
        Stock stock = stocks.get(code);
        if (stock == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(stock);
    }

    @PostMapping("/{code}/ship")
    public ResponseEntity<String> ship(@PathVariable("code") String code,
                                       @RequestParam("qty") int qty) {
        Stock stock = stocks.get(code);
        if (stock == null) {
            return ResponseEntity.notFound().build();
        }
        if (qty <= 0) {
            return ResponseEntity.badRequest().body("数量は1以上で指定してください");
        }
        if (qty > stock.quantity()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("在庫不足です(残り " + stock.quantity() + ")");
        }
        Stock shipped = new Stock(stock.code(), stock.name(), stock.quantity() - qty);
        stocks.put(code, shipped);
        return ResponseEntity.ok("出荷しました(残り " + shipped.quantity() + ")");
    }
}

/** 在庫。JSON では {"code":"A001","name":"ボールペン","quantity":12} になる */
record Stock(String code, String name, int quantity) { }
