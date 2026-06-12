package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtPackageReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.CtScanner;
import spoon.reflect.visitor.filter.TypeFilter;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.HashMap;

/**
 * GENERIC, REUSABLE Spoon transformation rule for the assertj-core 3.23.0 breaking change.
 * 
 * BREAKING CHANGE ANALYSIS:
 * - assertj-core 3.23.0 removed embedded ByteBuddy classes from package:
 *     org.assertj.core.internal.bytebuddy.*
 * - ByteBuddy is now an external dependency at:
 *     net.bytebuddy.*
 * - This affects any project that was using ByteBuddy classes through assertj-core's
 *   internal package
 * 
 * TRANSFORMATION RULE:
 * - Match pattern: org.assertj.core.internal.bytebuddy.*
 * - Replacement: net.bytebuddy.*
 * - Applies to: Import statements, type references, static imports
 * 
 * This rule is generic and can be applied to ANY Maven project affected by this
 * breaking change by simply changing the input source directory path.
 * 
 * USAGE:
 *   java Main <source-directory> [output-directory]
 *   
 *   source-directory: Directory containing Java source files to transform
 *   output-directory: Directory to write transformed files (default: overwrite source)
 */
public class Main {
    
    // EXACT PACKAGE MAPPING FOR THE BREAKING CHANGE
    // This is the ONLY project-specific configuration needed
    private static final Map<String, String> PACKAGE_MAPPINGS = createPackageMappings();
    
    private static Map<String, String> createPackageMappings() {
        Map<String, String> mappings = new HashMap<>();
        
        // Core transformation: assertj-core internal ByteBuddy -> external ByteBuddy
        // This handles ALL subpackages recursively
        mappings.put("org.assertj.core.internal.bytebuddy", "net.bytebuddy");
        
        return mappings;
    }
    
    /**
     * Spoon visitor that transforms type references from old to new packages.
     * This handles both import statements and type usage in code.
     */
    static class ByteBuddyPackageTransformer extends CtScanner {
        
        private int transformCount = 0;
        
        @Override
        public <T> void visitCtTypeReference(CtTypeReference<T> typeRef) {
            super.visitCtTypeReference(typeRef);
            
            String qualifiedName = typeRef.getQualifiedName();
            if (qualifiedName != null) {
                for (Map.Entry<String, String> entry : PACKAGE_MAPPINGS.entrySet()) {
                    String oldPackage = entry.getKey();
                    String newPackage = entry.getValue();
                    
                    if (qualifiedName.startsWith(oldPackage)) {
                        // Replace the package prefix
                        String newQualifiedName = newPackage + qualifiedName.substring(oldPackage.length());
                        
                        // Create new type reference with updated package
                        CtTypeReference<T> newTypeRef = typeRef.getFactory()
                            .createReference(newQualifiedName);
                        
                        // Replace the type reference in the AST
                        typeRef.replace(newTypeRef);
                        transformCount++;
                        System.out.println("  Transformed type: " + qualifiedName + " -> " + newQualifiedName);
                        break;
                    }
                }
            }
        }
        
        public int getTransformCount() {
            return transformCount;
        }
    }
    
    /**
     * Main entry point for the transformation.
     * 
     * @param args Command line arguments:
     *             args[0]: Source directory to transform
     *             args[1]: Output directory for transformed code (optional)
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            printUsage();
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args.length > 1 ? args[1] : sourceDir;
        
        printHeader(sourceDir, outputDir);
        
        try {
            // Run Spoon-based AST transformation
            int spoonTransforms = runSpoonTransformation(sourceDir, outputDir);
            
            // Run backup file-based transformation for imports
            int fileTransforms = runFileBasedTransformation(outputDir);
            
            int totalTransforms = spoonTransforms + fileTransforms;
            
            printSummary(totalTransforms);
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Run the Spoon AST transformation.
     */
    private static int runSpoonTransformation(String sourceDir, String outputDir) throws Exception {
        System.out.println("1. Building AST model from source files...");
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setPreserveLineNumbers(true);
        
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(outputDir);
        
        CtModel model = launcher.getModel();
        
        System.out.println("2. Applying ByteBuddy package transformation...");
        ByteBuddyPackageTransformer transformer = new ByteBuddyPackageTransformer();
        model.getRootPackage().accept(transformer);
        
        System.out.println("3. Generating transformed source files...");
        launcher.prettyprint();
        
        return transformer.getTransformCount();
    }
    
    /**
     * Run file-based string replacement as backup for import statements.
     * Spoon can sometimes miss import transformations.
     */
    private static int runFileBasedTransformation(String outputDir) {
        final int[] fileCount = {0};
        
        try {
            Files.walk(Paths.get(outputDir))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> {
                    try {
                        String content = new String(Files.readAllBytes(path));
                        String originalContent = content;
                        
                        for (Map.Entry<String, String> entry : PACKAGE_MAPPINGS.entrySet()) {
                            String oldPackage = entry.getKey();
                            String newPackage = entry.getValue();
                            
                            // Transform regular imports
                            content = content.replace(
                                "import " + oldPackage + ".",
                                "import " + newPackage + "."
                            );
                            
                            // Transform static imports
                            content = content.replace(
                                "import static " + oldPackage + ".",
                                "import static " + newPackage + "."
                            );
                        }
                        
                        if (!content.equals(originalContent)) {
                            Files.write(path, content.getBytes());
                            fileCount[0]++;
                        }
                    } catch (Exception e) {
                        System.err.println("Error processing file " + path + ": " + e.getMessage());
                    }
                });
            
            if (fileCount[0] > 0) {
                System.out.println("4. File-based backup transformation modified " + fileCount[0] + " file(s)");
            }
            
        } catch (Exception e) {
            System.err.println("Error in file-based transformation: " + e.getMessage());
        }
        
        return fileCount[0];
    }
    
    private static void printUsage() {
        System.err.println("GENERIC TRANSFORMATION RULE: assertj-core 3.23.0 ByteBuddy Package Migration");
        System.err.println("=================================================================");
        System.err.println("Usage: java Main <source-directory> [output-directory]");
        System.err.println("  source-directory: Directory containing Java source files to transform");
        System.err.println("  output-directory: Directory to write transformed files (default: overwrite source)");
        System.err.println();
        System.err.println("This transformation fixes the breaking change in assertj-core 3.23.0");
        System.err.println("where ByteBuddy classes were moved from:");
        System.err.println("  org.assertj.core.internal.bytebuddy.*");
        System.err.println("to:");
        System.err.println("  net.bytebuddy.*");
    }
    
    private static void printHeader(String sourceDir, String outputDir) {
        System.out.println("=================================================================");
        System.out.println("GENERIC TRANSFORMATION: assertj-core 3.23.0 ByteBuddy Migration");
        System.out.println("=================================================================");
        System.out.println("Breaking Change: ByteBuddy moved from internal to external package");
        System.out.println("Old: org.assertj.core.internal.bytebuddy.*");
        System.out.println("New: net.bytebuddy.*");
        System.out.println();
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);
        System.out.println("=================================================================");
    }
    
    private static void printSummary(int totalTransforms) {
        System.out.println("=================================================================");
        System.out.println("TRANSFORMATION COMPLETE!");
        System.out.println("=================================================================");
        System.out.println("Total transformations applied: " + totalTransforms);
        System.out.println();
        System.out.println("NEXT STEPS:");
        System.out.println("1. Ensure ByteBuddy dependency is available:");
        System.out.println("   <dependency>");
        System.out.println("     <groupId>net.bytebuddy</groupId>");
        System.out.println("     <artifactId>byte-buddy</artifactId>");
        System.out.println("     <version>1.12.10</version> <!-- or later -->");
        System.out.println("   </dependency>");
        System.out.println();
        System.out.println("2. The transformed code should now compile with assertj-core 3.23.0+");
        System.out.println("=================================================================");
    }
}