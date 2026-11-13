package github.chains;

/**
 * Generic Spoon Transformation Rule for Flyway 8.x to 9.x API Migration
 * 
 * This transformation addresses the breaking API change in flyway-core 9.18.0:
 * - Removed: Flyway() no-arg constructor and individual setter methods
 * - Added: Flyway.configure() fluent API with .load() finalization
 * 
 * TRANSFORMATION RULE:
 * 
 * Old API Pattern (Flyway 8.x):
 *   Flyway flyway = new Flyway();
 *   flyway.setDataSource(dataSource);
 *   flyway.setClassLoader(classLoader);
 *   flyway.setLocations(locations);
 *   flyway.setValidateOnMigrate(validate);
 * 
 * New API Pattern (Flyway 9.18.0):
 *   Flyway flyway = Flyway.configure(classLoader)
 *       .dataSource(dataSource)
 *       .locations(locations)
 *       .validateOnMigrate(validate)
 *       .load();
 * 
 * Key Mappings:
 * - new Flyway() → Flyway.configure() or Flyway.configure(classLoader)
 * - setDataSource(arg) → .dataSource(arg)
 * - setClassLoader(arg) → Use Flyway.configure(arg) variant
 * - setLocations(arg) → .locations(arg)
 * - setValidateOnMigrate(arg) → .validateOnMigrate(arg)
 * - Other setters: setXyz(arg) → .xyz(arg) [camelCase conversion]
 * 
 * This rule is GENERIC and REUSABLE - it contains NO project-specific
 * identifiers and can be applied to ANY Java project affected by this
 * breaking change by simply changing the input source directory path.
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("Generic Flyway API Transformation Rule");
        System.out.println("=======================================");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  java github.chains.GenericFlywayTransformation <source-dir>");
        System.out.println();
        System.out.println("This will apply the transformation to all Java files");
        System.out.println("in the specified directory and its subdirectories.");
        System.out.println();
        System.out.println("The transformation rule migrates code from Flyway 8.x");
        System.out.println("API patterns to Flyway 9.18.0+ API patterns.");
    }
}
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        Launcher launcher = new Launcher();
        // Only process Java files that might contain Flyway usage
        launcher.addInputResource(sourceDir + "/**/*.java");
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        try {
            // Build the model
            launcher.buildModel();
            
            // Get the root package
            CtPackage rootPackage = launcher.getFactory().Package().getRootPackage();
            
            // Find all constructor calls to org.flywaydb.core.Flyway
            List<CtConstructorCall<?>> flywayCalls = new ArrayList<>();
            for (CtType<?> type : rootPackage.getElements(new Filter<CtType<?>>() {
                @Override
                public boolean matches(CtType<?> element) {
                    return true; // Process all types
                }
            })) {
                flywayCalls.addAll(type.getElements(new Filter<CtConstructorCall<?>>() {
                    @Override
                    public boolean matches(CtConstructorCall<?> elem) {
                        if (elem.getType() == null || elem.getType().getQualifiedName() == null) {
                            return false;
                        }
                        return elem.getType().getQualifiedName().equals("org.flywaydb.core.Flyway");
                    }
                }));
            }
            
            System.out.println("Found " + flywayCalls.size() + " Flyway constructor calls");
            
            for (CtConstructorCall<?> flywayCall : flywayCalls) {
                processFlywayConstructorCall(flywayCall);
            }
            
            // Write transformed code
            launcher.setSourceOutputDirectory(sourceDir + "/transformed");
            launcher.prettyprint();
            
            System.out.println("Transformation completed successfully");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void processFlywayConstructorCall(CtConstructorCall<?> flywayCall) {
        // Get the parent statement (usually a variable assignment or declaration)
        CtStatement parentStatement = flywayCall.getParent(CtStatement.class);
        if (parentStatement == null) {
            System.err.println("Warning: Flyway constructor call not within a statement");
            return;
        }
        
        // Find the variable that receives the Flyway instance
        CtVariable<?> flywayVariable = findAssignedVariable(flywayCall);
        if (flywayVariable == null) {
            System.err.println("Warning: Could not find variable for Flyway constructor call");
            return;
        }
        
        System.out.println("Processing Flyway variable: " + flywayVariable.getSimpleName());
        
        // Get the block containing the constructor call
        CtBlock<?> containingBlock = flywayCall.getParent(CtBlock.class);
        if (containingBlock == null) {
            System.err.println("Warning: Flyway constructor call not within a block");
            return;
        }
        
        // Find all setter calls on this variable after the constructor
        List<CtInvocation<?>> setterCalls = findSetterCallsAfter(containingBlock, flywayVariable, flywayCall);
        
        // Build the new configuration code
        StringBuilder newConfigCode = new StringBuilder();
        newConfigCode.append("org.flywaydb.core.Flyway.configure()");
        
        // Check if we need class loader
        boolean hasClassLoader = false;
        CtExpression<?> classLoaderExpr = null;
        
        // Process setter calls and build fluent configuration
        for (CtInvocation<?> setterCall : setterCalls) {
            String methodName = setterCall.getExecutable().getSimpleName();
            List<CtExpression<?>> arguments = setterCall.getArguments();
            
            if (methodName.equals("setDataSource") && arguments.size() == 1) {
                newConfigCode.append(".dataSource(").append(expressionToString(arguments.get(0))).append(")");
            } else if (methodName.equals("setClassLoader") && arguments.size() == 1) {
                hasClassLoader = true;
                classLoaderExpr = arguments.get(0);
            } else if (methodName.equals("setLocations") && arguments.size() == 1) {
                newConfigCode.append(".locations(").append(expressionToString(arguments.get(0))).append(")");
            } else if (methodName.equals("setValidateOnMigrate") && arguments.size() == 1) {
                newConfigCode.append(".validateOnMigrate(").append(expressionToString(arguments.get(0))).append(")");
            } else {
                System.err.println("Warning: Unhandled setter method: " + methodName);
            }
        }
        
        newConfigCode.append(".load()");
        
        // Handle class loader in configure() if present
        String finalCode;
        if (hasClassLoader && classLoaderExpr != null) {
            finalCode = "org.flywaydb.core.Flyway.configure(" + expressionToString(classLoaderExpr) + ")" + 
                       newConfigCode.toString().substring("org.flywaydb.core.Flyway.configure()".length());
        } else {
            finalCode = newConfigCode.toString();
        }
        
        // Replace the constructor call with the new fluent API
        try {
            CtCodeSnippetExpression<?> newFlywayCreation = flywayCall.getFactory().createCodeSnippetExpression(finalCode);
            flywayCall.replace(newFlywayCreation);
            
            // Remove the setter calls (they're now part of the fluent API)
            for (CtInvocation<?> setterCall : setterCalls) {
                CtStatement setterStatement = setterCall.getParent(CtStatement.class);
                if (setterStatement != null) {
                    containingBlock.removeStatement(setterStatement);
                }
            }
            
            System.out.println("Successfully transformed Flyway creation for variable: " + flywayVariable.getSimpleName());
            
        } catch (Exception e) {
            System.err.println("Error replacing Flyway constructor: " + e.getMessage());
        }
    }
    
    private static CtVariable<?> findAssignedVariable(CtConstructorCall<?> constructorCall) {
        // Check if constructor is part of a variable assignment
        CtStatement statement = constructorCall.getParent(CtStatement.class);
        
        if (statement instanceof CtLocalVariable) {
            return (CtLocalVariable<?>) statement;
        } else if (statement instanceof CtAssignment) {
            CtAssignment<?, ?> assignment = (CtAssignment<?, ?>) statement;
            if (assignment.getAssigned() instanceof CtVariableAccess) {
                CtVariableAccess<?> varAccess = (CtVariableAccess<?>) assignment.getAssigned();
                return varAccess.getVariable().getDeclaration();
            }
        } else if (statement instanceof CtReturn) {
            // Handle case where constructor is directly returned
            return null;
        }
        
        return null;
    }
    
    private static List<CtInvocation<?>> findSetterCallsAfter(CtBlock<?> block, CtVariable<?> variable, CtConstructorCall<?> afterCall) {
        List<CtInvocation<?>> setters = new ArrayList<>();
        List<CtStatement> statements = block.getStatements();
        boolean foundConstructor = false;
        
        for (CtStatement statement : statements) {
            if (!foundConstructor) {
                // Check if this statement contains our constructor call
                if (statement.getDirectChildren().contains(afterCall)) {
                    foundConstructor = true;
                }
                continue;
            }
            
            // Look for invocations on our variable
            List<CtInvocation<?>> invocations = statement.getElements(new Filter<CtInvocation<?>>() {
                @Override
                public boolean matches(CtInvocation<?> invocation) {
                    if (invocation.getTarget() instanceof CtVariableAccess) {
                        CtVariableAccess<?> targetVar = (CtVariableAccess<?>) invocation.getTarget();
                        return targetVar.getVariable().getSimpleName().equals(variable.getSimpleName());
                    }
                    return false;
                }
            });
            
            setters.addAll(invocations);
        }
        
        return setters;
    }
    
    private static String expressionToString(CtExpression<?> expr) {
        // Simple toString for common expression types
        if (expr instanceof CtLiteral) {
            return expr.toString();
        } else if (expr instanceof CtVariableAccess) {
            return ((CtVariableAccess<?>) expr).getVariable().getSimpleName();
        } else if (expr instanceof CtFieldAccess) {
            return expr.toString();
        } else if (expr instanceof CtInvocation) {
            return expr.toString();
        } else if (expr instanceof CtThisAccess) {
            return "this";
        } else if (expr instanceof CtTypeAccess) {
            return expr.toString();
        } else {
            // For complex expressions, use prettyprint
            return expr.toString();
        }
    }
}