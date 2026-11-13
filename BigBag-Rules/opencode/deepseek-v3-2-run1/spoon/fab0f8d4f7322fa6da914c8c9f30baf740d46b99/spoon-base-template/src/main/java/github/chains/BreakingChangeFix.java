package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtNewClass;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;

/**
 * Generic transformation to fix breaking dependency changes where:
 * 1. org.cactoos.io.BytesOf and org.cactoos.text.HexOf classes are no longer available
 * 2. Code using new Digest.Sha256(new HexOf(new BytesOf(sha.digest())).asString())
 *    should be replaced with new Digest.Sha256(sha.digest())
 * 3. org.cactoos.list.ListOf should be replaced with java.util.Arrays.asList()
 * 
 * This is a generic transformation that can be applied to any project
 * affected by the removal of cactoos dependency.
 */
public class BreakingChangeFix {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java BreakingChangeFix <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Fixing breaking dependency changes in: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        CtModel model = launcher.buildModel();
        
        // Process all types in the model
        for (CtType<?> type : model.getAllTypes()) {
            System.out.println("Processing type: " + type.getQualifiedName());
            
            // 1. Fix Digest.Sha256(new HexOf(new BytesOf(...)).asString()) pattern
            fixDigestSha256Calls(type);
            
            // 2. Fix ListOf usage
            fixListOfUsage(type);
            
            // 3. Remove cactoos imports
            removeCactoosImports(type);
        }
        
        // Apply transformations
        launcher.setSourceOutputDirectory(sourceDir);
        launcher.prettyprint();
        
        System.out.println("Transformation complete!");
    }
    
    private static void fixDigestSha256Calls(CtType<?> type) {
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
                        
                        if (hexOfNewClass.getArguments().size() == 1 && 
                            hexOfNewClass.getArguments().get(0) instanceof CtNewClass) {
                            
                            CtNewClass<?> bytesOfNewClass = (CtNewClass<?>) hexOfNewClass.getArguments().get(0);
                            if (bytesOfNewClass.getType() != null && 
                                bytesOfNewClass.getType().getQualifiedName() != null &&
                                bytesOfNewClass.getType().getQualifiedName().contains("BytesOf")) {
                                
                                if (bytesOfNewClass.getArguments().size() == 1) {
                                    // Replace with new Digest.Sha256(bytesOfArgument)
                                    call.setArguments(List.of(bytesOfNewClass.getArguments().get(0)));
                                    System.out.println("  Fixed Digest.Sha256 call");
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    private static void fixListOfUsage(CtType<?> type) {
        List<CtNewClass<?>> listOfCalls = type.getElements(
            new TypeFilter<CtNewClass<?>>(CtNewClass.class) {
                @Override
                public boolean matches(CtNewClass<?> newClass) {
                    try {
                        return newClass.getType() != null && 
                               newClass.getType().getQualifiedName() != null &&
                               newClass.getType().getQualifiedName().contains("ListOf");
                    } catch (Exception e) {
                        return false;
                    }
                }
            }
        );
        
        for (CtNewClass<?> listOfCall : listOfCalls) {
            // Replace new ListOf<T>(...) with Arrays.asList(...)
            // Note: This would need a more sophisticated transformation
            // to handle type parameters and import changes
            System.out.println("  Found ListOf usage that needs manual replacement with Arrays.asList()");
        }
    }
    
    private static void removeCactoosImports(CtType<?> type) {
        List<CtImport> imports = type.getElements(new TypeFilter<CtImport>(CtImport.class) {});
        
        for (CtImport imp : imports) {
            String importStr = imp.toString();
            if (importStr.contains("org.cactoos")) {
                System.out.println("  Removing import: " + importStr);
                try {
                    imp.delete();
                } catch (Exception e) {
                    System.err.println("  Failed to remove import: " + importStr + ", error: " + e.getMessage());
                }
            }
        }
    }
}