import java.util.List;

/** 商品(Controller から List<Product> として渡される) */
record Product(String name, int price, int stock) {
}

public class ProductListPage {

    // TODO: ダミーデータ入りの HTML に th: 属性を足して、products を表示するテンプレートにする
    //   - <tr class="item"> を th:each で繰り返す(No. はステータス変数の count)
    //   - 商品名は th:text、価格は #numbers.formatInteger(…, 1, 'COMMA') + '円'
    //   - 在庫 0 は「売り切れ」、それ以外は「残り○個」(th:if / th:unless)
    //   - products が空なら表を出さず「商品がありません」だけを出す
    public static final String TEMPLATE = """
            <table>
              <tr><th>No.</th><th>商品名</th><th>価格</th><th>在庫</th></tr>
              <tr class="item">
                <td class="no">1</td>
                <td class="name">ボールペン</td>
                <td class="price">1,200円</td>
                <td class="stock">残り12個</td>
              </tr>
              <tr class="item">
                <td class="no">2</td>
                <td class="name">ノート</td>
                <td class="price">98円</td>
                <td class="stock">売り切れ</td>
              </tr>
            </table>
            <p class="empty">商品がありません</p>
            """;

    /** 画面に出すサンプルデータ(STS なら Controller が DB から取ってくる) */
    public static List<Product> sampleProducts() {
        return List.of(
                new Product("ボールペン", 1200, 12),
                new Product("ノート", 98, 0));
    }
}
