package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtNewClass;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.visitor.SignaturePrinter;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtExpression;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtExecutableReference;

import java.util.List;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -cp <classpath> github.chains.Main <source-directory> <output-directory>");
            System.err.println("Example: java -cp target/classes github.chains.Main /workspace/nem /workspace/output");
            System.exit(1);
        }

        String sourceDir = args[0];
        String outputDir = args[1];

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(outputDir);

        Factory factory = launcher.getFactory();

        launcher.addProcessor(new spoon.processing.AbstractProcessor<CtNewClass<?>>() {
            @Override
            public void process(CtNewClass<?> newClass) {
                CtTypeReference<?> typeRef = newClass.getType();
                if (typeRef == null) return;

                String typeName = typeRef.getQualifiedName();
                if (!"org.flywaydb.core.Flyway".equals(typeName)) {
                    return;
                }

                CtExecutableReference<?> constructorRef = newClass.getExecutable();
                if (constructorRef == null || constructorRef.getParameters().size() != 0) {
                    return;
                }

                CtBlock<?> parentBlock = newClass.getParent(CtBlock.class);
                if (parentBlock == null) return;

                List<CtStatement> statements = parentBlock.getStatements();
                int constructorIndex = statements.indexOf(newClass.getParent(CtStatement.class));
                if (constructorIndex == -1) return;

                List<CtInvocation<?>> setterCalls = new ArrayList<>();
                List<String> setterMethods = List.of("setDataSource", "setClassLoader", "setLocations", "setValidateOnMigrate");

                for (int i = constructorIndex + 1; i < statements.size(); i++) {
                    CtStatement stmt = statements.get(i);
                    if (stmt instanceof CtInvocation) {
                        CtInvocation<?> invocation = (CtInvocation<?>) stmt;
                        CtExecutableReference<?> execRef = invocation.getExecutable();
                        if (execRef != null && setterMethods.contains(execRef.getSimpleName())) {
                            setterCalls.add(invocation);
                        } else {
                            break;
                        }
                    } else if (stmt instanceof CtAssignment) {
                        CtAssignment<?, ?> assignment = (CtAssignment<?, ?>) stmt;
                        CtExpression<?> assignmentExpr = assignment.getAssignment();
                        if (assignmentExpr instanceof CtInvocation) {
                            CtInvocation<?> invocation = (CtInvocation<?>) assignmentExpr;
                            CtExecutableReference<?> execRef = invocation.getExecutable();
                            if (execRef != null && setterMethods.contains(execRef.getSimpleName())) {
                                setterCalls.add(invocation);
                            } else {
                                break;
                            }
                        } else {
                            break;
                        }
                    } else {
                        break;
                    }
                }

                if (setterCalls.isEmpty()) return;

                // Create the fluent API call
                StringBuilder builder = new StringBuilder();
                builder.append("org.flywaydb.core.Flyway.configure()");
                
                // Track if we have any setters to replace
                boolean hasSetters = false;
                
                for (CtInvocation<?> setterCall : setterCalls) {
                    String methodName = setterCall.getExecutable().getSimpleName();
                    List<CtExpression<?>> arguments = setterCall.getArguments();

                    if ("setDataSource".equals(methodName) && arguments.size() == 1) {
                        builder.append("\n\t\t.dataSource(").append(arguments.get(0).toString()).append(")");
                        hasSetters = true;
                    } else if ("setClassLoader".equals(methodName) && arguments.size() == 1) {
                        builder.append("\n\t\t.classLoader(").append(arguments.get(0).toString()).append(")");
                        hasSetters = true;
                    } else if ("setLocations".equals(methodName) && arguments.size() == 1) {
                        builder.append("\n\t\t.locations(").append(arguments.get(0).toString()).append(")");
                        hasSetters = true;
                    } else if ("setValidateOnMigrate".equals(methodName) && arguments.size() == 1) {
                        builder.append("\n\t\t.validateOnMigrate(").append(arguments.get(0).toString()).append(")");
                        hasSetters = true;
                    }
                }

                if (hasSetters) {
                    builder.append("\n\t\t.load()");
                    
                    // Find the variable declaration to replace
                    CtStatement parentStmt = newClass.getParent(CtStatement.class);
                    if (parentStmt instanceof CtLocalVariable) {
                        CtLocalVariable<?> localVar = (CtLocalVariable<?>) parentStmt;
                        
                        // Create replacement as a return statement
                        String replacement = builder.toString();
                        
                        // Get the parent method
                        CtMethod<?> parentMethod = newClass.getParent(CtMethod.class);
                        if (parentMethod != null && "flyway".equals(parentMethod.getSimpleName())) {
                            // Replace entire method body with return statement
                            CtBlock<?> methodBody = parentMethod.getBody();
                            if (methodBody != null) {
                                // Clear method body and add return statement
                                methodBody.getStatements().clear();
                                factory.createCodeSnippetStatement("return " + replacement + ";").insertBefore(methodBody);
                            }
                        } else {
                            // Replace variable assignment
                            localVar.replace(factory.createCodeSnippetExpression("return " + replacement));
                        }
                        
                        // Delete the setter calls
                        for (CtInvocation<?> setterCall : setterCalls) {
                            setterCall.delete();
                        }
                    }
                }
            }
        });

        launcher.run();
        System.out.println("Transformation completed successfully!");
        System.out.println("Output directory: " + outputDir);
    }
}