import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.parser.Part;
import org.springframework.data.repository.query.parser.PartTree;

import static org.junit.jupiter.api.Assertions.fail;

class ApprovalRequestTest {

    /** 解析済みのメソッド(メソッド本体と、Spring Data が読み取った条件) */
    record Parsed(Method method, PartTree tree, List<List<Part>> or) {

        /** 条件が「property の等価比較」1つだけか */
        boolean onlyEquals(String property) {
            return or.size() == 1 && or.get(0).size() == 1
                    && or.get(0).get(0).getProperty().toDotPath().equals(property)
                    && or.get(0).get(0).getType() == Part.Type.SIMPLE_PROPERTY;
        }

        boolean isPlainFind() {
            return !tree.isCountProjection() && !tree.isExistsProjection() && !tree.isDelete()
                    && !tree.isLimiting();
        }

        Sort.Direction direction(String property) {
            Sort.Order order = tree.getSort().getOrderFor(property);
            return order == null ? null : order.getDirection();
        }

        boolean firstParam(Class<?> type) {
            return method.getParameterCount() == 1 && method.getParameterTypes()[0] == type;
        }

        /** 戻り値が container<element> か(List<ApprovalRequest> など) */
        boolean returns(Class<?> container, Class<?> element) {
            Type t = method.getGenericReturnType();
            return t instanceof ParameterizedType p && p.getRawType() == container
                    && p.getActualTypeArguments()[0] == element;
        }
    }

    /** Repository に宣言されたメソッドを、Spring Data と同じ方法(PartTree)で解析する */
    private static List<Parsed> parseAll(Class<?> repository, Class<?> domain) {
        List<Parsed> result = new ArrayList<>();
        for (Method m : repository.getDeclaredMethods()) {
            if (m.isDefault() || m.isSynthetic() || Modifier.isStatic(m.getModifiers())) {
                continue;
            }
            PartTree tree;
            try {
                tree = new PartTree(m.getName(), domain);
            } catch (RuntimeException e) {
                return fail(repository.getSimpleName() + " のメソッド " + m.getName()
                        + " の名前を Spring Data が解析できません: " + e.getMessage()
                        + "(プロパティ名のつづりと、" + domain.getSimpleName() + " のフィールド名が一致しているか確認してください)");
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

    private static void find(Class<?> repository, Class<?> domain, Predicate<Parsed> condition, String message) {
        if (parseAll(repository, domain).stream().noneMatch(condition)) {
            fail(message);
        }
    }

    @Test
    @DisplayName("1. EmployeeRepository:メールアドレスで社員を探し、Optional で返す")
    void findByEmail() {
        find(EmployeeRepository.class, Employee.class,
                p -> p.isPlainFind() && p.onlyEquals("email") && p.firstParam(String.class)
                        && p.returns(Optional.class, Employee.class),
                "EmployeeRepository に「メールアドレスが一致する社員」を返すメソッドが見つかりません。"
                        + "find + By + Email で、戻り値は Optional<Employee>、引数は String です"
                        + "(見つからないことがあるので List でも Employee でもなく Optional にします)");
    }

    @Test
    @DisplayName("2. 自分の申請一覧:申請者で絞り、作成日時の新しい順(Desc)")
    void byApplicant() {
        find(ApprovalRequestRepository.class, ApprovalRequest.class,
                p -> p.isPlainFind() && p.onlyEquals("applicant")
                        && p.direction("createdAt") == Sort.Direction.DESC
                        && p.firstParam(Employee.class) && p.returns(List.class, ApprovalRequest.class),
                "「申請者が一致する申請を作成日時の新しい順に」返すメソッドが見つかりません。"
                        + "By + Applicant + OrderBy + CreatedAt + Desc で、引数は Employee、戻り値は List<ApprovalRequest> です"
                        + "(Asc だと古い順になります)");
    }

    @Test
    @DisplayName("3. 承認待ち一覧:状態で絞り、申請日時の古い順(Asc)")
    void byStatus() {
        find(ApprovalRequestRepository.class, ApprovalRequest.class,
                p -> p.isPlainFind() && p.onlyEquals("status")
                        && p.direction("submittedAt") == Sort.Direction.ASC
                        && p.firstParam(RequestStatus.class) && p.returns(List.class, ApprovalRequest.class),
                "「状態が一致する申請を申請日時の古い順に」返すメソッドが見つかりません。"
                        + "By + Status + OrderBy + SubmittedAt + Asc で、引数は RequestStatus です"
                        + "(並べるのは createdAt ではなく submittedAt)");
    }

    @Test
    @DisplayName("4. 件数バッジ:状態が一致する申請の件数を count で数える")
    void countByStatus() {
        find(ApprovalRequestRepository.class, ApprovalRequest.class,
                p -> p.tree().isCountProjection() && p.onlyEquals("status")
                        && p.firstParam(RequestStatus.class)
                        && (p.method().getReturnType() == long.class || p.method().getReturnType() == Long.class),
                "「状態が一致する申請の件数」を返すメソッドが見つかりません。find ではなく count で始め、"
                        + "count + By + Status、戻り値は long にします"
                        + "(findBy... で List を取って size() すると、全件を読み込んでしまいます)");
    }

    @Test
    @DisplayName("5. ApprovalHistoryRepository:申請で絞り、日時 at の古い順(Asc)")
    void historyByRequest() {
        find(ApprovalHistoryRepository.class, ApprovalHistory.class,
                p -> p.isPlainFind() && p.onlyEquals("request")
                        && p.direction("at") == Sort.Direction.ASC
                        && p.firstParam(ApprovalRequest.class) && p.returns(List.class, ApprovalHistory.class),
                "ApprovalHistoryRepository に「申請が一致する履歴を日時の古い順に」返すメソッドが見つかりません。"
                        + "By + Request + OrderBy + At + Asc で、引数は ApprovalRequest、戻り値は List<ApprovalHistory> です");
    }
}
