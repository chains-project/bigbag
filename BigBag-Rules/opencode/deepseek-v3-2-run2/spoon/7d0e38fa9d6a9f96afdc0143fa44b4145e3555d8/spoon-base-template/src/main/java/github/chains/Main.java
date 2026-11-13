package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.visitor.Query;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.QueueProcessingManager;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtNewArray;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.reference.CtTypeReference;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -cp target/classes github.chains.Main <source-directory>");
            System.err.println("Optional: Provide configuration file as second argument");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDir);
        
        try {
            CtModel model = launcher.buildModel();
            
            // Create and add processor
            MethodSignatureMigrationProcessor processor = new MethodSignatureMigrationProcessor();
            
            // Configure the processor for the Dns API breaking change
            // This is where we specify the breaking change pattern
            processor.addMigrationRule(
                "com.google.api.services.dns.Dns$Changes", 
                "create", 
                3,  // Old argument count: create(project, zone, change)
                1,  // Insert position for new parameter
                "global"  // Value to insert
            );
            processor.addMigrationRule("com.google.api.services.dns.Dns$Changes", "get", 3, 1, "global");
            processor.addMigrationRule("com.google.api.services.dns.Dns$Changes", "list", 2, 1, "global");
            processor.addMigrationRule("com.google.api.services.dns.Dns$ManagedZones", "create", 2, 1, "global");
            processor.addMigrationRule("com.google.api.services.dns.Dns$ManagedZones", "get", 2, 1, "global");
            processor.addMigrationRule("com.google.api.services.dns.Dns$ManagedZones", "delete", 2, 1, "global");
            processor.addMigrationRule("com.google.api.services.dns.Dns$ManagedZones", "list", 1, 1, "global");
            processor.addMigrationRule("com.google.api.services.dns.Dns$ResourceRecordSets", "list", 2, 1, "global");
            processor.addMigrationRule("com.google.api.services.dns.Dns$Projects", "get", 1, 1, "global");
            
            QueueProcessingManager processingManager = new QueueProcessingManager(launcher.getFactory());
            processingManager.addProcessor(processor);
            processingManager.process(model.getRootPackage());
            
            // Write transformed code
            launcher.setSourceOutputDirectory("/tmp/spoon-output");
            launcher.prettyprint();
            
            System.out.println("Transformation complete. Output written to /tmp/spoon-output");
            System.out.println("Total method calls transformed: " + processor.getTransformedCount());
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    static class MethodSignatureMigrationProcessor extends AbstractProcessor<CtInvocation<?>> {
        private int transformedCount = 0;
        private Map<String, Map<String, MigrationRule>> migrationRules = new HashMap<>();
        
        static class MigrationRule {
            String className;
            String methodName;
            int oldArgCount;
            int insertPosition;
            String insertValue;
            
            MigrationRule(String className, String methodName, int oldArgCount, int insertPosition, String insertValue) {
                this.className = className;
                this.methodName = methodName;
                this.oldArgCount = oldArgCount;
                this.insertPosition = insertPosition;
                this.insertValue = insertValue;
            }
        }
        
        public void addMigrationRule(String className, String methodName, int oldArgCount, int insertPosition, String insertValue) {
            migrationRules
                .computeIfAbsent(className, k -> new HashMap<>())
                .put(methodName, new MigrationRule(className, methodName, oldArgCount, insertPosition, insertValue));
        }
        
        @Override
        public boolean isToBeProcessed(CtInvocation<?> invocation) {
            String methodName = invocation.getExecutable().getSimpleName();
            CtExpression<?> target = invocation.getTarget();
            
            if (target == null || target.getType() == null) {
                return false;
            }
            
            String targetType = target.getType().getQualifiedName();
            List<CtExpression<?>> arguments = invocation.getArguments();
            
            // Check if we have a migration rule for this class and method
            Map<String, MigrationRule> classRules = migrationRules.get(targetType);
            if (classRules != null) {
                MigrationRule rule = classRules.get(methodName);
                if (rule != null && arguments.size() == rule.oldArgCount) {
                    return true;
                }
            }
            
            return false;
        }
        
        @Override
        public void process(CtInvocation<?> invocation) {
            try {
                String methodName = invocation.getExecutable().getSimpleName();
                String targetType = invocation.getTarget().getType().getQualifiedName();
                
                MigrationRule rule = migrationRules.get(targetType).get(methodName);
                if (rule == null) {
                    return;
                }
                
                System.out.println("Processing invocation: " + invocation);
                System.out.println("  Method: " + methodName + " on " + targetType);
                System.out.println("  Old arg count: " + invocation.getArguments().size());
                
                // Get current arguments
                List<CtExpression<?>> arguments = new ArrayList<>(invocation.getArguments());
                
                // Insert the new parameter
                if (rule.insertPosition >= 0 && rule.insertPosition <= arguments.size()) {
                    CtLiteral<String> valueLiteral = getFactory().createLiteral(rule.insertValue);
                    arguments.add(rule.insertPosition, valueLiteral);
                    
                    // Update the invocation with new arguments
                    invocation.setArguments(arguments);
                    
                    transformedCount++;
                    System.out.println("  -> Added '" + rule.insertValue + "' at position " + rule.insertPosition);
                    System.out.println("  -> New arg count: " + invocation.getArguments().size());
                }
            } catch (Exception e) {
                System.err.println("Error processing invocation: " + invocation + " - " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        public int getTransformedCount() {
            return transformedCount;
        }
    }
}