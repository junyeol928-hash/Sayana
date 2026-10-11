import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.parser.Part;
import org.springframework.data.repository.query.parser.PartTree;

import static org.junit.jupiter.api.Assertions.fail;

class BookTest {

    /** 解析済みのメソッド(メソッド本体と、Spring Data が読み取った条件) */
    record Parsed(Method method, PartTree tree, List<List<Part>> or) {

        /** OR で区切られていない(1グループの)条件の一覧 */
        List<Part> andParts() {
            return or.size() == 1 ? or.get(0) : List.of();
        }

        boolean isPlainFind() {
            return !tree.isCountProjection() && !tree.isExistsProjection() && !tree.isDelete()
                    && method.getReturnType() == List.class;
        }

        boolean hasPart(int index, String property, Part.Type type) {
            List<Part> parts = andParts();
            return parts.size() > index
                    && parts.get(index).getProperty().toDotPath().equals(property)
                    && parts.get(index).getType() == type;
        }

        Sort.Direction direction(String property) {
            Sort.Order order = tree.getSort().getOrderFor(property);
            return order == null ? null : order.getDirection();
        }
    }

    /** BookRepository に宣言されたメソッドを、Spring Data と同じ方法(PartTree)で解析する */
    private static List<Parsed> parseAll() {
        List<Parsed> result = new ArrayList<>();
        for (Method m : BookRepository.class.getDeclaredMethods()) {
            if (m.isDefault() || m.isSynthetic() || java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                continue;
            }
            if (m.getAnnotation(org.springframework.data.jpa.repository.Query.class) != null) {
                continue; // @Query 付きはメソッド名を解析しない
            }
            PartTree tree;
            try {
                tree = new PartTree(m.getName(), Book.class);
            } catch (RuntimeException e) {
                return fail("メソッド " + m.getName() + " の名前を Spring Data が解析できません: " + e.getMessage()
                        + "(プロパティ名のつづりと、Book のフィールド名が一致しているか確認してください)");
            }
            List<List<Part>> or = new ArrayList<>();
            tree.forEach(orPart -> {
                List<Part> parts = new ArrayList<>();
                orPart.forEach(parts::add);
                or.add(parts);
            });
            int args = or.stream().flatMap(List::stream).mapToInt(Part::getNumberOfArguments).sum();
            if (args != m.getParameterCount()) {
                fail("メソッド " + m.getName() + " は名前から " + args + " 個の引数が必要ですが、"
                        + m.getParameterCount() + " 個宣言されています");
            }
            result.add(new Parsed(m, tree, or));
        }
        return result;
    }

    private static Parsed find(Predicate<Parsed> condition, String message) {
        return parseAll().stream().filter(condition).findFirst().orElseGet(() -> fail(message));
    }

    @Test
    @DisplayName("1. 著者名の完全一致で探すメソッドがある")
    void byAuthor() {
        find(p -> p.isPlainFind() && p.andParts().size() == 1
                        && p.hasPart(0, "author", Part.Type.SIMPLE_PROPERTY)
                        && !p.tree().isLimiting() && p.tree().getSort().isUnsorted(),
                "「著者名が完全一致する本」を返すメソッドが見つかりません。find + By + プロパティ名(author)で、戻り値は List<Book> です");
    }

    @Test
    @DisplayName("2. 書名の部分一致(Containing)で探すメソッドがある")
    void titleContaining() {
        find(p -> p.isPlainFind() && p.andParts().size() == 1
                        && p.hasPart(0, "title", Part.Type.CONTAINING),
                "「書名に keyword を含む本」を返すメソッドが見つかりません。title の後ろに Containing を付けます"
                        + "(Like だと % を自分で付ける必要があるので、ここでは Containing を使ってください)");
    }

    @Test
    @DisplayName("3. 価格の範囲(Between)で探し、安い順に並べるメソッドがある")
    void priceBetweenOrderByPrice() {
        find(p -> p.isPlainFind() && p.andParts().size() == 1
                        && p.hasPart(0, "price", Part.Type.BETWEEN)
                        && p.direction("price") == Sort.Direction.ASC,
                "「価格が min 以上 max 以下の本を安い順に」返すメソッドが見つかりません。"
                        + "price の後ろに Between、最後に OrderByPriceAsc を付けます(引数は min と max の2つ)");
    }

    @Test
    @DisplayName("4. カテゴリ指定で、発行年の新しい順に上位3冊だけ返すメソッドがある")
    void top3ByCategory() {
        find(p -> p.isPlainFind() && p.andParts().size() == 1
                        && p.hasPart(0, "category", Part.Type.SIMPLE_PROPERTY)
                        && p.tree().isLimiting() && Integer.valueOf(3).equals(p.tree().getMaxResults())
                        && p.direction("publishedYear") == Sort.Direction.DESC,
                "「指定カテゴリの本のうち発行年が新しい上位3冊」を返すメソッドが見つかりません。"
                        + "find の直後に Top3、最後に OrderByPublishedYearDesc を付けます(Asc だと古い順になります)");
    }

    @Test
    @DisplayName("5. 在庫数が指定未満の冊数を数える(count)メソッドがある")
    void countByStockLessThan() {
        find(p -> p.tree().isCountProjection() && p.andParts().size() == 1
                        && p.hasPart(0, "stock", Part.Type.LESS_THAN)
                        && (p.method().getReturnType() == long.class || p.method().getReturnType() == Long.class),
                "「在庫数が threshold 未満の本の冊数」を返すメソッドが見つかりません。find ではなく count で始め、"
                        + "stock の後ろに LessThan(未満)を付けて、戻り値は long にします(LessThanEqual は「以下」です)");
    }

    @Test
    @DisplayName("6. 著者名が一致 かつ 発行年が指定年以降(And)で探すメソッドがある")
    void authorAndYear() {
        find(p -> p.isPlainFind() && p.andParts().size() == 2
                        && p.hasPart(0, "author", Part.Type.SIMPLE_PROPERTY)
                        && p.hasPart(1, "publishedYear", Part.Type.GREATER_THAN_EQUAL),
                "「著者名が一致し、かつ発行年が year 以降」の本を返すメソッドが見つかりません。"
                        + "Author と PublishedYearGreaterThanEqual を And でつなぎます(「以降」は year 自身を含むので GreaterThan ではありません)");
    }
}
