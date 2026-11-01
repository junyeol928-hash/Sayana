import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class KeyIssueControllerTest {

    private MockMvc mvc;
    private KeyIssueService service;
    private LocalValidatorFactoryBean validator;

    @BeforeEach
    void setUp() {
        service = new KeyIssueService();
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new KeyIssueController(service))
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

    /** POST /api/keys を投げてステータスを返す。Controller の外まで例外が漏れたら、根本原因を添えて失敗させる */
    private int issue(String json) {
        try {
            return mvc.perform(post("/api/keys").contentType(MediaType.APPLICATION_JSON).content(json))
                    .andReturn().getResponse().getStatus();
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            throw new AssertionError("リクエスト " + json + " で例外が Controller の外まで漏れました(本番なら 500)。"
                    + "根本原因(一番下の Caused by): " + root
                    + "。Service に入る前の入力チェック(DTO の制約アノテーション)で 400 にしてください", e);
        }
    }

    @Test
    @DisplayName("正しい入力なら 201(keyVersion は 1 と 255 も OK)")
    void valid() {
        assertEquals(201, issue("{\"cardId\":\"C100\",\"keyVersion\":1}"), "keyVersion = 1 は範囲内なので 201 のはずです");
        assertEquals(201, issue("{\"cardId\":\"C101\",\"keyVersion\":255}"), "keyVersion = 255 は範囲内なので 201 のはずです");
        assertEquals(2, service.issuedCount(), "正しい入力 2 件は、どちらも Service で発行されるはずです");
    }

    @Test
    @DisplayName("keyVersion が無いリクエストは、500 ではなく 400 になる")
    void missingVersion() {
        assertEquals(400, issue("{\"cardId\":\"C100\"}"),
                "keyVersion が無いときは 400 のはずです。null の keyVersion が Service まで届き、int に変換するところで NullPointerException になっています。"
                        + "KeyIssueRequest の keyVersion に @NotNull を付けてください");
        assertEquals(0, service.issuedCount(), "入力エラーのときは Service が呼ばれてはいけません(発行 0 件のはず)");
    }

    @Test
    @DisplayName("keyVersion が 1〜255 の範囲外なら 400(0 と 256 は NG)")
    void outOfRange() {
        assertEquals(400, issue("{\"cardId\":\"C100\",\"keyVersion\":0}"),
                "keyVersion = 0 は仕様の範囲(1〜255)外なので 400 のはずです。@Min(1) を付けてください");
        assertEquals(400, issue("{\"cardId\":\"C100\",\"keyVersion\":256}"),
                "keyVersion = 256 は仕様の範囲(1〜255)外なので 400 のはずです。@Max(255) を付けてください");
        assertEquals(0, service.issuedCount(), "範囲外の入力では Service が呼ばれてはいけません(発行 0 件のはず)");
    }

    @Test
    @DisplayName("cardId が空なら 400(もともとある @NotBlank を消さない)")
    void blankCardId() {
        assertEquals(400, issue("{\"cardId\":\" \",\"keyVersion\":1}"),
                "cardId が空白だけなら 400 のはずです。cardId の @NotBlank を消していませんか");
    }
}
