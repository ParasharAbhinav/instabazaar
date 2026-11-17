package com.instabazaar.util;

import com.instabazaar.exception.DomainException;
import java.net.URI;
import java.util.Locale;
import java.util.Set;

public final class ExternalLinks {
    private ExternalLinks() {}

    public static String validate(String value, String kind) {
        String cleaned = Text.clean(value);
        if (cleaned.isEmpty()) return "";
        Set<String> hosts = switch (kind) {
            case "whatsapp" -> Set.of("wa.me", "api.whatsapp.com");
            case "instagram" -> Set.of("instagram.com", "www.instagram.com");
            case "facebook" -> Set.of("facebook.com", "www.facebook.com", "fb.com", "www.fb.com");
            default -> Set.of("instagram.com", "www.instagram.com", "facebook.com", "www.facebook.com", "fb.com", "www.fb.com");
        };
        try {
            URI uri = URI.create(cleaned);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !hosts.contains(host)
                    || uri.getRawUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)
                    || cleaned.chars().anyMatch(character -> Character.isISOControl(character))) {
                throw new IllegalArgumentException();
            }
            if (kind.equals("whatsapp") && host.equals("wa.me") && !uri.getPath().matches("/[1-9][0-9]{6,14}")) {
                throw new IllegalArgumentException();
            }
            if (kind.equals("whatsapp") && host.equals("api.whatsapp.com")) {
                String query = uri.getRawQuery() == null ? "" : uri.getRawQuery();
                if (!uri.getPath().matches("/send/?") || !query.matches("(?:[^&]*&)*phone=[1-9][0-9]{6,14}(?:&[^&]*)*")) {
                    throw new IllegalArgumentException();
                }
            }
            return uri.toASCIIString();
        } catch (IllegalArgumentException exception) {
            throw DomainException.invalid("Use a valid HTTPS " + kind + " link on its official domain.");
        }
    }
}
