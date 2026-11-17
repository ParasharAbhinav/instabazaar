package com.instabazaar.shared.validation;

import static com.instabazaar.shared.exception.BusinessException.invalid;

import com.instabazaar.shared.exception.BusinessException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Objects;

public final class Rules {
  private Rules() {}

  public static <T> T required(T value, String field) {
    if (value == null) throw invalid(field, "Required.");
    return value;
  }

  public static String text(
      String value, String field, int max, boolean nullable, boolean nonblank) {
    if (value == null) {
      if (nullable) return null;
      throw invalid(field, "Required.");
    }
    if (value.length() > max || value.indexOf(0) >= 0)
      throw invalid(field, "Exceeds the permitted length or contains NUL.");
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (Character.isHighSurrogate(c)) {
        if (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(++i)))
          throw invalid(field, "Invalid Unicode.");
      } else if (Character.isLowSurrogate(c)) throw invalid(field, "Invalid Unicode.");
    }
    if (nonblank && value.isBlank()) throw invalid(field, "Must not be blank.");
    return value;
  }

  public static String name(String value, String field, int max) {
    return text(value, field, max, false, true).strip();
  }

  public static BigDecimal money(BigDecimal value, String field) {
    required(value, field);
    if (value.scale() > 2)
      throw invalid(field, "At most two fractional decimal places are permitted.");
    if (value.signum() < 0 || value.compareTo(new BigDecimal("9999999999.99")) > 0)
      throw invalid(field, "Price is outside the supported range.");
    try {
      return value.setScale(2, RoundingMode.UNNECESSARY);
    } catch (ArithmeticException ex) {
      throw invalid(field, "At most two fractional decimal places are permitted.");
    }
  }

  public static void version(Long expected, Long actual) {
    if (expected == null || expected < 0)
      throw invalid("version", "A nonnegative version is required.");
    if (!Objects.equals(expected, actual)) throw BusinessException.conflict();
  }

  public static <T> void collection(Collection<T> values, String field, int max) {
    required(values, field);
    if (values.size() > max) throw invalid(field, "Too many entries.");
    for (T value : values)
      if (value == null) throw invalid(field, "Null entries are not permitted.");
  }

  public static int offset(int page, int size) {
    if (page < 0 || size < 1 || size > 100 || (long) page * size > 10000)
      throw new BusinessException(
          "INVALID_FILTER", 400, "Page, size or offset is outside the permitted range.");
    return page * size;
  }

  public static <T> com.instabazaar.shared.dto.Contracts.Page<T> page(
      java.util.List<T> items, int page, int size, long count) {
    offset(page, size);
    return new com.instabazaar.shared.dto.Contracts.Page<>(
        items, page, size, count, (count + size - 1) / size);
  }
}
