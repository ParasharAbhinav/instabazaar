package com.instabazaar.shared.exception;

import com.instabazaar.shared.dto.Contracts;
import com.instabazaar.shared.web.HttpErrors;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
  private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);
  private final HttpErrors errors;

  public GlobalExceptionHandler(HttpErrors errors) {
    this.errors = errors;
  }

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<Contracts.ApiError> business(
      BusinessException failure, HttpServletRequest request) {
    return respond(failure, request);
  }

  @ExceptionHandler({OptimisticLockException.class, OptimisticLockingFailureException.class})
  public ResponseEntity<Contracts.ApiError> optimistic(
      Exception failure, HttpServletRequest request) {
    return respond(BusinessException.conflict(), request);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<Contracts.ApiError> denied(
      AccessDeniedException failure, HttpServletRequest request) {
    return respond(
        new BusinessException("ACCESS_DENIED", 403, "This operation is not permitted."), request);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<Contracts.ApiError> constraints(
      ConstraintViolationException failure, HttpServletRequest request) {
    var fields =
        failure.getConstraintViolations().stream()
            .map(
                violation ->
                    new BusinessException.FieldError(
                        violation.getPropertyPath().toString(), violation.getMessage()))
            .distinct()
            .sorted(java.util.Comparator.comparing(BusinessException.FieldError::field))
            .toList();
    return respond(
        new BusinessException("VALIDATION_FAILED", 400, "Request validation failed.", fields),
        request);
  }

  @ExceptionHandler(DataAccessException.class)
  public ResponseEntity<Contracts.ApiError> database(
      DataAccessException failure, HttpServletRequest request) {
    return respond(failure, request);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Contracts.ApiError> unexpected(
      Exception failure, HttpServletRequest request) {
    return respond(failure, request);
  }

  @ExceptionHandler(MultipartException.class)
  public ResponseEntity<Contracts.ApiError> multipart(
      MultipartException failure, HttpServletRequest request) {
    return respond(
        new BusinessException("MALFORMED_MULTIPART", 400, "The multipart request is malformed."),
        request);
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException failure,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    var fields =
        failure.getBindingResult().getFieldErrors().stream()
            .map(
                error ->
                    new BusinessException.FieldError(
                        error.getField(),
                        error.getDefaultMessage() == null
                            ? "Invalid value."
                            : error.getDefaultMessage()))
            .distinct()
            .sorted(java.util.Comparator.comparing(BusinessException.FieldError::field))
            .toList();
    return mvc(
        new BusinessException("VALIDATION_FAILED", 400, "Request validation failed.", fields),
        headers,
        request);
  }

  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception failure,
      Object body,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    BusinessException safe =
        switch (status.value()) {
          case 400 ->
              new BusinessException(
                  failure
                          instanceof
                          org.springframework.http.converter.HttpMessageNotReadableException
                      ? "MALFORMED_JSON"
                      : "VALIDATION_FAILED",
                  400,
                  "The request is malformed or contains invalid values.");
          case 404 -> BusinessException.missing();
          case 405 ->
              new BusinessException("METHOD_NOT_ALLOWED", 405, "HTTP method is not supported.");
          case 406 ->
              new BusinessException(
                  "NOT_ACCEPTABLE", 406, "The requested response format is not supported.");
          case 413 ->
              new BusinessException("UPLOAD_TOO_LARGE", 413, "Upload exceeds the permitted size.");
          case 415 ->
              new BusinessException(
                  "UNSUPPORTED_MEDIA_TYPE", 415, "The request content type is not supported.");
          default ->
              status.is5xxServerError()
                  ? null
                  : new BusinessException(
                      "REQUEST_REJECTED", status.value(), "The request was rejected.");
        };
    return mvc(safe == null ? failure : safe, headers, request);
  }

  private ResponseEntity<Contracts.ApiError> respond(
      Throwable failure, HttpServletRequest request) {
    var mapped = errors.map(failure, request);
    if (mapped.status() >= 500) {
      LOG.error(
          "API failure traceId={} code={}", mapped.body().traceId(), mapped.body().code(), failure);
    }
    return ResponseEntity.status(mapped.status())
        .contentType(MediaType.APPLICATION_JSON)
        .header("Cache-Control", "no-store")
        .header("X-Trace-ID", mapped.body().traceId())
        .body(mapped.body());
  }

  private ResponseEntity<Object> mvc(Throwable failure, HttpHeaders headers, WebRequest request) {
    var servlet = ((ServletWebRequest) request).getRequest();
    var response = respond(failure, servlet);
    var combined = new HttpHeaders();
    combined.putAll(headers); // Preserve Allow, Accept, and other MVC protocol headers.
    combined.putAll(response.getHeaders());
    return new ResponseEntity<>(response.getBody(), combined, response.getStatusCode());
  }
}
