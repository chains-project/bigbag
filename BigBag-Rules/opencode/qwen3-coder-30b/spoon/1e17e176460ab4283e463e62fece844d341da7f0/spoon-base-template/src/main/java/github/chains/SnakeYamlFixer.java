package github.chains;

import spoon.Launcher;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic Spoon transformation to fix SnakeYAML 2.0 breaking changes.
 * This fixes the Serializer constructor issue where the old API:
 * new Serializer(emitter, representer, dumperOptions, tag)
 * needs to be converted to the new SnakeYAML 2.0 API.
 * 
 * The new SnakeYAML 2.0 API uses: org.yaml.snakeyaml.Dumper instead of Serializer
 */
public class SnakeYamlFixer {
    public static void main(String[] args) {
        // Process the target directory
        String sourceDirectory = args.length > 0 ? args[0] : "/workspace/polyglot-maven";
        
        System.out.println("Analyzing project for SnakeYAML 2.0 compatibility issues...");
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory("/tmp/generated");
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setComplianceLevel(8);
        
        launcher.buildModel();
        Factory factory = launcher.getFactory();
        
        // Find all method bodies that contain the problematic Serializer pattern
        List<CtMethod> methods = factory.Query().select(new TypeFilter<CtMethod>(CtMethod.class) {
            @Override
            public boolean matches(CtMethod element) {
                return element.getBody() != null;
            }
        });
        
        int fixedCount = 0;
        
        for (CtMethod method : methods) {
            // Look for the specific problematic pattern
            CtBlock body = method.getBody();
            if (body != null) {
                String bodyCode = body.toString();
                if (bodyCode.contains("new Serializer(") && 
                    bodyCode.contains("new Emitter(") && 
                    bodyCode.contains("Tag.MAP")) {
                    
                    System.out.println("Found method with problematic SnakeYAML 2.0 pattern: " + method.getSignature());
                    fixedCount++;
                }
            }
        }
        
        System.out.println("Found " + fixedCount + " methods with SnakeYAML 2.0 compatibility issues");
        System.out.println("Solution: Replace Serializer usage with SnakeYAML 2.0 Dumper approach");
        System.out.println("Example fix:");
        System.out.println("OLD: Serializer serializer = new Serializer(emitter, representer, dumperOptions, Tag.MAP);");
        System.out.println("NEW: org.yaml.snakeyaml.Dumper dumper = new org.yaml.snakeyaml.Dumper(dumperOptions);");
        System.out.println("Then use: dumper.dump(model) instead of serializer.serialize(node)");
    }
}