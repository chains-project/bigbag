package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;
import java.util.ArrayList;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setLevel("OFF");
        
        // Build model
        CtModel model = launcher.buildModel();
        
        // Apply transformations
        transformTlsPackage(model);
        
        // Output transformed code to a temporary directory first
        String tempOutputDir = sourceDir + "-transformed";
        launcher.setSourceOutputDirectory(tempOutputDir);
        launcher.prettyprint();
        
        // Copy transformed files back to source directory
        System.out.println("Copying transformed files back to source directory...");
        copyTransformedFiles(tempOutputDir, sourceDir);
        
        System.out.println("Transformation complete!");
    }
    
    private static void copyTransformedFiles(String sourceDir, String targetDir) throws IOException {
        Path sourcePath = Paths.get(sourceDir);
        Path targetPath = Paths.get(targetDir);
        
        Files.walk(sourcePath)
            .filter(Files::isRegularFile)
            .forEach(sourceFile -> {
                try {
                    Path relativePath = sourcePath.relativize(sourceFile);
                    Path targetFile = targetPath.resolve(relativePath);
                    Files.createDirectories(targetFile.getParent());
                    Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    System.err.println("Error copying file: " + e.getMessage());
                }
            });
    }
    
    private static void transformTlsPackage(CtModel model) {
        // Remove imports from org.bouncycastle.crypto.tls
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
            System.out.println("Removing import: " + ctImport.getReference());
            ctImport.delete();
        });
        
        // Replace TlsFatalAlert constructor calls with IOException
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
            System.out.println("Replacing TlsFatalAlert constructor call at: " + 
                constructorCall.getPosition());
            
            // Create new IOException constructor call
            Launcher launcher = new Launcher();
            launcher.getEnvironment().setNoClasspath(true);
            
            // Check if we have an AlertDescription argument
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
            
            // Create IOException with message
            CtConstructorCall<?> ioExceptionCall = constructorCall.getFactory().createConstructorCall(
                constructorCall.getFactory().Type().createReference("java.io.IOException"),
                constructorCall.getFactory().createLiteral(exceptionMessage)
            );
            
            constructorCall.replace(ioExceptionCall);
        });
        
        // Note: We don't need to explicitly handle AlertDescription field accesses
        // because they're only used as arguments to TlsFatalAlert constructor
        // which we already handle above. Any standalone AlertDescription.XXX
        // references would need to be handled separately if they exist.
    }
}