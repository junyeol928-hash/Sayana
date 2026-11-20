import java.util.List;

// 検索結果の1ページ分と、ページ送りの表示に必要な情報
public record PageView<T>(
        List<T> content, int number, int size, long totalElements) {

    public static <T> PageView<T> of(List<T> all, int page, int size) {
        if (page < 0 || size < 1) {
            throw new IllegalArgumentException(
                    "page は 0 以上、size は 1 以上にしてください");
        }
        // int の掛け算は桁あふれするので long で
        long from = (long) page * size;
        if (from >= all.size()) {
            return new PageView<>(List.of(), page, size, all.size());
        }
        int to = (int) Math.min(from + size, all.size());
        return new PageView<>(List.copyOf(all.subList((int) from, to)),
                page, size, all.size());
    }

    public int totalPages() {
        // 切り上げ
        return (int) ((totalElements + size - 1) / size);
    }

    public boolean hasPrevious() {
        return number > 0;
    }

    public boolean hasNext() {
        return number + 1 < totalPages();
    }

    public String label() {
        if (totalElements == 0) {
            return "該当する申請はありません";
        }
        String total = String.format("%,d", totalElements);
        if (content.isEmpty()) {
            return "全 " + total + " 件(このページに申請はありません)";
        }
        long first = (long) number * size + 1;
        long last = first + content.size() - 1;
        return "全 " + total + " 件中 " + String.format("%,d", first) + "〜"
                + String.format("%,d", last) + " 件目";
    }
}
