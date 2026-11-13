package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.visitor.filter.TypeFilter;

/**
 * Main class for applying the JavaParser import fix transformation.
 * 
 * <p>This transformation fixes compilation errors caused by JavaParser 3.24.0
 * breaking changes, specifically classes that moved from the {@code printer}
 * package to the {@code printer.configuration} package.</p>
 * 
 * <p>Usage: {@code java -jar spoon-transform.jar <source-directory>}</p>
 * <p>Example: {@code java -jar spoon-transform.jar /path/to/project/src/main/java}</p>
 * 
 * <p>The transformation will modify Java source files in place, so it's recommended
 * to use version control or backup files before running.</p>
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transform.jar <source-directory>");
            System.err.println("Example: java -jar spoon-transform.jar /path/to/project/src/main/java");
            System.err.println("");
            System.err.println("This transformation fixes JavaParser 3.24.0 breaking changes:");
            System.err.println("- Moves PrettyPrinterConfiguration from com.github.javaparser.printer");
            System.err.println("  to com.github.javaparser.printer.configuration");
            System.err.println("- Similar fixes for other configuration classes");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying JavaParser 3.24.0 import fixes to: " + sourceDir);
        System.out.println("Fixing imports for classes that moved to printer.configuration package...");
        
        try {
            Launcher launcher = new Launcher();
            launcher.getEnvironment().setNoClasspath(true);
            launcher.getEnvironment().setAutoImports(true);
            launcher.addInputResource(sourceDir);
            
            // Build model
            CtModel model = launcher.buildModel();
            
            // Apply transformation
            JavaParserImportFixer fixer = new JavaParserImportFixer();
            int filesProcessed = 0;
            for (CtCompilationUnit cu : model.getElements(new TypeFilter<>(CtCompilationUnit.class))) {
                fixer.process(cu);
                filesProcessed++;
            }
            
            // Write changes back
            launcher.prettyprint();
            
            System.out.println("Transformation completed successfully.");
            System.out.println("Processed " + filesProcessed + " Java source files.");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}