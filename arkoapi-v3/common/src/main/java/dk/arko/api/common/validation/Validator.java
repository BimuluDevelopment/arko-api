package dk.arko.api.common.validation;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Fluent validation framework for input validation.
 */
public final class Validator<T> {

    private final T value;
    private final List<String> errors = new ArrayList<>();
    private final String fieldName;

    private Validator(T value, String fieldName) {
        this.value = value;
        this.fieldName = fieldName;
    }

    public static <T> Validator<T> of(T value, String fieldName) {
        return new Validator<>(value, fieldName);
    }

    public static <T> Validator<T> of(T value) {
        return new Validator<>(value, "value");
    }

    // ─── General ───────────────────────────────────────────────

    public Validator<T> notNull() {
        if (value == null) errors.add(fieldName + " must not be null");
        return this;
    }

    public Validator<T> satisfies(Predicate<T> condition, String message) {
        if (value != null && !condition.test(value)) errors.add(message);
        return this;
    }

    // ─── String ────────────────────────────────────────────────

    public Validator<T> notEmpty() {
        if (value instanceof String s && s.isEmpty()) errors.add(fieldName + " must not be empty");
        return this;
    }

    public Validator<T> notBlank() {
        if (value instanceof String s && s.isBlank()) errors.add(fieldName + " must not be blank");
        return this;
    }

    public Validator<T> minLength(int min) {
        if (value instanceof String s && s.length() < min) errors.add(fieldName + " must be at least " + min + " characters");
        return this;
    }

    public Validator<T> maxLength(int max) {
        if (value instanceof String s && s.length() > max) errors.add(fieldName + " must be at most " + max + " characters");
        return this;
    }

    public Validator<T> matches(Pattern pattern, String message) {
        if (value instanceof String s && !pattern.matcher(s).matches()) errors.add(message);
        return this;
    }

    public Validator<T> alphanumeric() {
        return matches(Pattern.compile("[a-zA-Z0-9]+"), fieldName + " must be alphanumeric");
    }

    // ─── Number ────────────────────────────────────────────────

    public Validator<T> min(long min) {
        if (value instanceof Number n && n.longValue() < min) errors.add(fieldName + " must be >= " + min);
        return this;
    }

    public Validator<T> max(long max) {
        if (value instanceof Number n && n.longValue() > max) errors.add(fieldName + " must be <= " + max);
        return this;
    }

    public Validator<T> range(long min, long max) {
        return min(min).max(max);
    }

    public Validator<T> positive() {
        if (value instanceof Number n && n.doubleValue() <= 0) errors.add(fieldName + " must be positive");
        return this;
    }

    // ─── Collection ────────────────────────────────────────────

    public Validator<T> notEmptyCollection() {
        if (value instanceof Collection<?> c && c.isEmpty()) errors.add(fieldName + " must not be empty");
        return this;
    }

    public Validator<T> maxSize(int max) {
        if (value instanceof Collection<?> c && c.size() > max) errors.add(fieldName + " must have at most " + max + " elements");
        return this;
    }

    // ─── UUID ──────────────────────────────────────────────────

    public Validator<T> validUUID() {
        if (value instanceof String s) {
            try { UUID.fromString(s); } catch (Exception e) { errors.add(fieldName + " must be a valid UUID"); }
        }
        return this;
    }

    // ─── Result ────────────────────────────────────────────────

    public boolean isValid() { return errors.isEmpty(); }

    public List<String> getErrors() { return List.copyOf(errors); }

    public T getOrThrow() {
        if (!isValid()) throw new ValidationException(errors);
        return value;
    }

    public Optional<T> get() {
        return isValid() ? Optional.ofNullable(value) : Optional.empty();
    }

    public static class ValidationException extends RuntimeException {
        private final List<String> errors;
        public ValidationException(List<String> errors) {
            super("Validation failed: " + String.join(", ", errors));
            this.errors = errors;
        }
        public List<String> getErrors() { return errors; }
    }
}
