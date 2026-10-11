import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;

enum Role { APPLICANT, APPROVER, ADMIN }

enum RequestType { EXPENSE, LEAVE, PURCHASE }

enum RequestStatus { DRAFT, SUBMITTED, APPROVED, RETURNED, WITHDRAWN }

// 社員(完成済み。アノテーションの書き方の見本)
@Entity
class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    protected Employee() {
    }

    Employee(String name, String email, Role role) {
        this.name = name;
        this.email = email;
        this.role = role;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public Role getRole() { return role; }
}

// 申請
// TODO: 1. エンティティであることを示すアノテーション
public class ApprovalRequest {

    // TODO: 2. 主キー + DB の自動採番(IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    // TODO: 3. enum を名前の文字列で保存する(nullable = false, length = 20 の @Column も)
    private RequestType type;

    private Integer amount;          // LEAVE(休暇)では null

    @Column(length = 1000)
    private String reason;

    // TODO: 3. enum を名前の文字列で保存する(nullable = false, length = 20 の @Column も)
    private RequestStatus status = RequestStatus.DRAFT;

    // TODO: 4. 多対一・LAZY・必須。外部キー列は applicant_id
    private Employee applicant;

    // TODO: 5. 多対一・LAZY。外部キー列は approver_id(決まるまで null)
    private Employee approver;

    @Column(length = 500)
    private String comment;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    private LocalDateTime submittedAt;
    private LocalDateTime decidedAt;

    // TODO: 7. JPA 用の引数なしコンストラクタ(protected)

    public ApprovalRequest(String title, RequestType type,
            Integer amount, String reason, Employee applicant) {
        this.title = title;
        this.type = type;
        this.amount = amount;
        this.reason = reason;
        this.applicant = applicant;
    }

    // TODO: 6. 保存(INSERT)の直前に呼ばれるようにする
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public RequestType getType() { return type; }
    public Integer getAmount() { return amount; }
    public String getReason() { return reason; }
    public RequestStatus getStatus() { return status; }
    public Employee getApplicant() { return applicant; }
    public Employee getApprover() { return approver; }
    public String getComment() { return comment; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public LocalDateTime getDecidedAt() { return decidedAt; }
}
