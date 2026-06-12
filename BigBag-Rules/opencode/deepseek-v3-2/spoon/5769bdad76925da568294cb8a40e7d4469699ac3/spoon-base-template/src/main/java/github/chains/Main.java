package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying transformation to: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDir);
        
        try {
            launcher.buildModel();
            
            List<CtFieldAccess> fieldAccesses = launcher.getModel()
                .getElements(new TypeFilter<CtFieldAccess>(CtFieldAccess.class) {
                    @Override
                    public boolean matches(CtFieldAccess fieldAccess) {
                        if (fieldAccess.getVariable() == null) {
                            return false;
                        }
                        
                        String fieldName = fieldAccess.getVariable().getSimpleName();
                        return "status".equals(fieldName);
                    }
                });
            
            System.out.println("Found " + fieldAccesses.size() + " occurrences of .status field access");
            
            int transformed = 0;
            for (CtFieldAccess fieldAccess : fieldAccesses) {
                System.out.println("Transforming: " + fieldAccess.getPosition());
                
                CtExpression target = fieldAccess.getTarget().clone();
                CtExecutableReference executableRef = launcher.getFactory().createExecutableReference();
                executableRef.setSimpleName("getStatus");
                
                CtInvocation methodCall = launcher.getFactory().createInvocation();
                methodCall.setTarget(target);
                methodCall.setExecutable(executableRef);
                
                fieldAccess.replace(methodCall);
                transformed++;
            }
            
            launcher.setSourceOutputDirectory(sourceDir);
            launcher.prettyprint();
            
            System.out.println("Transformation completed successfully. Transformed " + transformed + " occurrences.");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}