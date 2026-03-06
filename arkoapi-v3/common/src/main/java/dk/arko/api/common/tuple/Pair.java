package dk.arko.api.common.tuple;

/**
 * Immutable pair of two values.
 */
public record Pair<A, B>(A first, B second) {
    public static <A, B> Pair<A, B> of(A first, B second) {
        return new Pair<>(first, second);
    }
}
