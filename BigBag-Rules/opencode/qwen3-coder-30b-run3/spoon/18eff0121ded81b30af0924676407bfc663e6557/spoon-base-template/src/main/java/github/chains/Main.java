package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtNewClass;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic Spoon transformation to fix breaking changes in dependency APIs.
 * Specifically handles removal of com.gargoylesoftware.htmlunit.ScriptResult
 * from acceptance-test-harness dependency.
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformer.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        
        // Initialize Spoon
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(sourceDir + "-transformed");
        launcher.getEnvironment().setComplianceLevel(8);
        
        // Build the model
        CtModel model = launcher.buildModel();
        Factory factory = launcher.getFactory();
        
        // Apply transformation
        transform(model, factory);
        
        // Write the result
        launcher.setSourceOutputDirectory(sourceDir + "-transformed");
        launcher.process();
    }
    
    /**
     * Transform the code to replace ScriptResult usage with direct handling
     */
    public static void transform(CtModel model, Factory factory) {
        // Find all usages of ScriptResult constructor calls
        List<CtNewClass> scriptResultCalls = model.getElements(new TypeFilter<>(CtNewClass.class) {
            @Override
            public boolean matches(CtNewClass element) {
                return element.getArguments().size() > 0;
            }
        });
        
        // Find all usages of ScriptResult methods
        List<CtInvocation> scriptResultMethodCalls = model.getElements(new TypeFilter<>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation element) {
                return element.getTarget() != null && 
                       element.getTarget().getType() != null &&
                       element.getTarget().getType().getQualifiedName() != null &&
                       element.getTarget().getType().getQualifiedName().contains("ScriptResult");
            }
        });
        
        // Replace ScriptResult usage by removing the wrapper and using direct values
        for (CtInvocation invocation : scriptResultMethodCalls) {
            if (invocation.getTarget() != null) {
                // Direct usage, just replace with target
                invocation.replace(invocation.getTarget());
            }
        }
        
        // Remove imports for ScriptResult
        List<CtImport> importsToRemove = model.getElements(new TypeFilter<>(CtImport.class) {
            @Override
            public boolean matches(CtImport element) {
                return element.getReference() != null && 
                       element.getReference().getSimpleName().equals("ScriptResult");
            }
        });
        
        for (CtImport imp : importsToRemove) {
            imp.delete();
        }
    }
}