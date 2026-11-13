package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtExpression;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.visitor.ProcessingVisitor;
import spoon.reflect.visitor.Filter;
import spoon.processing.AbstractProcessor;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtExecutableReference;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Properties;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * A generic Spoon transformation to fix breaking API changes in library dependencies.
 * This transformation handles the common pattern where factory methods change from
 * accepting simple parameters to accepting a configuration object.
 * 
 * Example transformation:
 * Old: KDTree.create(2)
 * New: KDTree.create(IndexConfig.create().setDimensions(2))
 * 
 * The transformation is parameterized to handle different:
 * - Target class names (e.g., "org.tinspin.index.kdtree.KDTree")
 * - Method names (e.g., "create")
 * - Configuration class names (e.g., "org.tinspin.index.IndexConfig")
 * - Parameter mapping rules
 * 
 * Configuration is loaded from a properties file or can be set programmatically.
 */
public class Main extends AbstractProcessor<CtInvocation<?>> {
    
    // Configuration for the transformation
    public static class TransformationRule {
        final String targetClassName;
        final String methodName;
        final String configClassName;
        final Map<Integer, String> paramToSetterMap; // Parameter index -> setter method name
        
        public TransformationRule(String targetClassName, String methodName, 
                          String configClassName, Map<Integer, String> paramToSetterMap) {
            this.targetClassName = targetClassName;
            this.methodName = methodName;
            this.configClassName = configClassName;
            this.paramToSetterMap = paramToSetterMap;
        }
    }
    
    // Define the transformation rules
    private final List<TransformationRule> rules = new ArrayList<>();
    private Properties config = new Properties();
    
    public Main() {
        // Load default configuration
        loadDefaultConfig();
    }
    
    public Main(String configFile) {
        loadConfigFromFile(configFile);
    }
    
    public Main(List<TransformationRule> customRules) {
        this.rules.addAll(customRules);
    }
    
    private void loadDefaultConfig() {
        // Default rule for KDTree.create(int dimensions)
        Map<Integer, String> kdTreeParamMap = new HashMap<>();
        kdTreeParamMap.put(0, "setDimensions"); // First parameter maps to setDimensions()
        rules.add(new TransformationRule(
            "org.tinspin.index.kdtree.KDTree",
            "create",
            "org.tinspin.index.IndexConfig",
            kdTreeParamMap
        ));
        
        // Default rule for CoverTree.create(int dimensions, double base, PointDistanceFunction distFunc)
        Map<Integer, String> coverTreeParamMap = new HashMap<>();
        coverTreeParamMap.put(0, "setDimensions"); // dimensions parameter
        // Note: base and distance function parameters would need custom handling
        rules.add(new TransformationRule(
            "org.tinspin.index.covertree.CoverTree",
            "create",
            "org.tinspin.index.IndexConfig",
            coverTreeParamMap
        ));
    }
    
    private void loadConfigFromFile(String configFile) {
        try (FileInputStream fis = new FileInputStream(configFile)) {
            config.load(fis);
            // Parse configuration from properties file
            // Format: rule1.targetClass=org.tinspin.index.kdtree.KDTree
            //         rule1.method=create
            //         rule1.configClass=org.tinspin.index.IndexConfig
            //         rule1.param0.setter=setDimensions
            //         rule1.param1.setter=setSomeOtherProperty
            // ... etc.
            
            int ruleIndex = 1;
            while (config.containsKey("rule" + ruleIndex + ".targetClass")) {
                String targetClass = config.getProperty("rule" + ruleIndex + ".targetClass");
                String method = config.getProperty("rule" + ruleIndex + ".method");
                String configClass = config.getProperty("rule" + ruleIndex + ".configClass");
                
                Map<Integer, String> paramMap = new HashMap<>();
                int paramIndex = 0;
                while (config.containsKey("rule" + ruleIndex + ".param" + paramIndex + ".setter")) {
                    String setter = config.getProperty("rule" + ruleIndex + ".param" + paramIndex + ".setter");
                    paramMap.put(paramIndex, setter);
                    paramIndex++;
                }
                
                rules.add(new TransformationRule(targetClass, method, configClass, paramMap));
                ruleIndex++;
            }
            
        } catch (IOException e) {
            System.err.println("Error loading config file: " + e.getMessage());
            loadDefaultConfig();
        }
    }
    
    public void addRule(TransformationRule rule) {
        rules.add(rule);
    }
    
    @Override
    public void process(CtInvocation<?> invocation) {
        // Get the executable reference
        CtExecutableReference<?> execRef = invocation.getExecutable();
        if (execRef == null) {
            return;
        }
        
        // Get the declaring type
        CtTypeReference<?> declaringType = execRef.getDeclaringType();
        if (declaringType == null) {
            return;
        }
        
        String methodName = execRef.getSimpleName();
        String declaringTypeName = declaringType.getQualifiedName();
        
        // Check if this invocation matches any of our rules
        for (TransformationRule rule : rules) {
            if (rule.targetClassName.equals(declaringTypeName) && 
                rule.methodName.equals(methodName)) {
                
                // Apply the transformation
                transformInvocation(invocation, rule);
                break;
            }
        }
    }
    
    private void transformInvocation(CtInvocation<?> invocation, TransformationRule rule) {
        try {
            // Get the factory method to create the config object
            getFactory().createMethod().createReference(
                getFactory().Type().createReference(rule.configClassName),
                "create"
            );
            
            // Create a constructor call for the config object
            // First create the config object: IndexConfig.create()
            CtInvocation<?> configCreation = getFactory().createInvocation(
                null,
                getFactory().createExecutableReference().setDeclaringType(
                    getFactory().Type().createReference(rule.configClassName)
                ).setSimpleName("create")
            );
            
            CtExpression<?> currentConfig = configCreation;
            
            // Apply setters for each parameter
            List<CtExpression<?>> arguments = invocation.getArguments();
            for (int i = 0; i < arguments.size(); i++) {
                String setterName = rule.paramToSetterMap.get(i);
                if (setterName != null) {
                    CtExpression<?> arg = arguments.get(i);
                    
                    // Create setter invocation: config.setXxx(arg)
                    CtInvocation<?> setterInvocation = getFactory().createInvocation(
                        currentConfig,
                        getFactory().createExecutableReference().setDeclaringType(
                            getFactory().Type().createReference(rule.configClassName)
                        ).setSimpleName(setterName),
                        arg
                    );
                    
                    currentConfig = setterInvocation;
                }
            }
            
            // Replace the original invocation with the new one
            // Original: KDTree.create(2)
            // New: KDTree.create(IndexConfig.create().setDimensions(2))
            CtInvocation<?> newInvocation = getFactory().createInvocation(
                null,
                getFactory().createExecutableReference().setDeclaringType(
                    getFactory().Type().createReference(rule.targetClassName)
                ).setSimpleName(rule.methodName),
                currentConfig
            );
            
            invocation.replace(newInvocation);
            
        } catch (Exception e) {
            System.err.println("Error transforming invocation: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    @Override
    public void processingDone() {
        System.out.println("Transformation processing completed.");
    }
    
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java github.chains.Main <inputSourceDir> <outputDir> [configFile]");
            System.out.println("Example: java github.chains.Main /path/to/project/src /path/to/output");
            System.out.println("Example with config: java github.chains.Main /path/to/project/src /path/to/output config.properties");
            System.out.println("\nThe transformation will:");
            System.out.println("1. Parse Java source code from inputSourceDir");
            System.out.println("2. Apply transformations based on rules (default or from config file)");
            System.out.println("3. Write transformed code to outputDir");
            System.out.println("\nDefault transformation rules handle:");
            System.out.println("  - KDTree.create(int) -> KDTree.create(IndexConfig.create().setDimensions(int))");
            System.out.println("  - CoverTree.create(int, ...) -> CoverTree.create(IndexConfig.create().setDimensions(int))");
            return;
        }
        
        String inputSourceDir = args[0];
        String outputDir = args[1];
        String configFile = args.length > 2 ? args[2] : null;
        
        System.out.println("Starting Spoon transformation...");
        System.out.println("Input directory: " + inputSourceDir);
        System.out.println("Output directory: " + outputDir);
        if (configFile != null) {
            System.out.println("Configuration file: " + configFile);
        }
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        
        // Configure launcher
        launcher.addInputResource(inputSourceDir);
        launcher.setSourceOutputDirectory(outputDir);
        
        // Add our processor
        Main processor;
        if (configFile != null) {
            processor = new Main(configFile);
        } else {
            processor = new Main();
        }
        
        launcher.addProcessor(processor);
        
        // Run the transformation
        try {
            launcher.run();
            System.out.println("Transformation completed successfully!");
            System.out.println("Transformed code written to: " + outputDir);
            
            // Print statistics
            System.out.println("\nApplied " + processor.rules.size() + " transformation rule(s)");
            for (int i = 0; i < processor.rules.size(); i++) {
                TransformationRule rule = processor.rules.get(i);
                System.out.println("Rule " + (i+1) + ": " + rule.targetClassName + "." + rule.methodName + 
                                 " -> " + rule.configClassName);
            }
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
        }
    }
}