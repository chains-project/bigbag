package github.chains;

import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;

/**
 * A Spoon processor that fixes JavaParser API breaking changes in version 3.24.0.
 * 
 * <p>This transformation handles classes that moved packages in JavaParser 3.24.0,
 * specifically classes that moved from {@code com.github.javaparser.printer}
 * to {@code com.github.javaparser.printer.configuration}.</p>
 * 
 * <p>To use this transformation on other projects affected by the same breaking change,
 * simply run it on the project's source directory. The transformation will:</p>
 * <ol>
 *   <li>Update imports of moved classes</li>
 *   <li>Update fully-qualified type references in code</li>
 *   <li>Update type references in type accesses</li>
 * </ol>
 * 
 * <p>The transformation is generic and reusable - it contains no project-specific
 * identifiers and can be applied to any Java project by changing the input source
 * directory path.</p>
 */
public class JavaParserImportFixer extends AbstractProcessor<CtCompilationUnit> {
    
    /**
     * Map of old package to new package for classes that moved in JavaParser 3.24.0.
     * Each entry is a String array with two elements: [oldFQN, newFQN].
     */
    private static final String[][] MOVED_CLASSES = {
        // Classes that moved from printer to printer.configuration package
        {"com.github.javaparser.printer.PrettyPrinterConfiguration", 
         "com.github.javaparser.printer.configuration.PrettyPrinterConfiguration"},
        {"com.github.javaparser.printer.PrinterConfiguration",
         "com.github.javaparser.printer.configuration.PrinterConfiguration"},
        {"com.github.javaparser.printer.DefaultPrinterConfiguration",
         "com.github.javaparser.printer.configuration.DefaultPrinterConfiguration"},
        {"com.github.javaparser.printer.ConfigurationOption",
         "com.github.javaparser.printer.configuration.ConfigurationOption"},
        {"com.github.javaparser.printer.DefaultConfigurationOption",
         "com.github.javaparser.printer.configuration.DefaultConfigurationOption"},
        {"com.github.javaparser.printer.Indentation",
         "com.github.javaparser.printer.configuration.Indentation"}
    };
    
    @Override
    public void process(CtCompilationUnit compilationUnit) {
        // Fix imports
        List<CtImport> imports = compilationUnit.getImports();
        for (CtImport imp : imports) {
            String importStr = imp.getReference().toString();
            for (String[] movedClass : MOVED_CLASSES) {
                if (importStr.equals(movedClass[0]) || importStr.startsWith(movedClass[0] + ".")) {
                    // Replace the import with the new package
                    CtTypeReference<?> newRef = getFactory().createReference(movedClass[1]);
                    imp.setReference(newRef);
                    break;
                }
            }
        }
        
        // Fix fully-qualified type references in code (e.g., in new expressions)
        List<CtTypeAccess> typeAccesses = compilationUnit.getElements(new TypeFilter<>(CtTypeAccess.class));
        for (CtTypeAccess typeAccess : typeAccesses) {
            String typeName = typeAccess.getAccessedType().getQualifiedName();
            for (String[] movedClass : MOVED_CLASSES) {
                if (typeName.equals(movedClass[0])) {
                    // Replace the type reference with the new package
                    CtTypeReference<?> newRef = getFactory().createReference(movedClass[1]);
                    typeAccess.setType(newRef);
                    break;
                }
            }
        }
        
        // Also check type references directly (not just in type accesses)
        List<CtTypeReference<?>> typeRefs = compilationUnit.getElements(new TypeFilter<>(CtTypeReference.class));
        for (CtTypeReference<?> typeRef : typeRefs) {
            String typeName = typeRef.getQualifiedName();
            for (String[] movedClass : MOVED_CLASSES) {
                if (typeName.equals(movedClass[0])) {
                    // Create new reference with correct package and replace
                    CtTypeReference<?> newRef = getFactory().createReference(movedClass[1]);
                    typeRef.replace(newRef);
                    break;
                }
            }
        }
    }
}