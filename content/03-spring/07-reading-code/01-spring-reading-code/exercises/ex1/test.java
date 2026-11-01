import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class KeyIssueServiceTest {

    private MockMvc mvc;
    private InMemoryKeyRepository repository;
    private LocalValidatorFactoryBean validator;

    interface MvcCheck {
        void run() throws Exception;
    }

    /** MockMvc の検証失敗や、Controller の外まで漏れた例外に、日本語で「何を直せばよいか」を添える */
    private static void hint(String message, MvcCheck check) throws Exception {
        try {
            check.run();
        } catch (AssertionError e) {
            throw new AssertionError(message + "(詳細: " + e.getMessage() + ")", e);
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            throw new AssertionError("例外が Controller の外まで漏れました(本番なら 500 になります)。根本原因: "
                    + root + "。ApiExceptionHandler の @ExceptionHandler が受け取る型と、Service が投げている例外の型は同じですか? "
                    + message, e);
        }
    }

    @BeforeEach
    void setUp() {
        repository = new InMemoryKeyRepository();
        repository.save(new KeyEntity("K-0001", "C001", 1, "ACTIVE"));  // 有効な鍵があるカード
        repository.save(new KeyEntity("K-0002", "C002", 1, "REVOKED")); // 失効済みの鍵しかないカード
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new KeyIssueController(new KeyIssueService(repository)))
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(new StringHttpMessageConverter(StandardCharsets.UTF_8),
                        new MappingJackson2HttpMessageConverter())
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .build();
    }

    @AfterEach
    void tearDown() {
        validator.close();
    }

    private ResultActions issue(String cardId, int keyVersion) throws Exception {
        return mvc.perform(post("/api/keys").contentType(MediaType.APPLICATION_JSON)
                .content("{\"cardId\":\"" + cardId + "\",\"keyVersion\":" + keyVersion + "}"));
    }

    @Test
    @DisplayName("鍵の無いカードは 201 で発行され、1件だけ保存される")
    void issueNewKey() throws Exception {
        hint("鍵の無いカード C100 は 201 + {\"keyId\":\"K-0003\",\"status\":\"ACTIVE\"} のはずです。"
                + "保存してから重複確認をすると、今保存した鍵が見つかって必ず「重複」になります。処理詳細の 2 → 3 → 5 の順に並べ直してください",
                () -> issue("C100", 1)
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.keyId").value("K-0003"))
                        .andExpect(jsonPath("$.status").value("ACTIVE")));
        assertEquals(3, repository.count(), "初期データ 2 件 + 今回の 1 件で、鍵は 3 件のはずです");
    }

    @Test
    @DisplayName("有効な鍵があるカードは 409 + E409 になる")
    void duplicateIsConflict() throws Exception {
        hint("C001 には ACTIVE な鍵があるので、409 + {\"code\":\"E409\",\"message\":\"カード C001 には有効な鍵がすでにあります\"} のはずです。"
                + "409 に変えているのは ApiExceptionHandler です。Service はその Advice が受け取る KeyAlreadyExistsException を投げてください",
                () -> issue("C001", 2)
                        .andExpect(status().isConflict())
                        .andExpect(jsonPath("$.code").value("E409"))
                        .andExpect(jsonPath("$.message").value("カード C001 には有効な鍵がすでにあります")));
    }

    @Test
    @DisplayName("重複エラーのときは、鍵を保存しない")
    void duplicateDoesNotSave() throws Exception {
        try {
            issue("C001", 2);
        } catch (Exception e) {
            // 例外の型の食い違いは duplicateIsConflict で指摘する。ここでは保存件数だけを見る
        }
        assertEquals(2, repository.count(),
                "重複エラーなのに鍵が保存されています。処理詳細では「3. 重複ならエラー」が「5. 保存」より先です。save を重複確認の後ろに移してください");
    }

    @Test
    @DisplayName("失効済み(REVOKED)の鍵しかないカードには、新しく発行できる")
    void revokedOnlyCanIssue() throws Exception {
        hint("C002 の鍵は REVOKED(失効済み)なので、重複扱いにせず 201 で発行するはずです",
                () -> issue("C002", 2).andExpect(status().isCreated()));
    }

    @Test
    @DisplayName("同じカードに続けて発行すると、1回目は 201、2回目は 409")
    void issueTwice() throws Exception {
        hint("1 回目の C200 は 201 のはずです", () -> issue("C200", 1).andExpect(status().isCreated()));
        hint("2 回目の C200 は、1 回目に発行した鍵が ACTIVE なので 409 のはずです",
                () -> issue("C200", 1).andExpect(status().isConflict()));
        assertEquals(3, repository.count(), "保存されるのは 1 回目の 1 件だけです(初期データ 2 件 + 1 件 = 3 件)");
    }
}
