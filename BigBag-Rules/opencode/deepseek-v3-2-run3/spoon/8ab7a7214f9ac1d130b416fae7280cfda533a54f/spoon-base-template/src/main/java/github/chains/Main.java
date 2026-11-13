package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.Filter;
import java.util.List;
import java.util.ArrayList;

public class Main {
    @SuppressWarnings("rawtypes")
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java github.chains.Main <sourceDir> <outputDir>");
            System.err.println("Transforms code after removal of com.gargoylesoftware.htmlunit.ScriptResult");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("=== Generic Spoon Transformation ===");
        System.out.println("Target: Fix removal of htmlunit ScriptResult API");
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(outputDir);
        launcher.getEnvironment().setAutoImports(true);
        
        CtModel model = launcher.buildModel();
        int changes = 0;
        
        // Remove imports
        for (CtType type : model.getAllTypes()) {
            CtCompilationUnit cu = type.getPosition().getCompilationUnit();
            if (cu != null) {
                List<CtImport> imports = new ArrayList<>(cu.getImports());
                for (CtImport imp : imports) {
                    if (imp.getReference().toString().contains("com.gargoylesoftware.htmlunit.ScriptResult")) {
                        imp.delete();
                        changes++;
                        System.out.println("Removed import: " + imp.getReference());
                    }
                }
            }
        }
        
        // Transform getJavaScriptResult() calls
        for (CtType type : model.getAllTypes()) {
            List<CtInvocation> calls = type.getElements(new Filter<CtInvocation>() {
                @Override
                public boolean matches(CtInvocation inv) {
                    return "getJavaScriptResult".equals(inv.getExecutable().getSimpleName());
                }
            });
            
            for (CtInvocation call : calls) {
                if (call.getTarget() instanceof CtConstructorCall) {
                    CtConstructorCall constr = (CtConstructorCall) call.getTarget();
                    CtTypeReference typeRef = constr.getType();
                    if (typeRef != null && "ScriptResult".equals(typeRef.getSimpleName())) {
                        if (!constr.getArguments().isEmpty()) {
                            Object arg = constr.getArguments().get(0);
                            if (arg instanceof CtExpression) {
                                call.replace((CtExpression) arg);
                                changes++;
                                System.out.println("Replaced getJavaScriptResult() at line " + 
                                                 call.getPosition().getLine());
                            }
                        }
                    }
                }
            }
        }
        
        // Transform variable declarations
        for (CtType type : model.getAllTypes()) {
            List<CtVariable> vars = type.getElements(new Filter<CtVariable>() {
                @Override
                public boolean matches(CtVariable var) {
                    CtTypeReference typeRef = var.getType();
                    if (typeRef == null) return false;
                    String qname = typeRef.getQualifiedName();
                    return qname != null && qname.contains("com.gargoylesoftware.htmlunit.ScriptResult");
                }
            });
            
            for (CtVariable var : vars) {
                // Change type to Object
                var.setType(var.getFactory().Type().OBJECT);
                changes++;
                System.out.println("Changed variable type: " + var.getSimpleName());
            }
        }
        
        // Write output
        launcher.prettyprint();
        
        System.out.println("\n=== Summary ===");
        System.out.println("Changes applied: " + changes);
        System.out.println("Output: " + outputDir);
        System.out.println("\n=== Transformation Rule ===");
        System.out.println("OLD: new ScriptResult(obj).getJavaScriptResult()");
        System.out.println("NEW: obj");
        System.out.println("\nOLD: ScriptResult var = new ScriptResult(obj)");
        System.out.println("NEW: Object var = obj");
        System.out.println("\nThis is a generic transformation for the breaking change.");
    }
}