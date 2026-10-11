import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

// TODO: エンティティであることと、テーブル名 employees を指定する
public class Employee {

    // TODO: 主キー。値は DB の自動採番(IDENTITY)に任せる
    private Long id;

    // TODO: NOT NULL、最大50文字
    private String name;

    // TODO: 列名は mail_address、重複不可
    private String email;

    // TODO: 多対一(社員 → 部署)。遅延ロード、外部キー列は department_id
    private Department department;

    // TODO: JPA 用の引数なしコンストラクタ(protected)を追加する

    public Employee(String name, String email, Department department) {
        this.name = name;
        this.email = email;
        this.department = department;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public Department getDepartment() { return department; }
}

// 部署エンティティ(@OneToMany 以外は完成済み。書き方の見本にしてください)
@Entity
@Table(name = "departments")
class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    // TODO: 一対多(部署 → 社員)。関連の持ち主は Employee 側の department フィールド
    private List<Employee> employees = new ArrayList<>();

    protected Department() {
    }

    Department(String name) {
        this.name = name;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public List<Employee> getEmployees() { return employees; }
}
