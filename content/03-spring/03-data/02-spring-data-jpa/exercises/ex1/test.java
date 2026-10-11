import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class EmployeeTest {

    private static Field field(Class<?> type, String name) {
        try {
            return type.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            return fail(type.getSimpleName() + " にフィールド " + name + " がありません。フィールド名を変えていないか確認してください");
        }
    }

    @Test
    @DisplayName("Employee に @Entity と @Table(name = \"employees\") が付いている")
    void entityAndTable() {
        assertNotNull(Employee.class.getAnnotation(Entity.class),
                "Employee クラスに @Entity を付けてください。付けないと JPA はただのクラスとして無視します");
        Table table = Employee.class.getAnnotation(Table.class);
        assertNotNull(table, "Employee クラスに @Table(name = \"employees\") を付けてテーブル名を指定してください");
        assertEquals("employees", table.name(), "@Table の name は \"employees\" にしてください");
    }

    @Test
    @DisplayName("id は Long 型で @Id と @GeneratedValue(IDENTITY) が付いている")
    void id() {
        Field id = field(Employee.class, "id");
        assertEquals(Long.class, id.getType(), "id の型は Long(ラッパー型)にしてください。保存前は null で「まだ採番されていない」を表します");
        assertNotNull(id.getAnnotation(Id.class), "id フィールドに @Id を付けて主キーにしてください");
        GeneratedValue gv = id.getAnnotation(GeneratedValue.class);
        assertNotNull(gv, "id フィールドに @GeneratedValue を付けて、採番を DB に任せてください");
        assertEquals(GenerationType.IDENTITY, gv.strategy(),
                "@GeneratedValue の strategy は GenerationType.IDENTITY(DB の自動採番)にしてください");
    }

    @Test
    @DisplayName("name は NOT NULL・50文字、email は列名 mail_address・重複不可")
    void columns() {
        Column name = field(Employee.class, "name").getAnnotation(Column.class);
        assertNotNull(name, "name フィールドに @Column を付けてください");
        assertFalse(name.nullable(), "name の @Column に nullable = false を指定してください(NOT NULL 制約)");
        assertEquals(50, name.length(), "name の @Column に length = 50 を指定してください(既定は 255)");

        Column email = field(Employee.class, "email").getAnnotation(Column.class);
        assertNotNull(email, "email フィールドに @Column を付けてください");
        assertEquals("mail_address", email.name(), "email の @Column に name = \"mail_address\" を指定して列名を変えてください");
        assertTrue(email.unique(), "email の @Column に unique = true を指定してください(重複不可)");
    }

    @Test
    @DisplayName("department は @ManyToOne(fetch = LAZY) と @JoinColumn(name = \"department_id\")")
    void manyToOne() {
        Field dept = field(Employee.class, "department");
        assertEquals(Department.class, dept.getType(), "department の型は Department にしてください(ID の Long ではなくオブジェクトで持ちます)");
        ManyToOne m2o = dept.getAnnotation(ManyToOne.class);
        assertNotNull(m2o, "department フィールドに @ManyToOne を付けてください(社員 多 : 部署 1)");
        assertEquals(FetchType.LAZY, m2o.fetch(),
                "@ManyToOne の既定は EAGER(即時ロード)です。fetch = FetchType.LAZY を指定してください");
        JoinColumn jc = dept.getAnnotation(JoinColumn.class);
        assertNotNull(jc, "department フィールドに @JoinColumn(name = \"department_id\") を付けて外部キー列を指定してください");
        assertEquals("department_id", jc.name(), "@JoinColumn の name は \"department_id\" にしてください");
    }

    @Test
    @DisplayName("Department の employees は @OneToMany(mappedBy = \"department\")")
    void oneToMany() {
        Field employees = field(Department.class, "employees");
        assertEquals(List.class, employees.getType(), "employees の型は List<Employee> のままにしてください");
        OneToMany o2m = employees.getAnnotation(OneToMany.class);
        assertNotNull(o2m, "Department の employees フィールドに @OneToMany を付けてください");
        assertEquals("department", o2m.mappedBy(),
                "@OneToMany の mappedBy は、Employee 側のフィールド名 \"department\" にしてください(列名 department_id ではありません)");
    }

    @Test
    @DisplayName("Employee に private でない引数なしコンストラクタがあり、フィールドは private")
    void noArgConstructorAndPrivateFields() {
        Constructor<?> noArg = null;
        for (Constructor<?> c : Employee.class.getDeclaredConstructors()) {
            if (c.getParameterCount() == 0) {
                noArg = c;
            }
        }
        assertNotNull(noArg, "Employee に引数なしコンストラクタ protected Employee() {} を追加してください。JPA は DB の行からオブジェクトを作るときに使います");
        assertTrue(Modifier.isProtected(noArg.getModifiers()) || Modifier.isPublic(noArg.getModifiers()),
                "引数なしコンストラクタは protected(または public)にしてください。private だと JPA の仕様違反です");
        for (String f : List.of("id", "name", "email", "department")) {
            assertTrue(Modifier.isPrivate(field(Employee.class, f).getModifiers()),
                    "フィールド " + f + " は private にしてください");
        }
        Department dev = new Department("開発部");
        Employee e = new Employee("佐藤", "sato@example.com", dev);
        assertEquals("佐藤", e.getName(), "コンストラクタで name を設定してください");
        assertEquals(dev, e.getDepartment(), "コンストラクタで department を設定してください");
    }
}
