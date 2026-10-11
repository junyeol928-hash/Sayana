import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

class ApprovalRequestTest {

    private static Field field(String name) {
        try {
            return ApprovalRequest.class.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            return fail("フィールド " + name + " が見つかりません。フィールド名は変えないでください");
        }
    }

    @Test
    @DisplayName("1. クラスに @Entity が付いている")
    void entity() {
        assertNotNull(ApprovalRequest.class.getAnnotation(Entity.class),
                "ApprovalRequest クラスに @Entity を付けてください。付けないとテーブルに対応づけられません");
    }

    @Test
    @DisplayName("2. id が主キーで、DB の自動採番(IDENTITY)になっている")
    void id() {
        Field id = field("id");
        assertNotNull(id.getAnnotation(Id.class), "id に @Id を付けてください");
        GeneratedValue gv = id.getAnnotation(GeneratedValue.class);
        assertNotNull(gv, "id に @GeneratedValue を付けてください");
        assertEquals(GenerationType.IDENTITY, gv.strategy(),
                "@GeneratedValue の strategy は GenerationType.IDENTITY にしてください");
    }

    @Test
    @DisplayName("3. type と status は @Enumerated(EnumType.STRING) で文字列として保存する")
    void enumerated() {
        for (String name : new String[] {"type", "status"}) {
            Enumerated en = field(name).getAnnotation(Enumerated.class);
            assertNotNull(en, name + " に @Enumerated(EnumType.STRING) を付けてください。"
                    + "付けないと定数の順番(0, 1, 2…)で保存され、enum の並びを変えると既存データの意味がずれます");
            assertEquals(EnumType.STRING, en.value(),
                    name + " の @Enumerated は EnumType.STRING にしてください(ORDINAL は順番で保存されるので危険です)");
        }
    }

    @Test
    @DisplayName("4. applicant は必須の多対一(LAZY)で、外部キー列は applicant_id")
    void applicant() {
        Field f = field("applicant");
        ManyToOne m = f.getAnnotation(ManyToOne.class);
        assertNotNull(m, "applicant に @ManyToOne を付けてください(1人の社員が多くの申請を出すので「多対一」です)");
        assertEquals(FetchType.LAZY, m.fetch(),
                "applicant の @ManyToOne は fetch = FetchType.LAZY にしてください(既定は EAGER)");
        JoinColumn jc = f.getAnnotation(JoinColumn.class);
        boolean required = !m.optional() || (jc != null && !jc.nullable());
        assertTrue(required, "申請者のいない申請はないので、@ManyToOne(optional = false) にしてください");
        assertNotNull(jc, "applicant に @JoinColumn(name = \"applicant_id\") を付けてください");
        assertEquals("applicant_id", jc.name(), "applicant の外部キー列名は applicant_id にしてください");
    }

    @Test
    @DisplayName("5. approver は省略可能な多対一(LAZY)で、外部キー列は approver_id")
    void approver() {
        Field f = field("approver");
        ManyToOne m = f.getAnnotation(ManyToOne.class);
        assertNotNull(m, "approver に @ManyToOne を付けてください");
        assertEquals(FetchType.LAZY, m.fetch(),
                "approver の @ManyToOne は fetch = FetchType.LAZY にしてください");
        assertTrue(m.optional(),
                "approver は承認・差戻しされるまで null なので、optional = false にしてはいけません");
        JoinColumn jc = f.getAnnotation(JoinColumn.class);
        assertNotNull(jc, "approver に @JoinColumn(name = \"approver_id\") を付けてください");
        assertEquals("approver_id", jc.name(), "approver の外部キー列名は approver_id にしてください");
        assertTrue(jc.nullable(), "approver_id は null を許す列にしてください(nullable = false にしない)");
    }

    @Test
    @DisplayName("6. @PrePersist のメソッドで作成日時が入り、状態は DRAFT から始まる")
    void prePersist() throws Exception {
        Method hook = null;
        for (Method m : ApprovalRequest.class.getDeclaredMethods()) {
            if (m.isAnnotationPresent(PrePersist.class)) {
                hook = m;
            }
        }
        if (hook == null) {
            fail("onCreate() に @PrePersist を付けてください。付けないと保存時に呼ばれず、"
                    + "created_at が null のまま INSERT されて NOT NULL 制約違反になります");
        }
        Employee haru = new Employee("ハル", "haru@example.com", Role.APPLICANT);
        ApprovalRequest r = new ApprovalRequest("交通費", RequestType.EXPENSE, 1280, "客先訪問", haru);
        assertNull(r.getCreatedAt(), "作成日時は保存の直前(@PrePersist)に入れるので、new した直後は null のはずです");
        assertEquals(RequestStatus.DRAFT, r.getStatus(), "新しい申請の状態は DRAFT から始まるはずです");
        hook.setAccessible(true);
        hook.invoke(r);
        assertNotNull(r.getCreatedAt(), "@PrePersist のメソッドを呼んだら createdAt が入るはずです");
    }

    @Test
    @DisplayName("7. JPA 用の引数なしコンストラクタが protected で用意されている")
    void noArgConstructor() {
        Constructor<?> c;
        try {
            c = ApprovalRequest.class.getDeclaredConstructor();
        } catch (NoSuchMethodException e) {
            fail("引数なしコンストラクタ protected ApprovalRequest() {} を追加してください。"
                    + "JPA(Hibernate)は DB から読んだ行を、まず引数なしで new してから詰め替えます");
            return;
        }
        assertFalse(Modifier.isPrivate(c.getModifiers()),
                "引数なしコンストラクタを private にすると JPA の仕様違反です。protected にしてください");
        assertTrue(Modifier.isProtected(c.getModifiers()) || Modifier.isPublic(c.getModifiers()),
                "引数なしコンストラクタは protected にしてください(アプリのコードからうっかり使われないように)");
    }
}
