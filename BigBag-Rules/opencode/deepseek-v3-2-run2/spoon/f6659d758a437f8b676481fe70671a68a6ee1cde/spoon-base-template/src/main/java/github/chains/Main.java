package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtLambda;
import spoon.reflect.code.CtExpression;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.factory.Factory;

import java.util.HashSet;
import java.util.Set;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.exit(1);
        }

        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        System.out.println("Transformation: Replace org.yaml.snakeyaml.inspector.TrustedTagInspector with lambda 'tag -> true'");

        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);

        CtModel model = launcher.buildModel();

        // Find all constructor calls to TrustedTagInspector
        Set<CtType<?>> affectedTypes = new HashSet<>();
        
        model.getElements(new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {
            @Override
            public boolean matches(CtConstructorCall<?> constructorCall) {
                CtTypeReference<?> typeRef = constructorCall.getType();
                if (typeRef != null) {
                    String typeName = typeRef.getQualifiedName();
                    // Match TrustedTagInspector from snakeyaml (any version)
                    if (typeName != null && typeName.endsWith("TrustedTagInspector")) {
                        System.out.println("Found TrustedTagInspector constructor: " + typeName);
                        return true;
                    }
                }
                return false;
            }
        }).forEach(constructorCall -> {
            String fileName = constructorCall.getPosition().getFile() != null ? 
                constructorCall.getPosition().getFile().getName() : "unknown";
            int line = constructorCall.getPosition().getLine();
            System.out.println("Found TrustedTagInspector constructor call at: " + fileName + ":" + line);

            try {
                // Replace with a lambda: tag -> true
                // This implements TagInspector.isGlobalTagAllowed(Tag tag) in snakeyaml 2.1
                Factory factory = constructorCall.getFactory();
                
                // Create a simple lambda expression: tag -> true
                // Create using code snippet for simplicity
                CtExpression<?> replacement = factory.createCodeSnippetExpression("tag -> true");
                
                constructorCall.replace(replacement);
                
                // Track the containing type for import cleanup
                CtType<?> containingType = constructorCall.getParent(CtType.class);
                if (containingType != null) {
                    affectedTypes.add(containingType);
                }
                
                System.out.println("Successfully replaced TrustedTagInspector constructor with lambda expression");
            } catch (Exception e) {
                System.err.println("Error replacing constructor call at " + fileName + ":" + line + ": " + e.getMessage());
                e.printStackTrace();
            }
        });

        // Clean up imports: remove TrustedTagInspector import if no longer used
        affectedTypes.forEach(ctType -> {
            CtCompilationUnit compUnit = ctType.getPosition().getCompilationUnit();
            if (compUnit != null) {
                String fileName = compUnit.getFile() != null ? compUnit.getFile().getName() : "unknown";
                
                // Check if TrustedTagInspector import exists and remove it
                boolean removedImport = false;
                var imports = compUnit.getImports();
                var iterator = imports.iterator();
                while (iterator.hasNext()) {
                    CtImport imp = iterator.next();
                    if (imp.getReference() != null) {
                        String importStr = imp.getReference().toString();
                        if (importStr.contains("TrustedTagInspector")) {
                            iterator.remove();
                            System.out.println("Removed import: " + importStr + " from " + fileName);
                            removedImport = true;
                        }
                    }
                }
                
                // Also check for static imports
                iterator = imports.iterator();
                while (iterator.hasNext()) {
                    CtImport imp = iterator.next();
                    if (imp.getReference() != null) {
                        String importStr = imp.getReference().toString();
                        if (importStr.contains("TrustedTagInspector")) {
                            iterator.remove();
                            System.out.println("Removed static import: " + importStr + " from " + fileName);
                            removedImport = true;
                        }
                    }
                }
            }
        });

        // Write transformed code back
        launcher.setSourceOutputDirectory(sourceDir);
        launcher.prettyprint();
        
        System.out.println("\nTransformation completed successfully!");
        System.out.println("Summary:");
        System.out.println("- Replaced all TrustedTagInspector constructor calls with lambda 'tag -> true'");
        System.out.println("- Removed TrustedTagInspector imports");
        System.out.println("- This fix works for snakeyaml 2.1+ where TrustedTagInspector was removed");
        System.out.println("- The lambda implements TagInspector.isGlobalTagAllowed(Tag) interface");
    }
}