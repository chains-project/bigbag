package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.util.List;
import java.util.Set;

/**
 * Generic transformation rule for fixing reflections library breaking change:
 * FilterBuilder.apply(String) -> FilterBuilder.test(String)
 * 
 * This transformation is applicable to ANY Java project affected by the 
 * reflections 0.10.2 API change where FilterBuilder.apply() was renamed to test().
 */
public class Main {
    
    private static final String FILTER_BUILDER_FQN = "org.reflections.util.FilterBuilder";
    private static final String OLD_METHOD_NAME = "apply";
    private static final String NEW_METHOD_NAME = "test";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.err.println("Example: java -jar spoon-transformation.jar /path/to/project/src/main/java");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            Launcher launcher = new Launcher();
            launcher.addInputResource(sourceDir);
            launcher.getEnvironment().setNoClasspath(true);
            launcher.getEnvironment().setAutoImports(true);
            launcher.getEnvironment().setCommentEnabled(true);
            launcher.setSourceOutputDirectory(new File(sourceDir).getParentFile());
            
            CtModel model = launcher.buildModel();
            
            int transformations = applyTransformation(model);
            
            System.out.println("Applied " + transformations + " transformations");
            
            if (transformations > 0) {
                launcher.prettyprint();
                System.out.println("Transformation completed successfully");
            } else {
                System.out.println("No transformations needed");
            }
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static int applyTransformation(CtModel model) {
        List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation<?> invocation) {
                return isFilterBuilderApplyCall(invocation);
            }
        });
        
        int count = 0;
        for (CtInvocation<?> invocation : invocations) {
            try {
                invocation.getExecutable().setSimpleName(NEW_METHOD_NAME);
                System.out.println("Transformed: " + invocation.getPosition().getCompilationUnit().getFile().getName() 
                    + " at line " + invocation.getPosition().getLine());
                count++;
            } catch (Exception e) {
                System.err.println("Failed to transform invocation at " + invocation.getPosition());
            }
        }
        
        return count;
    }
    
    private static boolean isFilterBuilderApplyCall(CtInvocation<?> invocation) {
        try {
            String methodName = invocation.getExecutable().getSimpleName();
            if (!OLD_METHOD_NAME.equals(methodName)) {
                return false;
            }
            
            CtType<?> targetType = invocation.getTarget().getType().getTypeDeclaration();
            if (targetType == null) {
                return false;
            }
            
            String targetTypeName = targetType.getQualifiedName();
            
            if (FILTER_BUILDER_FQN.equals(targetTypeName)) {
                return true;
            }
            
            if (targetType instanceof CtClass) {
                CtClass<?> targetClass = (CtClass<?>) targetType;
                Set<CtTypeReference<?>> superInterfaces = targetClass.getSuperInterfaces();
                for (CtTypeReference<?> superInterface : superInterfaces) {
                    if (FILTER_BUILDER_FQN.equals(superInterface.getQualifiedName())) {
                        return true;
                    }
                }
                
                CtTypeReference<?> superClassRef = targetClass.getSuperclass();
                if (superClassRef != null && FILTER_BUILDER_FQN.equals(superClassRef.getQualifiedName())) {
                    return true;
                }
            }
            
            return false;
            
        } catch (Exception e) {
            return false;
        }
    }
}