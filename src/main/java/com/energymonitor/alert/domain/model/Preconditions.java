package com.energymonitor.alert.domain.model;

/**
 * Argument checks shared by the Alerts domain model.
 *
 * <p>Deliberately package-private and framework-free: the domain must stay plain Java.
 * Every failure is an {@link IllegalArgumentException}, so no custom exception hierarchy
 * is imposed on future phases.
 */
final class Preconditions {

    private Preconditions() {
    }

    static <T> T notNull(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
        return value;
    }

    static String text(String value, String field) {
        notNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    static String text(String value, int maxLength, String field) {
        text(value, field);
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(field + " must be at most " + maxLength + " characters");
        }
        return value;
    }

    static String optionalText(String value, int maxLength, String field) {
        if (value == null) {
            return null;
        }
        return text(value, maxLength, field);
    }
}
