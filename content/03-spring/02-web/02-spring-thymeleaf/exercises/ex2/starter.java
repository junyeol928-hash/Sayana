import java.time.LocalDateTime;

/** 注文。status は "ORDERED"(受付済)・"SHIPPED"(出荷済)など */
record Order(long id, String customer, LocalDateTime orderedAt, int total, String status) {

    /** 出荷前ならキャンセルできる(テンプレートから *{cancellable()} で呼べる) */
    public boolean cancellable() {
        return "ORDERED".equals(status);
    }
}

public class OrderPage {

    /** 共通レイアウト(完成品。テンプレート名は "layout") */
    public static final String LAYOUT = """
            <header th:fragment="header(title)" class="site">
              <a th:href="@{/}">Java商店</a>
              <h1 th:text="${title}">ページタイトル</h1>
            </header>
            """;

    // TODO: ダミーの HTML を、order と page を表示するテンプレートにする
    //   - ヘッダーは th:replace="~{layout :: header('注文詳細')}" で LAYOUT のフラグメントを使う
    //   - <section> に th:object="${order}"、中は *{…} で値を出す
    //   - 日時は #temporals.format、合計は #numbers.formatInteger
    //   - キャンセルリンクは *{cancellable()} が true のときだけ。URL は @{/orders/{id}/cancel(id=…)}
    //   - 戻るリンクは @{/orders(page=${page})}
    public static final String DETAIL = """
            <header class="site">
              <a href="/">Java商店</a>
              <h1>注文詳細</h1>
            </header>
            <section class="order">
              <p class="id">注文番号: 1001</p>
              <p class="customer">お客様: 山田 太郎 様</p>
              <p class="date">注文日時: 2026/10/11 09:05</p>
              <p class="total">合計: 3,480円</p>
              <a class="cancel" href="/orders/1001/cancel">この注文をキャンセル</a>
              <a class="back" href="/orders?page=1">注文一覧へ戻る</a>
            </section>
            """;
}
