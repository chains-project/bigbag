/*
 * Example demonstrating how the generic Spoon transformation would work
 * This shows the kind of patterns it would detect and fix
 */

// Example 1: Constructor call that might have changed signatures
public class ExampleObjectFactory extends ObjectFactory {
    
    // This kind of constructor call might be problematic
    public Object buildBean(Class clazz, Map<String, Object> extraContext) {
        // Old xwork2 API call that might have changed
        ActionConfig config = new ActionConfig("action", "namespace", "className");
        return super.buildBean(clazz, extraContext);
    }
}

// Example 2: Method call that might have changed signature
public class ExampleInterceptor implements Interceptor {
    
    public String intercept(ActionInvocation invocation) throws Exception {
        // This method call might have changed in newer xwork2 versions
        return invocation.invoke();
    }
}

// Example 3: Builder pattern that might have changed
public class ConfigBuilderExample {
    
    public void example() {
        // This kind of builder might have changed in newer API versions
        ActionConfig.Builder builder = new ActionConfig.Builder("name", "namespace", "className");
    }
}