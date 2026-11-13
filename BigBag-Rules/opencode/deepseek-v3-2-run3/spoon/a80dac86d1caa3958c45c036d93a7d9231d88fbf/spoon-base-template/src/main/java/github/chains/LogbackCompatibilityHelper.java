package github.chains;

import java.lang.reflect.Method;

/**
 * Helper class for logback compatibility workaround.
 * Provides reflection-based methods to avoid compile-time dependencies
 * on logback classes that require SLF4J 2.x.
 */
public class LogbackCompatibilityHelper {
    
    /**
     * Set logger level using reflection to avoid compile-time dependency
     * on ch.qos.logback.classic.Logger which implements SLF4J 2.x interfaces.
     */
    public static void setLoggerLevelUsingReflection(Object logger, Object level) {
        if (logger == null || level == null) {
            return;
        }
        
        try {
            Class<?> loggerClass = logger.getClass();
            Class<?> levelClass = level.getClass();
            
            // Find the setLevel method
            Method setLevelMethod = loggerClass.getMethod("setLevel", levelClass);
            setLevelMethod.invoke(logger, level);
            
        } catch (Exception e) {
            // Log error but don't fail - this is a compatibility workaround
            System.err.println("Failed to set logger level via reflection: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Add appender to logger using reflection to avoid compile-time dependency
     * on ch.qos.logback.classic.Logger which implements SLF4J 2.x interfaces.
     */
    public static void addAppenderUsingReflection(Object logger, Object appender) {
        if (logger == null || appender == null) {
            return;
        }
        
        try {
            Class<?> loggerClass = logger.getClass();
            Class<?> appenderClass = appender.getClass();
            
            // Find the addAppender method
            Method addAppenderMethod = loggerClass.getMethod("addAppender", appenderClass);
            addAppenderMethod.invoke(logger, appender);
            
        } catch (Exception e) {
            // Log error but don't fail - this is a compatibility workaround
            System.err.println("Failed to add appender via reflection: " + e.getMessage());
            e.printStackTrace();
        }
    }
}