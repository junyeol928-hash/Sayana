import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

// 検索画面のクエリパラメータ(文字列)から、安全な PageRequest を組み立てる
public class PagingParams {

    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 50;
    static final Set<String> SORTABLE = Set.of("createdAt", "submittedAt", "amount");
    static final Sort.Order DEFAULT_ORDER = Sort.Order.desc("createdAt");
    static final Sort.Order TIE_BREAKER = Sort.Order.desc("id");

    private PagingParams() {
    }

    public static PageRequest of(String page, String size, String sort) {
        // TODO: parseOrder の結果と TIE_BREAKER(id の降順)を並べた Sort を作り、
        //       PageRequest.of(page, size, sort) を返す
        return PageRequest.of(0, 1);
    }

    static int parsePage(String page) {
        // TODO: 整数にできなければ 0、負なら 0
        return 0;
    }

    static int parseSize(String size) {
        // TODO: 整数にできなければ DEFAULT_SIZE、できたら 1〜MAX_SIZE に丸める
        return 1;
    }

    static Sort.Order parseOrder(String sort) {
        // TODO: "項目" または "項目,方向" を読む。
        //       項目が SORTABLE に無い・方向が asc/desc でない・形がおかしい → DEFAULT_ORDER
        //       方向の省略は昇順(Sort.Order.asc)
        return DEFAULT_ORDER;
    }
}
