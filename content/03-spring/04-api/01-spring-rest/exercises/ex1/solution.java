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

    @GetMapping
    public List<Item> list(@RequestParam(defaultValue = "false") boolean inStock) {
        return items.values().stream()
                .filter(i -> !inStock || i.stock() > 0)
                .toList();
    }

    @GetMapping("/{code}")
    public ResponseEntity<Item> find(@PathVariable String code) {
        // Optional が空なら 404、中身があれば 200 + 本文
        return ResponseEntity.of(Optional.ofNullable(items.get(code)));
    }
}
