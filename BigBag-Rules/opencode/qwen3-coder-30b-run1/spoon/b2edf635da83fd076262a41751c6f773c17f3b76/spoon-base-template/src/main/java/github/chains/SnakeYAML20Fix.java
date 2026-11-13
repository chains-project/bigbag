package github.chains;

import java.io.File;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import spoon.Launcher;
import spoon.SpoonModelBuilder;
import spoon.compiler.SpoonResource;
import spoon.compiler.SpoonResourceHelper;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtPackage;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.reflect.code.CtConstructorCallImpl;

/**
 * A generic Spoon transformation for fixing SnakeYAML 2.0 breaking changes.
 * This transformation addresses the most common constructor signature changes
 * when migrating from SnakeYAML 1.x to 2.0.
 */
public class SnakeYAML20Fix {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java SnakeYAML20Fix <source_directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        String outputDirectory = args.length > 1 ? args[1] : sourceDirectory + "-transformed";
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setComplianceLevel(8);
        launcher.getEnvironment().setNoClasspath(true);
        
        try {
            // Add source directory
            launcher.addInputResource(sourceDirectory);
            
            // Build model
            launcher.buildModel();
            
            Factory factory = launcher.getFactory();
            
            // Process all constructor calls to Yaml class
            List<CtConstructorCall> yamlConstructors = factory.getModel().getElements(new TypeFilter<>(CtConstructorCall.class) {
                @Override
                public boolean matches(CtConstructorCall element) {
                    return element.getType() != null && 
                           "org.yaml.snakeyaml.Yaml".equals(element.getType().getQualifiedName());
                }
            });
            
            for (CtConstructorCall constructorCall : yamlConstructors) {
                fixYamlConstructorCall(constructorCall);
            }
            
            // Generate output
            launcher.setSourceOutputDirectory(outputDirectory);
            launcher.prettyprint();
            
            System.out.println("Transformation complete! Output saved to: " + outputDirectory);
            
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Fixes SnakeYAML constructor calls to be compatible with version 2.0
     */
    private static void fixYamlConstructorCall(CtConstructorCall constructorCall) {
        Factory factory = constructorCall.getFactory();
        
        // Get the constructor parameters
        List<CtExpression<?>> arguments = constructorCall.getArguments();
        
        // Check if it's a parameterless constructor (Yaml())
        if (arguments.isEmpty()) {
            // Replace with Yaml(new LoaderOptions())
            CtConstructorCall newCall = factory.Core().createConstructorCall();
            newCall.setExecutable(factory.Executable().createReference(
                factory.Type().createReference("org.yaml.snakeyaml.Yaml"),
                factory.Type().createReference("org.yaml.snakeyaml.LoaderOptions")
            ));
            
            // Create LoaderOptions() constructor call
            CtConstructorCall loaderOptionsCall = factory.Core().createConstructorCall();
            loaderOptionsCall.setExecutable(factory.Executable().createReference(
                factory.Type().createReference("org.yaml.snakeyaml.LoaderOptions")
            ));
            
            newCall.addArgument(loaderOptionsCall);
            constructorCall.replace(newCall);
            return;
        }
        
        // Check for Yaml(boolean allowDuplicateKeys) constructor
        if (arguments.size() == 1 && 
            "boolean".equals(arguments.get(0).getType().getSimpleName())) {
            // This constructor was removed in SnakeYAML 2.0
            // Replace with Yaml(new LoaderOptions()) and setAllowDuplicateKeys
            CtConstructorCall newCall = factory.Core().createConstructorCall();
            newCall.setExecutable(factory.Executable().createReference(
                factory.Type().createReference("org.yaml.snakeyaml.Yaml")
            ));
            
            // Create LoaderOptions() constructor call
            CtConstructorCall loaderOptionsCall = factory.Core().createConstructorCall();
            loaderOptionsCall.setExecutable(factory.Executable().createReference(
                factory.Type().createReference("org.yaml.snakeyaml.LoaderOptions")
            ));
            
            newCall.addArgument(loaderOptionsCall);
            
            // Add method call to setAllowDuplicateKeys
            CtInvocation setAllowDuplicateKeys = factory.Core().createInvocation();
            setAllowDuplicateKeys.setTarget(newCall);
            setAllowDuplicateKeys.setExecutable(factory.Executable().createReference(
                factory.Type().createReference("org.yaml.snakeyaml.Yaml"),
                "setAllowDuplicateKeys",
                factory.Type().createReference("boolean")
            ));
            setAllowDuplicateKeys.addArgument(arguments.get(0));
            
            // Replace the original constructor call with the new one
            constructorCall.replace(newCall);
            return;
        }
        
        // Check for Yaml(Constructor) constructor
        if (arguments.size() == 1 && 
            "org.yaml.snakeyaml.constructor.Constructor".equals(arguments.get(0).getType().getQualifiedName())) {
            // This constructor signature changed in SnakeYAML 2.0
            // Replace with Yaml(new Constructor(), new LoaderOptions())
            CtConstructorCall newCall = factory.Core().createConstructorCall();
            newCall.setExecutable(factory.Executable().createReference(
                factory.Type().createReference("org.yaml.snakeyaml.Yaml")
            ));
            
            // Keep the Constructor argument
            newCall.addArgument(arguments.get(0));
            
            // Create LoaderOptions() constructor call
            CtConstructorCall loaderOptionsCall = factory.Core().createConstructorCall();
            loaderOptionsCall.setExecutable(factory.Executable().createReference(
                factory.Type().createReference("org.yaml.snakeyaml.LoaderOptions")
            ));
            
            newCall.addArgument(loaderOptionsCall);
            
            constructorCall.replace(newCall);
            return;
        }
        
        // Check for Yaml(Representer, Constructor) constructor
        if (arguments.size() == 2 && 
            "org.yaml.snakeyaml.representer.Representer".equals(arguments.get(0).getType().getQualifiedName()) &&
            "org.yaml.snakeyaml.constructor.Constructor".equals(arguments.get(1).getType().getQualifiedName())) {
            // This constructor signature changed in SnakeYAML 2.0
            // Replace with Yaml(new Representer(), new Constructor(), new LoaderOptions())
            CtConstructorCall newCall = factory.Core().createConstructorCall();
            newCall.setExecutable(factory.Executable().createReference(
                factory.Type().createReference("org.yaml.snakeyaml.Yaml")
            ));
            
            // Keep the Representer and Constructor arguments
            newCall.addArgument(arguments.get(0));
            newCall.addArgument(arguments.get(1));
            
            // Create LoaderOptions() constructor call
            CtConstructorCall loaderOptionsCall = factory.Core().createConstructorCall();
            loaderOptionsCall.setExecutable(factory.Executable().createReference(
                factory.Type().createReference("org.yaml.snakeyaml.LoaderOptions")
            ));
            
            newCall.addArgument(loaderOptionsCall);
            
            constructorCall.replace(newCall);
            return;
        }
    }
}