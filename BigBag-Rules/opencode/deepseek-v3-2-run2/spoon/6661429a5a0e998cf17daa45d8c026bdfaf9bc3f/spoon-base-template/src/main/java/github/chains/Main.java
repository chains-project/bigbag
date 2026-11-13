package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.visitor.CtScanner;
import java.util.*;

/**
 * Generic transformation for fixing breaking API changes in ScriptResult class.
 * 
 * Old API pattern:
 *   import com.gargoylesoftware.htmlunit.ScriptResult;
 *   ScriptResult scriptResult = new ScriptResult(object);
 *   return scriptResult.getJavaScriptResult().toString();
 * 
 * New API pattern (after transformation):
 *   // import removed (no longer needed)
 *   return object.toString();
 * 
 * This transformation handles:
 * 1. Removal of com.gargoylesoftware.htmlunit.ScriptResult imports
 * 2. Transformation of new ScriptResult(object) to object.toString()
 * 3. Removal of .getJavaScriptResult() calls
 * 4. Type changes for ScriptResult variables to String
 */
public class Main {
    // Configurable old and new class names
    private static final String OLD_CLASS = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String NEW_CLASS = "org.jenkinsci.test.acceptance.plugins.scriptler.ScriptResult";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java Main <sourceDirectory> [outputDirectory]");
            System.out.println("  sourceDirectory: Root directory of Java source files to transform");
            System.out.println("  output directory: Where to write transformed files (default: sourceDirectory)");
            return;
        }
        
        String sourceDir = args[0];
        String outputDir = args.length > 1 ? args[1] : sourceDir;
        
        System.out.println("Transforming " + OLD_CLASS + " usage...");
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(outputDir);
        
        CtModel model = launcher.buildModel();
        
        // Create and run transformer
        ScriptResultTransformer transformer = new ScriptResultTransformer();
        model.getRootPackage().accept(transformer);
        
        launcher.prettyprint();
        System.out.println("Transformation complete! Processed " + transformer.getTransformCount() + " items.");
    }
    
    static class ScriptResultTransformer extends CtScanner {
        private int transformCount = 0;
        
        public int getTransformCount() {
            return transformCount;
        }
        
        @Override
        public <T> void visitCtConstructorCall(CtConstructorCall<T> ctor) {
            super.visitCtConstructorCall(ctor);
            
            if (ctor.getType() != null && OLD_CLASS.equals(ctor.getType().getQualifiedName())) {
                System.out.println("[TRANSFORM] Constructor: " + ctor);
                transformCount++;
                
                List<CtExpression<?>> args = ctor.getArguments();
                if (args.size() == 1) {
                    CtExpression<?> arg = args.get(0);
                    
                    // Replace new ScriptResult(arg) with arg.toString()
                    CtInvocation<String> toStringCall = ctor.getFactory().createInvocation();
                    toStringCall.setTarget(arg.clone());
                    
                    CtExecutableReference<String> toStringRef = ctor.getFactory().createExecutableReference();
                    toStringRef.setSimpleName("toString");
                    toStringCall.setExecutable(toStringRef);
                    
                    ctor.replace(toStringCall);
                }
            }
        }
        
        @Override
        public <T> void visitCtInvocation(CtInvocation<T> invoc) {
            super.visitCtInvocation(invoc);
            
            if ("getJavaScriptResult".equals(invoc.getExecutable().getSimpleName())) {
                System.out.println("[TRANSFORM] Method call: " + invoc);
                transformCount++;
                
                CtExpression<?> target = invoc.getTarget();
                
                if (target instanceof CtConstructorCall) {
                    // Pattern: new ScriptResult(arg).getJavaScriptResult()
                    // Replace with arg.toString()
                    CtConstructorCall<?> ctor = (CtConstructorCall<?>) target;
                    List<CtExpression<?>> args = ctor.getArguments();
                    if (args.size() == 1) {
                        CtInvocation<String> toStringCall = invoc.getFactory().createInvocation();
                        toStringCall.setTarget(args.get(0).clone());
                        
                        CtExecutableReference<String> toStringRef = invoc.getFactory().createExecutableReference();
                        toStringRef.setSimpleName("toString");
                        toStringCall.setExecutable(toStringRef);
                        
                        invoc.replace(toStringCall);
                    }
                } else {
                    // Pattern: variable.getJavaScriptResult()
                    // Replace with variable (which should be String after transformation)
                    invoc.replace(target.clone());
                }
            }
        }
        
        @Override
        public <T> void visitCtLocalVariable(CtLocalVariable<T> variable) {
            super.visitCtLocalVariable(variable);
            
            CtTypeReference type = variable.getType();
            if (type != null && OLD_CLASS.equals(type.getQualifiedName())) {
                System.out.println("[TRANSFORM] Variable type: " + variable.getSimpleName());
                transformCount++;
                
                // Change ScriptResult type to String
                type.setSimpleName("String");
                // Also need to update the package
                CtTypeReference stringType = variable.getFactory().Type().createReference("java.lang.String");
                variable.setType(stringType);
            }
        }
        
        @Override
        public void visitCtImport(CtImport ctImport) {
            super.visitCtImport(ctImport);
            
            if (ctImport.getReference() instanceof CtTypeReference) {
                CtTypeReference ref = (CtTypeReference) ctImport.getReference();
                if (OLD_CLASS.equals(ref.getQualifiedName())) {
                    System.out.println("[TRANSFORM] Removing import: " + ref.getQualifiedName());
                    transformCount++;
                    
                    // Remove the import entirely
                    ctImport.delete();
                }
            }
        }
    }
}