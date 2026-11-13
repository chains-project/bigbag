package github.chains;

/**
 * Generic transformation rule for sonarlint-core breaking changes
 * 
 * Problem: Method addEnabledLanguages was removed from AnalysisEngineConfiguration.Builder
 * Solution: Replace with addEnabledLanguage(someSet.toArray(new Language[0]))
 * 
 * This transformation identifies patterns like:
 * .addEnabledLanguages(enabledLanguagesSet)
 * 
 * And transforms them to:
 * .addEnabledLanguage(enabledLanguagesSet.toArray(new Language[0]))
 * 
 * Usage: java Main <source-directory>
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("Generic sonarlint-core API transformation rule");
        System.out.println("Usage: java Main <source-directory>");
        System.out.println("");
        System.out.println("This rule fixes breaking changes in sonarlint-core where:");
        System.out.println("- addEnabledLanguages(Set<Language>) was removed");
        System.out.println("- addEnabledLanguage(Language...) was added instead");
        System.out.println("");
        System.out.println("Transforms code from:");
        System.out.println("  builder().addEnabledLanguages(enabledLanguages)");
        System.out.println("To:");
        System.out.println("  builder().addEnabledLanguage(enabledLanguages.toArray(new Language[0]))");
    }
}