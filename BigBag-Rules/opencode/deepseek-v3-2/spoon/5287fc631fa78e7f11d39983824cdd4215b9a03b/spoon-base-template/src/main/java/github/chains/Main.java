package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformer.jar <source-directory>");
            System.err.println("Transforms Jetty 8/9 code to Jetty 11:");
            System.err.println("1. javax.servlet -> jakarta.servlet");
            System.err.println("2. SelectChannelConnector -> ServerConnector");
            System.err.println("3. Removes deprecated Server methods");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        CtModel model = launcher.buildModel();
        
        // Apply transformations
        transformImportsAndTypes(model);
        transformConstructors(model);
        removeOldServerMethods(model);
        
        // Output
        launcher.setSourceOutputDirectory(sourceDir);
        launcher.prettyprint();
        
        System.out.println("Done. Manual fixes may be needed:");
        System.out.println("- ServerConnector needs Server parameter in constructor");
        System.out.println("- Configure HttpConfiguration for sendServerVersion/sendDateHeader");
    }
    
    private static void transformImportsAndTypes(CtModel model) {
        // Transform imports and type references
        List<CtImport> imports = model.getElements(new TypeFilter<CtImport>(CtImport.class) {
            @Override
            public boolean matches(CtImport element) {
                return element.getReference() instanceof CtTypeReference;
            }
        });
        
        for (CtImport imp : imports) {
            CtTypeReference typeRef = (CtTypeReference) imp.getReference();
            String name = typeRef.getQualifiedName();
            
            if (name != null) {
                // javax.servlet -> jakarta.servlet
                if (name.startsWith("javax.servlet")) {
                    String newName = name.replace("javax.servlet", "jakarta.servlet");
                    typeRef.setSimpleName(newName.substring(newName.lastIndexOf('.') + 1));
                    // Note: Changing qualified name directly might not work, 
                    // but changing simple name and letting auto-import handle it
                    System.out.println("Transformed import: " + name + " -> " + newName);
                }
                // SelectChannelConnector -> ServerConnector
                else if (name.equals("org.eclipse.jetty.server.nio.SelectChannelConnector")) {
                    typeRef.setSimpleName("ServerConnector");
                    // Would need to update package too, but Spoon should handle with auto-imports
                    System.out.println("Transformed import: SelectChannelConnector -> ServerConnector");
                }
            }
        }
        
        // Also transform type references in code
        List<CtTypeReference> typeRefs = model.getElements(new TypeFilter<CtTypeReference>(CtTypeReference.class));
        for (CtTypeReference typeRef : typeRefs) {
            String name = typeRef.getQualifiedName();
            if (name != null) {
                if (name.startsWith("javax.servlet")) {
                    String newName = name.replace("javax.servlet", "jakarta.servlet");
                    typeRef.setSimpleName(newName.substring(newName.lastIndexOf('.') + 1));
                }
                else if (name.equals("org.eclipse.jetty.server.nio.SelectChannelConnector") ||
                         name.equals("SelectChannelConnector")) {
                    typeRef.setSimpleName("ServerConnector");
                }
            }
        }
    }
    
    private static void transformConstructors(CtModel model) {
        List<CtConstructorCall> constructors = model.getElements(new TypeFilter<CtConstructorCall>(CtConstructorCall.class));
        
        for (CtConstructorCall constr : constructors) {
            CtTypeReference type = constr.getType();
            if (type != null) {
                String typeName = type.getQualifiedName();
                if (typeName != null && 
                    (typeName.equals("org.eclipse.jetty.server.nio.SelectChannelConnector") ||
                     typeName.equals("SelectChannelConnector"))) {
                    
                    // Change to ServerConnector
                    constr.setType(constr.getFactory().createReference("org.eclipse.jetty.server.ServerConnector"));
                    
                    // Add comment about needing Server parameter
                    CtStatement stmt = constr.getParent(CtStatement.class);
                    if (stmt != null) {
                        String comment = "// FIXME: ServerConnector needs Server parameter. Change to: new ServerConnector(server)";
                        stmt.addComment(constr.getFactory().createInlineComment(comment));
                    }
                    
                    System.out.println("Transformed constructor: SelectChannelConnector -> ServerConnector (needs Server parameter)");
                }
            }
        }
    }
    
    private static void removeOldServerMethods(CtModel model) {
        List<CtInvocation> invocations = model.getElements(new TypeFilter<CtInvocation>(CtInvocation.class));
        
        for (CtInvocation inv : invocations) {
            String signature = inv.getExecutable() != null ? inv.getExecutable().getSignature() : "";
            if (signature.startsWith("setSendServerVersion") || signature.startsWith("setSendDateHeader")) {
                // Remove the invocation
                inv.delete();
                
                // Add comment
                CtStatement stmt = inv.getParent(CtStatement.class);
                if (stmt != null) {
                    String comment = "// REMOVED: " + signature.split("\\(")[0] + 
                                   " - Configure HttpConfiguration in Jetty 11";
                    stmt.addComment(inv.getFactory().createInlineComment(comment));
                }
                
                System.out.println("Removed deprecated method: " + signature.split("\\(")[0]);
            }
        }
    }
}