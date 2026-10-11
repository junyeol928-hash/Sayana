import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

import org.springframework.data.jpa.repository.JpaRepository;

enum Role { APPLICANT, APPROVER, ADMIN }

enum RequestType { EXPENSE, LEAVE, PURCHASE }

enum RequestStatus { DRAFT, SUBMITTED, APPROVED, RETURNED, WITHDRAWN }

enum Action { SUBMIT, APPROVE, RETURN, WITHDRAW }

// ===== エンティティ(完成済み。プロパティ名を確認するのに使ってください) =====

@Entity
class Employee {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String email;
    @Enumerated(EnumType.STRING)
    private Role role;

    protected Employee() {
    }
}

@Entity
public class ApprovalRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String title;
    @Enumerated(EnumType.STRING)
    private RequestType type;
    private Integer amount;
    private String reason;
    @Enumerated(EnumType.STRING)
    private RequestStatus status = RequestStatus.DRAFT;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Employee applicant;
    @ManyToOne(fetch = FetchType.LAZY)
    private Employee approver;
    private String comment;
    private LocalDateTime createdAt;
    private LocalDateTime submittedAt;
    private LocalDateTime decidedAt;

    protected ApprovalRequest() {
    }
}

@Entity
class ApprovalHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private ApprovalRequest request;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Employee actor;
    @Enumerated(EnumType.STRING)
    private Action action;
    private String comment;
    private LocalDateTime at;

    protected ApprovalHistory() {
    }
}

// ===== Repository(ここに派生クエリを宣言する) =====

interface EmployeeRepository extends JpaRepository<Employee, Long> {

    // 1. ログイン:メールアドレスで社員を探す
    Optional<Employee> findByEmail(String email);
}

interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {

    // 2. 自分の申請一覧(新しい順)
    List<ApprovalRequest> findByApplicantOrderByCreatedAtDesc(Employee applicant);

    // 3. 承認待ち一覧(待たせている順)
    List<ApprovalRequest> findByStatusOrderBySubmittedAtAsc(RequestStatus status);

    // 4. 件数バッジ
    long countByStatus(RequestStatus status);
}

interface ApprovalHistoryRepository extends JpaRepository<ApprovalHistory, Long> {

    // 5. 詳細画面の履歴(古い順)
    List<ApprovalHistory> findByRequestOrderByAtAsc(ApprovalRequest request);
}
