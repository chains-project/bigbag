package github.chains;

import org.slf4j.LoggerFactory;

/**
 * Utility class to handle compatibility between logback-classic 1.4.4 and SLF4J 1.7.36.
 * 
 * Breaking Change: In logback-classic 1.4.4, ch.qos.logback.classic.Logger implements
 * org.slf4j.spi.LoggingEventAware, which doesn't exist in SLF4J 1.7.36.
 * 
 * This causes compilation errors when casting LoggerFactory.getLogger() to
 * ch.qos.logback.classic.Logger.
 * 
 * Usage: Replace (Logger) LoggerFactory.getLogger(X.class) with
 * LogbackCompatibility.getLogger(X.class)
 * 
 * Note: This class uses reflection to avoid compile-time dependencies on logback.
 */
public class LogbackCompatibility {
    
    /**
     * Gets a logback Logger instance in a way compatible with logback-classic 1.4.4
     * and SLF4J 1.7.36. Uses reflection to avoid compile-time dependency issues.
     * 
     * @param clazz the class for which to get the logger
     * @return a logger instance (actual type depends on logging implementation)
     * @throws RuntimeException if compatibility cannot be achieved
     */
    public static Object getLogger(Class<?> clazz) {
        try {
            // Try to get via LoggerContext first (preferred approach for logback)
            Object loggerFactory = LoggerFactory.getILoggerFactory();
            Class<?> loggerContextClass = Class.forName("ch.qos.logback.classic.LoggerContext");
            if (loggerContextClass.isInstance(loggerFactory)) {
                return loggerContextClass.getMethod("getLogger", Class.class)
                    .invoke(loggerFactory, clazz);
            }
        } catch (ClassNotFoundException e) {
            // logback-classic not in classpath, use SLF4J directly
            return LoggerFactory.getLogger(clazz);
        } catch (NoClassDefFoundError e) {
            // This happens if org.slf4j.spi.LoggingEventAware is missing (SLF4J 1.7.36)
            handleCompatibilityIssue(e);
        } catch (Exception e) {
            // Other exceptions, try fallback
            handleCompatibilityIssue(e);
        }
        
        // Fallback: use SLF4J directly (may fail at runtime if casting to Logger is needed)
        return LoggerFactory.getLogger(clazz);
    }
    
    /**
     * Attempts to cast the logger to ch.qos.logback.classic.Logger.
     * Use with caution - may fail if LoggingEventAware interface is missing.
     */
    @SuppressWarnings("unchecked")
    public static <T> T castToLogger(Object logger) {
        try {
            Class<T> loggerClass = (Class<T>) Class.forName("ch.qos.logback.classic.Logger");
            return loggerClass.cast(logger);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("ch.qos.logback.classic.Logger not found in classpath", e);
        } catch (ClassCastException e) {
            throw new RuntimeException(
                "Failed to cast to logback Logger due to compatibility issue. " +
                "logback-classic 1.4.4 implements org.slf4j.spi.LoggingEventAware " +
                "which doesn't exist in SLF4J 1.7.36.", e);
        }
    }
    
    /**
     * Sets the log level using reflection.
     * 
     * @param logger the logger object (should be a logback Logger instance)
     * @param level the level to set (can be any object, will be passed as-is)
     * @throws Exception if reflection fails
     */
    public static void setLevel(Object logger, Object level) throws Exception {
        logger.getClass().getMethod("setLevel", level.getClass()).invoke(logger, level);
    }
    
    /**
     * Adds an appender using reflection.
     * 
     * @param logger the logger object (should be a logback Logger instance)
     * @param appender the appender to add (can be any object, will be passed as-is)
     * @throws Exception if reflection fails
     */
    public static void addAppender(Object logger, Object appender) throws Exception {
        logger.getClass().getMethod("addAppender", appender.getClass()).invoke(logger, appender);
    }
    
    private static void handleCompatibilityIssue(Throwable t) {
        System.err.println("WARNING: Logback compatibility issue detected.");
        System.err.println("logback-classic 1.4.4 requires SLF4J 2.x, but SLF4J 1.7.36 may be being used.");
        System.err.println("The interface org.slf4j.spi.LoggingEventAware may be missing.");
        System.err.println("Consider updating your SLF4J dependency to version 2.x.");
    }
    
    /**
     * Provides guidance for fixing the breaking change.
     */
    public static String getTransformationGuide() {
        return """
==================================================================
LOGBACK-CLASSIC 1.4.4 BREAKING CHANGE FIX
==================================================================
Issue: ch.qos.logback.classic.Logger now implements 
       org.slf4j.spi.LoggingEventAware (SLF4J 2.x interface)
       which doesn't exist in SLF4J 1.7.36.

Symptoms:
- Compilation error: "cannot access org.slf4j.spi.LoggingEventAware"
- Occurs when casting: (Logger) LoggerFactory.getLogger(X.class)

Solutions (choose one):
1. Update SLF4J to 2.x (recommended for new projects)
2. Downgrade logback-classic to 1.3.x or earlier
3. Use LogbackCompatibility utility class (this file)

Transformation pattern:
OLD: Logger logger = (Logger) LoggerFactory.getLogger(X.class);
NEW: Object logger = LogbackCompatibility.getLogger(X.class);
     // Use reflection for logback-specific methods:
     LogbackCompatibility.setLevel(logger, Level.INFO);
     LogbackCompatibility.addAppender(logger, appender);

Alternative: Update test code to avoid logback-specific APIs
==================================================================""";
    }
}