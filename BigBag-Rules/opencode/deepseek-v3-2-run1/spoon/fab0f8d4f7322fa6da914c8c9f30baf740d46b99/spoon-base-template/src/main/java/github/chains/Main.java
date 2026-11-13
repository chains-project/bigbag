package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtNewClass;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming source directory: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setCommentEnabled(true);
        
        CtModel model = launcher.buildModel();
        
        // Find all types in the model
        for (CtType<?> type : model.getAllTypes()) {
            // Find constructor calls to Digest.Sha256
            List<CtConstructorCall<?>> sha256Calls = type.getElements(
                new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {
                    @Override
                    public boolean matches(CtConstructorCall<?> call) {
                        try {
                            return call.getType() != null && 
                                   call.getType().getQualifiedName() != null &&
                                   call.getType().getQualifiedName().contains("Digest$Sha256");
                        } catch (Exception e) {
                            return false;
                        }
                    }
                }
            );
            
            for (CtConstructorCall<?> call : sha256Calls) {
                // Check if this is a call with HexOf(BytesOf(...)).asString() pattern
                if (call.getArguments().size() == 1) {
                    CtInvocation<?> arg = call.getArguments().get(0).getElements(
                        new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
                            @Override
                            public boolean matches(CtInvocation<?> invocation) {
                                return invocation.getTarget() != null &&
                                       invocation.getExecutable() != null &&
                                       "asString".equals(invocation.getExecutable().getSimpleName());
                            }
                        }
                    ).stream().findFirst().orElse(null);
                    
                    if (arg != null && arg.getTarget() instanceof CtNewClass) {
                        CtNewClass<?> hexOfNewClass = (CtNewClass<?>) arg.getTarget();
                        if (hexOfNewClass.getType() != null && 
                            hexOfNewClass.getType().getQualifiedName() != null &&
                            hexOfNewClass.getType().getQualifiedName().contains("HexOf")) {
                            
                            // Check if HexOf has BytesOf as argument
                            if (hexOfNewClass.getArguments().size() == 1 && 
                                hexOfNewClass.getArguments().get(0) instanceof CtNewClass) {
                                
                                CtNewClass<?> bytesOfNewClass = (CtNewClass<?>) hexOfNewClass.getArguments().get(0);
                                if (bytesOfNewClass.getType() != null && 
                                    bytesOfNewClass.getType().getQualifiedName() != null &&
                                    bytesOfNewClass.getType().getQualifiedName().contains("BytesOf")) {
                                    
                                    // Found the pattern: new Digest.Sha256(new HexOf(new BytesOf(...)).asString())
                                    // Extract the argument to BytesOf (should be byte array expression)
                                    if (bytesOfNewClass.getArguments().size() == 1) {
                                        // Replace the entire constructor call with new Digest.Sha256(bytesOfArgument)
                                        call.setArguments(java.util.Collections.singletonList(bytesOfNewClass.getArguments().get(0)));
                                        System.out.println("Transformed Digest.Sha256 call in " + type.getQualifiedName());
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Apply transformations
        launcher.setSourceOutputDirectory(sourceDir);
        launcher.prettyprint();
        
        System.out.println("Transformation complete!");
    }
}