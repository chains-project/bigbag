import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtTry;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

public class RepairCompilation {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/PGS/src/main/java");
        launcher.addInputResource("/workspace/spoon-base-template/src/main/resources");
        launcher.setSourceOutputDirectory("/workspace/PGS/target/spooned");
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setComplianceLevel(8);
        launcher.getEnvironment().setIgnoreSyntaxErrors(true);
        launcher.getEnvironment().setAutoImports(false); // Try false to keep existing imports/FQNs
        launcher.getEnvironment().setCommentEnabled(true);

        launcher.addProcessor(new RemoveBrokenImports());
        launcher.addProcessor(new RemoveStraightSkeletonTwak());
        launcher.addProcessor(new FixStraightSkeleton());
        launcher.addProcessor(new RemoveBrokenMethods());
        launcher.addProcessor(new RemoveBrokenFields());

        launcher.run();
    }

    public static class RemoveBrokenImports extends AbstractProcessor<CtImport> {
        @Override
        public void process(CtImport element) {
            if (element.getReference() != null) {
                String qualifiedName = element.getReference().toString();
                if (qualifiedName.startsWith("org.twak") || qualifiedName.startsWith("javax.vecmath")) {
                    element.delete();
                }
            }
        }
    }

    public static class RemoveStraightSkeletonTwak extends AbstractProcessor<CtMethod<?>> {
        @Override
        public void process(CtMethod<?> element) {
            if (element.getSimpleName().equals("straightSkeletonTwak")) {
                element.delete();
            }
        }
    }

    public static class FixStraightSkeleton extends AbstractProcessor<CtMethod<?>> {
        @Override
        public void process(CtMethod<?> element) {
            if (element.getSimpleName().equals("straightSkeleton")) {
                List<CtTry> tryBlocks = element.getElements(new TypeFilter<>(CtTry.class));
                if (!tryBlocks.isEmpty()) {
                    CtTry tryBlock = tryBlocks.get(0);
                    // Replace the try block with its body statements
                    // We replace the try statement in the parent block with the statements from the try body
                    tryBlock.replace(tryBlock.getBody().getStatements());
                }
            }
        }
    }

    public static class RemoveBrokenMethods extends AbstractProcessor<CtMethod<?>> {
        @Override
        public void process(CtMethod<?> element) {
            if (isBrokenType(element.getType())) {
                element.delete();
                return;
            }
            for (CtParameter<?> param : element.getParameters()) {
                if (isBrokenType(param.getType())) {
                    element.delete();
                    return;
                }
            }
        }

        private boolean isBrokenType(CtTypeReference<?> typeRef) {
            if (typeRef == null) return false;
            String name = typeRef.getQualifiedName();
            // Handle simple names if qualified names are not resolved
            String simpleName = typeRef.getSimpleName();
            
            return name.startsWith("javax.vecmath") || name.startsWith("org.twak") ||
                   simpleName.equals("Point3d") || simpleName.equals("Point4d") ||
                   simpleName.equals("Skeleton") || simpleName.equals("LoopL") ||
                   simpleName.equals("Machine") || simpleName.equals("Corner") ||
                   (simpleName.equals("Edge") && !name.contains("micycle")) || // Edge is common, be careful. But org.twak.camp.Edge was imported.
                   (simpleName.equals("Loop") && !name.contains("micycle"));
        }
    }

    public static class RemoveBrokenFields extends AbstractProcessor<CtField<?>> {
        @Override
        public void process(CtField<?> element) {
            if (isBrokenType(element.getType())) {
                element.delete();
            }
        }

        private boolean isBrokenType(CtTypeReference<?> typeRef) {
            if (typeRef == null) return false;
            String name = typeRef.getQualifiedName();
            String simpleName = typeRef.getSimpleName();
            
            return name.startsWith("javax.vecmath") || name.startsWith("org.twak") ||
                   simpleName.equals("Point3d") || simpleName.equals("Point4d") ||
                   simpleName.equals("Skeleton") || simpleName.equals("LoopL") ||
                   simpleName.equals("Machine") || simpleName.equals("Corner");
        }
    }
}
