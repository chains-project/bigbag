package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.BinaryOperatorKind;
import spoon.reflect.code.CtBinaryOperator;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Generic Spoon transformation to fix MySQL connector breaking changes.
 * This transformation updates:
 * - Import statements from com.mysql.jdbc.exceptions.* to com.mysql.cj.jdbc.exceptions.*
 * - instanceof checks using the old package to use the new package
 * 
 * The transformation is parameterized by the old and new package prefixes,
 * making it reusable for any similar package migration scenarios.
 */
public class Main extends AbstractProcessor<CtType<?>> {
    
    // Configuration parameters - can be set via properties
    private String oldPackagePrefix = "com.mysql.jdbc.exceptions";
    private String newPackagePrefix = "com.mysql.cj.jdbc.exceptions";
    
    @Override
    public void process(CtType<?> type) {
        // Process import statements
        processImports(type);
        
        // Process instanceof checks
        processInstanceofChecks(type);
    }
    
    private void processImports(CtType<?> type) {
        for (CtImport ctImport : type.getPosition().getCompilationUnit().getImports()) {
            if (ctImport.getReference() instanceof CtTypeReference) {
                CtTypeReference<?> typeRef = (CtTypeReference<?>) ctImport.getReference();
                String qualifiedName = typeRef.getQualifiedName();
                
                if (qualifiedName.startsWith(oldPackagePrefix)) {
                    // Replace old package with new package
                    String newQualifiedName = qualifiedName.replace(oldPackagePrefix, newPackagePrefix);
                    // Create new type reference and replace the import
                    CtTypeReference<?> newTypeRef = getFactory().Type().createReference(newQualifiedName);
                    ctImport.setReference(newTypeRef);
                }
            }
        }
    }
    
    private void processInstanceofChecks(CtType<?> type) {
        // Find all binary operators that are instanceof checks
        List<CtBinaryOperator<?>> instanceofOps = type.getElements(new TypeFilter<>(CtBinaryOperator.class));
        
        for (CtBinaryOperator<?> op : instanceofOps) {
            if (op.getKind() == BinaryOperatorKind.INSTANCEOF) {
                // Check if the right operand is a type access using the old package
                if (op.getRightHandOperand() instanceof CtTypeAccess) {
                    CtTypeAccess<?> typeAccess = (CtTypeAccess<?>) op.getRightHandOperand();
                    CtTypeReference<?> typeRef = typeAccess.getAccessedType();
                    
                    if (typeRef != null && typeRef.getQualifiedName().startsWith(oldPackagePrefix)) {
                        // Replace with new package
                        String newQualifiedName = typeRef.getQualifiedName().replace(oldPackagePrefix, newPackagePrefix);
                        CtTypeReference<?> newTypeRef = getFactory().Type().createReference(newQualifiedName);
                        CtTypeAccess<?> newTypeAccess = getFactory().Code().createTypeAccess(newTypeRef);
                        op.setRightHandOperand(newTypeAccess);
                    }
                }
            }
        }
    }
    
    @Override
    public Set<Class<? extends CtElement>> getProcessedElementTypes() {
        // Process all types to ensure we catch all import statements and instanceof checks
        Set<Class<? extends CtElement>> types = new HashSet<>();
        types.add(CtType.class);
        return types;
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory> [output-directory]");
            System.err.println("  source-directory: Path to the source code to transform");
            System.err.println("  output-directory: Optional path for transformed output (default: same as source)");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args.length > 1 ? args[1] : sourceDir;
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setSourceOutputDirectory(new java.io.File(outputDir));
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        // Add our transformation processor
        Main processor = new Main();
        launcher.addProcessor(processor);
        
        // Run the transformation
        launcher.run();
        
        System.out.println("Transformation completed successfully!");
        System.out.println("Source: " + sourceDir);
        System.out.println("Output: " + outputDir);
    }
}