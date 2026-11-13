package github.chains;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.WildcardType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.utils.SourceRoot;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    private static final String MANAGED_OBJECT_TYPE = "org.snmp4j.agent.ManagedObject";

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected one argument: source root");
        }

        Path root = Paths.get(args[0]);
        SourceRoot sourceRoot = new SourceRoot(root);
        sourceRoot.tryToParse();
        sourceRoot.getCompilationUnits().forEach(cu -> cu.accept(new Transformer(), null));
        sourceRoot.saveAll();
    }

    private static final class Transformer extends ModifierVisitor<Void> {
        @Override
        public Visitable visit(ClassOrInterfaceType type, Void arg) {
            super.visit(type, arg);
            if (!isManagedObject(type)) {
                return type;
            }
            if (type.getTypeArguments().isPresent() && !type.getTypeArguments().get().isEmpty()) {
                return type;
            }
            type.setTypeArguments(NodeList.nodeList(new WildcardType()));
            return type;
        }

        private static boolean isManagedObject(ClassOrInterfaceType type) {
            return MANAGED_OBJECT_TYPE.equals(type.getNameWithScope()) || "ManagedObject".equals(type.getNameAsString());
        }
    }
}
