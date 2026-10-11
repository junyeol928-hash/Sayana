/** 商品(Controller から List<Product> として渡される) */
record Product(String name, int price, int stock) {
}

public class ProductListPage {

    public static final String TEMPLATE = """
            <table th:unless="${products.isEmpty()}">
              <tr><th>No.</th><th>商品名</th><th>価格</th><th>在庫</th></tr>
              <tr class="item" th:each="p, st : ${products}">
                <td class="no" th:text="${st.count}">1</td>
                <td class="name" th:text="${p.name}">ボールペン</td>
                <td class="price"
                    th:text="${#numbers.formatInteger(p.price, 1, 'COMMA')} + '円'">1,200円</td>
                <td class="stock" th:if="${p.stock == 0}">売り切れ</td>
                <td class="stock" th:unless="${p.stock == 0}"
                    th:text="'残り' + ${p.stock} + '個'">残り12個</td>
              </tr>
              <tr class="item" th:remove="all">
                <td class="no">2</td>
                <td class="name">ノート</td>
                <td class="price">98円</td>
                <td class="stock">売り切れ</td>
              </tr>
            </table>
            <p class="empty" th:if="${products.isEmpty()}">商品がありません</p>
            """;
}
