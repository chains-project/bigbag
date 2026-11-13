package github.chains;

import java.util.HashMap;
import java.util.Map;
import spoon.Launcher;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {

    private static final String OLD_TYPE = "com.jcabi.aspects.Tv";

    private static final Map<String, Integer> CONSTANTS = buildConstants();

    public static void main(final String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException(
                "Usage: Main <input-source-dir> <output-source-dir>"
            );
        }
        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(args[1]);
        launcher.buildModel();
        final Factory factory = launcher.getFactory();
        for (CtFieldAccess<?> access : launcher.getModel().getElements(
            new TypeFilter<>(CtFieldAccess.class)
        )) {
            final CtFieldReference<?> reference = access.getVariable();
            if (reference == null || !isOldImport(access) || !CONSTANTS.containsKey(reference.getSimpleName())) {
                continue;
            }
            final Integer value = resolve(reference.getSimpleName());
            if (value == null) {
                continue;
            }
            final CtLiteral<Integer> literal = factory.Code().createLiteral(value);
            access.replace(literal);
        }
        for (CtImport animport : launcher.getModel().getElements(new TypeFilter<>(CtImport.class))) {
            if (animport.getReference() != null
                && OLD_TYPE.equals(animport.getReference().getQualifiedName())) {
                animport.delete();
            }
        }
        launcher.prettyprint();
    }

    private static boolean isOldImport(final CtFieldAccess<?> access) {
        return access.getPosition() != null
            && access.getPosition().getCompilationUnit() != null
            && access.getPosition().getCompilationUnit().getImports() != null
            && access.getPosition().getCompilationUnit().getImports().stream()
            .filter(animport -> animport.getReference() != null)
            .anyMatch(animport -> OLD_TYPE.equals(animport.getReference().getQualifiedName()));
    }

    private static Integer resolve(final String name) {
        return CONSTANTS.get(name);
    }

    private static Map<String, Integer> buildConstants() {
        final Map<String, Integer> map = new HashMap<>();
        map.put("ZERO", 0);
        map.put("ONE", 1);
        map.put("TWO", 2);
        map.put("THREE", 3);
        map.put("FOUR", 4);
        map.put("FIVE", 5);
        map.put("SIX", 6);
        map.put("SEVEN", 7);
        map.put("EIGHT", 8);
        map.put("NINE", 9);
        map.put("TEN", 10);
        map.put("HUNDRED", 100);
        map.put("THOUSAND", 1000);
        map.put("MILLION", 1000000);
        return map;
    }
}
