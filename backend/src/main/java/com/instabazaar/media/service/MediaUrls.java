package com.instabazaar.media.service;

import java.net.URI;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MediaUrls {
  private final String origin;

  public MediaUrls(@Value("${instabazaar.media.public-origin:https://localhost}") String origin) {
    URI uri = URI.create(origin);
    if (!"https".equals(uri.getScheme())
        || uri.getHost() == null
        || uri.getUserInfo() != null
        || uri.getRawQuery() != null
        || uri.getRawFragment() != null
        || !(uri.getPath().isEmpty() || uri.getPath().equals("/")))
      throw new IllegalArgumentException("Configure a valid HTTPS media origin.");
    this.origin = origin.replaceAll("/$", "");
  }

  public String url(UUID id) {
    return id == null ? null : origin + "/api/v1/media/" + id;
  }
}
