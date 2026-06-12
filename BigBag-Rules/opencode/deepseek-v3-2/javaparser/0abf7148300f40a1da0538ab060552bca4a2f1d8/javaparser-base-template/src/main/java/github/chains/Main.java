package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.DoubleLiteralExpr;
import com.github.javaparser.ast.expr.LongLiteralExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.err.println("\nThis transformation fixes breaking API changes in JasperReports 6.19.1+");
            System.err.println("where JRPen.setLineWidth() changed from float parameter to Float parameter.");
            System.err.println("\nThe transformation will:");
            System.err.println("1. Find all calls to setLineWidth() with primitive arguments");
            System.err.println("2. Wrap primitive arguments in Float.valueOf()");
            System.err.println("3. Skip already-wrapped arguments (Float.valueOf() or cast to Float)");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming source directory: " + sourceDir);
        System.out.println("Fixing JasperReports API breaking change: JRPen.setLineWidth(float) -> JRPen.setLineWidth(Float)");
        
        // Configuration: Define method signatures that changed from primitive to boxed type
        // Format: "fully.qualified.ClassName.methodName(parameterType)"
        List<MethodTransformation> transformations = new ArrayList<>();
        
        // JasperReports 6.19.1 breaking change: JRPen.setLineWidth(float) -> JRPen.setLineWidth(Float)
        transformations.add(new MethodTransformation(
            "setLineWidth",
            "net.sf.jasperreports.engine.JRPen",
            new String[]{"java.lang.Float"},
            "Float",
            "valueOf"
        ));
        
        // Add more transformations here for other breaking changes
        // Example:
        // transformations.add(new MethodTransformation(
        //     "setSomeMethod",
        //     "some.package.ClassName", 
        //     new String[]{"java.lang.Integer"},
        //     "Integer",
        //     "valueOf"
        // ));
        
        // Create type solver for resolving types
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
        JavaParser javaParser = new JavaParser();
        javaParser.getParserConfiguration().setSymbolResolver(symbolSolver);
        
        // Find all Java files
        List<File> javaFiles = Files.walk(Paths.get(sourceDir))
            .filter(Files::isRegularFile)
            .filter(p -> p.toString().endsWith(".java"))
            .map(Path::toFile)
            .collect(Collectors.toList());
        
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        int transformedFiles = 0;
        int totalTransformations = 0;
        
        for (File javaFile : javaFiles) {
            try {
                CompilationUnit cu = javaParser.parse(javaFile).getResult().orElse(null);
                if (cu == null) {
                    continue;
                }
                
                // Create visitor to transform method calls
                PrimitiveToBoxedTransformer transformer = new PrimitiveToBoxedTransformer(transformations);
                cu.accept(transformer, null);
                
                if (transformer.getTransformationCount() > 0) {
                    // Write back the transformed file
                    Files.write(javaFile.toPath(), cu.toString().getBytes());
                    transformedFiles++;
                    totalTransformations += transformer.getTransformationCount();
                    System.out.println("Transformed " + transformer.getTransformationCount() + 
                                     " calls in " + javaFile.getPath());
                }
            } catch (Exception e) {
                System.err.println("Error processing " + javaFile.getPath() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        System.out.println("\nSummary:");
        System.out.println("Transformed " + transformedFiles + " files");
        System.out.println("Applied " + totalTransformations + " transformations");
        System.out.println("\nBreaking change fixed: JRPen.setLineWidth() now requires Float instead of float");
        System.out.println("Primitive arguments have been wrapped in Float.valueOf()");
    }
    
    private static class MethodTransformation {
        final String methodName;
        final String className; // Fully qualified class name
        final String[] parameterTypes; // Expected parameter types (boxed)
        final String boxedType; // e.g., "Float", "Integer"
        final String valueOfMethod; // e.g., "valueOf"
        
        MethodTransformation(String methodName, String className, String[] parameterTypes, 
                           String boxedType, String valueOfMethod) {
            this.methodName = methodName;
            this.className = className;
            this.parameterTypes = parameterTypes;
            this.boxedType = boxedType;
            this.valueOfMethod = valueOfMethod;
        }
    }
    
    private static class PrimitiveToBoxedTransformer extends ModifierVisitor<Void> {
        private final List<MethodTransformation> transformations;
        private int transformationCount = 0;
        
        public PrimitiveToBoxedTransformer(List<MethodTransformation> transformations) {
            this.transformations = transformations;
        }
        
        public int getTransformationCount() {
            return transformationCount;
        }
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // First visit children
            super.visit(n, arg);
            
            String methodName = n.getNameAsString();
            
            // Check if this method needs transformation
            for (MethodTransformation transformation : transformations) {
                if (transformation.methodName.equals(methodName)) {
                    // Check if we have the right number of arguments
                    if (n.getArguments().size() == transformation.parameterTypes.length) {
                        // For simplicity, we'll wrap primitive arguments
                        // A more sophisticated version would check parameter types
                        for (int i = 0; i < n.getArguments().size(); i++) {
                            Expression argExpr = n.getArgument(i);
                            String expectedType = transformation.parameterTypes[i];
                            
                            if ("java.lang.Float".equals(expectedType)) {
                                if (!isAlreadyBoxedAsFloat(argExpr)) {
                                    // Wrap in Float.valueOf()
                                    MethodCallExpr floatValueOf = new MethodCallExpr();
                                    floatValueOf.setScope(new NameExpr(transformation.boxedType));
                                    floatValueOf.setName(transformation.valueOfMethod);
                                    floatValueOf.addArgument(argExpr.clone());
                                    n.getArguments().set(i, floatValueOf);
                                    transformationCount++;
                                    System.out.println("  Wrapped argument " + i + " in " + 
                                                     transformation.boxedType + "." + 
                                                     transformation.valueOfMethod + "() for: " + 
                                                     n.toString().replace("\n", " "));
                                }
                            }
                            // Add handling for other boxed types (Integer, Double, etc.) here
                        }
                    }
                    break;
                }
            }
            
            return n;
        }
        
        private boolean isAlreadyBoxedAsFloat(Expression expr) {
            // Check if expression is already Float.valueOf() call
            if (expr instanceof MethodCallExpr) {
                MethodCallExpr methodCall = (MethodCallExpr) expr;
                if ("valueOf".equals(methodCall.getNameAsString())) {
                    if (methodCall.getScope().isPresent()) {
                        String scope = methodCall.getScope().get().toString();
                        if ("Float".equals(scope) || "java.lang.Float".equals(scope)) {
                            return true;
                        }
                    }
                }
            }
            
            // Check if expression is cast to Float
            if (expr instanceof CastExpr) {
                CastExpr castExpr = (CastExpr) expr;
                String typeName = castExpr.getType().asString();
                if ("Float".equals(typeName) || "java.lang.Float".equals(typeName)) {
                    return true;
                }
            }
            
            // Check if expression is a Double literal (JavaParser treats float literals as DoubleLiteralExpr)
            if (expr instanceof DoubleLiteralExpr) {
                DoubleLiteralExpr doubleExpr = (DoubleLiteralExpr) expr;
                String value = doubleExpr.getValue();
                // Check if it ends with 'f' or 'F' which indicates a float literal
                if (value.endsWith("f") || value.endsWith("F")) {
                    return true;
                }
            }
            
            return false;
        }
    }
}