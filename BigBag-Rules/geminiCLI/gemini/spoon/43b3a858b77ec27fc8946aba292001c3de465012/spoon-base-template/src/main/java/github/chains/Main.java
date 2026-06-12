package github.chains;

import spoon.Launcher;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) throws Exception {
        Launcher launcher = new Launcher();
        // Add input resources
        launcher.addInputResource("/workspace/pdb/src/test/java/com/feedzai/commons/sql/abstraction/engine/impl/abs/EngineCloseTest.java");
        launcher.addInputResource("/workspace/pdb/src/test/java/com/feedzai/commons/sql/abstraction/engine/impl/abs/EngineGeneralTest.java");
        launcher.addInputResource("/workspace/pdb/src/test/java/com/feedzai/commons/sql/abstraction/engine/impl/abs/BatchUpdateTest.java");
        
        // No classpath mode
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        // Build model
        launcher.buildModel();
        
        String[] classes = new String[] {
            "com.feedzai.commons.sql.abstraction.engine.impl.abs.EngineCloseTest",
            "com.feedzai.commons.sql.abstraction.engine.impl.abs.EngineGeneralTest",
            "com.feedzai.commons.sql.abstraction.engine.impl.abs.BatchUpdateTest"
        };
        
        for (String className : classes) {
            processClass(launcher, className);
        }

        // Set output directory to source root to overwrite
        launcher.setSourceOutputDirectory("/workspace/pdb/src/test/java");
        
        // Pretty print
        launcher.prettyprint();
        System.out.println("Transformation applied.");
        
        // Post-processing to remove bad imports that Spoon might have added back
        fixBadImports("/workspace/pdb/src/test/java/com/feedzai/commons/sql/abstraction/engine/impl/abs/EngineCloseTest.java");
        fixBadImports("/workspace/pdb/src/test/java/com/feedzai/commons/sql/abstraction/engine/impl/abs/EngineGeneralTest.java");
        fixBadImports("/workspace/pdb/src/test/java/com/feedzai/commons/sql/abstraction/engine/impl/abs/BatchUpdateTest.java");
    }
    
    private static void processClass(Launcher launcher, String className) {
        CtType<?> type = launcher.getFactory().Type().get(className);
        if (type != null) {
            System.out.println("Processing class: " + type.getQualifiedName());
            
            // 1. Clear initStatic body
            for (CtMethod<?> method : type.getMethodsByName("initStatic")) {
                 System.out.println("Found method: " + method.getSignature());
                 if (method.getBody() != null) {
                     System.out.println("Clearing body of initStatic");
                     method.getBody().getStatements().clear();
                 }
            }
            
            // 2. Fix imports (Attempt in model - best effort)
            CtCompilationUnit cu = type.getPosition().getCompilationUnit();
            if (cu != null) {
                Collection<CtImport> imports = cu.getImports();
                Iterator<CtImport> it = imports.iterator();
                while (it.hasNext()) {
                    CtImport imp = it.next();
                    String importStr = imp.toString();
                    if (importStr.contains("Parameterized.Parameters") || importStr.contains("Parameterized.Parameter")) {
                         System.out.println("Removing invalid import from model: " + importStr);
                         it.remove();
                    }
                }
            }
        }
    }
    
    private static void fixBadImports(String filePath) {
        try {
            List<String> lines = Files.readAllLines(Paths.get(filePath));
            List<String> newLines = new ArrayList<>();
            boolean modified = false;
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("import Parameterized.Parameters;") || 
                    trimmed.startsWith("import Parameterized.Parameter;")) {
                    System.out.println("Removing bad import line from " + filePath + ": " + trimmed);
                    modified = true;
                    continue;
                }
                newLines.add(line);
            }
            if (modified) {
                Files.write(Paths.get(filePath), newLines, StandardOpenOption.TRUNCATE_EXISTING);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}