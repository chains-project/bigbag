package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.Filter;
import spoon.reflect.visitor.Query;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformer.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Analyzing source directory: " + sourceDir);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        
        try {
            // Build model
            launcher.buildModel();
            Factory factory = launcher.getFactory();
            
            // Find all invocations of getClassInfo on CoverageDatabase
            List<CtInvocation<?>> invocations = Query.getElements(factory, new Filter<CtInvocation<?>>() {
                @Override
                public boolean matches(CtInvocation<?> invocation) {
                    // Check if method name is getClassInfo
                    if (!"getClassInfo".equals(invocation.getExecutable().getSimpleName())) {
                        return false;
                    }
                    
                    // Check if target expression type is CoverageDatabase or subtype
                    if (invocation.getTarget() != null && invocation.getTarget().getType() != null) {
                        CtTypeReference<?> targetType = invocation.getTarget().getType();
                        
                        // Check if type is CoverageDatabase or implements it
                        try {
                            // Check if type is CoverageDatabase or contains it in name
                            String typeName = targetType.getQualifiedName();
                            if (typeName != null && 
                                (typeName.equals("org.pitest.coverage.CoverageDatabase") || 
                                 typeName.contains("CoverageDatabase"))) {
                                return true;
                            }
                            
                            // Try to check subtype relationship
                            if (targetType.getTypeDeclaration() != null) {
                                // Simple check: look for CoverageDatabase in supertypes
                                for (CtTypeReference<?> superType : targetType.getTypeDeclaration().getSuperInterfaces()) {
                                    if (superType.getQualifiedName() != null && 
                                        superType.getQualifiedName().contains("CoverageDatabase")) {
                                        return true;
                                    }
                                }
                            }
                        } catch (Exception e) {
                            // If we can't determine, check by name
                            String typeName = targetType.getQualifiedName();
                            if (typeName != null && typeName.contains("CoverageDatabase")) {
                                return true;
                            }
                        }
                    }
                    
                    return false;
                }
            });
            
            System.out.println("\n=== Found " + invocations.size() + " getClassInfo() calls on CoverageDatabase ===");
            
            for (CtInvocation<?> inv : invocations) {
                System.out.println("\nLocation: " + inv.getPosition());
                System.out.println("Full invocation: " + inv);
                
                // Get containing method and class
                CtMethod<?> containingMethod = inv.getParent(CtMethod.class);
                CtClass<?> containingClass = inv.getParent(CtClass.class);
                
                if (containingMethod != null) {
                    System.out.println("In method: " + containingMethod.getSignature());
                }
                if (containingClass != null) {
                    System.out.println("In class: " + containingClass.getQualifiedName());
                }
                
                // Suggested fix
                System.out.println("\nSUGGESTED FIX:");
                System.out.println("The getClassInfo method has been removed from CoverageDatabase in pitest-entry 1.10.0.");
                System.out.println("It is now available in CodeSource.getClassInfo(Collection<ClassName>).");
                System.out.println("You need to:");
                System.out.println("1. Find or obtain a CodeSource instance (may be passed as parameter or available in context)");
                System.out.println("2. Replace: coverage.getClassInfo(...)");
                System.out.println("3. With: codeSource.getClassInfo(...)");
                System.out.println("4. Note: Parameter type changed from Set<ClassName> to Collection<ClassName> (compatible)");
            }
            
            if (invocations.isEmpty()) {
                System.out.println("No getClassInfo calls on CoverageDatabase found.");
            }
            
        } catch (Exception e) {
            System.err.println("Error analyzing code: " + e.getMessage());
            e.printStackTrace();
        }
    }
}