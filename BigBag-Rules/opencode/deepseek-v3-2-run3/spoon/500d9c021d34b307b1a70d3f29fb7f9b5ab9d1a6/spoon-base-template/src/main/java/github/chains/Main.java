package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.visitor.CtScanner;
import spoon.reflect.factory.Factory;

import java.util.List;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory> <output-directory>");
            System.err.println("Example: java -jar spoon-transformation.jar /path/to/project/src /path/to/transformed/src");
            System.exit(1);
        }

        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("Transforming MVC API from javax.mvc to jakarta.mvc");
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);

        try {
            Launcher launcher = new Launcher();
            launcher.getEnvironment().setNoClasspath(true);
            launcher.getEnvironment().setAutoImports(true);
            launcher.getEnvironment().setCommentEnabled(true);
            
            launcher.addInputResource(sourceDir);
            launcher.setSourceOutputDirectory(outputDir);
            
            CtModel model = launcher.buildModel();
            Factory factory = launcher.getFactory();
            
            // Process all types in the model
            for (CtType<?> type : model.getAllTypes()) {
                // Use a visitor to transform all references
                type.accept(new CtScanner() {
                    @Override
                    public <T> void visitCtTypeReference(CtTypeReference<T> typeRef) {
                        super.visitCtTypeReference(typeRef);
                        
                        // Check and replace javax.mvc type references
                        String qualifiedName = typeRef.getQualifiedName();
                        if (qualifiedName != null && qualifiedName.startsWith("javax.mvc.")) {
                            String newName = qualifiedName.replace("javax.mvc.", "jakarta.mvc.");
                            CtTypeReference<?> newTypeRef = factory.Type().createReference(newName);
                            typeRef.replace(newTypeRef);
                            System.out.println("Replaced type reference: " + qualifiedName + " -> " + newName);
                        }
                    }
                    
                    @Override
                    public void visitCtPackageReference(CtPackageReference packageRef) {
                        super.visitCtPackageReference(packageRef);
                        
                        // Check and replace javax.mvc package references
                        String qualifiedName = packageRef.getQualifiedName();
                        if (qualifiedName != null && qualifiedName.startsWith("javax.mvc")) {
                            String newName = qualifiedName.replace("javax.mvc", "jakarta.mvc");
                            CtPackageReference newPackageRef = factory.Package().createReference(newName);
                            packageRef.replace(newPackageRef);
                            System.out.println("Replaced package reference: " + qualifiedName + " -> " + newName);
                        }
                    }
                    
                    @Override
                    public void visitCtImport(CtImport ctImport) {
                        // Don't call super.visitCtImport() to avoid double processing
                        // Just handle the import replacement here
                        
                        CtReference importedRef = ctImport.getReference();
                        if (importedRef instanceof CtTypeReference) {
                            CtTypeReference<?> typeRef = (CtTypeReference<?>) importedRef;
                            String qualifiedName = typeRef.getQualifiedName();
                            if (qualifiedName != null && qualifiedName.startsWith("javax.mvc.")) {
                                String newName = qualifiedName.replace("javax.mvc.", "jakarta.mvc.");
                                CtTypeReference<?> newTypeRef = factory.Type().createReference(newName);
                                CtImport newImport = factory.Core().createImport();
                                newImport.setReference(newTypeRef);
                                ctImport.replace(newImport);
                                System.out.println("Replaced import: " + qualifiedName + " -> " + newName);
                            }
                        } else if (importedRef instanceof CtPackageReference) {
                            CtPackageReference packageRef = (CtPackageReference) importedRef;
                            String qualifiedName = packageRef.getQualifiedName();
                            if (qualifiedName != null && qualifiedName.startsWith("javax.mvc")) {
                                String newName = qualifiedName.replace("javax.mvc", "jakarta.mvc");
                                CtPackageReference newPackageRef = factory.Package().createReference(newName);
                                CtImport newImport = factory.Core().createImport();
                                newImport.setReference(newPackageRef);
                                ctImport.replace(newImport);
                                System.out.println("Replaced package import: " + qualifiedName + " -> " + newName);
                            }
                        }
                        
                        // Don't visit children
                    }
                });
            }
            
            // Pretty print the transformed code
            launcher.prettyprint();
            
            System.out.println("Transformation completed successfully!");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}