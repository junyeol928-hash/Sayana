import java.util.List;

// 検索結果の1ページ分と、ページ送りの表示に必要な情報
public record PageView<T>(List<T> content, int number, int size, long totalElements) {

    public static <T> PageView<T> of(List<T> all, int page, int size) {
        // TODO: page < 0 か size < 1 なら IllegalArgumentException
        // TODO: page ページ目の分だけ all から切り出す(範囲外なら空のリスト)
        //       開始位置は long で計算する
        return new PageView<>(List.of(), page, size, 0);
    }

    public int totalPages() {
        // TODO: 全ページ数(切り上げ)
        return 0;
    }

    public boolean hasPrevious() {
        // TODO
        return false;
    }

    public boolean hasNext() {
        // TODO
        return false;
    }

    public String label() {
        // TODO: 「該当する申請はありません」「全 N 件中 X〜Y 件目」「全 N 件(このページに申請はありません)」
        return "";
    }
}
