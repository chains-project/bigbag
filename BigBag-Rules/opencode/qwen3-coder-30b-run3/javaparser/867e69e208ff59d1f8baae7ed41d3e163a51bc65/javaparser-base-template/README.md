# Generic TFramedTransport Replacement Transformation

This transformation fixes the breaking change in Apache Thrift 0.16.0 where `TFramedTransport` was removed.

## Problem
In Apache Thrift 0.12.0, `TFramedTransport` was available but was removed in 0.16.0. Projects using this class will fail to compile with newer versions of libthrift.

## Solution
This transformation replaces all occurrences of:
1. `TFramedTransport` imports with `TIOStreamTransport` where appropriate
2. `TFramedTransport` variable declarations with `TTransport` (base class)
3. Constructor calls to `TFramedTransport` with `TIOStreamTransport`

## Usage
1. Compile this project:
   ```
   mvn compile
   ```

2. Run the transformation:
   ```
   java -cp target/classes:$(find ~/.m2/repository -name "*.jar" | tr '\n' ':') github.chains.Main <source_directory>
   ```

## How it Works
The transformation uses JavaParser to:
- Parse Java source files
- Locate imports of `TFramedTransport` and remove them
- Locate variable declarations of type `TFramedTransport` and change them to `TTransport`
- Locate `TFramedTransport` constructor calls and change them to `TIOStreamTransport`

This approach is generic and can be applied to any Maven project affected by this breaking change.