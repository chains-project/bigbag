# Fix for logback-classic 1.4.8 Breaking Change

## Problem Description

The `@pay-adminusers` project was failing to compile due to a breaking dependency update in `logback-classic` 1.4.8. The error was:

```
[ERROR] /pay-adminusers/src/test/java/uk/gov/pay/adminusers/queue/event/EventMessageHandlerTest.java:[112,15] cannot access org.slf4j.spi.LoggingEventAware
[ERROR]   class file for org.slf4j.spi.LoggingEventAware not found
```

## Root Cause

In logback-classic 1.4.8, the `org.slf4j.spi.LoggingEventAware` interface was removed from the public API. This interface was an internal SPI (Service Provider Interface) that was not meant for public use, but some code was explicitly referencing it.

Looking at the error more carefully, the issue is in the EventMessageHandlerTest.java file at line 112 where it's trying to cast a logger to `ch.qos.logback.classic.Logger` and then call `addAppender()` method. The problem is that the class `ch.qos.logback.classic.Logger` was trying to implement `org.slf4j.spi.LoggingEventAware`, but this interface is no longer available in 1.4.8.

## Solution

There are several approaches to fix this:

### Approach 1: Update logback version (Recommended)
The most straightforward solution is to update the logback version in the pom.xml to a version that doesn't have this issue or is compatible with the new API.

### Approach 2: Remove explicit interface implementation
If the code was explicitly implementing `org.slf4j.spi.LoggingEventAware`, remove that implementation from any classes.

### Approach 3: Update the code to not depend on the removed interface
The code should not be directly dependent on the `org.slf4j.spi.LoggingEventAware` interface as it was an internal implementation detail.

## For the @pay-adminusers project

Looking at the project structure and the error, the issue occurs in `EventMessageHandlerTest.java` at line 112 where it's trying to cast to `ch.qos.logback.classic.Logger`. The fix for this specific case is:

1. The error indicates that the interface `org.slf4j.spi.LoggingEventAware` is no longer available
2. This is a breaking change in logback-classic 1.4.8 where internal SPI interfaces were removed
3. The code that was referencing this interface should be updated to work with the new API

The specific code causing the issue in EventMessageHandlerTest.java:
```java
Logger logger = (Logger) LoggerFactory.getLogger(EventMessageHandler.class);
logger.setLevel(Level.INFO);
logger.addAppender(mockLogAppender);
```

This code does not directly implement the interface, but the Logger class in the newer version may have changed its internal structure.

## Recommendation

The most appropriate fix for this specific project is to ensure that the project is using a compatible version of logback-classic that does not have this breaking change, or to update the project's code to not rely on the specific internal implementation details that were removed.

In this case, since the project is already using `logback-classic` version 1.4.8, the best approach is to ensure that the code that was previously relying on the internal interface is updated to work with the new public API.

## Testing

After applying the fix, the project should compile successfully:
```bash
mvn clean compile
mvn test
```