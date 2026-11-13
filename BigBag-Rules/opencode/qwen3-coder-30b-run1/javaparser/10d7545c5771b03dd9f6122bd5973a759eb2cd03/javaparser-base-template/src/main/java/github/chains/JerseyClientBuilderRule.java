/*
 * JavaParser transformation rule for Dropwizard JerseyClientBuilder constructor change
 * 
 * Problem: Dropwizard client dependency was updated from version 2.1.5 to 4.0.0
 * The JerseyClientBuilder constructor signature changed:
 * - Before: new JerseyClientBuilder(Environment env)
 * - After:  new JerseyClientBuilder().using(Environment env)
 * 
 * This transformation rule converts:
 *   new JerseyClientBuilder(env)
 *   .using(config.getJerseyClient())
 *   .withProvider(MultiPartFeature.class)
 *   .withProvider(JacksonJsonProvider.class)
 *   .build(getName());
 * 
 * To:
 *   new JerseyClientBuilder()
 *   .using(env)
 *   .using(config.getJerseyClient())
 *   .withProvider(MultiPartFeature.class)
 *   .withProvider(JacksonJsonProvider.class)
 *   .build(getName());
 */

package github.chains;

import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.NodeList;

/**
 * Transformation rule for JerseyClientBuilder constructor change from Dropwizard 2.1.5 to 4.0.0
 */
public class JerseyClientBuilderRule {
    
    /**
     * Visitor that transforms JerseyClientBuilder constructor calls
     */
    public static class JerseyClientBuilderTransformer extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(ObjectCreationExpr n, Void arg) {
            super.visit(n, arg);
            
            // Check if this is a JerseyClientBuilder instantiation with Environment parameter
            if (n.getType() != null && 
                n.getType().getName().getIdentifier().equals("JerseyClientBuilder")) {
                
                // Check if it has exactly one argument (the Environment)
                if (n.getArguments().size() == 1) {
                    Expression arg = n.getArguments().get(0);
                    
                    // Check if the argument is a variable name (typically "env")
                    if (arg instanceof NameExpr) {
                        NameExpr envVar = (NameExpr) arg;
                        
                        // Create the new JerseyClientBuilder().using(env) pattern
                        ObjectCreationExpr newBuilder = new ObjectCreationExpr();
                        newBuilder.setType(new ClassOrInterfaceType("JerseyClientBuilder"));
                        newBuilder.setArguments(new NodeList<>());
                        
                        // Create the using() method call with the Environment parameter
                        MethodCallExpr usingCall = new MethodCallExpr();
                        usingCall.setScope(newBuilder);
                        usingCall.setName("using");
                        usingCall.setArguments(new NodeList<>(envVar));
                        
                        // Replace the original ObjectCreationExpr with the using() call
                        n.replace(usingCall);
                    }
                }
            }
        }
    }
}