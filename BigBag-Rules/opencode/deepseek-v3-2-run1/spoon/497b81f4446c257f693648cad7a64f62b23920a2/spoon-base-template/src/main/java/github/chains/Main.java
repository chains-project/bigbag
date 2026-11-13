package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtNewClass;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;
import java.util.stream.Collectors;

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
        launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
        launcher.addInputResource(sourceDir);
        
        try {
            CtModel model = launcher.buildModel();
            
            // Apply transformation to fix cactoos HexOf/BytesOf usage
            fixCactoosHexBytesUsage(model);
            
            // Remove cactoos imports
            removeCactoosImports(model);
            
            // Write transformed code
            launcher.setSourceOutputDirectory(sourceDir);
            launcher.prettyprint();
            
            System.out.println("Transformation completed successfully!");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void fixCactoosHexBytesUsage(CtModel model) {
        // Find all constructor calls to Digest.Sha256
        List<CtConstructorCall<?>> sha256Calls = model.getElements(new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class))
            .stream()
            .filter(call -> call.getType() != null && 
                call.getType().getQualifiedName().equals("com.artipie.docker.Digest$Sha256"))
            .collect(Collectors.toList());
        
        for (CtConstructorCall<?> call : sha256Calls) {
            // Check if this call has a single argument that matches the pattern:
            // new HexOf(new BytesOf(...)).asString()
            if (call.getArguments().size() == 1) {
                try {
                    CtInvocation<?> arg = (CtInvocation<?>) call.getArguments().get(0);
                    if (arg.getTarget() instanceof CtNewClass && 
                        arg.getExecutable().getSimpleName().equals("asString")) {
                        
                        CtNewClass<?> hexOfNewClass = (CtNewClass<?>) arg.getTarget();
                        if (hexOfNewClass.getType().getQualifiedName().equals("org.cactoos.text.HexOf")) {
                            
                            // Check if HexOf constructor has BytesOf as argument
                            if (hexOfNewClass.getArguments().size() == 1) {
                                Object bytesOfArg = hexOfNewClass.getArguments().get(0);
                                if (bytesOfArg instanceof CtNewClass) {
                                    CtNewClass<?> bytesOfNewClass = (CtNewClass<?>) bytesOfArg;
                                    if (bytesOfNewClass.getType().getQualifiedName().equals("org.cactoos.io.BytesOf")) {
                                        
                                        // Extract the actual byte array expression from BytesOf constructor
                                        if (bytesOfNewClass.getArguments().size() == 1) {
                                            // Replace the entire expression with just the byte array
                                            call.getArguments().set(0, bytesOfNewClass.getArguments().get(0));
                                            System.out.println("Fixed Digest.Sha256 constructor call at: " + 
                                                call.getPosition());
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (ClassCastException e) {
                    // Argument is not in the expected pattern, skip
                    continue;
                }
            }
        }
        
        // Also find standalone HexOf/BytesOf usage (just in case)
        List<CtNewClass<?>> hexOfCalls = model.getElements(new TypeFilter<CtNewClass<?>>(CtNewClass.class))
            .stream()
            .filter(newClass -> newClass.getType() != null && 
                newClass.getType().getQualifiedName().equals("org.cactoos.text.HexOf"))
            .collect(Collectors.toList());
            
        for (CtNewClass<?> hexOfCall : hexOfCalls) {
            // Try to find if this HexOf is part of a Digest.Sha256 constructor
            // If not, we might need to handle it differently
            System.out.println("Found standalone HexOf call at: " + hexOfCall.getPosition());
        }
    }
    
    private static void removeCactoosImports(CtModel model) {
        // Get all imports
        List<CtImport> imports = model.getElements(new TypeFilter<CtImport>(CtImport.class));
        
        // Remove cactoos imports
        for (CtImport imp : imports) {
            String importStr = imp.toString();
            if (importStr.contains("org.cactoos.")) {
                imp.delete();
                System.out.println("Removed import: " + importStr);
            }
        }
    }
}