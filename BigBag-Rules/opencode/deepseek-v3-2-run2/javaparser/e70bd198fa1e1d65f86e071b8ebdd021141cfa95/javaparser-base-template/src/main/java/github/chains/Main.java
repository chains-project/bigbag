package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.printer.configuration.PrettyPrinterConfiguration;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.tinspin.index.Index.PointEntryKnn;

public class Main {

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        String sourceDir = args[0];
        Path sourcePath = Paths.get(sourceDir);
        if (!Files.exists(sourcePath) || !Files.isDirectory(sourcePath)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        List<Path> javaFiles = Files.walk(sourcePath).filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        System.out.println("Found " + javaFiles.size() + " Java files to process");
        // Configure type solver for better resolution
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        typeSolver.add(new JavaParserTypeSolver(sourcePath.toFile()));
        JavaParser javaParser = new JavaParser();
        javaParser.getParserConfiguration().setSymbolResolver(new JavaSymbolSolver(typeSolver));
        int totalChanges = 0;
        int filesChanged = 0;
        for (Path javaFile : javaFiles) {
            try {
                CompilationUnit cu = javaParser.parse(javaFile).getResult().orElse(null);
                if (cu == null)
                    continue;
                boolean fileModified = false;
                // Fix 1: Update imports
                List<ImportDeclaration> imports = cu.findAll(ImportDeclaration.class);
                for (ImportDeclaration importDecl : imports) {
                    String importName = importDecl.getNameAsString();
                    // Replace PointIndex import with PointMap
                    if (importName.equals("org.tinspin.index.PointIndex")) {
                        importDecl.setName("org.tinspin.index.PointMap");
                        System.out.println("Updated import in " + javaFile + ": " + importName + " -> org.tinspin.index.PointMap");
                        fileModified = true;
                        totalChanges++;
                    } else // Replace PointDistanceFunction import with PointDistance
                    if (importName.equals("org.tinspin.index.PointDistanceFunction")) {
                        importDecl.setName("org.tinspin.index.PointDistance");
                        System.out.println("Updated import in " + javaFile + ": " + importName + " -> org.tinspin.index.PointDistance");
                        fileModified = true;
                        totalChanges++;
                    } else // Replace PointEntryDist import with Index.PointEntryKnn
                    if (importName.equals("org.tinspin.index.PointEntryDist")) {
                        importDecl.setName("org.tinspin.index.Index.PointEntryKnn");
                        System.out.println("Updated import in " + javaFile + ": " + importName + " -> org.tinspin.index.Index.PointEntryKnn");
                        fileModified = true;
                        totalChanges++;
                    }
                }
                // Fix 2: Update type references in variable declarations, method parameters, and return types
                List<ClassOrInterfaceType> classTypes = cu.findAll(ClassOrInterfaceType.class);
                for (ClassOrInterfaceType type : classTypes) {
                    String typeName = type.getNameAsString();
                    // Replace PointIndex<T> with PointMap<T>
                    if (typeName.equals("PointIndex")) {
                        type.setName("PointMap");
                        totalChanges++;
                    } else // Replace PointDistanceFunction with PointDistance
                    if (typeName.equals("PointDistanceFunction")) {
                        type.setName("PointDistance");
                        totalChanges++;
                    } else // Replace PointEntryDist<T> with Index.PointEntryKnn<T>
                    if (typeName.equals("PointEntryDist")) {
                        type.setName("Index.PointEntryKnn");
                        totalChanges++;
                    }
                }
                // Fix 3: Update KDTree.create() method calls with distance function parameter
                List<MethodCallExpr> methodCalls = cu.findAll(MethodCallExpr.class);
                for (MethodCallExpr methodCall : methodCalls) {
                    if (methodCall.getNameAsString().equals("create")) {
                        Optional<Expression> scope = methodCall.getScope();
                        if (scope.isPresent()) {
                            String scopeName = scope.get().toString();
                            if (scopeName.equals("KDTree") || scopeName.endsWith(".KDTree")) {
                                // Check if this is a KDTree.create() call with 2 parameters
                                if (methodCall.getArguments().size() == 2) {
                                    Expression arg1 = methodCall.getArgument(0);
                                    Expression arg2 = methodCall.getArgument(1);
                                    // Check if second argument is a lambda or method reference (distance function)
                                    boolean isDistanceFunction = arg2 instanceof LambdaExpr;
                                    if (!isDistanceFunction && arg2 instanceof NameExpr) {
                                        String arg2Str = arg2.toString();
                                        isDistanceFunction = !arg2Str.matches("\\d+");
                                    }
                                    if (isDistanceFunction) {
                                        // Remove the second parameter (distance function)
                                        methodCall.getArguments().remove(1);
                                        System.out.println("Updated KDTree.create() call in " + javaFile + ": removed distance function parameter");
                                        totalChanges++;
                                        // Find the parent variable declaration to add cast if needed
                                        Optional<Node> parent = methodCall.getParentNode();
                                        while (parent.isPresent()) {
                                            if (parent.get() instanceof VariableDeclarationExpr) {
                                                VariableDeclarationExpr varDecl = (VariableDeclarationExpr) parent.get();
                                                for (VariableDeclarator var : varDecl.getVariables()) {
                                                    Type varType = var.getType();
                                                    if (varType instanceof ClassOrInterfaceType) {
                                                        ClassOrInterfaceType classType = (ClassOrInterfaceType) varType;
                                                        if (classType.getNameAsString().equals("PointIndex")) {
                                                            // Change PointIndex to PointMap
                                                            classType.setName("PointMap");
                                                            totalChanges++;
                                                        }
                                                    }
                                                }
                                                break;
                                            }
                                            parent = parent.get().getParentNode();
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                // Fix 4: Handle CoverTree.create() calls with distance function
                for (MethodCallExpr methodCall : methodCalls) {
                    if (methodCall.getNameAsString().equals("create")) {
                        Optional<Expression> scope = methodCall.getScope();
                        if (scope.isPresent()) {
                            String scopeName = scope.get().toString();
                            if (scopeName.equals("CoverTree") || scopeName.endsWith(".CoverTree")) {
                                // CoverTree.create() with 3 parameters: dimensions, base, distance
                                if (methodCall.getArguments().size() == 3) {
                                    // Remove the distance function parameter (keep dimensions and base)
                                    methodCall.getArguments().remove(2);
                                    System.out.println("Updated CoverTree.create() call in " + javaFile + ": removed distance function parameter");
                                    totalChanges++;
                                }
                            }
                        }
                    }
                }
                // Fix 5: Update query1NN() calls (uppercase NN) to query1nn() (lowercase nn)
                for (MethodCallExpr methodCall : methodCalls) {
                    if (methodCall.getNameAsString().equals("query1NN")) {
                        methodCall.setName("query1nn");
                        System.out.println("Updated query1NN to query1nn in " + javaFile);
                        totalChanges++;
                        // Also check if the result is being assigned to a PointEntryDist variable
                        Optional<Node> parent = methodCall.getParentNode();
                        while (parent.isPresent()) {
                            if (parent.get() instanceof VariableDeclarationExpr) {
                                VariableDeclarationExpr varDecl = (VariableDeclarationExpr) parent.get();
                                for (VariableDeclarator var : varDecl.getVariables()) {
                                    Type varType = var.getType();
                                    if (varType instanceof ClassOrInterfaceType) {
                                        ClassOrInterfaceType classType = (ClassOrInterfaceType) varType;
                                        if (classType.getNameAsString().equals("PointEntryDist")) {
                                            // Change PointEntryDist to Index.PointEntryKnn
                                            classType.setName("Index.PointEntryKnn");
                                            totalChanges++;
                                        }
                                    }
                                }
                                break;
                            }
                            parent = parent.get().getParentNode();
                        }
                    }
                }
                // Fix 6: Update .dist() method calls on query1nn() results
                for (MethodCallExpr methodCall : methodCalls) {
                    if (methodCall.getNameAsString().equals("dist")) {
                        Optional<Expression> scope = methodCall.getScope();
                        if (scope.isPresent()) {
                            // Check if this is a call on a query1nn() result
                            if (scope.get() instanceof MethodCallExpr) {
                                MethodCallExpr parentCall = (MethodCallExpr) scope.get();
                                if (parentCall.getNameAsString().equals("query1nn") || parentCall.getNameAsString().equals("query1NN")) {
                                    // dist() method still exists on Index.PointEntryKnn, no change needed
                                    System.out.println("Found dist() call on query1nn() result in " + javaFile);
                                }
                            }
                        }
                    }
                }
                // Fix 7: Update variable initializations with inline distance functions
                List<VariableDeclarator> variables = cu.findAll(VariableDeclarator.class);
                for (VariableDeclarator var : variables) {
                    if (var.getInitializer().isPresent()) {
                        Expression init = var.getInitializer().get();
                        Type varType = var.getType();
                        if (varType instanceof ClassOrInterfaceType) {
                            ClassOrInterfaceType classType = (ClassOrInterfaceType) varType;
                            String typeName = classType.getNameAsString();
                            // Update PointDistanceFunction variable declarations
                            if (typeName.equals("PointDistanceFunction")) {
                                classType.setName("PointDistance");
                                totalChanges++;
                                // If initializer is a lambda, it should still work with PointDistance interface
                                System.out.println("Updated PointDistanceFunction variable in " + javaFile);
                            }
                        }
                    }
                }
                // Fix 8: Add missing import for Index class if needed
                boolean hasPointEntryKnnUsage = cu.toString().contains("PointEntryKnn") || cu.toString().contains("Index.PointEntryKnn");
                boolean hasIndexImport = cu.getImports().stream().anyMatch(imp -> imp.getNameAsString().equals("org.tinspin.index.Index") || imp.getNameAsString().equals("org.tinspin.index.Index.PointEntryKnn"));
                if (hasPointEntryKnnUsage && !hasIndexImport) {
                    // Add import for Index.PointEntryKnn if not already present
                    cu.addImport("org.tinspin.index.Index.PointEntryKnn");
                    System.out.println("Added missing import for Index.PointEntryKnn in " + javaFile);
                    fileModified = true;
                    totalChanges++;
                }
                // Write the modified file back if changes were made
                if (fileModified || totalChanges > 0) {
                    PrettyPrinterConfiguration config = new PrettyPrinterConfiguration();
                    config.setPrintComments(true);
                    config.setColumnAlignParameters(true);
                    config.setColumnAlignFirstMethodChain(false);
                    String modifiedCode = cu.toString(config);
                    Files.write(javaFile,
                                modifiedCode.getBytes());
                    filesChanged++;
                    System.out.println("Saved changes to " + javaFile + " (" + totalChanges + " changes)");
                }
            } catch (Exception e) {
                System.err.println("Error processing file " + javaFile + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        System.out.println("\nTransformation complete!");
        System.out.println("Files changed: " + filesChanged);
        System.out.println("Total changes applied: " + totalChanges);
    }
}
