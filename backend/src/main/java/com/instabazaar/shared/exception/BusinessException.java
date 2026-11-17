package com.instabazaar.shared.exception;

import java.util.List;

public final class BusinessException extends RuntimeException {
  private final String code;
  private final int status;
  private final List<FieldError> fieldErrors;

  public record FieldError(String field, String message) {}

  public BusinessException(String code, int status, String message) {
    this(code, status, message, List.of());
  }

  public BusinessException(String code, int status, String message, List<FieldError> fields) {
    super(message);
    this.code = code;
    this.status = status;
    this.fieldErrors = List.copyOf(fields);
  }

  public String getCode() {
    return code;
  }

  public int getStatus() {
    return status;
  }

  public List<FieldError> getFieldErrors() {
    return fieldErrors;
  }

  public static BusinessException missing() {
    return new BusinessException("RESOURCE_NOT_FOUND", 404, "Resource not found.");
  }

  public static BusinessException conflict() {
    return new BusinessException(
        "VERSION_CONFLICT", 409, "Reload the current resource before editing.");
  }

  public static BusinessException invalid(String field, String message) {
    return new BusinessException(
        "VALIDATION_FAILED",
        400,
        "Request validation failed.",
        List.of(new FieldError(field, message)));
  }

  public static BusinessException transition() {
    return new BusinessException(
        "INVALID_STATE_TRANSITION", 422, "This operation is not allowed in the current state.");
  }
}
