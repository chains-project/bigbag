package github.chains.japicmp.model;

public record ValueChange<T>(T oldValue, T newValue, boolean changed) {

    public static <T> ValueChange<T> empty() {
        return new ValueChange<>(null, null, false);
    }
}

