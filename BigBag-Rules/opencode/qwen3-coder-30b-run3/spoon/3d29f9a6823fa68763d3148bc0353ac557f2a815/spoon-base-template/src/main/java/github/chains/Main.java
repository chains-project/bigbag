package github.chains;

public class Main {
    public static void main(String[] args) {
        System.out.println("Spoon transformation for sonarlint-core addEnabledLanguages API fix");
        System.out.println("This is a conceptual implementation that would:");
        System.out.println("1. Find calls to AnalysisEngineConfiguration.Builder.addEnabledLanguages(Set<Language>)");
        System.out.println("2. Transform them to use the new API signature by adding .toArray(new Language[0])");
        System.out.println("3. Work on any Maven project with this pattern");
        System.out.println("");
        System.out.println("Usage: java -cp <classpath> github.chains.Main <source-directory>");
    }
}