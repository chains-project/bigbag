package github.chains;

import java.lang.reflect.Method;

/**
 * Compatibility helper for Logback Logger casts that fail due to 
 * missing LoggingEventAware interface.
 * 
 * This class provides a workaround for the breaking change in
 * logback-classic 1.4.5 where ch.qos.logback.classic.Logger
 * now implements org.slf4j.spi.LoggingEventAware, which may
 * not be available in older slf4j-api versions.
 */
public class LogbackCompat {
    
    /**
     * Sets the log level using reflection to avoid compile-time
     * dependency on ch.qos.logback.classic.Logger which now
     * implements org.slf4j.spi.LoggingEventAware.
     * 
     * @param logger the logger instance from LoggerFactory.getLogger()
     * @param level the log level to set
     */
    public static void setLogLevel(Object logger, Object level) {
        try {
            // Use reflection to avoid compile-time reference to Logger class
            Class<?> loggerClass = Class.forName("ch.qos.logback.classic.Logger");
            Class<?> levelClass = Class.forName("ch.qos.logback.classic.Level");
            
            // Check if the logger is actually a Logback Logger
            if (loggerClass.isInstance(logger)) {
                Method setLevelMethod = loggerClass.getMethod("setLevel", levelClass);
                setLevelMethod.invoke(logger, level);
            } else {
                // Not a Logback logger - silently ignore
                System.err.println("Warning: Not a Logback logger, cannot set level");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to set log level using reflection", e);
        }
    }
    
    /**
     * Alternative method that accepts logger name as String
     */
    public static void setLogLevel(String loggerName, Object level) {
        Object logger = org.slf4j.LoggerFactory.getLogger(loggerName);
        setLogLevel(logger, level);
    }
}