package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.CtScanner;
import java.util.List;

/**
 * Generic Spoon transformation to fix breaking dependency changes
 * where com.gargoylesoftware.htmlunit.ScriptResult is replaced by
 * org.jenkinsci.test.acceptance.plugins.scriptler.ScriptResult
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Apply transformation
        System.out.println("Starting transformation...");
        ScriptResultTransformation transformation = new ScriptResultTransformation(launcher.getFactory());
        transformation.scan(model);
        System.out.println("Transformation scanning completed.");
        
        // Write transformed code back
        launcher.setSourceOutputDirectory("/tmp/spoon-output/");
        launcher.prettyprint();
        
        System.out.println("Transformation completed successfully!");
    }
    
    /**
     * Generic transformation that replaces:
     * - Import: com.gargoylesoftware.htmlunit.ScriptResult -> org.jenkinsci.test.acceptance.plugins.scriptler.ScriptResult
     * - Constructor: new ScriptResult(Object) -> new ScriptResult(String)
     * - Method: getJavaScriptResult() -> toString()
     */
    static class ScriptResultTransformation extends CtScanner {
        
        private final spoon.reflect.factory.Factory factory;
        private static final String OLD_TYPE = "com.gargoylesoftware.htmlunit.ScriptResult";
        private static final String NEW_TYPE = "org.jenkinsci.test.acceptance.plugins.scriptler.ScriptResult";
        
        public ScriptResultTransformation(spoon.reflect.factory.Factory factory) {
            this.factory = factory;
        }
        
        @Override
        public void scan(spoon.reflect.declaration.CtElement element) {
            System.out.println("Scanning element: " + element.getClass().getSimpleName());
            super.scan(element);
        }
        
        @Override
        public void visitCtImport(CtImport ctImport) {
            super.visitCtImport(ctImport);
            
            // Replace import statements
            if (ctImport.getReference() != null) {
                String importRef = ctImport.getReference().toString();
                if (importRef.contains("com.gargoylesoftware.htmlunit.ScriptResult")) {
                    System.out.println("Found import to replace: " + importRef);
                    CtTypeReference<?> newTypeRef = factory.Type().createReference(NEW_TYPE);
                    ctImport.setReference(newTypeRef);
                    System.out.println("Replaced import: " + OLD_TYPE + " -> " + NEW_TYPE);
                }
            }
        }
        
        @Override
        public <T> void visitCtConstructorCall(CtConstructorCall<T> ctConstructorCall) {
            super.visitCtConstructorCall(ctConstructorCall);
            
            // Replace constructor calls - use simple name matching since type resolution may not work
            String constructorCallStr = ctConstructorCall.toString();
            if (constructorCallStr.contains("new ScriptResult(")) {
                System.out.println("Found ScriptResult constructor call: " + constructorCallStr);
                
                // Create new constructor call with String parameter
                if (ctConstructorCall.getArguments().size() == 1) {
                    // Convert Object argument to String
                    CtConstructorCall<String> newConstructorCall = factory.Code()
                        .createConstructorCall(factory.Type().createReference(NEW_TYPE));
                    
                    // Convert the argument to String via toString() if needed
                    CtInvocation<String> toStringInvocation = factory.Core().createInvocation();
                    toStringInvocation.setTarget(ctConstructorCall.getArguments().get(0));
                    CtExecutableReference<String> toStringRef = factory.Core().createExecutableReference();
                    toStringRef.setSimpleName("toString");
                    toStringRef.setDeclaringType(factory.Type().createReference(Object.class));
                    toStringInvocation.setExecutable(toStringRef);
                    newConstructorCall.addArgument(toStringInvocation);
                    
                    ctConstructorCall.replace(newConstructorCall);
                    System.out.println("Replaced constructor call: new ScriptResult(Object) -> new ScriptResult(String)");
                }
            }
        }
        
        @Override
        public <T> void visitCtInvocation(CtInvocation<T> ctInvocation) {
            super.visitCtInvocation(ctInvocation);
            
            // Replace method calls - use simple name matching since type resolution may not work
            String invocationStr = ctInvocation.toString();
            if (invocationStr.contains(".getJavaScriptResult()")) {
                System.out.println("Found getJavaScriptResult method call: " + invocationStr);
                
                // Replace getJavaScriptResult() with toString()
                CtInvocation<String> newInvocation = factory.Core().createInvocation();
                newInvocation.setTarget(ctInvocation.getTarget());
                CtExecutableReference<String> toStringRef = factory.Core().createExecutableReference();
                toStringRef.setSimpleName("toString");
                toStringRef.setDeclaringType(factory.Type().createReference(Object.class));
                newInvocation.setExecutable(toStringRef);
                
                ctInvocation.replace(newInvocation);
                System.out.println("Replaced method call: getJavaScriptResult() -> toString()");
            }
        }
    }
}