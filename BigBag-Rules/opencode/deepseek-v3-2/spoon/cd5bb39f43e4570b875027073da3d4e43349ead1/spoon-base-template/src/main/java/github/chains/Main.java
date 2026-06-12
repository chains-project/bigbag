package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import java.util.List;
import java.util.ArrayList;
import java.io.File;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming source directory: " + sourceDir);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Counter for changes
        final int[] importChanges = {0};
        final int[] typeRefChanges = {0};
        final int[] commentsAdded = {0};
        
        // Process all types in the model
        for (CtType<?> type : model.getAllTypes()) {
            // Process imports - need to get them differently in newer Spoon
            List<CtImport> importsToRemove = new ArrayList<>();
            List<CtImport> importsToAdd = new ArrayList<>();
            
            // Get imports from the compilation unit
            if (type.getPosition() != null && type.getPosition().getCompilationUnit() != null) {
                List<CtImport> imports = type.getPosition().getCompilationUnit().getImports();
                
                for (CtImport imp : imports) {
                    String importStr = imp.toString();
                    if (importStr.contains("org.codehaus.plexus.util.xml.Xpp3Dom")) {
                        System.out.println("Found old import in " + type.getQualifiedName() + ": " + importStr);
                        
                        // Xpp3Dom was removed from plexus-utils 4.0.0
                        // It might be available in plexus-xml artifact with same package
                        // Or in maven-core transitive dependencies
                        // This is a breaking change that requires dependency adjustment
                        
                        // We could add a comment but not change the import
                        // since the package might be the same in plexus-xml
                        importChanges[0]++;
                    }
                }
            }
            
            // Also need to update type references in the code
            // This handles cases where Xpp3Dom is used in code but not imported
            // (e.g., fully qualified names)
            type.accept(new spoon.reflect.visitor.CtScanner() {
                @Override
                public <T> void visitCtTypeReference(spoon.reflect.reference.CtTypeReference<T> reference) {
                    super.visitCtTypeReference(reference);
                    
                    String qualifiedName = reference.getQualifiedName();
                    if (qualifiedName != null && qualifiedName.equals("org.codehaus.plexus.util.xml.Xpp3Dom")) {
                        System.out.println("Found type reference to old Xpp3Dom in " + type.getQualifiedName());
                        
                        // Xpp3Dom was removed from plexus-utils 4.0.0
                        // Cannot automatically replace - requires manual fix
                        // Might need to add plexus-xml dependency or update Maven version
                        typeRefChanges[0]++;
                    }
                }
            });
            
            // Add warning comment to the type if it uses Xpp3Dom
            if (importChanges[0] > 0 || typeRefChanges[0] > 0) {
                // Add a comment at the beginning of the type
                String warning = "/* WARNING: Xpp3Dom was removed from plexus-utils 4.0.0.\n" +
                                " * This class might need:\n" +
                                " * 1. plexus-xml dependency added to pom.xml\n" +
                                " * 2. Or Maven version upgrade to handle plexus-utils 4.0.0\n" +
                                " * 3. Or alternative XML API usage\n" +
                                " */\n";
                // Note: Adding comments directly is complex in Spoon
                // For now, we just report it
                commentsAdded[0]++;
            }
        }
        
        System.out.println("Analysis complete:");
        System.out.println("  Files using Xpp3Dom: " + importChanges[0]);
        System.out.println("  Type references to Xpp3Dom: " + typeRefChanges[0]);
        System.out.println("  Comments needed: " + commentsAdded[0]);
        System.out.println("\nACTION REQUIRED:");
        System.out.println("Xpp3Dom was removed from plexus-utils 4.0.0.");
        System.out.println("Possible solutions:");
        System.out.println("1. Add plexus-xml dependency to pom.xml:");
        System.out.println("   <dependency>");
        System.out.println("     <groupId>org.codehaus.plexus</groupId>");
        System.out.println("     <artifactId>plexus-xml</artifactId>");
        System.out.println("     <version>1.0.0</version> <!-- Check latest version -->");
        System.out.println("   </dependency>");
        System.out.println("2. Or upgrade Maven to version that handles plexus-utils 4.0.0");
        System.out.println("3. Or migrate to different XML API");
        
        // Don't actually rewrite since we can't fix automatically
        System.out.println("\nCode was NOT rewritten - manual intervention required.");
    }
}