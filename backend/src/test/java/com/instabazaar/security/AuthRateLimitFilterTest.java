package com.instabazaar.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AuthRateLimitFilterTest {

    private AuthRateLimitFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new AuthRateLimitFilter();
        chain = mock(FilterChain.class);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ignoresSpoofedXForwardedForFromUntrustedPublicPeer() throws Exception {
        for (int i = 0; i < AuthRateLimitFilter.MAX_AUTH_PER_MINUTE; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/login");
            req.setRemoteAddr("198.51.100.77");
            req.addHeader("X-Forwarded-For", "203.0.113." + i);
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, chain);
            assertEquals(200, res.getStatus());
        }

        MockHttpServletRequest blockedReq = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        blockedReq.setRemoteAddr("198.51.100.77");
        blockedReq.addHeader("X-Forwarded-For", "203.0.113.250");
        MockHttpServletResponse blockedRes = new MockHttpServletResponse();
        filter.doFilter(blockedReq, blockedRes, chain);

        assertEquals(429, blockedRes.getStatus());
        assertEquals("60", blockedRes.getHeader("Retry-After"));
        assertTrue(blockedRes.getContentAsString().contains("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    void defeatsSpoofedXForwardedForPrefixBehindTrustedReverseProxy() throws Exception {
        for (int i = 0; i < AuthRateLimitFilter.MAX_AUTH_PER_MINUTE; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/login");
            req.setRemoteAddr("172.18.0.5");
            req.addHeader("X-Forwarded-For", "192.0.2." + i + ", 203.0.113.99");
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, chain);
            assertEquals(200, res.getStatus());
        }

        MockHttpServletRequest blockedReq = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        blockedReq.setRemoteAddr("172.18.0.5");
        blockedReq.addHeader("X-Forwarded-For", "192.0.2.254, 203.0.113.99");
        MockHttpServletResponse blockedRes = new MockHttpServletResponse();
        filter.doFilter(blockedReq, blockedRes, chain);

        assertEquals(429, blockedRes.getStatus());
    }

    @Test
    void collapsesCompressedIpv6AddressesIntoIdentical64Bucket() throws Exception {
        assertEquals(
                "2001:0db8:abcd:0000::/64",
                AuthRateLimitFilter.normalizeIpOrIpv6Prefix("2001:db8:abcd::1")
        );
        assertEquals(
                "2001:0db8:abcd:0000::/64",
                AuthRateLimitFilter.normalizeIpOrIpv6Prefix("2001:db8:abcd::2")
        );

        for (int i = 0; i < AuthRateLimitFilter.MAX_REPORT_PER_MINUTE; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/reports");
            req.setRemoteAddr("2001:db8:abcd::" + (i + 1));
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, chain);
            assertEquals(200, res.getStatus());
        }

        MockHttpServletRequest blockedIpv6 = new MockHttpServletRequest("POST", "/api/v1/reports");
        blockedIpv6.setRemoteAddr("2001:db8:abcd::ffff");
        MockHttpServletResponse blockedRes = new MockHttpServletResponse();
        filter.doFilter(blockedIpv6, blockedRes, chain);

        assertEquals(429, blockedRes.getStatus());
    }

    @Test
    void keysAuthenticatedUsersSeparatelyBehindSharedNatIp() throws Exception {
        String sharedNatIp = "203.0.113.200";
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user-alpha", null, List.of())
        );
        for (int i = 0; i < AuthRateLimitFilter.MAX_REPORT_PER_MINUTE; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/reports");
            req.setRemoteAddr(sharedNatIp);
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, chain);
            assertEquals(200, res.getStatus());
        }

        // User Alpha is now rate-limited on /api/v1/reports
        MockHttpServletRequest alphaBlocked = new MockHttpServletRequest("POST", "/api/v1/reports");
        alphaBlocked.setRemoteAddr(sharedNatIp);
        MockHttpServletResponse alphaRes = new MockHttpServletResponse();
        filter.doFilter(alphaBlocked, alphaRes, chain);
        assertEquals(429, alphaRes.getStatus());

        // User Beta behind the exact same NAT IP is NOT blocked
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user-beta", null, List.of())
        );
        MockHttpServletRequest betaReq = new MockHttpServletRequest("POST", "/api/v1/reports");
        betaReq.setRemoteAddr(sharedNatIp);
        MockHttpServletResponse betaRes = new MockHttpServletResponse();
        filter.doFilter(betaReq, betaRes, chain);
        assertEquals(200, betaRes.getStatus());
    }
}
