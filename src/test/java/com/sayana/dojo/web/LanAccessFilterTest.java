package com.sayana.dojo.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.sayana.dojo.config.DojoProperties;

import jakarta.servlet.http.Cookie;

class LanAccessFilterTest {

    private LanAccessFilter filter(boolean enabled) {
        DojoProperties props = new DojoProperties();
        props.getLan().setEnabled(enabled);
        props.getLan().setKey("secret-key");
        return new LanAccessFilter(props, new MockEnvironment());
    }

    private static MockHttpServletRequest request(String remoteAddr) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/lessons/java-hello");
        req.setRemoteAddr(remoteAddr);
        return req;
    }

    @Test
    @DisplayName("LANモードでなければ何もしない")
    void disabled() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        filter(false).doFilter(request("192.168.1.20"), new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("PC自身(127.0.0.1)からは合言葉なしで通す")
    void loopbackPasses() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        filter(true).doFilter(request("127.0.0.1"), new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("スマホからは合言葉がないと 403")
    void lanWithoutKeyIsForbidden() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter(true).doFilter(request("192.168.1.20"), res, chain);
        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull();
        assertThat(res.getContentAsString()).contains("合言葉");
    }

    @Test
    @DisplayName("合言葉つきで開くとクッキーが付き、以後はクッキーで通る。間違った合言葉は通さない")
    void keyThenCookie() throws Exception {
        LanAccessFilter f = filter(true);
        MockHttpServletRequest first = request("192.168.1.20");
        first.setParameter("key", "secret-key");
        MockHttpServletResponse res = new MockHttpServletResponse();
        f.doFilter(first, res, new MockFilterChain());
        Cookie cookie = res.getCookie(LanAccessFilter.COOKIE);
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(res.getRedirectedUrl()).isEqualTo("/lessons/java-hello");

        MockHttpServletRequest next = request("192.168.1.20");
        next.setCookies(cookie);
        MockFilterChain chain = new MockFilterChain();
        f.doFilter(next, new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).isNotNull();

        MockHttpServletRequest wrong = request("192.168.1.20");
        wrong.setParameter("key", "guess");
        MockHttpServletResponse wrongRes = new MockHttpServletResponse();
        f.doFilter(wrong, wrongRes, new MockFilterChain());
        assertThat(wrongRes.getStatus()).isEqualTo(403);
    }
}
