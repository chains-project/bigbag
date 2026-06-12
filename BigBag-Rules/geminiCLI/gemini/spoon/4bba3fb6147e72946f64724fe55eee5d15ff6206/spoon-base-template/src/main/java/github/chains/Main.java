package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.experimental.CtUnresolvedImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) throws Exception {
        String projectRoot = "/workspace/cdi-test";
        
        List<Path> srcDirs;
        try (Stream<Path> walk = Files.walk(Paths.get(projectRoot))) {
            srcDirs = walk.filter(Files::isDirectory)
                          .filter(p -> p.endsWith("java") && p.toString().contains("src/"))
                          .collect(Collectors.toList());
        }

        for (Path srcDir : srcDirs) {
            List<File> filesToProcess;
            try (Stream<Path> walk = Files.walk(srcDir)) {
                filesToProcess = walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        try {
                            return new String(Files.readAllBytes(p)).contains("javax.annotation.");
                        } catch (Exception e) { return false; }
                    })
                    .map(Path::toFile)
                    .collect(Collectors.toList());
            }
            
            if (filesToProcess.isEmpty()) continue;

            for (File file : filesToProcess) {
                System.out.println("Processing " + file);
                Launcher launcher = new Launcher();
                launcher.addInputResource(file.getAbsolutePath());
                launcher.getEnvironment().setNoClasspath(true);
                launcher.getEnvironment().setAutoImports(false);
                
                try {
                    launcher.buildModel();
                } catch (Exception e) {
                    continue;
                }
                
                CtModel model = launcher.getModel();
                boolean anyChange = false;

                for (CtTypeReference ref : model.getElements(new TypeFilter<>(CtTypeReference.class))) {
                    if (ref.getQualifiedName().startsWith("javax.annotation.") && !ref.getQualifiedName().startsWith("javax.annotation.processing")) {
                        ref.getPackage().setSimpleName("jakarta.annotation");
                        anyChange = true;
                    }
                }

                for (CtCompilationUnit cu : launcher.getFactory().CompilationUnit().getMap().values()) {
                    List<CtImport> imports = new ArrayList<>(cu.getImports());
                    boolean changed = false;
                    for (int i=0; i<imports.size(); i++) {
                        CtImport imp = imports.get(i);
                        if (imp instanceof CtUnresolvedImport) {
                            CtUnresolvedImport unres = (CtUnresolvedImport) imp;
                            String refStr = unres.getUnresolvedReference();
                            if (refStr.startsWith("javax.annotation.") && !refStr.startsWith("javax.annotation.processing")) {
                                String newRefStr = refStr.replace("javax.annotation.", "jakarta.annotation.");
                                CtImport newImp = launcher.getFactory().createUnresolvedImport(newRefStr, unres.isStatic());
                                imports.set(i, newImp);
                                changed = true;
                                anyChange = true;
                            }
                        } else if (imp.getReference() != null && imp.getReference() instanceof CtTypeReference) {
                            CtTypeReference tRef = (CtTypeReference) imp.getReference();
                            if (tRef.getQualifiedName().startsWith("javax.annotation.") && !tRef.getQualifiedName().startsWith("javax.annotation.processing")) {
                                CtTypeReference newRef = launcher.getFactory().Type().createReference(tRef.getQualifiedName().replace("javax.annotation.", "jakarta.annotation."));
                                CtImport newImp = launcher.getFactory().createImport(newRef);
                                imports.set(i, newImp);
                                changed = true;
                                anyChange = true;
                            }
                        }
                    }
                    if (changed) {
                        cu.setImports(imports);
                    }
                }
                
                if (anyChange) {
                    System.out.println("Applying changes to " + file);
                    launcher.setSourceOutputDirectory(srcDir.toString());
                    launcher.prettyprint();
                }
            }
        }
    }
}
