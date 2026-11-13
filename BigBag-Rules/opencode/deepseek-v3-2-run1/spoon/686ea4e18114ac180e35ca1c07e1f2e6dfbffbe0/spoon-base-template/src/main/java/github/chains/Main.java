package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.*;

public class Main {
    
    // Map of old package patterns to new package patterns
    private static final Map<String, String> PACKAGE_MIGRATIONS = new HashMap<>();
    
    static {
        // Core package migrations from io.dropwizard.* to io.dropwizard.core.*
        PACKAGE_MIGRATIONS.put("io.dropwizard.Application", "io.dropwizard.core.Application");
        PACKAGE_MIGRATIONS.put("io.dropwizard.Configuration", "io.dropwizard.core.Configuration");
        PACKAGE_MIGRATIONS.put("io.dropwizard.ConfiguredBundle", "io.dropwizard.core.ConfiguredBundle");
        
        // Setup package migrations
        PACKAGE_MIGRATIONS.put("io.dropwizard.setup.Bootstrap", "io.dropwizard.core.setup.Bootstrap");
        PACKAGE_MIGRATIONS.put("io.dropwizard.setup.Environment", "io.dropwizard.core.setup.Environment");
        PACKAGE_MIGRATIONS.put("io.dropwizard.setup.AdminFactory", "io.dropwizard.core.setup.AdminFactory");
        PACKAGE_MIGRATIONS.put("io.dropwizard.setup.AdminEnvironment", "io.dropwizard.core.setup.AdminEnvironment");
        
        // Logging package migrations to common subpackage
        PACKAGE_MIGRATIONS.put("io.dropwizard.logging.AbstractAppenderFactory", "io.dropwizard.logging.common.AbstractAppenderFactory");
        PACKAGE_MIGRATIONS.put("io.dropwizard.logging.LoggingFactory", "io.dropwizard.logging.common.LoggingFactory");
        PACKAGE_MIGRATIONS.put("io.dropwizard.logging.filter.FilterFactory", "io.dropwizard.logging.common.filter.FilterFactory");
        PACKAGE_MIGRATIONS.put("io.dropwizard.logging.filter.LevelFilterFactory", "io.dropwizard.logging.common.filter.LevelFilterFactory");
        PACKAGE_MIGRATIONS.put("io.dropwizard.logging.layout.LayoutFactory", "io.dropwizard.logging.common.layout.LayoutFactory");
        PACKAGE_MIGRATIONS.put("io.dropwizard.logging.async.AsyncAppenderFactory", "io.dropwizard.logging.common.async.AsyncAppenderFactory");
        
        // CLI package migrations
        PACKAGE_MIGRATIONS.put("io.dropwizard.cli.Command", "io.dropwizard.core.cli.Command");
        PACKAGE_MIGRATIONS.put("io.dropwizard.cli.ConfiguredCommand", "io.dropwizard.core.cli.ConfiguredCommand");
        PACKAGE_MIGRATIONS.put("io.dropwizard.cli.CheckCommand", "io.dropwizard.core.cli.CheckCommand");
        PACKAGE_MIGRATIONS.put("io.dropwizard.cli.Cli", "io.dropwizard.core.cli.Cli");
        PACKAGE_MIGRATIONS.put("io.dropwizard.cli.ServerCommand", "io.dropwizard.core.cli.ServerCommand");
        PACKAGE_MIGRATIONS.put("io.dropwizard.cli.EnvironmentCommand", "io.dropwizard.core.cli.EnvironmentCommand");
        
        // Server package migrations
        PACKAGE_MIGRATIONS.put("io.dropwizard.server.ServerFactory", "io.dropwizard.core.server.ServerFactory");
        PACKAGE_MIGRATIONS.put("io.dropwizard.server.AbstractServerFactory", "io.dropwizard.core.server.AbstractServerFactory");
        PACKAGE_MIGRATIONS.put("io.dropwizard.server.DefaultServerFactory", "io.dropwizard.core.server.DefaultServerFactory");
        PACKAGE_MIGRATIONS.put("io.dropwizard.server.SimpleServerFactory", "io.dropwizard.core.server.SimpleServerFactory");
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.err.println("Example: java -jar spoon-transformation.jar /path/to/project/src/main/java");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying Dropwizard 4.0.0 migration transformation to: " + sourceDir);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        int transformations = 0;
        
        // Process all type references in the code
        transformations += migrateTypeReferences(model);
        
        // Process imports in compilation units
        transformations += migrateImports(model);
        
        System.out.println("Applied " + transformations + " transformations");
        
        // Write the transformed code back
        launcher.setSourceOutputDirectory(sourceDir);
        launcher.prettyprint();
        
        System.out.println("Transformation complete!");
    }
    
    private static int migrateTypeReferences(CtModel model) {
        int count = 0;
        
        // Find all type references in the code
        for (CtTypeReference<?> typeRef : model.getElements(new TypeFilter<CtTypeReference<?>>(CtTypeReference.class))) {
            String qualifiedName = typeRef.getQualifiedName();
            if (qualifiedName == null) continue;
            
            // Check if this type needs migration
            for (Map.Entry<String, String> migration : PACKAGE_MIGRATIONS.entrySet()) {
                String oldType = migration.getKey();
                String newType = migration.getValue();
                
                if (qualifiedName.equals(oldType)) {
                    // Exact match - replace the type reference
                    try {
                        CtTypeReference<?> newTypeRef = typeRef.getFactory().createReference(newType);
                        typeRef.replace(newTypeRef);
                        System.out.println("Migrated type reference: " + oldType + " -> " + newType);
                        count++;
                    } catch (Exception e) {
                        System.err.println("Failed to migrate type reference " + oldType + ": " + e.getMessage());
                    }
                    break;
                } else if (qualifiedName.startsWith(oldType + ".") || qualifiedName.startsWith(oldType + "$")) {
                    // Handle inner classes or subpackages
                    String suffix = qualifiedName.substring(oldType.length());
                    String newQualifiedName = newType + suffix;
                    try {
                        CtTypeReference<?> newTypeRef = typeRef.getFactory().createReference(newQualifiedName);
                        typeRef.replace(newTypeRef);
                        System.out.println("Migrated type reference: " + qualifiedName + " -> " + newQualifiedName);
                        count++;
                    } catch (Exception e) {
                        System.err.println("Failed to migrate type reference " + qualifiedName + ": " + e.getMessage());
                    }
                    break;
                }
            }
        }
        
        return count;
    }
    
    private static int migrateImports(CtModel model) {
        int count = 0;
        
        // Get all compilation units
        for (CtCompilationUnit cu : model.getElements(new TypeFilter<CtCompilationUnit>(CtCompilationUnit.class))) {
            // Get imports as a list
            List<CtImport> imports = new ArrayList<>(cu.getImports());
            boolean modified = false;
            
            // Check each import
            for (int i = 0; i < imports.size(); i++) {
                CtImport ctImport = imports.get(i);
                String importStr = ctImport.toString();
                
                // Check against migration patterns
                for (Map.Entry<String, String> migration : PACKAGE_MIGRATIONS.entrySet()) {
                    String oldType = migration.getKey();
                    String newType = migration.getValue();
                    
                    if (importStr.contains(oldType)) {
                        // Create new import
                        String newImportStr = importStr.replace(oldType, newType);
                        
                        // Handle wildcard imports
                        if (importStr.endsWith(".*;")) {
                            // Check if it's a wildcard import of the exact package
                            String oldPackage = oldType.substring(0, oldType.lastIndexOf('.'));
                            String newPackage = newType.substring(0, newType.lastIndexOf('.'));
                            
                            if (importStr.equals("import " + oldPackage + ".*;")) {
                                // Replace the import in the list
                                imports.set(i, cu.getFactory().createImport(cu.getFactory().createReference(newPackage + ".*")));
                                System.out.println("Migrated wildcard import: " + oldPackage + ".* -> " + newPackage + ".*");
                                count++;
                                modified = true;
                                break;
                            }
                        } else if (importStr.startsWith("import " + oldType + ";")) {
                            // Exact type import
                            imports.set(i, cu.getFactory().createImport(cu.getFactory().createReference(newType)));
                            System.out.println("Migrated import: " + oldType + " -> " + newType);
                            count++;
                            modified = true;
                            break;
                        }
                    }
                }
            }
            
            // If imports were modified, update the compilation unit
            if (modified) {
                // Clear existing imports and add modified ones
                cu.getImports().clear();
                for (CtImport imp : imports) {
                    cu.getImports().add(imp);
                }
            }
        }
        
        return count;
    }
}