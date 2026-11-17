package com.instabazaar.shared.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.instabazaar.shared.dto.Contracts;
import com.instabazaar.shared.exception.ServiceErrorMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/** One error body for MVC, CSRF, CORS, and authentication failures. */
@Component
public class HttpErrors {
  private static final Logger LOG = LoggerFactory.getLogger(HttpErrors.class);
  public static final String TRACE_ATTRIBUTE = HttpErrors.class.getName() + ".traceId";
  private final ServiceErrorMapper mapper;
  private final ObjectMapper json;

  public HttpErrors(ServiceErrorMapper mapper, ObjectMapper json) {
    this.mapper = mapper;
    this.json = json;
  }

  public record Failure(int status, Contracts.ApiError body) {}

  public static String traceId(HttpServletRequest request) {
    Object existing = request.getAttribute(TRACE_ATTRIBUTE);
    if (existing instanceof String trace) return trace;
    String trace = UUID.randomUUID().toString();
    request.setAttribute(TRACE_ATTRIBUTE, trace);
    return trace;
  }

  public Failure map(Throwable failure, HttpServletRequest request) {
    var mapped = mapper.map(failure, traceId(request));
    var error = mapped.body();
    return new Failure(
        mapped.status(),
        new Contracts.ApiError(
            error.code(),
            error.message(),
            error.fieldErrors(),
            error.traceId(),
            error.timestamp()));
  }

  public void write(Throwable failure, HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    if (response.isCommitted()) return;
    var mapped = map(failure, request);
    if (mapped.status() >= 500) {
      LOG.error(
          "Security or transport failure traceId={} code={}",
          mapped.body().traceId(),
          mapped.body().code(),
          failure);
    }
    response.setStatus(mapped.status());
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setHeader("Cache-Control", "no-store");
    response.setHeader("X-Trace-ID", mapped.body().traceId());
    json.writeValue(response.getOutputStream(), mapped.body());
  }
}
