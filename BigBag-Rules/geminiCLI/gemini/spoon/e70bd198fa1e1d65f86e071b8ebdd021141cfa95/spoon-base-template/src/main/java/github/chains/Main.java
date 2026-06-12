package github.chains;

import spoon.Launcher;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.code.*;
import spoon.reflect.visitor.filter.TypeFilter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) throws Exception {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/PGS/src/main/java/micycle/pgs/PGS_Meshing.java");
        launcher.addInputResource("/workspace/PGS/src/main/java/micycle/pgs/PGS_CirclePacking.java");
        launcher.addInputResource("/workspace/PGS/src/main/java/micycle/pgs/PGS_PointSet.java");
        
        String cp = new String(Files.readAllBytes(Paths.get("/workspace/PGS/cp.txt"))).trim();
        launcher.getEnvironment().setSourceClasspath(cp.split(":"));
        launcher.getEnvironment().setAutoImports(true);
        launcher.setSourceOutputDirectory("/workspace/PGS/src/main/java");
        launcher.buildModel();

        for (CtTypeReference<?> ref : launcher.getFactory().getModel().getElements(new TypeFilter<>(CtTypeReference.class))) {
            if (ref.getSimpleName().equals("PointIndex")) {
                ref.setSimpleName("PointMap");
            } else if (ref.getSimpleName().equals("PointDistanceFunction")) {
                ref.setSimpleName("PointDistance");
            } else if (ref.getSimpleName().equals("PointEntryDist")) {
                ref.setSimpleName("PointEntryKnn");
                CtTypeReference<?> indexRef = launcher.getFactory().Core().createTypeReference();
                indexRef.setSimpleName("Index");
                indexRef.setPackage(launcher.getFactory().Core().createPackageReference().setSimpleName("org.tinspin.index"));
                ref.setDeclaringType(indexRef);
            } else if (ref.getSimpleName().equals("CPVector")) {
                ref.setDeclaringType(null);
                ref.setPackage(null);
            }
        }

        for (CtInvocation<?> inv : launcher.getFactory().getModel().getElements(new TypeFilter<>(CtInvocation.class))) {
            if (inv.getExecutable().getSimpleName().equals("query1NN")) {
                inv.getExecutable().setSimpleName("query1nn");
            }
            if (inv.getExecutable().getSimpleName().equals("create") && 
                inv.getExecutable().getDeclaringType() != null && 
                inv.getExecutable().getDeclaringType().getSimpleName().equals("KDTree")) {
                if (inv.getArguments().size() == 2) {
                    inv.removeArgument(inv.getArguments().get(1));
                }
            }
        }

        for (spoon.reflect.cu.CompilationUnit cu : launcher.getFactory().CompilationUnit().getMap().values()) {
            List<CtImport> imports = new ArrayList<>(cu.getImports());
            imports.removeIf(i -> {
                String s = i.toString();
                return s.contains("PointIndex") || s.contains("PointDistanceFunction") || s.contains("PointEntryDist") || s.contains("org.tinspin.index.covertree");
            });
            cu.setImports(imports);
        }

        launcher.prettyprint();
    }
}
