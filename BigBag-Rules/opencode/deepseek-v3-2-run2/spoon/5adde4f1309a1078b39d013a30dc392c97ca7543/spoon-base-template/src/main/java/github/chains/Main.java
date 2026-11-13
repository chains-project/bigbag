package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtNewArray;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.reference.CtArrayTypeReference;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.factory.Factory;

import java.util.List;

/**
 * Generic transformation for SonarLint API breaking change.
 * 
 * Breaking Change Analysis:
 * - Old API: AnalysisEngineConfiguration.Builder.addEnabledLanguages(Set<Language>)
 * - New API: Method signature changed to addEnabledLanguages(Language...)
 * - Transformation: Convert Set argument to varargs array
 * 
 * This transformation is generic and can be applied to any project
 * affected by the same API breaking change.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -cp spoon-base-1.0-SNAPSHOT.jar github.chains.Main <source-dir> <output-dir>");
            System.err.println("Transformation: Fixes addEnabledLanguages(Set<Language>) -> addEnabledLanguages(Language...)");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("Applying SonarLint API transformation...");
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(outputDir);
        
        try {
            CtModel model = launcher.buildModel();
            
            // Find all addEnabledLanguages method calls
            List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
                @Override
                public boolean matches(CtInvocation<?> invocation) {
                    if (invocation.getExecutable() == null) return false;
                    String methodName = invocation.getExecutable().getSimpleName();
                    return "addEnabledLanguages".equals(methodName) && invocation.getArguments().size() == 1;
                }
            });
            
            System.out.println("Found " + invocations.size() + " addEnabledLanguages(Set<Language>) calls to transform");
            
            int transformed = 0;
            Factory factory = launcher.getFactory();
            
            for (CtInvocation<?> invocation : invocations) {
                try {
                    // Transform set.toArray(new Language[0])
                    CtExpression<?> setArg = invocation.getArguments().get(0);
                    
                    // Create Language type reference
                    CtTypeReference<?> languageType = factory.Type().createReference("org.sonarsource.sonarlint.core.commons.Language");
                    CtArrayTypeReference<?> languageArrayType = factory.Type().createArrayReference(languageType);
                    
                    // Create new Language[0] array
                    CtNewArray<?> newArray = factory.Core().createNewArray();
                    newArray.setType(languageArrayType);
                    
                    CtLiteral<Integer> zero = factory.Code().createLiteral(0);
                    newArray.addDimensionExpression(zero);
                    
                    // Create toArray method call
                    CtExecutableReference<?> toArrayRef = factory.Executable().createReference(
                        setArg.getType(),
                        languageArrayType,
                        "toArray",
                        languageArrayType
                    );
                    
                    CtInvocation<?> toArrayCall = factory.Code().createInvocation(
                        setArg.clone(),
                        toArrayRef,
                        newArray
                    );
                    
                    // Replace argument
                    invocation.getArguments().clear();
                    invocation.addArgument(toArrayCall);
                    
                    transformed++;
                    System.out.println("Transformed call at line " + invocation.getPosition().getLine());
                    
                } catch (Exception e) {
                    System.err.println("Error transforming invocation: " + e.getMessage());
                }
            }
            
            // Also update AnalysisEngineConfiguration type references if they exist
            // This is commented out as it requires more context about the specific project
            
            launcher.prettyprint();
            
            System.out.println("\nSuccessfully transformed " + transformed + " of " + invocations.size() + " calls");
            System.out.println("Output written to: " + outputDir);
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
        }
    }
}