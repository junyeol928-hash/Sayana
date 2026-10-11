import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 返す JSON の形(DTO)。record なので Jackson がそのまま JSON にしてくれる */
record Item(String code, String name, int stock) {
}

@RestController
@RequestMapping("/api/items")
public class InventoryController {

    // 本物のアプリなら DB。ここではメモリ上の Map(TreeMap なので商品コード順)
    private final Map<String, Item> items = new TreeMap<>();

    public InventoryController() {
        put(new Item("A-001", "ボールペン", 120));
        put(new Item("A-002", "ノート", 0));
        put(new Item("B-010", "クリアファイル", 35));
    }

    private void put(Item item) {
        items.put(item.code(), item);
    }

    // TODO: @GetMapping を付ける
    // TODO: 引数 inStock に @RequestParam(defaultValue = "false") を付ける
    public List<Item> list(boolean inStock) {
        // TODO: items.values() を返す。inStock が true なら stock > 0 の商品だけにする
        return List.of(); // ← ダミー
    }

    // TODO: @GetMapping("/{code}") を付け、引数 code に @PathVariable を付ける
    public ResponseEntity<Item> find(String code) {
        // TODO: 見つからなければ 404、見つかれば 200 + 本文
        //       ヒント: ResponseEntity.of(Optional.ofNullable(...))
        return ResponseEntity.notFound().build(); // ← ダミー
    }
}
