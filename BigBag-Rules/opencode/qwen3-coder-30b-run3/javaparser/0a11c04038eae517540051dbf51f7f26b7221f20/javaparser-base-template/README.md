# SnakeYAML 1.x to 2.0 API Transformation Tool

This is a generic JavaParser-based transformation tool that fixes breaking changes in SnakeYAML API when upgrading from version 1.x to 2.0.

## Problem

SnakeYAML 2.0 introduced a breaking change to the `Constructor` class constructor:
- **SnakeYAML 1.x**: `new Constructor(Configuration.class)`
- **SnakeYAML 2.0**: `new Constructor(Configuration.class, new LoaderOptions())`

## Solution

This tool automatically detects and fixes all instances of the old API pattern across all Java source files in a project.

## Usage

```bash
# Compile the tool
mvn compile

# Run the transformation on a project
java -cp target/classes github.chains.Main /path/to/your/project/src
```

## How It Works

The tool scans all `.java` files and looks for patterns like:
```java
Constructor constructor = new Constructor(Configuration.class);
```

And transforms them to:
```java
Constructor constructor = new Constructor(Configuration.class, new LoaderOptions());
```

## Requirements

- Java 11+
- Maven
- SnakeYAML 2.0+ dependency in target projects

## License

MIT