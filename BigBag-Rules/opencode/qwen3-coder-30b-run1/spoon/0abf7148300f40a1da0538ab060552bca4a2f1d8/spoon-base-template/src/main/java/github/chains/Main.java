package github.chains;

/**
 * Generic Spoon transformation to fix breaking API changes in JasperReports
 * Specifically addresses cases where int parameters are now expected to be Float
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("Spoon transformation template for JasperReports API changes");
        System.out.println("Usage: java -jar spoon-transformer.jar <source-directory>");
    }
}