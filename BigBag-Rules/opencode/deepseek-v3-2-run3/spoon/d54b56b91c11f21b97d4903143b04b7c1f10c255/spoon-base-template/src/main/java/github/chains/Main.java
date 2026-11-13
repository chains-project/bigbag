package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLambda;
import spoon.reflect.code.CtExpression;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import java.io.File;
import java.util.List;
import java.util.regex.Pattern;

public class Main {
    
    private static final String OLD_TYPE = "com.google.cloud.pubsublite.PublishMetadata";
    private static final String NEW_TYPE = "com.google.cloud.pubsublite.MessageMetadata";
    private static final String SET_CONTEXT_METHOD = "setContext";
    private static final Pattern SET_CONTEXT_PATTERN = 
        Pattern.compile("\\.setContext\\([^)]*\\)");
    
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java Main <sourceDir> <outputDir>");
            System.err.println("Transforms breaking changes in Google Cloud Pub/Sub Lite 0.18.0:");
            System.err.println("  1. " + OLD_TYPE + " -> " + NEW_TYPE);
            System.err.println("  2. Remove " + SET_CONTEXT_METHOD + "() method calls from builder chains");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("=== Google Cloud Pub/Sub Lite 0.18.0 Breaking Change Transformation ===");
        System.out.println("Source: " + sourceDir);
        System.out.println("Output: " + outputDir);
        
        try {
            // Create launcher
            Launcher launcher = new Launcher();
            launcher.getEnvironment().setNoClasspath(true);
            launcher.getEnvironment().setAutoImports(true);
            launcher.getEnvironment().setCommentEnabled(true);
            launcher.addInputResource(sourceDir);
            
            // Build model
            CtModel model = launcher.buildModel();
            
            int typeReplacements = 0;
            int importReplacements = 0;
            int contextRemovals = 0;
            
            // 1. Process compilation units for import fixes
            List<CtCompilationUnit> units = model.getElements(new TypeFilter<>(CtCompilationUnit.class));
            for (CtCompilationUnit unit : units) {
                String originalContent = unit.toString();
                String newContent = originalContent;
                
                // Replace imports
                if (newContent.contains("import " + OLD_TYPE + ";")) {
                    newContent = newContent.replace("import " + OLD_TYPE + ";", "import " + NEW_TYPE + ";");
                    importReplacements++;
                }
                
                if (!newContent.equals(originalContent)) {
                    // Update the unit content
                    unit.setContent(newContent);
                }
            }
            
            // 2. Replace type references
            List<CtTypeReference<?>> typeRefs = model.getElements(new TypeFilter<>(CtTypeReference.class));
            for (CtTypeReference<?> typeRef : typeRefs) {
                String qualifiedName = typeRef.getQualifiedName();
                if (qualifiedName != null && qualifiedName.equals(OLD_TYPE)) {
                    try {
                        CtTypeReference<?> newTypeRef = launcher.getFactory().createReference(NEW_TYPE);
                        typeRef.replace(newTypeRef);
                        typeReplacements++;
                    } catch (Exception e) {
                        System.err.println("Warning: Could not replace type reference: " + qualifiedName);
                    }
                }
            }
            
            // 3. Process source code for setContext removal using string replacement
            // This is a simpler approach than trying to manipulate AST for method chains
            List<CtType<?>> types = model.getElements(new TypeFilter<>(CtType.class));
            for (CtType<?> type : types) {
                String originalContent = type.getPosition().getCompilationUnit().toString();
                String newContent = originalContent;
                
                // Remove .setContext(...) calls
                // This regex will match .setContext(anything) in method chains
                java.util.regex.Matcher matcher = SET_CONTEXT_PATTERN.matcher(newContent);
                int count = 0;
                while (matcher.find()) {
                    count++;
                }
                if (count > 0) {
                    newContent = matcher.replaceAll("");
                    contextRemovals += count;
                }
                
                if (!newContent.equals(originalContent)) {
                    // Update the compilation unit
                    type.getPosition().getCompilationUnit().setContent(newContent);
                }
            }
            
            System.out.println("==========================================");
            System.out.println("Transformations applied:");
            System.out.println("  - Type replacements: " + typeReplacements);
            System.out.println("  - Import replacements: " + importReplacements);
            System.out.println("  - " + SET_CONTEXT_METHOD + " removals: " + contextRemovals);
            System.out.println("==========================================");
            
            // Output transformed code
            launcher.setSourceOutputDirectory(outputDir);
            launcher.prettyprint();
            
            System.out.println("Transformation complete. Output written to: " + outputDir);
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}