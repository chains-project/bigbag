package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.declaration.CtImport;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/docker-adapter/src/main/java/com/artipie/docker/misc/DigestedFlowable.java");
        launcher.addInputResource("/workspace/docker-adapter/src/main/java/com/artipie/docker/misc/DigestFromContent.java");
        launcher.setSourceOutputDirectory("/workspace/docker-adapter/src/main/java");
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(true);
        
        CtModel model = launcher.buildModel();
        
        for (CtInvocation<?> inv : model.getElements(new TypeFilter<>(CtInvocation.class))) {
            if (inv.getExecutable().getSimpleName().equals("asString")) {
                CtExpression<?> target = inv.getTarget();
                if (target instanceof CtConstructorCall) {
                    CtConstructorCall<?> hexOfCall = (CtConstructorCall<?>) target;
                    if (hexOfCall.getType() != null && hexOfCall.getType().getSimpleName().equals("HexOf")) {
                        CtExpression<?> arg = hexOfCall.getArguments().get(0);
                        if (arg instanceof CtConstructorCall) {
                            CtConstructorCall<?> bytesOfCall = (CtConstructorCall<?>) arg;
                            if (bytesOfCall.getType() != null && bytesOfCall.getType().getSimpleName().equals("BytesOf")) {
                                CtExpression<?> shaDigest = bytesOfCall.getArguments().get(0);
                                CtExpression<?> newExpr = launcher.getFactory().Code().createCodeSnippetExpression(
                                    "org.apache.commons.codec.binary.Hex.encodeHexString(" + shaDigest.toString() + ")"
                                );
                                inv.replace(newExpr);
                            }
                        }
                    }
                }
            }
        }
        
        for (spoon.reflect.declaration.CtCompilationUnit cu : launcher.getFactory().CompilationUnit().getMap().values()) {
            java.util.List<CtImport> toRemove = new java.util.ArrayList<>();
            for (CtImport imp : cu.getImports()) {
                if (imp.toString().contains("org.cactoos")) {
                    toRemove.add(imp);
                }
            }
            for (CtImport imp : toRemove) {
                imp.delete();
            }
        }
        
        launcher.prettyprint();
    }
}
