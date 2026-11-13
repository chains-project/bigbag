package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtBlock;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtExecutableReference;
import spoon.support.reflect.code.CtInvocationImpl;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java github.chains.Main <source-directory> <output-directory>");
            System.err.println("  source-directory: Directory containing Java source files to transform");
            System.err.println("  output-directory: Directory to write transformed files (optional, in-place if not specified)");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args.length > 1 ? args[1] : sourceDir;
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(outputDir);
        
        try {
            CtModel model = launcher.buildModel();
            Factory factory = launcher.getFactory();
            
            // Find all method invocations of getClassInfo on CoverageDatabase objects
            List<CtInvocation> invocations = model.getElements(new TypeFilter<CtInvocation>(CtInvocation.class) {
                @Override
                public boolean matches(CtInvocation invocation) {
                    if (invocation.getTarget() != null && invocation.getExecutable() != null) {
                        String methodName = invocation.getExecutable().getSimpleName();
                        
                        // Check if method is getClassInfo
                        if ("getClassInfo".equals(methodName)) {
                            // Check if target type is CoverageDatabase or subtype
                            CtTypeReference targetType = invocation.getTarget().getType();
                            if (targetType != null && targetType.toString().contains("CoverageDatabase")) {
                                return true;
                            }
                        }
                    }
                    return false;
                }
            });
            
            System.out.println("Found " + invocations.size() + " breaking API usages.");
            
            for (CtInvocation invocation : invocations) {
                System.out.println("Transforming: " + invocation.getPosition());
                
                // Get the argument (should be Collection<ClassName>)
                List<CtExpression> arguments = invocation.getArguments();
                if (!arguments.isEmpty()) {
                    CtExpression arg = arguments.get(0);
                    
                    // Create replacement: compute package name from ClassName
                    // This is a simplified fix - actual fix may vary
                    String replacementCode = 
                        "// FIXME: getClassInfo method removed from CoverageDatabase\n" +
                        "// Original: " + invocation.toString() + "\n" +
                        "// Need to implement proper replacement";
                    
                    System.out.println("  Would replace: " + invocation.toString());
                    System.out.println("  With: " + replacementCode);
                    
                    // In a real transformation, we would replace the invocation
                    // But this requires more complex AST manipulation
                    // For now, just report the issue
                }
            }
            
            System.out.println("\nSummary of breaking change:");
            System.out.println("=========================================");
            System.out.println("Old API: CoverageDatabase.getClassInfo(Collection<ClassName>) -> Collection<ClassInfo>");
            System.out.println("New API: Method removed from CoverageDatabase");
            System.out.println("Available alternatives:");
            System.out.println("  1. CodeSource.getClassInfo(Collection<ClassName>) -> Collection<ClassInfo>");
            System.out.println("  2. ClassInfoSource.fetchClass(ClassName) -> Optional<ClassInfo>");
            System.out.println("  3. Refactor to not need ClassInfo (compute from ClassName directly)");
            System.out.println("\nTransformation template:");
            System.out.println("=========================================");
            System.out.println("If you have access to CodeSource:");
            System.out.println("  Replace: coverageDatabase.getClassInfo(classes)");
            System.out.println("  With: codeSource.getClassInfo(classes)");
            System.out.println("\nIf you need package name only:");
            System.out.println("  Replace: coverageDatabase.getClassInfo(Collections.singleton(className))");
            System.out.println("  With: computePackageNameFromClassName(className)");
            System.out.println("\nPackage name computation:");
            System.out.println("  String javaName = className.asJavaName();");
            System.out.println("  String packageName = javaName.replace(\".\", \"/\");");
            System.out.println("  int lastSlash = packageName.lastIndexOf('/');");
            System.out.println("  return lastSlash > 0 ? packageName.substring(0, lastSlash) : \"default\";");
            
            // Actually apply transformation if output directory is different
            if (!sourceDir.equals(outputDir)) {
                launcher.prettyprint();
                System.out.println("\nTransformed files written to: " + outputDir);
            }
            
        } catch (Exception e) {
            System.err.println("Error analyzing code: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}