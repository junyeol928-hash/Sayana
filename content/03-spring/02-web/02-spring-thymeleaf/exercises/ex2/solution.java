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

    public static final String DETAIL = """
            <header th:replace="~{layout :: header('注文詳細')}"></header>
            <section class="order" th:object="${order}">
              <p class="id" th:text="|注文番号: *{id}|">注文番号: 1001</p>
              <p class="customer" th:text="|お客様: *{customer} 様|">お客様: 山田 太郎 様</p>
              <p class="date"
                 th:text="|注文日時: *{#temporals.format(orderedAt, 'yyyy/MM/dd HH:mm')}|">注文日時</p>
              <p class="total"
                 th:text="|合計: *{#numbers.formatInteger(total, 1, 'COMMA')}円|">合計</p>
              <a class="cancel" th:if="*{cancellable()}"
                 th:href="@{/orders/{id}/cancel(id=*{id})}">この注文をキャンセル</a>
              <a class="back" th:href="@{/orders(page=${page})}">注文一覧へ戻る</a>
            </section>
            """;
}
