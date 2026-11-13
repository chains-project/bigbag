package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

public class Main {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }

        String sourceDirectory = args[0];
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(sourceDirectory + "-processed");
        launcher.buildModel();

        Factory factory = launcher.getFactory();
        
        // Transform imports - change net.lingala.zip4j.core.ZipFile to net.lingala.zip4j.ZipFile
        List<CtImport> imports = factory.getModel().getElements(new TypeFilter<>(CtImport.class));
        boolean importChanged = false;
        for (CtImport imp : imports) {
            if (imp.getReference() != null) {
                String importStr = imp.getReference().toString();
                if (importStr.contains("net.lingala.zip4j.core.ZipFile")) {
                    // Replace with proper import
                    CtTypeReference<?> newImportRef = factory.Type().createReference("net.lingala.zip4j.ZipFile");
                    imp.setReference(newImportRef);
                    System.out.println("Fixed import: " + importStr + " -> net.lingala.zip4j.ZipFile");
                    importChanged = true;
                }
            }
        }

        // Transform instantiations - change net.lingala.zip4j.core.ZipFile to net.lingala.zip4j.ZipFile
        List<CtConstructorCall> constructors = factory.getModel().getElements(new TypeFilter<>(CtConstructorCall.class));
        boolean constructorChanged = false;
        for (CtConstructorCall constructor : constructors) {
            if (constructor.getType() != null) {
                String typeStr = constructor.getType().toString();
                if (typeStr.contains("net.lingala.zip4j.core.ZipFile")) {
                    // Replace with the new type reference
                    CtTypeReference<?> newTypeRef = factory.Type().createReference("net.lingala.zip4j.ZipFile");
                    constructor.setType(newTypeRef);
                    System.out.println("Fixed constructor: " + typeStr + " -> net.lingala.zip4j.ZipFile");
                    constructorChanged = true;
                }
            }
        }
        
        // Save the transformed model
        launcher.process();
        
        if (importChanged || constructorChanged) {
            System.out.println("Transformation completed successfully!");
        } else {
            System.out.println("No changes needed - no zip4j.core references found.");
        }
    }
}