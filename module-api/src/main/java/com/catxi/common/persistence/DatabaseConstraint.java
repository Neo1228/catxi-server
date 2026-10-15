package com.catxi.common.persistence;

import java.util.Locale;
import org.hibernate.exception.ConstraintViolationException;

public final class DatabaseConstraint {

  private DatabaseConstraint() {}

  public static boolean hasName(Throwable exception, String constraintName) {
    if (exception == null || constraintName == null || constraintName.isBlank()) {
      return false;
    }

    String expectedName = constraintName.toLowerCase(Locale.ROOT);
    for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
      if (cause instanceof ConstraintViolationException violation
          && violation.getConstraintName() != null) {
        String actualName = violation.getConstraintName().toLowerCase(Locale.ROOT);
        if (actualName.equals(expectedName)
            || actualName.endsWith("_" + expectedName)
            || actualName.endsWith("." + expectedName)) {
          return true;
        }
      }
    }
    return false;
  }
}
