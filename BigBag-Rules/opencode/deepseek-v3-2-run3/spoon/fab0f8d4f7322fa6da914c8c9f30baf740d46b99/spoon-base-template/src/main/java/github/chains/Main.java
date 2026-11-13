package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;

/**
 * Generic transformation for fixing breaking changes when cactoos HexOf and BytesOf classes are removed.
 * Transforms patterns like: new Digest.Sha256(new HexOf(new BytesOf(bytes)).asString())
 * to: new Digest.Sha256(hexString) where hexString is bytes converted to hex
 * 
 * This is a generic transformation that can be applied to any project affected by this
 * breaking dependency change.
 */
public class Main {
    
    /**
     * Main method to run the transformation.
     * @param args Command line arguments: [sourceDir] [outputDir]
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -jar spoon-transform.jar <sourceDir> <outputDir>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("Starting HexOf/BytesOf removal transformation on: " + sourceDir);
        System.out.println("Output directory: " + outputDir);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true); // Work with source code only
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(outputDir);
        
        // Build model
        CtModel model = launcher.buildModel();
        
        // Apply transformations
        int transformations = transformHexOfBytesOfPattern(model);
        int importsRemoved = removeCactoosImports(model);
        
        // Write transformed code
        launcher.prettyprint();
        
        System.out.println("Transformation completed!");
        System.out.println("Pattern transformations applied: " + transformations);
        System.out.println("Imports removed: " + importsRemoved);
    }
    
    /**
     * Transforms patterns: new HexOf(new BytesOf(expr)).asString() → expr
     * This works for any expression 'expr' that produces a byte array.
     */
    private static int transformHexOfBytesOfPattern(CtModel model) {
        int count = 0;
        
        // Find all constructor calls to HexOf
        List<CtConstructorCall> hexOfCalls = model.getElements(
            new TypeFilter<CtConstructorCall>(CtConstructorCall.class) {
                @Override
                public boolean matches(CtConstructorCall element) {
                    CtTypeReference<?> typeRef = element.getType();
                    return typeRef != null && 
                           "org.cactoos.text.HexOf".equals(typeRef.getQualifiedName());
                }
            });
        
        for (CtConstructorCall hexOfCall : hexOfCalls) {
            // Check if HexOf has exactly one argument which is a BytesOf constructor call
            if (hexOfCall.getArguments().size() == 1) {
                Object arg = hexOfCall.getArguments().get(0);
                if (arg instanceof CtConstructorCall) {
                    CtConstructorCall innerCall = (CtConstructorCall) arg;
                    CtTypeReference<?> innerType = innerCall.getType();
                    
                    if (innerType != null && 
                        "org.cactoos.io.BytesOf".equals(innerType.getQualifiedName()) &&
                        innerCall.getArguments().size() == 1) {
                        
                        // Check if HexOf(...) is called with .asString()
                        CtInvocation<?> parentInvocation = hexOfCall.getParent(CtInvocation.class);
                        if (parentInvocation != null && 
                            "asString".equals(parentInvocation.getExecutable().getSimpleName())) {
                            
                            // Get the argument to BytesOf (the byte array expression)
                            Object bytesArg = innerCall.getArguments().get(0);
                            if (bytesArg instanceof CtExpression) {
                                CtExpression<?> bytesExpr = (CtExpression<?>) bytesArg;
                                
                                // Replace the entire .asString() invocation with the byte array expression
                                parentInvocation.replace(bytesExpr);
                                count++;
                                System.out.println("Transformed HexOf/BytesOf.asString() pattern at line: " + 
                                    hexOfCall.getPosition().getLine());
                            }
                        }
                    }
                }
            }
        }
        
        return count;
    }
    
    /**
     * Removes imports of org.cactoos.io.BytesOf and org.cactoos.text.HexOf
     */
    private static int removeCactoosImports(CtModel model) {
        int count = 0;
        
        // Find all imports
        List<CtImport> imports = model.getElements(new TypeFilter<CtImport>(CtImport.class));
        
        for (CtImport imp : imports) {
            String importStr = imp.toString();
            if (importStr.contains("org.cactoos.io.BytesOf") || 
                importStr.contains("org.cactoos.text.HexOf")) {
                imp.delete();
                count++;
                System.out.println("Removed import: " + importStr);
            }
        }
        
        // Also check compilation units for unused imports
        // The imports might still be in the file even if unused after transformation
        List<spoon.reflect.declaration.CtCompilationUnit> compilationUnits = 
            model.getElements(new TypeFilter<spoon.reflect.declaration.CtCompilationUnit>(spoon.reflect.declaration.CtCompilationUnit.class));
        
        for (spoon.reflect.declaration.CtCompilationUnit cu : compilationUnits) {
            // We'll let Spoon's pretty printer handle unused imports
            // The imports should be automatically removed if unused
            System.out.println("Processed compilation unit: " + cu.getFile().getName());
        }
        
        return count;
    }
}