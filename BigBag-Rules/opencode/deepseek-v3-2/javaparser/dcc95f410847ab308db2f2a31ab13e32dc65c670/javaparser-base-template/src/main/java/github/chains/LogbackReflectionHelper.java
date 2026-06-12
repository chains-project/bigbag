package github.chains;

/**
 * Reflection helper for logback compatibility.
 * Uses reflection to avoid compile-time dependency on
 * org.slf4j.spi.LoggingEventAware interface (slf4j 2.0.x).
 */
public class LogbackReflectionHelper {
    
    /**
     * Invokes a method on a logback Logger using reflection.
     * 
     * @param loggerSource Either a Class<?> or String for logger name
     * @param methodName Method to invoke (setLevel, addAppender, etc.)
     * @param args Method arguments
     * @return Method return value
     */
    public static Object invoke(Object loggerSource, String methodName, Object... args) {
        try {
            // Get logger from LoggerFactory
            Class<?> loggerFactoryClass = Class.forName("org.slf4j.LoggerFactory");
            Object logger;
            
            if (loggerSource instanceof Class) {
                java.lang.reflect.Method getLoggerMethod = loggerFactoryClass.getMethod("getLogger", Class.class);
                logger = getLoggerMethod.invoke(null, loggerSource);
            } else if (loggerSource instanceof String) {
                java.lang.reflect.Method getLoggerMethod = loggerFactoryClass.getMethod("getLogger", String.class);
                logger = getLoggerMethod.invoke(null, loggerSource);
            } else {
                throw new IllegalArgumentException("loggerSource must be Class<?> or String");
            }
            
            // Get logback Logger class
            Class<?> loggerClass = Class.forName("ch.qos.logback.classic.Logger");
            
            // Find and invoke method
            Class<?>[] paramTypes = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) {
                paramTypes[i] = args[i] != null ? args[i].getClass() : Object.class;
            }
            
            java.lang.reflect.Method method = findMethod(loggerClass, methodName, paramTypes);
            if (method == null) {
                throw new RuntimeException("Method " + methodName + " not found with matching parameters");
            }
            
            return method.invoke(logger, args);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke " + methodName + " on logger", e);
        }
    }
    
    private static java.lang.reflect.Method findMethod(Class<?> clazz, String methodName, Class<?>[] paramTypes) {
        for (java.lang.reflect.Method method : clazz.getMethods()) {
            if (method.getName().equals(methodName) && 
                parametersMatch(method.getParameterTypes(), paramTypes)) {
                return method;
            }
        }
        return null;
    }
    
    private static boolean parametersMatch(Class<?>[] expected, Class<?>[] actual) {
        if (expected.length != actual.length) return false;
        for (int i = 0; i < expected.length; i++) {
            if (!expected[i].isAssignableFrom(actual[i])) {
                return false;
            }
        }
        return true;
    }
}