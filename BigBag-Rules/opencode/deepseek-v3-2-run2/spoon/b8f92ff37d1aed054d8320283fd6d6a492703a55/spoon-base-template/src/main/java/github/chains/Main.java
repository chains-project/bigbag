package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtPackage;
import spoon.reflect.visitor.Query;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.code.CtTypeAccess;
import java.util.List;
import java.util.Collections;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying transformation to: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        CtModel model = launcher.buildModel();
        
        // Find all method invocations matching TestListResolver.getWildcard()
        List<CtInvocation<?>> invocations = Query.getElements(model.getRootPackage(), new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation<?> invocation) {
                CtExecutableReference<?> execRef = invocation.getExecutable();
                if (execRef == null) {
                    return false;
                }
                
                CtTypeReference<?> declaringType = execRef.getDeclaringType();
                if (declaringType == null) {
                    return false;
                }
                
                // Match: org.apache.maven.surefire.api.testset.TestListResolver.getWildcard()
                boolean typeMatches = "org.apache.maven.surefire.api.testset.TestListResolver".equals(declaringType.getQualifiedName());
                boolean methodMatches = "getWildcard".equals(execRef.getSimpleName());
                
                return typeMatches && methodMatches;
            }
        });
        
        System.out.println("Found " + invocations.size() + " occurrences of TestListResolver.getWildcard()");
        
        // Apply transformation
        for (CtInvocation<?> invocation : invocations) {
            try {
                // Create replacement: TestListResolver.getEmptyTestListResolver()
                CtTypeReference<?> testListResolverType = invocation.getFactory().Type()
                    .createReference("org.apache.maven.surefire.api.testset.TestListResolver");
                
                // Create static method reference for getEmptyTestListResolver() (no parameters)
                // Note: The 3rd parameter should be the return type, which is also TestListResolver
                CtExecutableReference<?> newMethodRef = invocation.getFactory().Executable()
                    .createReference(testListResolverType, true, testListResolverType, 
                                   "getEmptyTestListResolver", Collections.emptyList());
                
                // Replace the invocation
                // For a static method call to TestListResolver.getEmptyTestListResolver()
                CtTypeAccess<?> typeAccess = invocation.getFactory().Code()
                    .createTypeAccess(testListResolverType);
                CtInvocation<?> newInvocation = invocation.getFactory().Code()
                    .createInvocation(typeAccess, newMethodRef);
                
                System.out.println("  Replacing at: " + invocation.getPosition());
                invocation.replace(newInvocation);
                
            } catch (Exception e) {
                System.err.println("Error replacing invocation at " + invocation.getPosition() + ": " + e.getMessage());
            }
        }
        
        // Write modified files
        launcher.setSourceOutputDirectory(sourceDir);
        launcher.prettyprint();
        
        System.out.println("Transformation completed successfully");
    }
}