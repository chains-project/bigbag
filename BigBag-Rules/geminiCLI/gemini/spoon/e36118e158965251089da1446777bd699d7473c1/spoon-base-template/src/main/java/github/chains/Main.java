package github.chains;

import spoon.Launcher;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.factory.Factory;

import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/files-adapter/src/test/java/com/artipie/files/FileProxySliceTest.java");
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.buildModel();

        Factory factory = launcher.getFactory();
        CtTypeReference<?> targetType = factory.Type().createReference("java.util.AbstractMap.SimpleImmutableEntry");
        CtTypeReference<?> stringType = factory.Type().stringType();
        targetType.addActualTypeArgument(stringType);
        targetType.addActualTypeArgument(stringType);

        for (CtConstructorCall<?> call : launcher.getModel().getElements(new TypeFilter<>(CtConstructorCall.class))) {
            if (call.getType().getSimpleName().equals("MapEntry") || call.getType().getSimpleName().equals("SimpleImmutableEntry") || call.getType().getQualifiedName().equals("org.cactoos.map.MapEntry")) {
                call.getExecutable().setDeclaringType((CtTypeReference)targetType);
                call.setType((CtTypeReference)targetType);
                call.setActualTypeArguments(List.of(stringType, stringType));
            }
        }

        for (CtCompilationUnit cu : factory.CompilationUnit().getMap().values()) {
            List<CtImport> importsToRemove = new ArrayList<>();
            for (CtImport imp : cu.getImports()) {
                if (imp.toString().contains("MapEntry")) {
                    importsToRemove.add(imp);
                }
            }
            for (CtImport imp : importsToRemove) {
                cu.getImports().remove(imp);
            }
        }
        
        launcher.setSourceOutputDirectory("/workspace/files-adapter/src/test/java");
        launcher.prettyprint();
    }
}