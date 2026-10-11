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

    // TODO: GET /stocks/{code} に対応付ける(@GetMapping)
    public ResponseEntity<Stock> find(@PathVariable("code") String code) {
        // TODO: 見つかれば 200 + Stock(JSON になる)、無ければ 404
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build(); // ← ダミー
    }

    // TODO: POST /stocks/{code}/ship に対応付ける(@PostMapping)
    public ResponseEntity<String> ship(@PathVariable("code") String code,
                                       @RequestParam("qty") int qty) {
        // TODO: 商品が無ければ 404
        // TODO: qty が 0 以下なら 400 +「数量は1以上で指定してください」
        // TODO: qty が在庫より多ければ 409(HttpStatus.CONFLICT)+「在庫不足です(残り 12)」
        // TODO: それ以外は在庫を減らした新しい Stock を put し、200 +「出荷しました(残り 9)」
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build(); // ← ダミー
    }
}

/** 在庫。JSON では {"code":"A001","name":"ボールペン","quantity":12} になる */
record Stock(String code, String name, int quantity) { }
