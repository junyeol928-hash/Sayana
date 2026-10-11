import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

record Stock(String code, String name, int quantity) {
}

// TODO: @RestController と @RequestMapping("/api/stocks") を付ける
public class StockApiController {

    /** 在庫データ(商品コード → 在庫)。本物のアプリなら DB */
    private final Map<String, Stock> stocks = new HashMap<>();

    public StockApiController() {
        stocks.put("A001", new Stock("A001", "ボールペン", 12));
        stocks.put("B002", new Stock("B002", "ノート", 0));
        stocks.put("C003", new Stock("C003", "消しゴム", 5));
    }

    // TODO: GET /api/stocks/{code} に対応させる
    // あれば 200 + JSON、無ければ 404
    public ResponseEntity<Stock> find(String code) {
        return null; // ← ダミー
    }

    // TODO: POST /api/stocks/{code}/ship?qty=… に対応させる
    // 404(商品なし) → 400(qty が 0 以下) → 409(在庫不足) → 200(出荷後の在庫)の順にチェック
    public ResponseEntity<Stock> ship(String code, int qty) {
        return null; // ← ダミー
    }
}
