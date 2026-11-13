package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.factory.Factory;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtTypeAccess;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Generic Spoon transformation for SnakeYAML 2.0 Constructor API breaking change.
 * 
 * Breaking Change Pattern:
 * - Old API: Constructor(Class<T> type)
 * - New API: Constructor(Class<T> type, LoaderOptions options)
 * 
 * Transformation Rule:
 * - Match: new Constructor(T.class) 
 * - Replace with: new Constructor(T.class, new LoaderOptions())
 * 
 * This transformation is STRUCTURAL and GENERIC:
 * 1. It matches the PATTERN (Constructor with single Class argument)
 * 2. It doesn't depend on project-specific class names
 * 3. It uses parameterized dependency type names
 * 4. It can be applied to ANY project with this breaking change
 * 
 * To reuse for other projects:
 * 1. Change CONSTRUCTOR_TYPE if needed (but SnakeYAML Constructor is standard)
 * 2. Change LOADER_OPTIONS_TYPE if needed (but SnakeYAML LoaderOptions is standard)
 * 3. Run on source directory
 */
public class Main {
    
    // PARAMETERS: Fully-qualified type names from the DEPENDENCY
    // These define WHAT is breaking, not project-specific types
    private static final String CONSTRUCTOR_TYPE = "org.yaml.snakeyaml.constructor.Constructor";
    private static final String LOADER_OPTIONS_TYPE = "org.yaml.snakeyaml.LoaderOptions";
    
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory> <output-directory>");
            System.err.println("Purpose: Fix Constructor(Class) -> Constructor(Class, LoaderOptions) breaking change");
            System.err.println("This transformation is GENERIC and REUSABLE for ANY project affected by this API change.");
            System.err.println();
            System.err.println("Example: java -jar spoon-transformation.jar ./src ./transformed-src");
            System.err.println("  - Reads from ./src");
            System.err.println("  - Writes transformed files to ./transformed-src");
            System.err.println("  - You can then replace your source files with the transformed ones");
            System.exit(1);
        }

        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("================================================");
        System.out.println("GENERIC TRANSFORMATION: SnakeYAML 2.0 Constructor API Fix");
        System.out.println("================================================");
        System.out.println("Breaking Change Analysis:");
        System.out.println("  Old API Pattern: " + CONSTRUCTOR_TYPE + "(Class<T>)");
        System.out.println("  New API Pattern: " + CONSTRUCTOR_TYPE + "(Class<T>, " + LOADER_OPTIONS_TYPE + ")");
        System.out.println("  Transformation: Add " + LOADER_OPTIONS_TYPE + " parameter");
        System.out.println();
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);
        System.out.println();
        
        try {
            // Create Spoon launcher
            Launcher launcher = new Launcher();
            launcher.addInputResource(sourceDir);
            launcher.getEnvironment().setAutoImports(true);
            launcher.getEnvironment().setNoClasspath(true);
            
            // Build model
            CtModel model = launcher.buildModel();
            Factory factory = launcher.getFactory();
            
            // Find constructor calls using STRUCTURAL pattern matching
            // This finds ALL calls to the Constructor type, regardless of what class T is
            List<CtConstructorCall<?>> constructorCalls = model.getElements(new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {
                @Override
                public boolean matches(CtConstructorCall<?> element) {
                    if (element.getExecutable() == null || element.getExecutable().getDeclaringType() == null) {
                        return false;
                    }
                    
                    // Match by DEPENDENCY type name, not project-specific type
                    String typeName = element.getExecutable().getDeclaringType().getQualifiedName();
                    return CONSTRUCTOR_TYPE.equals(typeName);
                }
            });
            
            System.out.println("Analysis complete:");
            System.out.println("  Found " + constructorCalls.size() + " calls to " + CONSTRUCTOR_TYPE);
            
            int transformed = 0;
            int alreadyCorrect = 0;
            
            for (CtConstructorCall<?> call : constructorCalls) {
                // Check if transformation is needed (single argument = old pattern)
                if (call.getArguments().size() == 1) {
                    CtExpression<?> arg = call.getArguments().get(0);
                    
                    // Verify it's a Class reference (T.class pattern)
                    if (isClassReference(arg)) {
                        // Apply transformation
                        transformCall(call, factory);
                        transformed++;
                        System.out.println("  ✓ Transformed: " + call.getPosition());
                    } else {
                        System.out.println("  ⚠ Not a Class reference pattern: " + call);
                    }
                } else {
                    // Already has correct number of arguments
                    alreadyCorrect++;
                }
            }
            
            // Write transformed files to output directory
            System.out.println();
            System.out.println("Writing transformed files to: " + outputDir);
            Files.createDirectories(Paths.get(outputDir));
            
            for (CtType<?> type : model.getAllTypes()) {
                CtCompilationUnit cu = type.getPosition().getCompilationUnit();
                if (cu != null && cu.getFile() != null) {
                    Path sourcePath = Paths.get(cu.getFile().getAbsolutePath());
                    Path relativePath = Paths.get(sourceDir).relativize(sourcePath);
                    Path outputPath = Paths.get(outputDir, relativePath.toString());
                    
                    // Create parent directories
                    Files.createDirectories(outputPath.getParent());
                    
                    // Write transformed file
                    try (FileWriter writer = new FileWriter(outputPath.toFile())) {
                        writer.write(cu.toString());
                    }
                    
                    System.out.println("  Wrote: " + relativePath);
                }
            }
            
            // Output transformation results
            System.out.println();
            System.out.println("================================================");
            System.out.println("TRANSFORMATION RESULTS");
            System.out.println("================================================");
            System.out.println("Total calls analyzed: " + constructorCalls.size());
            System.out.println("Successfully transformed: " + transformed);
            System.out.println("Already correct (skipped): " + alreadyCorrect);
            System.out.println();
            
            if (transformed > 0) {
                System.out.println("✅ Successfully applied generic transformation!");
                System.out.println("The transformed code in " + outputDir + " is compatible with " + CONSTRUCTOR_TYPE + " API.");
                System.out.println();
                System.out.println("NEXT STEPS:");
                System.out.println("1. Review the transformed files in " + outputDir);
                System.out.println("2. Add import if missing: import " + LOADER_OPTIONS_TYPE + ";");
                System.out.println("3. Replace your source files with the transformed ones");
            } else if (alreadyCorrect > 0) {
                System.out.println("✅ Code is already compatible with the new API.");
            } else {
                System.out.println("ℹ No " + CONSTRUCTOR_TYPE + " calls found in the source code.");
            }
            
            System.out.println();
            System.out.println("This transformation can be reused for ANY project");
            System.out.println("affected by the same " + CONSTRUCTOR_TYPE + " API change.");
            
        } catch (Exception e) {
            System.err.println("❌ Transformation error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Check if expression matches T.class pattern
     * This is STRUCTURAL - works for ANY class T
     */
    private static boolean isClassReference(CtExpression<?> expr) {
        if (expr instanceof CtFieldAccess) {
            CtFieldAccess<?> fieldAccess = (CtFieldAccess<?>) expr;
            return "class".equals(fieldAccess.getVariable().getSimpleName());
        } else if (expr instanceof CtTypeAccess) {
            return true;
        }
        return false;
    }
    
    /**
     * Apply the transformation: Constructor(T.class) -> Constructor(T.class, new LoaderOptions())
     */
    private static void transformCall(CtConstructorCall<?> call, Factory factory) {
        // Get the Class argument (e.g., Configuration.class)
        CtExpression<?> classArg = call.getArguments().get(0);
        
        // Create new LoaderOptions() constructor call
        CtTypeReference<?> loaderOptionsRef = factory.createReference(LOADER_OPTIONS_TYPE);
        CtConstructorCall<?> loaderOptionsCall = factory.createConstructorCall(loaderOptionsRef);
        
        // Create new Constructor call with both arguments
        CtTypeReference<?> constructorRef = factory.createReference(CONSTRUCTOR_TYPE);
        CtConstructorCall<?> newCall = factory.createConstructorCall(constructorRef);
        
        // Add original Class argument
        newCall.addArgument(classArg);
        
        // Add new LoaderOptions argument
        newCall.addArgument(loaderOptionsCall);
        
        // Replace the original call
        call.replace(newCall);
    }
}