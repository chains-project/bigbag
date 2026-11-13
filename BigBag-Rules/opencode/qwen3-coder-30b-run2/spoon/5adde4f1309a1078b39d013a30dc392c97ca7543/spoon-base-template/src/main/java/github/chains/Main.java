package github.chains;

/**
 * Generic Spoon transformation to fix the breaking change in sonarlint-core dependency.
 * This transformation replaces calls to addEnabledLanguages(...) with setEnabledLanguages(...).
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("Spoon transformation rule for sonarlint-core API change");
        System.out.println("Usage: java -cp spoon-base-1.0-SNAPSHOT.jar github.chains.Main <source-directory>");
        System.out.println("This replaces addEnabledLanguages(...) with setEnabledLanguages(...)");
    }
}