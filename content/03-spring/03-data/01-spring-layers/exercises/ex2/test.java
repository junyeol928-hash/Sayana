import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class InventoryAppTest {

    private AnnotationConfigApplicationContext ctx;

    @AfterEach
    void close() {
        if (ctx != null) {
            ctx.close();
        }
    }

    /** コンテナを作って、指定した Bean を取り出す。失敗したら原因を日本語で示す */
    private <T> T bean(Class<T> type) {
        if (ctx == null) {
            try {
                ctx = InventoryApp.createContext();
            } catch (Exception e) {
                fail("createContext() でコンテナを作れませんでした。3つのクラスを全部登録し、"
                        + "コンストラクタインジェクションになっているか確認してください。原因: " + e.getMessage());
            }
        }
        try {
            return ctx.getBean(type);
        } catch (Exception e) {
            fail(type.getSimpleName() + " の Bean を取り出せません。createContext() で "
                    + "new AnnotationConfigApplicationContext(InventoryController.class, InventoryService.class, "
                    + "InMemoryStockRepository.class) のように登録していますか? 原因: " + e.getMessage());
            return null;
        }
    }

    private static List<Field> instanceFields(Class<?> c) {
        List<Field> list = new ArrayList<>();
        for (Field f : c.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers())) {
                list.add(f);
            }
        }
        return list;
    }

    @Test
    @DisplayName("コンテナから3層の Bean を取り出せ、Repository の実体はメモリ実装")
    void contextAssembles() {
        InventoryController controller = bean(InventoryController.class);
        InventoryService service = bean(InventoryService.class);
        StockRepository repo = bean(StockRepository.class);
        assertInstanceOf(InMemoryStockRepository.class, repo, "StockRepository の Bean は InMemoryStockRepository のはずです");
        assertSame(service, bean(InventoryService.class), "Bean はシングルトンなので、何度取り出しても同じインスタンスです");
        assertTrue(controller != null, "InventoryController の Bean がありません");
    }

    @Test
    @DisplayName("各クラスに層に合った注釈(@Controller / @Service / @Repository)が付いている")
    void annotations() {
        assertTrue(InventoryController.class.isAnnotationPresent(Controller.class), "InventoryController に @Controller を付けてください");
        assertTrue(InventoryService.class.isAnnotationPresent(Service.class), "InventoryService に @Service を付けてください");
        assertTrue(InMemoryStockRepository.class.isAnnotationPresent(Repository.class), "InMemoryStockRepository に @Repository を付けてください");
    }

    @Test
    @DisplayName("依存の向き:Controller は Service だけを持ち、Service は StockRepository インターフェースに依存する")
    void dependencyDirection() {
        List<Field> cf = instanceFields(InventoryController.class);
        assertTrue(cf.stream().anyMatch(f -> f.getType() == InventoryService.class && Modifier.isFinal(f.getModifiers())),
                "InventoryController には InventoryService 型の final フィールドが必要です(コンストラクタで受け取る)");
        assertFalse(cf.stream().anyMatch(f -> StockRepository.class.isAssignableFrom(f.getType())),
                "Controller が Repository を直接持っています。層を飛ばさず、Service を通してください");

        List<Field> sf = instanceFields(InventoryService.class);
        assertTrue(sf.stream().anyMatch(f -> f.getType() == StockRepository.class && Modifier.isFinal(f.getModifiers())),
                "InventoryService には StockRepository(インターフェース)型の final フィールドが必要です");
        assertFalse(sf.stream().anyMatch(f -> f.getType() == InMemoryStockRepository.class),
                "Service がメモリ実装のクラスに直接依存しています。型は StockRepository(インターフェース)にしてください");
    }

    @Test
    @DisplayName("入荷して出荷すると、Controller → Service → Repository を通って在庫が変わる")
    void receiveAndShip() {
        InventoryController c = bean(InventoryController.class);
        assertEquals("入荷:A-001 在庫 10 個", c.receive("A-001", 10), "入荷後の在庫数を返してください");
        assertEquals("入荷:A-001 在庫 15 個", c.receive("A-001", 5), "入荷は今の在庫に足し算です");
        assertEquals("出荷:A-001 残り 12 個", c.ship("A-001", 3), "出荷後の在庫数を返してください");
        assertEquals(12, bean(StockRepository.class).findQuantity("A-001"),
                "在庫数が Repository に保存されていません。saveQuantity を呼んでいますか?");
        assertEquals("入荷:B-002 在庫 1 個", c.receive("B-002", 1), "SKU ごとに在庫を分けて管理してください");
    }

    @Test
    @DisplayName("在庫不足はエラーで在庫は変わらない。在庫ちょうどの出荷は OK(境界値)")
    void shortage() {
        InventoryController c = bean(InventoryController.class);
        c.receive("A-001", 10);
        assertEquals("エラー:A-001 の在庫が足りません(在庫 10 個)", c.ship("A-001", 11),
                "在庫より多い出荷は IllegalStateException(\"A-001 の在庫が足りません(在庫 10 個)\")にしてください");
        assertEquals(10, bean(StockRepository.class).findQuantity("A-001"), "在庫不足のときは在庫を変えないでください");
        assertEquals("出荷:A-001 残り 0 個", c.ship("A-001", 10), "在庫ちょうどの出荷は OK です(残り 0 個)");
    }

    @Test
    @DisplayName("数量が 0 以下なら入荷・出荷ともエラー")
    void invalidQuantity() {
        InventoryController c = bean(InventoryController.class);
        assertEquals("エラー:数量は1以上にしてください", c.receive("A-001", 0), "入荷数 0 は IllegalArgumentException にしてください");
        assertEquals("エラー:数量は1以上にしてください", c.ship("A-001", -1), "出荷数がマイナスなら IllegalArgumentException にしてください");
        assertEquals(0, bean(StockRepository.class).findQuantity("A-001"), "数量エラーのときは在庫を変えないでください");
    }
}
