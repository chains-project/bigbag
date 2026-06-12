package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class Main {

    public static void main(String[] args) throws IOException {
        Path projectRoot = Paths.get("../pitest-mutation-testing-elements-plugin");

        processMutationTestSummaryData(projectRoot);
        processJsonParser(projectRoot);
        processMutationReportListener(projectRoot);
        processMutationHtmlReportListenerTest(projectRoot);
        processMutationTestSummaryDataTest(projectRoot);
        processJsonParserTest(projectRoot);
    }

    private static void save(CompilationUnit cu, Path path) throws IOException {
        Files.write(path, cu.toString().getBytes());
        System.out.println("Saved " + path);
    }

    private static void processMutationTestSummaryData(Path root) throws IOException {
        Path path = root.resolve("src/main/java/org/pitest/elements/models/MutationTestSummaryData.java");
        CompilationUnit cu = StaticJavaParser.parse(path);

        // Replace imports
        replaceImport(cu, "org.pitest.classinfo.ClassInfo", "org.pitest.classinfo.ClassName");

        cu.getClassByName("MutationTestSummaryData").ifPresent(cls -> {
            // Change field type
            cls.getFieldByName("classes").ifPresent(f -> {
                f.getVariable(0).setType("Set<ClassName>");
            });

            // Change constructor parameter
            cls.getConstructors().forEach(ctor -> {
                ctor.getParameterByName("classes").ifPresent(p -> p.setType("Collection<ClassName>"));
            });

            // Change getClasses return type
            cls.getMethodsByName("getClasses").forEach(m -> m.setType("Collection<ClassName>"));

            // Fix getPackageName: this.classes.iterator().next().getName().asJavaName() -> ...next().asJavaName()
            cls.getMethodsByName("getPackageName").forEach(m -> {
                m.walk(MethodCallExpr.class, mc -> {
                    if (mc.getNameAsString().equals("asJavaName")) {
                        mc.getScope().ifPresent(scope -> {
                            if (scope.isMethodCallExpr() && ((MethodCallExpr) scope).getNameAsString().equals("getName")) {
                                // Replace a.getName().asJavaName() with a.asJavaName()
                                ((MethodCallExpr) scope).getScope().ifPresent(innerScope -> {
                                    mc.setScope(innerScope);
                                });
                            }
                        });
                    }
                });
            });
        });

        save(cu, path);
    }

    private static void processJsonParser(Path root) throws IOException {
        Path path = root.resolve("src/main/java/org/pitest/elements/utils/JsonParser.java");
        CompilationUnit cu = StaticJavaParser.parse(path);

        replaceImport(cu, "org.pitest.classinfo.ClassInfo", "org.pitest.classinfo.ClassName");

        cu.getClassByName("JsonParser").ifPresent(cls -> {
            // Change methods signatures
            changeParamType(cls, "getLines", "MutationTestSummaryData", "MutationTestSummaryData"); 
            // Internal logic of getLines needs check? 
            // final Collection<ClassInfo> classes = summaryData.getClasses();
            // It will infer Type from getClasses() which we changed. 
            // But if it is explicit: final Collection<ClassInfo> classes = ...
            // We need to change the variable declaration.
            
            cls.getMethodsByName("getLines").forEach(m -> {
                m.getBody().ifPresent(body -> {
                    body.findAll(VariableDeclarator.class).forEach(v -> {
                        if (v.getNameAsString().equals("classes")) {
                            v.setType("Collection<ClassName>");
                        }
                    });
                });
            });

            changeParamType(cls, "findReaderForSource", "Collection<ClassInfo>", "Collection<ClassName>");
            changeParamType(cls, "classInfoToNames", "Collection<ClassInfo>", "Collection<ClassName>");

            // Fix classInfoToNames body: a.getName().asJavaName() -> a.asJavaName()
            cls.getMethodsByName("classInfoToNames").forEach(m -> {
                m.walk(MethodCallExpr.class, mc -> {
                    if (mc.getNameAsString().equals("asJavaName")) {
                        mc.getScope().ifPresent(scope -> {
                             if (scope.isMethodCallExpr() && ((MethodCallExpr) scope).getNameAsString().equals("getName")) {
                                ((MethodCallExpr) scope).getScope().ifPresent(innerScope -> {
                                    mc.setScope(innerScope);
                                });
                            }
                        });
                    }
                });
            });
        });

        save(cu, path);
    }

    private static void processMutationReportListener(Path root) throws IOException {
        Path path = root.resolve("src/main/java/org/pitest/elements/MutationReportListener.java");
        CompilationUnit cu = StaticJavaParser.parse(path);
        
        // Remove import ClassInfo/ClassName not needed if not used, but let's check.
        // It imports java.util.Collections.
        
        cu.getClassByName("MutationReportListener").ifPresent(cls -> {
            // Update createSummaryData
            cls.getMethodsByName("createSummaryData").forEach(m -> {
                // Remove 'coverage' parameter if possible, or leave it. 
                // Let's remove the parameter from the method and the call sites.
                // But call sites are: createSummaryData(this.coverage, mutationMetaData)
                
                // First, fix the body.
                // coverage.getClassInfo(Collections.singleton(data.getMutatedClass()))
                // -> Collections.singleton(data.getMutatedClass())
                m.walk(MethodCallExpr.class, mc -> {
                    if (mc.getNameAsString().equals("getClassInfo")) {
                        // Replace 'coverage.getClassInfo(arg)' with 'arg'
                        if (mc.getArguments().size() == 1) {
                            mc.replace(mc.getArgument(0));
                        }
                    }
                });
                
                // Now remove the parameter 'coverage' from definition
                m.getParameters().removeIf(p -> p.getNameAsString().equals("coverage"));
            });
            
            // Update call sites in updatePackageSummary
             cls.getMethodsByName("updatePackageSummary").forEach(m -> {
                 m.walk(MethodCallExpr.class, mc -> {
                     if (mc.getNameAsString().equals("createSummaryData")) {
                         // remove first argument if it matches 'this.coverage' or 'coverage'
                         if (mc.getArguments().size() == 2) {
                             mc.getArguments().remove(0);
                         }
                     }
                 });
             });
        });

        save(cu, path);
    }
    
    private static void processMutationHtmlReportListenerTest(Path root) throws IOException {
        Path path = root.resolve("src/test/java/org/pitest/elements/MutationHtmlReportListenerTest.java");
        CompilationUnit cu = StaticJavaParser.parse(path);
        
        cu.getClassByName("MutationHtmlReportListenerTest").ifPresent(cls -> {
            cls.getMethodsByName("setUp").forEach(m -> {
                m.getBody().ifPresent(body -> {
                    // Remove: when(this.coverageDb.getClassInfo(anyCollection())).thenReturn(Collections.singleton(this.classInfo));
                    body.findAll(MethodCallExpr.class, mc -> mc.getNameAsString().equals("getClassInfo"))
                        .forEach(mc -> {
                             // Find the statement containing this call and remove it.
                             mc.findAncestor(com.github.javaparser.ast.stmt.Statement.class).ifPresent(stmt -> stmt.remove());
                        });
                });
            });
        });
        
        save(cu, path);
    }

    private static void processMutationTestSummaryDataTest(Path root) throws IOException {
        Path path = root.resolve("src/test/java/org/pitest/elements/models/MutationTestSummaryDataTest.java");
        CompilationUnit cu = StaticJavaParser.parse(path);

        replaceImport(cu, "org.pitest.classinfo.ClassInfo", "org.pitest.classinfo.ClassName");
        cu.addImport("java.util.UUID");

        cu.getClassByName("MutationTestSummaryDataTest").ifPresent(cls -> {
            // makeClass -> makeClassName, return ClassName
            cls.getMethodsByName("makeClass").forEach(m -> {
                m.setType("ClassName");
                m.getBody().ifPresent(body -> {
                    // return ClassName.fromString("package.Foo" + UUID.randomUUID());
                    ObjectCreationExpr newObj = new ObjectCreationExpr();
                    
                    MethodCallExpr uuidCall = new MethodCallExpr(new ClassOrInterfaceType(null, "UUID").getNameAsExpression(), "randomUUID");
                    
                    MethodCallExpr call = new MethodCallExpr(new ClassOrInterfaceType(null, "ClassName").getNameAsExpression(), "fromString");
                    // "package.Foo" + UUID.randomUUID()
                     com.github.javaparser.ast.expr.BinaryExpr binExpr = new com.github.javaparser.ast.expr.BinaryExpr(
                                     new com.github.javaparser.ast.expr.StringLiteralExpr("package.Foo"),
                                     uuidCall,
                                     com.github.javaparser.ast.expr.BinaryExpr.Operator.PLUS
                                 );
                    call.addArgument(binExpr);
                    
                    body.getStatements().clear();
                    body.addStatement(new com.github.javaparser.ast.stmt.ReturnStmt(call));
                });
            });
            
            // update usages of ClassInfo
             cls.getMethods().forEach(m -> {
                 m.getParameters().forEach(p -> {
                     if (p.getType().toString().equals("ClassInfo")) {
                         p.setType("ClassName");
                     }
                 });
                 m.getBody().ifPresent(body -> {
                     body.findAll(VariableDeclarator.class).forEach(v -> {
                         if (v.getType().toString().contains("ClassInfo")) {
                             if (v.getType().toString().equals("ClassInfo"))
                                 v.setType("ClassName");
                             else if (v.getType().toString().contains("Collection<ClassInfo>"))
                                 v.setType("Collection<ClassName>");
                         }
                     });
                 });
             });
        });

        save(cu, path);
    }

    private static void processJsonParserTest(Path root) throws IOException {
        Path path = root.resolve("src/test/java/org/pitest/elements/utils/JsonParserTest.java");
        CompilationUnit cu = StaticJavaParser.parse(path);

        replaceImport(cu, "org.pitest.classinfo.ClassInfo", "org.pitest.classinfo.ClassName");
        replaceImport(cu, "org.pitest.classinfo.MockClassInfoBuilder", null); // Remove

        // Remove MockClassInfo class
        cu.findAll(ClassOrInterfaceDeclaration.class, c -> c.getNameAsString().equals("MockClassInfo"))
          .forEach(c -> c.remove());
          
        cu.getClassByName("JsonParserTest").ifPresent(cls -> {
             // replace createPackageSummaryMap logic
             cls.getMethodsByName("createPackageSummaryMap").forEach(m -> {
                 m.getBody().ifPresent(body -> {
                     // final ClassInfo classInfo = new MockClassInfo(fileName);
                     // -> final ClassName classInfo = ClassName.fromString("package." + fileName);
                     
                     body.findAll(ObjectCreationExpr.class, oce -> oce.getType().getNameAsString().equals("MockClassInfo"))
                         .forEach(oce -> {
                             // We need to replace the variable declaration too
                             oce.findAncestor(VariableDeclarator.class).ifPresent(v -> {
                                 v.setType("ClassName");
                                 MethodCallExpr call = new MethodCallExpr(new ClassOrInterfaceType(null, "ClassName").getNameAsExpression(), "fromString");
                                 // "package." + fileName
                                 com.github.javaparser.ast.expr.BinaryExpr binExpr = new com.github.javaparser.ast.expr.BinaryExpr(
                                     new com.github.javaparser.ast.expr.StringLiteralExpr("package."),
                                     oce.getArgument(0),
                                     com.github.javaparser.ast.expr.BinaryExpr.Operator.PLUS
                                 );
                                 call.addArgument(binExpr);
                                 v.setInitializer(call);
                             });
                         });
                 });
             });
        });

        save(cu, path);
    }

    private static void replaceImport(CompilationUnit cu, String oldImport, String newImport) {
        cu.getImports().removeIf(id -> id.getNameAsString().equals(oldImport));
        if (newImport != null) {
            cu.addImport(newImport);
        }
    }
    
    private static void changeParamType(ClassOrInterfaceDeclaration cls, String methodName, String oldType, String newType) {
        cls.getMethodsByName(methodName).forEach(m -> {
            m.getParameters().forEach(p -> {
                if (p.getType().toString().equals(oldType)) {
                    p.setType(newType);
                }
            });
        });
    }
}