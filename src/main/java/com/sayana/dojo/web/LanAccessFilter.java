package com.sayana.dojo.web;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.HtmlUtils;

import com.sayana.dojo.config.DojoProperties;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * LANモードの入口チェック。
 * <p>
 * このアプリは提出されたコードを PC 上で実行するので、同じ Wi-Fi の誰でも開ける状態は危ない。
 * PC 自身(127.0.0.1)からのアクセスはそのまま通し、それ以外は合言葉(?key=...)を一度渡したブラウザだけ通す。
 * LANモードでないとき(既定)は 127.0.0.1 でしか待ち受けないので、このフィルタは何もしない。
 */
@Component
public class LanAccessFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LanAccessFilter.class);
    static final String COOKIE = "dojo_key";

    private final DojoProperties.Lan config;
    private final Environment env;
    private volatile String key;

    public LanAccessFilter(DojoProperties props, Environment env) {
        this.config = props.getLan();
        this.env = env;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !config.isEnabled();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (isLoopback(request.getRemoteAddr())) {
            chain.doFilter(request, response);
            return;
        }
        String given = request.getParameter("key");
        if (given != null && matches(given)) {
            Cookie cookie = new Cookie(COOKIE, key());
            cookie.setHttpOnly(true);
            cookie.setPath("/");
            cookie.setMaxAge(60 * 60 * 24 * 90);
            cookie.setAttribute("SameSite", "Lax");
            response.addCookie(cookie);
            // 合言葉を URL に残さない
            response.sendRedirect(request.getRequestURI());
            return;
        }
        if (request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if (COOKIE.equals(c.getName()) && matches(c.getValue())) {
                    chain.doFilter(request, response);
                    return;
                }
            }
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("text/html;charset=UTF-8");
        response.getWriter().write("""
                <!DOCTYPE html><html lang="ja"><meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Java道場</title>
                <body style="font-family:sans-serif;padding:24px;line-height:1.8">
                <h1>☕ Java道場</h1>
                <p>PC以外から開くには合言葉が必要です。</p>
                <p>PCでアプリを起動したときのログに出ている <b>「スマホで開く」</b> のURL(<code>?key=</code> つき)を開いてください。</p>
                <p style="color:#888;font-size:.9em">あなたのアドレス: %s</p>
                </body></html>
                """.formatted(HtmlUtils.htmlEscape(String.valueOf(request.getRemoteAddr()))));
    }

    private boolean matches(String candidate) {
        return MessageDigest.isEqual(candidate.getBytes(StandardCharsets.UTF_8), key().getBytes(StandardCharsets.UTF_8));
    }

    static boolean isLoopback(String addr) {
        try {
            return addr != null && InetAddress.getByName(addr).isLoopbackAddress();
        } catch (IOException e) {
            return false;
        }
    }

    /** 合言葉。設定が空なら data/lan-key.txt に作って使い回す(再起動してもブックマークが使える)。 */
    String key() {
        String k = key;
        if (k == null) {
            synchronized (this) {
                if (key == null) {
                    key = loadOrCreateKey();
                }
                k = key;
            }
        }
        return k;
    }

    private String loadOrCreateKey() {
        if (config.getKey() != null && !config.getKey().isBlank()) {
            return config.getKey().strip();
        }
        Path file = Path.of("data", "lan-key.txt");
        try {
            if (Files.exists(file)) {
                String saved = Files.readString(file, StandardCharsets.UTF_8).strip();
                if (!saved.isEmpty()) {
                    return saved;
                }
            }
            byte[] bytes = new byte[12];
            new SecureRandom().nextBytes(bytes);
            String created = HexFormat.of().formatHex(bytes);
            Files.createDirectories(file.getParent());
            Files.writeString(file, created, StandardCharsets.UTF_8);
            return created;
        } catch (IOException e) {
            throw new IllegalStateException("LANモードの合言葉を保存できません: " + file.toAbsolutePath(), e);
        }
    }

    /** 起動したら、スマホで開く URL をログに出す。 */
    @EventListener(ApplicationReadyEvent.class)
    public void announce() {
        if (!config.isEnabled()) {
            return;
        }
        String port = env.getProperty("local.server.port", env.getProperty("server.port", "8080"));
        List<String> urls = new ArrayList<>();
        for (String ip : lanAddresses()) {
            urls.add("http://" + ip + ":" + port + "/?key=" + key());
        }
        StringBuilder sb = new StringBuilder("\n\n==== LANモード ====\n");
        if (urls.isEmpty()) {
            sb.append("LANのIPアドレスが見つかりませんでした。PCが Wi-Fi/有線LAN につながっているか確認してください。\n");
        } else {
            sb.append("同じWi-Fiのスマホで、次のどれかを開いてください(合言葉つき。人に教えないこと):\n");
            urls.forEach(u -> sb.append("  スマホで開く: ").append(u).append('\n'));
        }
        sb.append("Windows ファイアウォールの確認が出たら「プライベート ネットワーク」を許可してください。\n");
        sb.append("==================\n");
        log.info(sb.toString());
    }

    private static List<String> lanAddresses() {
        List<String> result = new ArrayList<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) {
                    continue;
                }
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) {
                        result.add(a.getHostAddress());
                    }
                }
            }
        } catch (SocketException e) {
            log.debug("ネットワークインターフェースを列挙できませんでした", e);
        }
        return result;
    }
}
