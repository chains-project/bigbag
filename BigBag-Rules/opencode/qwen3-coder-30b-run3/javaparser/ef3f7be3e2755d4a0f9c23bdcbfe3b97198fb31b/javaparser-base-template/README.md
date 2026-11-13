# Generic JavaParser Transformation for tinspin-indexes Breaking Changes

This project provides a generic JavaParser transformation to fix breaking changes in the tinspin-indexes library when upgrading from version 1.x to 2.0.0.

## Problem

In tinspin-indexes 2.0.0, the `queryKnn` methods now require a distance parameter:

**Old API:**
```java
public abstract org.tinspin.index.Index$PointIteratorKnn queryKnn(double[] p0, int p1)
public abstract org.tinspin.index.Index$BoxIteratorKnn queryKnn(double[] p0, int p1)
```

**New API:**
```java
public abstract org.tinspin.index.Index$PointIteratorKnn queryKnn(double[] p0, int p1, org.tinspin.index.PointDistance p2)
public abstract org.tinspin.index.Index$BoxIteratorKnn queryKnn(double[] p0, int p1, org.tinspin.index.BoxDistance p2)
```

## Solution

This JavaParser-based transformation identifies method calls with the old `queryKnn` signature and modifies them to include the required distance parameter.

## Usage

```bash
# To run the transformation on a project
mvn compile exec:java -Dexec.mainClass="github.chains.Main"
```

## Implementation Details

The transformation:
1. Identifies `queryKnn` method calls with 2 parameters (`double[]`, `int`)
2. Inserts appropriate default distance parameters (`PointDistance.L2` or `BoxDistance.EDGE`) 
3. Works generically across any project using tinspin-indexes

## Files Modified

- `src/main/java/github/chains/Main.java`: Contains the transformation logic

## Configuration

The transformation can be configured to use different default distance metrics by modifying the `QueryKnnTransformation` class.