package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtTargetedExpression;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtImportKind;
import spoon.reflect.factory.CodeFactory;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

public class Main {
    
    private static final Map<String, Integer> TV_CONSTANTS = new HashMap<>();
    static {
        // Mapping from Tv constant names to numeric values (as per jcabi-aspects 0.24.1)
        TV_CONSTANTS.put("THREE", 3);
        TV_CONSTANTS.put("FOUR", 4);
        TV_CONSTANTS.put("FIVE", 5);
        TV_CONSTANTS.put("SIX", 6);
        TV_CONSTANTS.put("SEVEN", 7);
        TV_CONSTANTS.put("EIGHT", 8);
        TV_CONSTANTS.put("NINE", 9);
        TV_CONSTANTS.put("TEN", 10);
        TV_CONSTANTS.put("FIFTEEN", 15);
        TV_CONSTANTS.put("TWENTY", 20);
        TV_CONSTANTS.put("THIRTY", 30);
        TV_CONSTANTS.put("FORTY", 40);
        TV_CONSTANTS.put("FIFTY", 50);
        TV_CONSTANTS.put("SIXTY", 60);
        TV_CONSTANTS.put("SEVENTY", 70);
        TV_CONSTANTS.put("EIGHTY", 80);
        TV_CONSTANTS.put("NINETY", 90);
        TV_CONSTANTS.put("HUNDRED", 100);
        TV_CONSTANTS.put("THOUSAND", 1000);
        TV_CONSTANTS.put("MILLION", 1000000);
        TV_CONSTANTS.put("BILLION", 1000000000);
    }
    
    private static final String TARGET_CLASS = "com.jcabi.aspects.Tv";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory> [output-directory]");
            System.err.println("  source-directory: root of source code to transform");
            System.err.println("  output-directory: optional, defaults to source-directory (in-place)");
            return;
        }
        
        String sourcePath = args[0];
        String outputPath = args.length >= 2 ? args[1] : sourcePath;
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourcePath);
        // The broken dependency class is removed from the new version, so it can
        // never be on the classpath: the model must be built in no-classpath mode.
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setCopyResources(false);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        CtModel model = launcher.buildModel();
        
        // Step 1: Replace Tv.CONSTANT field accesses with integer literals
        transformFieldAccesses(model);
        
        // Step 2: Remove import statements for com.jcabi.aspects.Tv
        removeTvImports(model);
        
        // Write transformed model back
        launcher.setSourceOutputDirectory(outputPath);
        try {
            launcher.prettyprint();
        } catch (Exception e) {
            System.err.println("Error during pretty-printing: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("Transformation completed. Source directory: " + sourcePath);
        if (!sourcePath.equals(outputPath)) {
            System.out.println("Output written to: " + outputPath);
        }
    }
    
    private static void transformFieldAccesses(CtModel model) {
        Factory factory = model.getRootPackage().getFactory();
        CodeFactory codeFactory = factory.Code();
        
        // Find all field reads that reference com.jcabi.aspects.Tv constants
        List<CtFieldRead<?>> tvFieldReads = model.getElements(new TypeFilter<CtFieldRead<?>>(CtFieldRead.class))
            .stream()
            .filter(Main::referencesRemovedType)
            .collect(Collectors.toList());
        
        System.out.println("Found " + tvFieldReads.size() + " Tv.CONSTANT field reads");
        
        for (CtFieldRead<?> fieldRead : tvFieldReads) {
            String fieldName = fieldRead.getVariable().getSimpleName();
            Integer literalValue = TV_CONSTANTS.get(fieldName);
            
            if (literalValue != null) {
                // Create integer literal
                CtLiteral<Integer> literal = codeFactory.createLiteral(literalValue);
                literal.setType(factory.Type().INTEGER_PRIMITIVE);
                
                // Replace field read with literal
                fieldRead.replace(literal);
                System.out.println("  Replaced " + TARGET_CLASS + "." + fieldName + " with " + literalValue);
            } else {
                System.err.println("  Warning: Unknown Tv constant '" + fieldName + "' - skipping");
            }
        }
    }
    
    private static void removeTvImports(CtModel model) {
        // Get all compilation units
        List<CtCompilationUnit> compilationUnits = model.getElements(new TypeFilter<CtCompilationUnit>(CtCompilationUnit.class));
        
        for (CtCompilationUnit cu : compilationUnits) {
            // Get imports and find those importing Tv
            List<CtImport> imports = cu.getImports();
            List<CtImport> toRemove = imports.stream()
                .filter(imp -> {
                    if (imp.getImportKind() != CtImportKind.TYPE) {
                        return false;
                    }
                    String importStr = imp.getReference().toString();
                    return importStr.equals(TARGET_CLASS);
                })
                .collect(Collectors.toList());
            
            if (!toRemove.isEmpty()) {
                for (CtImport imp : toRemove) {
                    imp.delete();
                    System.out.println("  Removed import: " + imp.getReference());
                }
            }
        }
    }
}