package com.sayana.dojo.exec;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 採点時に追加するライブラリの jar を探す。
 * <p>
 * アプリ自身のクラスパスに載っている jar を「代表クラスがどの jar から読まれたか」で特定する。
 * そのため ./mvnw spring-boot:run・STS・テストのどれで動かしても同じように見つかる。
 */
final class LibraryClasspath {

    private static final Logger log = LoggerFactory.getLogger(LibraryClasspath.class);

    /** ライブラリ名 → その jar に入っている代表クラス。thymeleaf と jpa は spring と一緒に指定する。 */
    private static final Map<String, List<String>> MARKERS = Map.of(
            // Thymeleaf(TemplateEngine で文字列テンプレートを描画して確かめる用)
            "thymeleaf", List.of(
                    "org.thymeleaf.TemplateEngine",
                    "org.thymeleaf.spring6.SpringTemplateEngine",
                    "org.attoparser.MarkupParser",
                    "org.unbescape.html.HtmlEscape"),
            // JPA のアノテーションと Spring Data のリポジトリ(DB は動かさない。メソッド名の解析などに使う)
            "jpa", List.of(
                    "jakarta.persistence.Entity",
                    "org.springframework.data.repository.Repository",
                    "org.springframework.data.jpa.repository.JpaRepository"),
            "spring", List.of(
                    // Spring Framework 本体
                    "org.springframework.core.SpringVersion",
                    "org.springframework.beans.factory.BeanFactory",
                    "org.springframework.context.ApplicationContext",
                    "org.springframework.aop.Advisor",
                    "org.springframework.expression.ExpressionParser",
                    "org.apache.commons.logging.LogFactory",
                    "io.micrometer.observation.ObservationRegistry",
                    "io.micrometer.common.KeyValue",
                    "org.springframework.transaction.PlatformTransactionManager",
                    // Web MVC と MockMvc
                    "org.springframework.web.bind.annotation.RestController",
                    "org.springframework.web.servlet.DispatcherServlet",
                    "org.springframework.test.web.servlet.MockMvc",
                    "jakarta.servlet.Servlet",
                    // JSON
                    "com.fasterxml.jackson.databind.ObjectMapper",
                    "com.fasterxml.jackson.core.JsonFactory",
                    "com.fasterxml.jackson.annotation.JsonProperty",
                    "com.fasterxml.jackson.datatype.jsr310.JavaTimeModule",
                    "com.jayway.jsonpath.JsonPath",
                    "net.minidev.json.JSONValue",
                    "net.minidev.asm.FieldFilter",
                    "org.objectweb.asm.ClassReader",
                    "org.slf4j.LoggerFactory",
                    "org.hamcrest.Matcher",
                    // MockMvc の content().json(...) が使う
                    "org.skyscreamer.jsonassert.JSONCompareMode",
                    "org.json.JSONObject",
                    // Bean Validation
                    "jakarta.validation.Validation",
                    "org.hibernate.validator.HibernateValidator",
                    "org.jboss.logging.Logger",
                    "com.fasterxml.classmate.TypeResolver",
                    "jakarta.el.ExpressionFactory"));

    private LibraryClasspath() {
    }

    static List<Path> resolve(List<String> libraries) {
        Set<Path> jars = new LinkedHashSet<>();
        for (String lib : libraries) {
            List<String> markers = MARKERS.get(lib.strip());
            if (markers == null) {
                throw new IllegalArgumentException("未知のライブラリ指定です: " + lib + "(使えるのは " + MARKERS.keySet() + ")");
            }
            for (String className : markers) {
                Path p = locate(className);
                if (p != null) {
                    jars.add(p);
                }
            }
        }
        return List.copyOf(jars);
    }

    private static Path locate(String className) {
        try {
            Class<?> c = Class.forName(className, false, LibraryClasspath.class.getClassLoader());
            CodeSource source = c.getProtectionDomain().getCodeSource();
            if (source == null || source.getLocation() == null || !"file".equals(source.getLocation().getProtocol())) {
                log.debug("{} の jar の場所が分かりません", className);
                return null;
            }
            Path p = Path.of(source.getLocation().toURI());
            return Files.exists(p) ? p : null;
        } catch (ClassNotFoundException | URISyntaxException | RuntimeException | LinkageError e) {
            log.debug("{} が見つかりません", className, e);
            return null;
        }
    }
}
