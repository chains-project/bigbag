package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.Filter;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -jar spoon-transformation.jar <sourceDir> <outputDir>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("Applying Jackson 2.14+ compatibility transformation...");
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(outputDir);
        
        // Get the model
        CtModel model = launcher.buildModel();
        
        // Apply transformation for Jackson 2.14+ breaking changes
        // Rule 1: JsonSerializeAs annotation was removed in Jackson 2.14+
        int removedImports = 0;
        int removedAnnotations = 0;
        
        // Find and remove imports of JsonSerializeAs
        List<CtImport> imports = model.getElements(new TypeFilter<CtImport>(CtImport.class) {
            @Override
            public boolean matches(CtImport element) {
                String importStr = element.toString();
                return importStr.contains("JsonSerializeAs");
            }
        });
        
        for (CtImport imp : imports) {
            System.out.println("Found import of removed annotation: " + imp);
            imp.delete();
            removedImports++;
        }
        
        // Find and remove usages of @JsonSerializeAs annotation
        List<CtAnnotation<?>> annotations = model.getElements(new TypeFilter<CtAnnotation<?>>(CtAnnotation.class) {
            @Override
            public boolean matches(CtAnnotation<?> element) {
                String annotationStr = element.toString();
                return annotationStr.contains("JsonSerializeAs");
            }
        });
        
        for (CtAnnotation<?> ann : annotations) {
            System.out.println("Found usage of removed annotation: " + ann + " in " + ann.getParent());
            ann.delete();
            removedAnnotations++;
        }
        
        // Generate transformed code
        launcher.prettyprint();
        
        System.out.println("\nTransformation Summary:");
        System.out.println("=======================");
        System.out.println("Removed " + removedImports + " imports of JsonSerializeAs");
        System.out.println("Removed " + removedAnnotations + " usages of @JsonSerializeAs annotation");
        System.out.println("Transformed code written to: " + outputDir);
        
        System.out.println("\nBreaking Change Analysis:");
        System.out.println("=========================");
        System.out.println("Jackson 2.14+ introduced breaking changes:");
        System.out.println("1. JsonSerializeAs annotation was removed");
        System.out.println("2. Projects must ensure Jackson component version consistency:");
        System.out.println("   - jackson-databind, jackson-core, and jackson-annotations");
        System.out.println("   - All must be version >= 2.14.0 and match each other");
        System.out.println("3. Check for other deprecated APIs removed in 2.14+");
        
        System.out.println("\nGeneric Transformation Rule:");
        System.out.println("============================");
        System.out.println("This transformation can be applied to ANY Java project affected by");
        System.out.println("Jackson 2.14+ breaking changes. It:");
        System.out.println("1. Removes references to removed JsonSerializeAs annotation");
        System.out.println("2. Can be extended to handle other removed/deprecated APIs");
        System.out.println("3. Works on any codebase by specifying source directory");
        System.out.println("4. Produces transformed code in output directory");
        
        System.out.println("\nMaven Dependency Fix (manual):");
        System.out.println("==============================");
        System.out.println("In pom.xml, ensure all Jackson dependencies have same version:");
        System.out.println("<dependency>");
        System.out.println("  <groupId>com.fasterxml.jackson.core</groupId>");
        System.out.println("  <artifactId>jackson-databind</artifactId>");
        System.out.println("  <version>2.14.0</version> <!-- or higher, all same -->");
        System.out.println("</dependency>");
        System.out.println("<dependency>");
        System.out.println("  <groupId>com.fasterxml.jackson.core</groupId>");
        System.out.println("  <artifactId>jackson-core</artifactId>");
        System.out.println("  <version>2.14.0</version> <!-- MUST match databind -->");
        System.out.println("</dependency>");
        System.out.println("<dependency>");
        System.out.println("  <groupId>com.fasterxml.jackson.core</groupId>");
        System.out.println("  <artifactId>jackson-annotations</artifactId>");
        System.out.println("  <version>2.14.0</version> <!-- MUST match databind -->");
        System.out.println("</dependency>");
    }
}