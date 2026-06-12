package github.chains;

import spoon.Launcher;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.visitor.filter.*;
import spoon.reflect.code.*;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/PGS/src/main/java/micycle/pgs/PGS_Meshing.java");
        launcher.addInputResource("/workspace/PGS/src/main/java/micycle/pgs/PGS_CirclePacking.java");
        launcher.addInputResource("/workspace/PGS/src/main/java/micycle/pgs/PGS_PointSet.java");
        launcher.setSourceOutputDirectory("/workspace/PGS/src/main/java");
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.buildModel();

        // 1. Fix PGS_Meshing.java
        CtType<?> meshingClass = launcher.getFactory().Type().get("micycle.pgs.PGS_Meshing");
        if (meshingClass != null) {
            List<CtLocalVariable> localVars = meshingClass.getElements(new TypeFilter<>(CtLocalVariable.class));
            for (CtLocalVariable var : localVars) {
                if ("tree".equals(var.getSimpleName()) && var.getType().getSimpleName().equals("PointIndex")) {
                    CtTypeReference kdtreeRef = launcher.getFactory().Type().createReference("org.tinspin.index.kdtree.KDTree");
                    kdtreeRef.addActualTypeArgument(launcher.getFactory().Type().createReference("org.tinfour.common.Vertex"));
                    var.setType(kdtreeRef);

                    CtExpression init = var.getDefaultExpression();
                    if (init instanceof CtInvocation) {
                        CtInvocation inv = (CtInvocation) init;
                        if (inv.getExecutable().getSimpleName().equals("create") && inv.getArguments().size() == 2) {
                            inv.removeArgument((CtExpression) inv.getArguments().get(1));
                        }
                    }
                }
            }

            List<CtInvocation> invocations = meshingClass.getElements(new TypeFilter<>(CtInvocation.class));
            for (CtInvocation inv : invocations) {
                if ("query1NN".equals(inv.getExecutable().getSimpleName())) {
                    inv.getExecutable().setSimpleName("query1nn");
                }
            }
        }

        // 2. Fix PGS_CirclePacking.java
        CtType<?> circlePackingClass = launcher.getFactory().Type().get("micycle.pgs.PGS_CirclePacking");
        if (circlePackingClass != null) {
            List<CtField> fields = circlePackingClass.getElements(new TypeFilter<>(CtField.class));
            for (CtField field : fields) {
                if ("circleDistanceMetric".equals(field.getSimpleName())) {
                    CtTypeReference pdRef = launcher.getFactory().Type().createReference("org.tinspin.index.PointDistance");
                    field.setType(pdRef);
                }
            }

            List<CtLocalVariable> localVars = circlePackingClass.getElements(new TypeFilter<>(CtLocalVariable.class));
            for (CtLocalVariable var : localVars) {
                if ("nn".equals(var.getSimpleName()) && var.getType().getSimpleName().equals("PointEntryDist")) {
                    CtTypeReference pekRef = launcher.getFactory().Type().createReference("org.tinspin.index.Index.PointEntryKnn");
                    pekRef.addActualTypeArgument(launcher.getFactory().Type().createReference("processing.core.PVector"));
                    var.setType(pekRef);
                }
            }

            List<CtInvocation> invocations = circlePackingClass.getElements(new TypeFilter<>(CtInvocation.class));
            for (CtInvocation inv : invocations) {
                if ("query1NN".equals(inv.getExecutable().getSimpleName())) {
                    inv.getExecutable().setSimpleName("query1nn");
                }
            }
        }

        // 3. Fix PGS_PointSet.java
        CtType<?> pointSetClass = launcher.getFactory().Type().get("micycle.pgs.PGS_PointSet");
        if (pointSetClass != null) {
            List<CtInvocation> invocations = pointSetClass.getElements(new TypeFilter<>(CtInvocation.class));
            for (CtInvocation inv : invocations) {
                if ("query1NN".equals(inv.getExecutable().getSimpleName())) {
                    inv.getExecutable().setSimpleName("query1nn");
                }
            }
        }

        // Spoon bug with inner class references in lambdas when noClasspath=true:
        // We will replace "micycle.pgs.CPVector" with "CPVector" in the source code directly or via Spoon.
        List<CtTypeReference> typeRefs = launcher.getFactory().getModel().getElements(new TypeFilter<>(CtTypeReference.class));
        for (CtTypeReference ref : typeRefs) {
            if ("micycle.pgs.PGS_PointSet.CPVector".equals(ref.getQualifiedName()) || "micycle.pgs.CPVector".equals(ref.getQualifiedName())) {
                ref.setSimpleName("CPVector");
                ref.setPackage(null); // Remove package so it prints just CPVector
                if (ref.getDeclaringType() != null) {
                    ref.setDeclaringType(null);
                }
            }
        }

        launcher.prettyprint();
    }
}
