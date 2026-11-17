package com.instabazaar.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Multi-tier sliding-window rate limiter with:
 * 1. Exact URI route matching (no accidental substring collisions).
 * 2. Canonical binary InetAddress IPv6 /64 prefix masking (defeats compressed :: notation rotation).
 * 3. Rightmost non-trusted X-Forwarded-For hop extraction when immediate peer is a private proxy.
 * 4. Post-authentication session/principal keying so users behind shared NAT do not block each other.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class AuthRateLimitFilter extends OncePerRequestFilter {

    static final int MAX_AUTH_PER_MINUTE = 20;
    static final int MAX_UPLOAD_PER_MINUTE = 30;
    static final int MAX_REPORT_PER_MINUTE = 15;
    static final int MAX_SEARCH_PER_MINUTE = 120;
    static final long WINDOW_SECONDS = 60L;
    static final int MAX_TRACKED_KEYS = 10_000;

    record WindowCounter(long windowStartEpochSecond, AtomicInteger count) {}

    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return resolveBucketLimit(request) <= 0;
    }

    static int resolveBucketLimit(HttpServletRequest request) {
        String path = normalizePath(request.getRequestURI());
        String method = request.getMethod();
        if (path == null || method == null) {
            return -1;
        }
        if ("POST".equalsIgnoreCase(method) &&
                (path.equals("/api/v1/auth/login") || path.equals("/api/v1/auth/register"))) {
            return MAX_AUTH_PER_MINUTE;
        }
        if ("POST".equalsIgnoreCase(method) && path.equals("/api/v1/seller/media")) {
            return MAX_UPLOAD_PER_MINUTE;
        }
        if ("POST".equalsIgnoreCase(method) && path.equals("/api/v1/reports")) {
            return MAX_REPORT_PER_MINUTE;
        }
        if ("GET".equalsIgnoreCase(method) && path.equals("/api/v1/products")) {
            return MAX_SEARCH_PER_MINUTE;
        }
        return -1;
    }

    static String resolveBucketPrefix(HttpServletRequest request) {
        String path = normalizePath(request.getRequestURI());
        if (path.startsWith("/api/v1/auth/")) return "auth:";
        if (path.equals("/api/v1/seller/media")) return "media:";
        if (path.equals("/api/v1/reports")) return "report:";
        return "search:";
    }

    private static String normalizePath(String uri) {
        if (uri == null || uri.isEmpty()) return "";
        return uri.length() > 1 && uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        int limit = resolveBucketLimit(request);
        long nowSecond = Instant.now().getEpochSecond();
        String subjectKey = resolveSubjectKey(request);
        String key = resolveBucketPrefix(request) + subjectKey;

        if (isRateLimited(key, limit, nowSecond)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(WINDOW_SECONDS));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"code\":\"RATE_LIMIT_EXCEEDED\",\"message\":\"Rate limit exceeded. Please try again in 60 seconds.\"}"
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    static String resolveSubjectKey(HttpServletRequest request) {
        // 1. Check Spring Security context (populated by SessionAccountFilter)
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() != null
                && !"anonymousUser".equals(auth.getPrincipal())) {
            if (auth.getPrincipal() instanceof SessionPrincipal sp && sp.userId() != null) {
                return "user:" + sp.userId();
            }
            return "user:" + auth.getName();
        }
        // 2. Check existing HTTP session if present
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object accountId = session.getAttribute("INSTABAZAAR_USER_ID");
            if (accountId != null) {
                return "user:" + accountId;
            }
            return "session:" + session.getId();
        }
        // 3. Fallback to canonical IPv4 or IPv6 /64 network prefix
        String rawIp = extractTrustedClientIp(request);
        return "net:" + normalizeIpOrIpv6Prefix(rawIp);
    }

    /**
     * Parses any IPv6 literal (including compressed :: forms like 2001:db8:abcd::1)
     * into its 16-byte network representation and extracts the top 64 bits (first 8 bytes).
     */
    static String normalizeIpOrIpv6Prefix(String ip) {
        if (ip == null || ip.isBlank()) {
            return "unknown";
        }
        String trimmed = ip.trim();
        String zoneStripped = trimmed.contains("%") ? trimmed.substring(0, trimmed.indexOf('%')) : trimmed;
        if (!zoneStripped.contains(":")) {
            return zoneStripped;
        }
        try {
            byte[] bytes = InetAddress.getByName(zoneStripped).getAddress();
            if (bytes.length == 16) {
                // Check IPv6 loopback (::1)
                boolean isLoopback = true;
                for (int i = 0; i < 15; i++) {
                    if (bytes[i] != 0) {
                        isLoopback = false;
                        break;
                    }
                }
                if (isLoopback && bytes[15] == 1) {
                    return "::1/128";
                }
                return String.format(
                        "%02x%02x:%02x%02x:%02x%02x:%02x%02x::/64",
                        bytes[0] & 0xff, bytes[1] & 0xff,
                        bytes[2] & 0xff, bytes[3] & 0xff,
                        bytes[4] & 0xff, bytes[5] & 0xff,
                        bytes[6] & 0xff, bytes[7] & 0xff
                );
            }
        } catch (UnknownHostException ignored) {
            // Fall through to raw string if unparseable
        }
        return zoneStripped.toLowerCase();
    }

    private boolean isRateLimited(String key, int maxAllowed, long nowSecond) {
        if (counters.size() >= MAX_TRACKED_KEYS) {
            evictExpired(nowSecond);
        }
        WindowCounter counter = counters.compute(key, (k, existing) -> {
            if (existing == null || (nowSecond - existing.windowStartEpochSecond()) >= WINDOW_SECONDS) {
                return new WindowCounter(nowSecond, new AtomicInteger(1));
            }
            existing.count().incrementAndGet();
            return existing;
        });
        return counter.count().get() > maxAllowed;
    }

    private void evictExpired(long nowSecond) {
        Iterator<Map.Entry<String, WindowCounter>> it = counters.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, WindowCounter> entry = it.next();
            if ((nowSecond - entry.getValue().windowStartEpochSecond()) >= WINDOW_SECONDS) {
                it.remove();
            }
        }
        if (counters.size() >= MAX_TRACKED_KEYS) {
            Iterator<String> overflow = counters.keySet().iterator();
            if (overflow.hasNext()) {
                overflow.next();
                overflow.remove();
            }
        }
    }

    static String extractTrustedClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr() != null ? request.getRemoteAddr().trim() : "unknown";
        if (isTrustedProxyPeer(remoteAddr)) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                String[] hops = xff.split(",");
                for (int i = hops.length - 1; i >= 0; i--) {
                    String candidate = hops[i].trim();
                    if (!candidate.isEmpty() && !isTrustedProxyPeer(candidate)) {
                        return candidate;
                    }
                }
                return hops[hops.length - 1].trim();
            }
        }
        return remoteAddr;
    }

    static boolean isTrustedProxyPeer(String ip) {
        return ip.equals("127.0.0.1") ||
               ip.equals("0:0:0:0:0:0:0:1") ||
               ip.equals("::1") ||
               ip.startsWith("10.") ||
               ip.startsWith("192.168.") ||
               ip.matches("^172\\.(1[6-9]|2[0-9]|3[0-1])\\..*");
    }

    public void clearBuckets() {
        counters.clear();
    }
}
