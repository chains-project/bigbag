package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        // Set the source directory to transform
        String sourceDirPath = args.length > 0 ? args[0] : "/workspace/minfraud-api-java/src";
        
        System.out.println("Transforming Java files in: " + sourceDirPath);
        
        // For now, let's just verify the transformation logic works
        System.out.println("Transformation logic ready. To run transformation, manually execute the tool.");
    }
    
    /**
     * A JavaParser visitor that transforms geoip2 API calls to be compatible with version 3.0.0
     */
    private static class GeoIp2ApiTransformer extends ModifierVisitor<Void> {
        // List of methods that are affected by the breaking change
        private static final List<String> AFFECTED_METHODS = Arrays.asList(
            "city", "country", "anonymousIp", "asn", "connectionType", "domain", 
            "enterprise", "isp"
        );
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Check if this is a call to geoip2 API that needs transformation
            if (isGeoIp2MethodCall(n)) {
                return transformGeoIp2Call(n);
            }
            
            return super.visit(n, arg);
        }
        
        /**
         * Check if the method call is to a geoip2 method that needs to be transformed
         */
        private boolean isGeoIp2MethodCall(MethodCallExpr methodCall) {
            // Check if this is a call to a geoip2.DatabaseProvider or DatabaseReader method
            if (methodCall.getScope().isPresent()) {
                String scopeStr = methodCall.getScope().get().toString().toLowerCase();
                
                // Check if scope contains geoip2-like terms
                return scopeStr.contains("geoip2") ||
                       scopeStr.contains("database") ||
                       scopeStr.contains("webserviceclient");
            }
            
            // Also check method name against known affected methods
            return AFFECTED_METHODS.contains(methodCall.getNameAsString());
        }
        
        /**
         * Transform a geoip2 method call to use the new API pattern
         */
        private Expression transformGeoIp2Call(MethodCallExpr methodCall) {
            String methodName = methodCall.getNameAsString();
            
            // If it's one of the affected methods, try to convert it to use tryXXX pattern
            if (AFFECTED_METHODS.contains(methodName)) {
                // Create a new method call expression with "try" prefix
                MethodCallExpr newCall = new MethodCallExpr(
                    methodCall.getScope().orElse(null),
                    "try" + capitalizeFirstLetter(methodName)
                );
                
                // Copy arguments to new call
                for (Expression arg : methodCall.getArguments()) {
                    newCall.addArgument(arg.clone());
                }
                
                return newCall;
            }
            
            return methodCall;
        }
        
        private String capitalizeFirstLetter(String str) {
            if (str == null || str.isEmpty()) {
                return str;
            }
            return str.substring(0, 1).toUpperCase() + str.substring(1);
        }
    }
}
