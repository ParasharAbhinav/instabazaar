package com.instabazaar.shared.validation;

public final class PhoneNumberValidator {
  private PhoneNumberValidator() {}

  public static String validate(String value, String field) {
    if (value != null && !value.matches("\\+[1-9][0-9]{1,14}"))
      throw com.instabazaar.shared.exception.BusinessException.invalid(
          field, "Use canonical international format.");
    return value;
  }
}
