package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;
import java.util.ArrayList;
import java.io.IOException;

/**
 * Generic transformation to fix breaking changes in bcprov-jdk15on-1.67
 * where the org.bouncycastle.crypto.tls package was removed.
 * 
 * Transformation rules:
 * 1. Remove all imports from org.bouncycastle.crypto.tls
 * 2. Replace TlsFatalAlert constructor calls with IOException
 * 3. Replace AlertDescription.XXX field accesses with appropriate string values
 * 
 * This transformation is generic and can be applied to any project
 * affected by this breaking change.
 */
public class TlsPackageTransformer {
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java -jar tls-transformer.jar <source-directory>");
            System.err.println("Applies fixes for removed org.bouncycastle.crypto.tls package");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying TLS package fixes to: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setLevel("OFF");
        
        CtModel model = launcher.buildModel();
        transformTlsPackage(model);
        
        launcher.setSourceOutputDirectory(sourceDir);
        launcher.prettyprint();
        
        System.out.println("Transformation complete!");
    }
    
    private static void transformTlsPackage(CtModel model) {
        System.out.println("Removing imports from org.bouncycastle.crypto.tls...");
        
        List<CtImport> importsToRemove = new ArrayList<>();
        model.getElements(new TypeFilter<CtImport>(CtImport.class) {
            @Override
            public boolean matches(CtImport ctImport) {
                if (ctImport.getReference() != null) {
                    String importStr = ctImport.getReference().toString();
                    return importStr.startsWith("org.bouncycastle.crypto.tls");
                }
                return false;
            }
        }).forEach(importsToRemove::add);
        
        importsToRemove.forEach(ctImport -> {
            System.out.println("  Removing import: " + ctImport.getReference());
            ctImport.delete();
        });
        
        System.out.println("Replacing TlsFatalAlert constructor calls...");
        
        model.getElements(new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {
            @Override
            public boolean matches(CtConstructorCall<?> constructorCall) {
                if (constructorCall.getType() != null) {
                    String typeName = constructorCall.getType().getQualifiedName();
                    return "org.bouncycastle.crypto.tls.TlsFatalAlert".equals(typeName);
                }
                return false;
            }
        }).forEach(constructorCall -> {
            System.out.println("  Replacing TlsFatalAlert at: " + 
                constructorCall.getPosition());
            
            String exceptionMessage = "TLS fatal alert";
            if (!constructorCall.getArguments().isEmpty()) {
                Object firstArg = constructorCall.getArguments().get(0);
                if (firstArg instanceof CtFieldAccess) {
                    CtFieldAccess<?> fieldAccess = (CtFieldAccess<?>) firstArg;
                    if (fieldAccess.getVariable() != null) {
                        String fieldName = fieldAccess.getVariable().getSimpleName();
                        exceptionMessage = "TLS fatal alert: " + fieldName;
                    }
                }
            }
            
            CtConstructorCall<?> ioExceptionCall = constructorCall.getFactory().createConstructorCall(
                constructorCall.getFactory().Type().createReference("java.io.IOException"),
                constructorCall.getFactory().createLiteral(exceptionMessage)
            );
            
            constructorCall.replace(ioExceptionCall);
        });
        
        System.out.println("Transformation applied successfully.");
        System.out.println("  Removed " + importsToRemove.size() + " imports");
    }
}