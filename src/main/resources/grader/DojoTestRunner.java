import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

/**
 * Java道場の採点ランナー(別JVMで実行される)。
 * 結果は1行1件・タブ区切り・メッセージは Base64 で結果ファイルに書く。
 * args: [テストクラス名, 結果ファイル, 提出クラス名]
 */
public class DojoTestRunner {

    public static void main(String[] args) throws Exception {
        String testClass = args[0];
        Path resultFile = Path.of(args[1]);
        String userClass = args.length > 2 ? args[2] : "";
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(resultFile, StandardCharsets.UTF_8))) {
            LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                    .selectors(DiscoverySelectors.selectClass(testClass))
                    .build();
            Launcher launcher = LauncherFactory.create();
            launcher.execute(request, new TestExecutionListener() {
                @Override
                public void executionSkipped(TestIdentifier id, String reason) {
                    if (id.isTest()) {
                        write(out, "SKIPPED", id.getDisplayName(), reason);
                    }
                }

                @Override
                public void executionFinished(TestIdentifier id, TestExecutionResult result) {
                    String message = result.getThrowable().map(t -> describe(t, userClass)).orElse("");
                    if (id.isTest()) {
                        write(out, result.getStatus().name(), id.getDisplayName(), message);
                    } else if (result.getStatus() != TestExecutionResult.Status.SUCCESSFUL) {
                        write(out, "CONTAINER_FAILED", id.getDisplayName(), message);
                    }
                }
            });
            out.println("END");
        }
    }

    private static void write(PrintWriter out, String status, String name, String message) {
        Base64.Encoder b64 = Base64.getEncoder();
        out.println("TEST\t" + status + "\t" + b64.encodeToString(name.getBytes(StandardCharsets.UTF_8)) + "\t"
                + b64.encodeToString((message == null ? "" : message).getBytes(StandardCharsets.UTF_8)));
        out.flush();
    }

    private static String describe(Throwable t, String userClass) {
        String name = t.getClass().getName();
        boolean assertion = name.startsWith("org.opentest4j") || name.startsWith("org.junit");
        StringBuilder sb = new StringBuilder();
        if (assertion) {
            sb.append(t.getMessage());
        } else {
            sb.append("例外 ").append(name);
            if (t.getMessage() != null) {
                sb.append(": ").append(t.getMessage());
            }
            for (StackTraceElement el : t.getStackTrace()) {
                if (el.getClassName().equals(userClass) || el.getClassName().startsWith(userClass + "$")) {
                    sb.append("\n    at ").append(el.getClassName()).append('.').append(el.getMethodName())
                            .append("(").append(el.getFileName()).append(":").append(el.getLineNumber()).append(")");
                    break;
                }
            }
        }
        return sb.toString();
    }
}
