# tinspin-indexes API Migration Tool

This tool helps migrate code that uses the tinspin-indexes library when upgrading between versions with breaking API changes.

## Usage

```bash
java -jar tinspin-migration-tool.jar <source_directory>
```

## Supported Fixes

This tool identifies and helps fix common breaking changes in the tinspin-indexes API:

### 1. PointIndex vs PointIndexMM Method Signature Changes

**For `remove()` method:**
- Old (PointIndex): `T remove(double[] point)`
- New (PointIndexMM): `boolean remove(double[] point, T value)`

**For `update()` method:**
- Old (PointIndex): `T update(double[] oldPoint, double[] newPoint)`
- New (PointIndexMM): `boolean update(double[] oldPoint, double[] newPoint, T value)`

### 2. Query Method Return Type Changes

**For `query1NN()` method:**
- Old: Returns the value directly
- New: Returns a `PointEntryDist<T>` object that requires `.value()` call to get the value

**For `queryKNN()` method:**
- Old: Returns `QueryIteratorKNN<T>`
- New: Returns `QueryIteratorKNN<PointEntryDist<T>>`

## How It Works

The tool uses JavaParser to:
1. Scan all Java files in the specified directory
2. Identify method calls on tinspin index objects (identified by variable names containing "tree", "index", "PointIndex", or "PointIndexMM")
3. Apply transformations based on the method name and signature
4. Add comments to indicate where manual intervention is needed

## Limitations

- This tool cannot automatically determine the exact type of index (PointIndex vs PointIndexMM) being used
- It cannot determine the generic type parameter T
- It cannot automatically update return type handling in calling code
- It adds comments to indicate where manual fixes are needed

## Manual Fixes Required

After running this tool, you will need to manually:

1. Identify whether each index variable is PointIndex or PointIndexMM
2. Update method calls based on the correct type
3. Add `.value()` calls where needed for query methods
4. Update return type handling in calling code

## Example Transformations

### Before (PointIndex)
```java
PointIndex<MyType> index = KDTree.create(2);
MyType removed = index.remove(point);
MyType updated = index.update(oldPoint, newPoint);
MyType result = index.query1NN(queryPoint);
```

### After (PointIndexMM)
```java
PointIndexMM<MyType> index = KDTree.create(2);
boolean removed = index.remove(point, myValue);
boolean updated = index.update(oldPoint, newPoint, myValue);
PointEntryDist<MyType> result = index.query1NN(queryPoint);
MyType actualResult = result.value();
```

## Running the Tool

1. Compile the tool:
   ```bash
   cd /workspace/javaparser-base-template
   mvn package
   ```

2. Run the tool on your source directory:
   ```bash
   java -jar target/tinspin-migration-tool.jar /path/to/your/source
   ```

3. Review the changes and make manual fixes as indicated by the comments

## Notes

This tool is designed to be used as a migration aid. It cannot automate all changes due to the complexity of type inference in Java. Always test your code thoroughly after running this tool and making manual changes.