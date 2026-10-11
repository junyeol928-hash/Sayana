package com.sayana.dojo.exec;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.sayana.dojo.config.DojoProperties;

class GraderTest {

    private final DojoProperties props = new DojoProperties();
    private final Grader grader = new Grader(props, new ExecutionGate(props));

    private static final String TEST = """
            import static org.junit.jupiter.api.Assertions.*;
            import org.junit.jupiter.api.*;

            class CalcTest {
                @Test
                @DisplayName("2 + 3 は 5")
                void add() {
                    assertEquals(5, Calc.add(2, 3), "add(2, 3) の結果");
                }

                @Test
                @DisplayName("負の数も足せる")
                void negative() {
                    assertEquals(-1, Calc.add(2, -3));
                }
            }
            """;

    @Test
    void 正解なら合格() {
        GradeResult r = grader.grade("Calc", """
                public class Calc {
                    public static int add(int a, int b) { return a + b; }
                }
                """, TEST, Duration.ofSeconds(10));
        assertThat(r.status()).as(r.summaryText() + "\n" + r.output()).isEqualTo(GradeResult.Status.PASSED);
        assertThat(r.tests()).extracting(GradeResult.TestCase::name).contains("2 + 3 は 5", "負の数も足せる");
    }

    @Test
    void 間違いなら失敗理由つきで不合格() {
        GradeResult r = grader.grade("Calc", """
                public class Calc {
                    public static int add(int a, int b) { return a - b; }
                }
                """, TEST, Duration.ofSeconds(10));
        assertThat(r.status()).isEqualTo(GradeResult.Status.FAILED);
        assertThat(r.tests()).filteredOn(t -> !t.passed()).first()
                .satisfies(t -> assertThat(t.message()).contains("expected"));
    }

    @Test
    void コンパイルエラーは日本語の診断を返す() {
        GradeResult r = grader.grade("Calc", """
                public class Calc {
                    public static int add(int a, int b) { return a + b }
                }
                """, TEST, Duration.ofSeconds(10));
        assertThat(r.status()).isEqualTo(GradeResult.Status.COMPILE_ERROR);
        assertThat(r.compileErrors()).first().satisfies(d -> {
            assertThat(d.source()).isEqualTo("あなたのコード");
            assertThat(d.line()).isEqualTo(2);
        });
    }

    @Test
    void 無限ループはタイムアウト() {
        long start = System.nanoTime();
        GradeResult r = grader.grade("Calc", """
                public class Calc {
                    public static int add(int a, int b) { while (true) { } }
                }
                """, TEST, Duration.ofSeconds(3));
        assertThat(r.status()).isEqualTo(GradeResult.Status.TIMEOUT);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(10));
    }

    @Test
    void メモリを食い潰してもアプリは無事() {
        GradeResult r = grader.grade("Calc", """
                import java.util.*;
                public class Calc {
                    public static int add(int a, int b) {
                        List<long[]> hog = new ArrayList<>();
                        while (true) { hog.add(new long[10_000_000]); }
                    }
                }
                """, TEST, Duration.ofSeconds(10));
        assertThat(r.passed()).isFalse();
    }

    @Test
    void Springの演習はDIコンテナとMockMvcで採点できる() {
        String code = """
                import org.springframework.context.annotation.*;
                import org.springframework.stereotype.*;
                import org.springframework.web.bind.annotation.*;
                import jakarta.validation.Valid;
                import jakarta.validation.constraints.*;

                @RestController
                public class GreetingController {
                    private final GreetingService service;

                    public GreetingController(GreetingService service) {
                        this.service = service;
                    }

                    @GetMapping("/hello/{name}")
                    public String hello(@PathVariable String name) {
                        return service.greet(name);
                    }

                    @PostMapping("/members")
                    public Member create(@Valid @RequestBody Member member) {
                        return member;
                    }
                }

                @Service
                class GreetingService {
                    String greet(String name) { return "こんにちは、" + name; }
                }

                record Member(@NotBlank String name, @Min(0) int age) { }
                """;
        String test = """
                import static org.junit.jupiter.api.Assertions.*;
                import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
                import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
                import org.junit.jupiter.api.*;
                import org.springframework.context.annotation.AnnotationConfigApplicationContext;
                import org.springframework.http.MediaType;
                import org.springframework.test.web.servlet.MockMvc;
                import org.springframework.test.web.servlet.setup.MockMvcBuilders;
                import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

                class GreetingControllerTest {
                    @Test
                    @DisplayName("DIコンテナからServiceが注入される")
                    void di() {
                        try (var ctx = new AnnotationConfigApplicationContext(GreetingController.class, GreetingService.class)) {
                            assertEquals("こんにちは、ハル", ctx.getBean(GreetingController.class).hello("ハル"));
                        }
                    }

                    @Test
                    @DisplayName("GET /hello/{name} とバリデーション")
                    void mvc() throws Exception {
                        var validator = new LocalValidatorFactoryBean();
                        validator.afterPropertiesSet();
                        MockMvc mvc = MockMvcBuilders.standaloneSetup(new GreetingController(new GreetingService()))
                                .setValidator(validator)
                                .setMessageConverters(
                                        new org.springframework.http.converter.StringHttpMessageConverter(
                                                java.nio.charset.StandardCharsets.UTF_8),
                                        new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter())
                                .defaultResponseCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8).build();
                        mvc.perform(get("/hello/モカ")).andExpect(status().isOk())
                                .andExpect(content().string("こんにちは、モカ"));
                        mvc.perform(post("/members").contentType(MediaType.APPLICATION_JSON)
                                .content("{\\"name\\":\\"ハル\\",\\"age\\":20}"))
                                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("ハル"));
                        mvc.perform(post("/members").contentType(MediaType.APPLICATION_JSON)
                                .content("{\\"name\\":\\"\\",\\"age\\":-1}"))
                                .andExpect(status().isBadRequest());
                    }
                }
                """;
        GradeResult r = grader.grade("GreetingController", code, test, java.util.List.of("spring"),
                Duration.ofSeconds(30));
        assertThat(r.status()).as(r.summaryText() + "\n" + r.output()).isEqualTo(GradeResult.Status.PASSED);
    }

    @Test
    void ThymeleafとJPAの指定でも採点できる() {
        String code = """
                import jakarta.persistence.*;
                import java.util.List;
                import org.springframework.data.jpa.repository.JpaRepository;

                @Entity
                public class Employee {
                    @Id @GeneratedValue Long id;
                    String name;
                    String department;
                }

                interface EmployeeRepository extends JpaRepository<Employee, Long> {
                    List<Employee> findByDepartmentOrderByNameAsc(String department);
                }
                """;
        String test = """
                import static org.junit.jupiter.api.Assertions.*;
                import org.junit.jupiter.api.*;
                import org.springframework.data.repository.query.parser.PartTree;
                import org.thymeleaf.context.Context;
                import org.thymeleaf.templateresolver.StringTemplateResolver;

                class EmployeeTest {
                    @Test
                    void entity() {
                        assertTrue(Employee.class.isAnnotationPresent(jakarta.persistence.Entity.class));
                    }

                    @Test
                    void derivedQuery() throws Exception {
                        var m = EmployeeRepository.class.getMethod("findByDepartmentOrderByNameAsc", String.class);
                        var tree = new PartTree(m.getName(), Employee.class);
                        assertTrue(tree.getSort().isSorted());
                    }

                    @Test
                    void template() {
                        var engine = new org.thymeleaf.spring6.SpringTemplateEngine();
                        engine.setTemplateResolver(new StringTemplateResolver());
                        var ctx = new Context();
                        ctx.setVariable("name", "ハル");
                        assertEquals("<p>ハル</p>", engine.process("<p th:text=\\"${name}\\">x</p>", ctx));
                    }
                }
                """;
        GradeResult r = grader.grade("Employee", code, test, java.util.List.of("spring", "jpa", "thymeleaf"),
                Duration.ofSeconds(30));
        assertThat(r.status()).as(r.summaryText() + "\n" + r.output()).isEqualTo(GradeResult.Status.PASSED);
    }
}
