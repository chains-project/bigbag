package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.expr.Expression;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public class Main {
    public static void main(String[] args) throws Exception {
        File file = new File("/workspace/biapi/src/main/java/xdev/tableexport/export/ReportBuilder.java");
        FileInputStream in = new FileInputStream(file);
        CompilationUnit cu = StaticJavaParser.parse(in);
        in.close();

        cu.findAll(MethodCallExpr.class).forEach(mce -> {
            if (mce.getNameAsString().equals("setFontSize")) {
                if (mce.getArguments().size() == 1) {
                    Expression arg = mce.getArgument(0);
                    
                    // Check if it is Float.valueOf(...)
                    if (arg.isMethodCallExpr()) {
                        MethodCallExpr valueOfCall = arg.asMethodCallExpr();
                        if (valueOfCall.getNameAsString().equals("valueOf") && valueOfCall.getScope().isPresent() && valueOfCall.getScope().get().toString().equals("Float")) {
                             // It is Float.valueOf(...)
                             // Get the inner argument (which might be the cast or just getSize)
                             Expression inner = valueOfCall.getArgument(0);
                             
                             // If inner is cast (float) f.getSize(), strip the cast first to get f.getSize()
                             if (inner.isCastExpr()) {
                                 inner = inner.asCastExpr().getExpression();
                             }
                             
                             // Now inner should be f.getSize()
                             if (inner.isMethodCallExpr() && inner.asMethodCallExpr().getNameAsString().equals("getSize")) {
                                 // Replace Float.valueOf(...) with (float) f.getSize()
                                 CastExpr newCast = new CastExpr(PrimitiveType.floatType(), inner.clone());
                                 mce.setArgument(0, newCast);
                                 System.out.println("Applied fix: setFontSize((float) f.getSize())");
                             }
                        }
                    }
                }
            }
        });

        FileOutputStream out = new FileOutputStream(file);
        out.write(cu.toString().getBytes());
        out.close();
    }
}
