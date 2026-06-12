package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtReturn;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.CtModel;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class Main {
    private static final Map<String, String> SETTER_TO_FLUENT = new HashMap<>();
    static {
        SETTER_TO_FLUENT.put("setDataSource", "dataSource");
        SETTER_TO_FLUENT.put("setClassLoader", "classLoader");
        SETTER_TO_FLUENT.put("setLocations", "locations");
        SETTER_TO_FLUENT.put("setValidateOnMigrate", "validateOnMigrate");
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            return;
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming Flyway API in: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        
        CtModel model = launcher.buildModel();
        Factory factory = launcher.getFactory();
        
        List<CtConstructorCall> constructors = model.getElements(
            new TypeFilter<CtConstructorCall>(CtConstructorCall.class) {
                @Override
                public boolean matches(CtConstructorCall cc) {
                    return cc.getType() != null && 
                           "org.flywaydb.core.Flyway".equals(cc.getType().getQualifiedName());
                }
            });
        
        System.out.println("Found " + constructors.size() + " Flyway constructor(s)");
        
        for (CtConstructorCall constructor : constructors) {
            transformConstructor(constructor, factory);
        }
        
        launcher.setSourceOutputDirectory(sourceDir);
        launcher.prettyprint();
        System.out.println("Transformation complete!");
    }
    
    private static void transformConstructor(CtConstructorCall constructor, Factory factory) {
        try {
            CtStatement stmt = constructor.getParent(CtStatement.class);
            if (stmt == null) return;
            
            String varName = null;
            if (stmt instanceof CtLocalVariable) {
                varName = ((CtLocalVariable) stmt).getSimpleName();
            } else if (stmt instanceof CtAssignment) {
                CtAssignment assign = (CtAssignment) stmt;
                if (assign.getAssigned() instanceof CtVariableAccess) {
                    varName = ((CtVariableAccess) assign.getAssigned()).getVariable().getSimpleName();
                }
            } else if (stmt instanceof CtReturn) {
                transformReturnCase(constructor, factory, (CtReturn) stmt);
                return;
            }
            
            if (varName == null) return;
            
            CtBlock block = stmt.getParent(CtBlock.class);
            if (block == null) return;
            
            CtTypeReference flywayType = factory.Type().createReference("org.flywaydb.core.Flyway");
            CtExecutableReference configureRef = factory.createExecutableReference();
            configureRef.setDeclaringType(flywayType);
            configureRef.setSimpleName("configure");
            
            CtExpression current = factory.createInvocation(
                factory.createTypeAccess(flywayType),
                configureRef
            );
            
            List<CtStatement> statements = block.getStatements();
            int idx = statements.indexOf(stmt);
            if (idx == -1) return;
            
            for (int i = idx + 1; i < statements.size(); i++) {
                CtStatement s = statements.get(i);
                if (!(s instanceof CtInvocation)) break;
                
                CtInvocation inv = (CtInvocation) s;
                if (!isSetterOnVar(inv, varName)) break;
                
                String setter = inv.getExecutable().getSimpleName();
                String fluent = SETTER_TO_FLUENT.get(setter);
                if (fluent == null) continue;
                
                CtExecutableReference fluentRef = factory.createExecutableReference();
                fluentRef.setDeclaringType(flywayType);
                fluentRef.setSimpleName(fluent);
                
                List<CtExpression> args = inv.getArguments();
                current = factory.createInvocation(
                    current,
                    fluentRef,
                    args.toArray(new CtExpression[args.size()])
                );
                
                s.delete();
            }
            
            CtExecutableReference loadRef = factory.createExecutableReference();
            loadRef.setDeclaringType(flywayType);
            loadRef.setSimpleName("load");
            
            CtExpression finalExpr = factory.createInvocation(current, loadRef);
            
            if (stmt instanceof CtLocalVariable) {
                ((CtLocalVariable) stmt).setAssignment(finalExpr);
            } else if (stmt instanceof CtAssignment) {
                ((CtAssignment) stmt).setAssignment(finalExpr);
            }
            
        } catch (Exception e) {
            System.err.println("Error at " + constructor.getPosition() + ": " + e.getMessage());
        }
    }
    
    private static void transformReturnCase(CtConstructorCall constructor, Factory factory, CtReturn returnStmt) {
        try {
            CtTypeReference flywayType = factory.Type().createReference("org.flywaydb.core.Flyway");
            
            CtExecutableReference configureRef = factory.createExecutableReference();
            configureRef.setDeclaringType(flywayType);
            configureRef.setSimpleName("configure");
            
            CtExpression configure = factory.createInvocation(
                factory.createTypeAccess(flywayType),
                configureRef
            );
            
            CtExecutableReference loadRef = factory.createExecutableReference();
            loadRef.setDeclaringType(flywayType);
            loadRef.setSimpleName("load");
            
            CtExpression load = factory.createInvocation(configure, loadRef);
            returnStmt.setReturnedExpression(load);
            
        } catch (Exception e) {
            System.err.println("Error in return case: " + e.getMessage());
        }
    }
    
    private static boolean isSetterOnVar(CtInvocation inv, String varName) {
        if (!inv.getExecutable().getSimpleName().startsWith("set")) return false;
        if (!(inv.getTarget() instanceof CtVariableAccess)) return false;
        CtVariableAccess va = (CtVariableAccess) inv.getTarget();
        return varName.equals(va.getVariable().getSimpleName());
    }
}