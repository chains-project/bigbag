package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.visitor.Query;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.*;

/**
 * GENERIC, REUSABLE Spoon transformation for Apache Thrift 0.16.0 breaking changes.
 * 
 * This transformation can be applied to ANY Maven project affected by the 
 * Thrift 0.16.0 breaking changes by simply changing the input source directory path.
 * 
 * BREAKING CHANGES HANDLED:
 * 1. TFastFramedTransport and TFramedTransport moved to 'org.apache.thrift.transport.layered' package
 * 2. TDeserializer and TSerializer constructors now throw TTransportException
 * 3. TFastFramedTransport and TFramedTransport constructors now throw TTransportException
 * 
 * TRANSFORMATION STRATEGY:
 * 1. Update import statements for moved classes
 * 2. Find constructor calls that need exception handling
 * 3. Provide guidance for manual fixes (exception handling)
 * 
 * The transformation is STRUCTURAL and GENERIC - it uses patterns to match
 * the old API patterns, not project-specific identifiers.
 */
public class FinalThrift0160Transformation {
    
    // Map of old fully-qualified class names to new ones
    // Parameterized by dependency types, NOT client-specific identifiers
    private static final Map<String, String> CLASS_MIGRATIONS = new HashMap<>();
    
    // Classes whose constructors now throw TTransportException
    private static final Set<String> EXCEPTION_THROWING_CONSTRUCTORS = new HashSet<>();
    
    static {
        // Package relocation migrations
        CLASS_MIGRATIONS.put(
            "org.apache.thrift.transport.TFastFramedTransport",
            "org.apache.thrift.transport.layered.TFastFramedTransport"
        );
        CLASS_MIGRATIONS.put(
            "org.apache.thrift.transport.TFramedTransport",
            "org.apache.thrift.transport.layered.TFramedTransport"
        );
        CLASS_MIGRATIONS.put(
            "org.apache.thrift.transport.TFastFramedTransport$Factory",
            "org.apache.thrift.transport.layered.TFastFramedTransport$Factory"
        );
        CLASS_MIGRATIONS.put(
            "org.apache.thrift.transport.TFramedTransport$Factory",
            "org.apache.thrift.transport.layered.TFramedTransport$Factory"
        );
        
        // Classes with new checked exceptions in constructors
        EXCEPTION_THROWING_CONSTRUCTORS.add("org.apache.thrift.transport.layered.TFastFramedTransport");
        EXCEPTION_THROWING_CONSTRUCTORS.add("org.apache.thrift.transport.layered.TFramedTransport");
        EXCEPTION_THROWING_CONSTRUCTORS.add("org.apache.thrift.TDeserializer");
        EXCEPTION_THROWING_CONSTRUCTORS.add("org.apache.thrift.TSerializer");
        // Also include old names for pattern matching
        EXCEPTION_THROWING_CONSTRUCTORS.add("org.apache.thrift.transport.TFastFramedTransport");
        EXCEPTION_THROWING_CONSTRUCTORS.add("org.apache.thrift.transport.TFramedTransport");
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            printUsage();
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args.length > 1 ? args[1] : sourceDir + "-migrated";
        
        System.out.println("================================================");
        System.out.println("GENERIC Thrift 0.16.0 Breaking Change Fix");
        System.out.println("================================================");
        System.out.println("Source: " + sourceDir);
        System.out.println("Output: " + outputDir);
        System.out.println();
        
        try {
            migrateThrift0160(sourceDir, outputDir);
        } catch (Exception e) {
            System.err.println("Migration failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void printUsage() {
        System.err.println("Usage: java FinalThrift0160Transformation <source-directory> [output-directory]");
        System.err.println();
        System.err.println("Examples:");
        System.err.println("  # Transform in-place (backup recommended)");
        System.err.println("  java FinalThrift0160Transformation /path/to/src");
        System.err.println();
        System.err.println("  # Transform to new directory");
        System.err.println("  java FinalThrift0160Transformation /path/to/src /path/to/transformed");
        System.err.println();
        System.err.println("This transformation fixes Apache Thrift 0.16.0 breaking changes:");
        System.err.println("1. TFastFramedTransport/TFramedTransport moved to 'layered' package");
        System.err.println("2. TDeserializer/TSerializer constructors throw TTransportException");
        System.err.println("3. TFastFramedTransport/TFramedTransport constructors throw TTransportException");
    }
    
    private static void migrateThrift0160(String sourceDir, String outputDir) throws Exception {
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(true); // Don't require full classpath
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.addInputResource(sourceDir);
        
        System.out.println("Phase 1: Building code model...");
        CtModel model = launcher.buildModel();
        spoon.reflect.factory.Factory factory = launcher.getFactory();
        
        System.out.println("\nPhase 2: Applying transformations...");
        MigrationReport report = new MigrationReport();
        
        // Apply import migrations
        migrateImports(factory, report);
        
        // Find constructor calls that need exception handling
        findExceptionNeedingConstructors(factory, report);
        
        // Find type references that need updating
        migrateTypeReferences(factory, report);
        
        System.out.println("\nPhase 3: Generating output and report...");
        
        if (report.hasTransformations()) {
            launcher.setSourceOutputDirectory(outputDir);
            launcher.prettyprint();
            
            System.out.println("✓ Transformed code written to: " + outputDir);
        } else {
            System.out.println("✓ No import/type reference transformations needed.");
        }
        
        // Print comprehensive report
        report.printReport();
        
        System.out.println("\n================================================");
        System.out.println("MIGRATION COMPLETE");
        System.out.println("================================================");
        System.out.println("\nNEXT STEPS:");
        System.out.println("1. Review the constructor calls identified above");
        System.out.println("2. For each, either:");
        System.out.println("   a) Wrap in try-catch block for TTransportException");
        System.out.println("   b) Add 'throws TTransportException' to method signature");
        System.out.println("3. Run your build to verify all compilation errors are fixed");
        System.out.println("4. Run tests to ensure runtime behavior is correct");
    }
    
    private static void migrateImports(spoon.reflect.factory.Factory factory, MigrationReport report) {
        List<CtImport> imports = Query.getElements(factory, new TypeFilter<>(CtImport.class));
        
        for (CtImport imp : imports) {
            String importString = imp.getReference().toString();
            
            for (Map.Entry<String, String> migration : CLASS_MIGRATIONS.entrySet()) {
                if (importString.equals(migration.getKey())) {
                    CtTypeReference<?> newRef = factory.Type().createReference(migration.getValue());
                    imp.setReference(newRef);
                    report.addImportMigration(migration.getKey(), migration.getValue());
                    break;
                }
            }
        }
    }
    
    private static void migrateTypeReferences(spoon.reflect.factory.Factory factory, MigrationReport report) {
        List<CtTypeReference<?>> typeRefs = Query.getElements(factory, new TypeFilter<>(CtTypeReference.class));
        
        for (CtTypeReference<?> typeRef : typeRefs) {
            String typeName = typeRef.getQualifiedName();
            
            for (Map.Entry<String, String> migration : CLASS_MIGRATIONS.entrySet()) {
                if (typeName.equals(migration.getKey())) {
                    CtTypeReference<?> newRef = factory.Type().createReference(migration.getValue());
                    typeRef.replace(newRef);
                    report.addTypeReferenceMigration(migration.getKey(), migration.getValue());
                    break;
                }
            }
        }
    }
    
    private static void findExceptionNeedingConstructors(spoon.reflect.factory.Factory factory, MigrationReport report) {
        List<CtConstructorCall<?>> constructorCalls = Query.getElements(factory, new TypeFilter<>(CtConstructorCall.class));
        
        for (CtConstructorCall<?> call : constructorCalls) {
            CtTypeReference<?> type = call.getType();
            String typeName = type.getQualifiedName();
            
            // Check if this constructor now throws an exception
            for (String className : EXCEPTION_THROWING_CONSTRUCTORS) {
                if (typeName.equals(className) || typeName.startsWith(className + "$")) {
                    String location = getLocation(call);
                    report.addExceptionNeedingConstructor(className, location, call.toString());
                    break;
                }
            }
        }
    }
    
    private static String getLocation(CtElement element) {
        if (element.getPosition() != null && element.getPosition().isValidPosition()) {
            return element.getPosition().getFile().getName() + ":" + element.getPosition().getLine();
        }
        return "unknown";
    }
    
    /**
     * Helper class to track migration results and generate report.
     */
    private static class MigrationReport {
        private final List<String> importMigrations = new ArrayList<>();
        private final List<String> typeReferenceMigrations = new ArrayList<>();
        private final Map<String, List<String>> exceptionNeedingConstructors = new HashMap<>();
        
        void addImportMigration(String oldClass, String newClass) {
            importMigrations.add(String.format("  %s -> %s", oldClass, newClass));
        }
        
        void addTypeReferenceMigration(String oldClass, String newClass) {
            typeReferenceMigrations.add(String.format("  %s -> %s", oldClass, newClass));
        }
        
        void addExceptionNeedingConstructor(String className, String location, String code) {
            exceptionNeedingConstructors
                .computeIfAbsent(className, k -> new ArrayList<>())
                .add(String.format("  %s: %s", location, abbreviateCode(code)));
        }
        
        boolean hasTransformations() {
            return !importMigrations.isEmpty() || !typeReferenceMigrations.isEmpty();
        }
        
        void printReport() {
            System.out.println("\n================================================");
            System.out.println("MIGRATION REPORT");
            System.out.println("================================================");
            
            if (!importMigrations.isEmpty()) {
                System.out.println("\nIMPORT STATEMENTS UPDATED (" + importMigrations.size() + "):");
                importMigrations.forEach(System.out::println);
            }
            
            if (!typeReferenceMigrations.isEmpty()) {
                System.out.println("\nTYPE REFERENCES UPDATED (" + typeReferenceMigrations.size() + "):");
                typeReferenceMigrations.forEach(System.out::println);
            }
            
            if (!exceptionNeedingConstructors.isEmpty()) {
                System.out.println("\nCONSTRUCTOR CALLS NEEDING EXCEPTION HANDLING:");
                for (Map.Entry<String, List<String>> entry : exceptionNeedingConstructors.entrySet()) {
                    System.out.println("\n" + entry.getKey() + " (" + entry.getValue().size() + " calls):");
                    entry.getValue().forEach(System.out::println);
                }
                
                System.out.println("\nEXAMPLE FIX for TDeserializer constructor:");
                System.out.println("  // Old (no exception):");
                System.out.println("  TDeserializer deserializer = new TDeserializer();");
                System.out.println();
                System.out.println("  // New (with exception handling):");
                System.out.println("  TDeserializer deserializer;");
                System.out.println("  try {");
                System.out.println("    deserializer = new TDeserializer();");
                System.out.println("  } catch (TTransportException e) {");
                System.out.println("    throw new RuntimeException(\"Failed to create deserializer\", e);");
                System.out.println("  }");
            }
        }
        
        private String abbreviateCode(String code) {
            if (code.length() > 80) {
                return code.substring(0, 77) + "...";
            }
            return code;
        }
    }
}