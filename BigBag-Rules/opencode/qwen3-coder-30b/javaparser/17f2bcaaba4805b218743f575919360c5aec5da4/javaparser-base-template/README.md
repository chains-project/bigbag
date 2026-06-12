# Tinspin Indexes API Migration Tool

This tool helps migrate code using the tinspin-indexes library when upgrading between versions with breaking API changes.

## What it does

The tool automatically identifies tinspin method calls in Java code and adds helpful comments to guide developers through the necessary API changes. Specifically, it addresses the following breaking changes:

1. **Method signature changes in `remove()` and `update()` methods**
   - PointIndex.remove(double[]) → returns T
   - PointIndexMM.remove(double[], T) → returns boolean
   - PointIndex.update(double[], double[]) → returns T  
   - PointIndexMM.update(double[], double[], T) → returns boolean

2. **Return type changes in query methods**
   - query1NN() now returns PointEntryDist<T> instead of T directly
   - queryKNN() now returns QueryIteratorKNN<PointEntryDist<T>> instead of QueryIteratorKNN<T>

## Usage

```bash
java -jar tinspin-migration-tool-1.0.0.jar /path/to/your/java/source/directory
```

The tool will process all Java files in the specified directory and add comments to guide you through the necessary API changes.

## Example

Before:
```java
String removed = tree.remove(new double[]{1.0, 2.0});
PointEntryDist<String> entry = tree.query1NN(new double[]{1.0, 2.0});
String value = entry.value();
```

After:
```java
// TODO: Check if this is PointIndexMM.remove() (returns boolean) vs PointIndex.remove() (returns T). If PointIndexMM, change to: boolean success = tree.remove(...)
String removed = tree.remove(new double[]{1.0, 2.0});
// TODO: query1NN() now returns PointEntryDist<T>, use .value() to get the actual value: tree.query1NN(...).value()
PointEntryDist<String> entry = tree.query1NN(new double[]{1.0, 2.0});
String value = entry.value();
```

## Supported tinspin classes

- `org.tinspin.index.kdtree.KDTree`
- `org.tinspin.index.covertree.CoverTree`
- `org.tinspin.index.PointIndex`
- `org.tinspin.index.PointEntryDist`
- `org.tinspin.index.PointIndexMM`