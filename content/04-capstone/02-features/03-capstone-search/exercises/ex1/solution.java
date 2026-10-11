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
        Sort s = Sort.by(parseOrder(sort), TIE_BREAKER);
        return PageRequest.of(parsePage(page), parseSize(size), s);
    }

    static int parsePage(String page) {
        Integer n = toInt(page);
        return n == null ? 0 : Math.max(0, n);
    }

    static int parseSize(String size) {
        Integer n = toInt(size);
        return n == null ? DEFAULT_SIZE : Math.max(1, Math.min(MAX_SIZE, n));
    }

    static Sort.Order parseOrder(String sort) {
        if (sort == null || sort.isBlank()) {
            return DEFAULT_ORDER;
        }
        String[] parts = sort.split(",", -1);  // -1: 末尾の空要素も残す
        if (parts.length > 2) {
            return DEFAULT_ORDER;
        }
        String property = parts[0].strip();
        if (!SORTABLE.contains(property)) {
            return DEFAULT_ORDER;   // 許可していない項目は使わない
        }
        if (parts.length == 1) {
            return Sort.Order.asc(property);
        }
        Optional<Sort.Direction> dir = Sort.Direction.fromOptionalString(parts[1].strip());
        return dir.map(d -> new Sort.Order(d, property)).orElse(DEFAULT_ORDER);
    }

    private static Integer toInt(String s) {
        if (s == null) {
            return null;
        }
        try {
            return Integer.parseInt(s.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
