package org.hamcrest;

import org.hamcrest.beans.HasProperty;
import org.hamcrest.beans.HasPropertyWithValue;
import org.hamcrest.beans.SamePropertyValuesAs;
import org.hamcrest.collection.HasItemInArray;
import org.hamcrest.collection.IsArray;
import org.hamcrest.collection.IsArrayContainingInAnyOrder;
import org.hamcrest.collection.IsArrayContainingInOrder;
import org.hamcrest.collection.IsArrayWithSize;
import org.hamcrest.collection.IsCollectionWithSize;
import org.hamcrest.collection.IsEmptyCollection;
import org.hamcrest.collection.IsEmptyIterable;
import org.hamcrest.collection.IsIn;
import org.hamcrest.collection.IsIterableContainingInAnyOrder;
import org.hamcrest.collection.IsIterableContainingInOrder;
import org.hamcrest.collection.IsIterableContainingInRelativeOrder;
import org.hamcrest.collection.IsIterableWithSize;
import org.hamcrest.collection.IsMapContaining;
import org.hamcrest.core.AllOf;
import org.hamcrest.core.AnyOf;
import org.hamcrest.core.DescribedAs;
import org.hamcrest.core.Is;
import org.hamcrest.core.IsEqual;
import org.hamcrest.core.IsInstanceOf;
import org.hamcrest.core.IsNot;
import org.hamcrest.core.IsNull;
import org.hamcrest.core.IsSame;
import org.hamcrest.core.StringContains;
import org.hamcrest.core.StringEndsWith;
import org.hamcrest.core.StringStartsWith;
import org.hamcrest.number.BigDecimalCloseTo;
import org.hamcrest.number.IsCloseTo;
import org.hamcrest.number.IsNaN;
import org.hamcrest.number.OrderingComparison;
import org.hamcrest.object.HasToString;
import org.hamcrest.text.CharSequenceLength;
import org.hamcrest.text.IsBlankString;
import org.hamcrest.text.IsEmptyString;
import org.hamcrest.text.IsEqualCompressingWhiteSpace;
import org.hamcrest.text.IsEqualIgnoringCase;
import org.hamcrest.text.MatchesPattern;
import org.hamcrest.text.StringContainsInOrder;
import org.hamcrest.xml.HasXPath;

public final class Matchers {
    private Matchers() {
        throw new UnsupportedOperationException();
    }

    public static <T> Matcher<T> allOf(final Iterable<Matcher<? super T>> matchers) { return AllOf.allOf(matchers); }
    @SafeVarargs public static <T> Matcher<T> allOf(final Matcher<? super T>... matchers) { return AllOf.allOf(matchers); }
    @SafeVarargs public static <T> AnyOf<T> anyOf(final Matcher<? super T>... matchers) { return AnyOf.anyOf(matchers); }
    public static <T> AnyOf<T> anyOf(final Iterable<Matcher<? super T>> matchers) { return AnyOf.anyOf(matchers); }
    public static <LHS> org.hamcrest.core.CombinableMatcher.CombinableBothMatcher<LHS> both(final Matcher<? super LHS> matcher) { return org.hamcrest.core.CombinableMatcher.both(matcher); }
    public static <LHS> org.hamcrest.core.CombinableMatcher.CombinableEitherMatcher<LHS> either(final Matcher<? super LHS> matcher) { return org.hamcrest.core.CombinableMatcher.either(matcher); }
    public static <T> Matcher<T> describedAs(final String description, final Matcher<T> matcher, final Object... values) { return DescribedAs.describedAs(description, matcher, values); }
    public static <U> Matcher<Iterable<? extends U>> everyItem(final Matcher<U> matcher) { return CoreMatchers.everyItem(matcher); }
    public static <T> Matcher<T> is(final Matcher<T> matcher) { return Is.is(matcher); }
    public static <T> Matcher<T> is(final T value) { return Is.is(value); }
    public static <T> Matcher<T> isA(final Class<?> type) { return Is.isA(type); }
    public static Matcher<Object> anything() { return CoreMatchers.anything(); }
    public static Matcher<Object> anything(final String description) { return CoreMatchers.anything(description); }
    public static <T> Matcher<Iterable<? super T>> hasItem(final Matcher<? super T> itemMatcher) { return CoreMatchers.hasItem(itemMatcher); }
    public static <T> Matcher<Iterable<? super T>> hasItem(final T item) { return CoreMatchers.hasItem(item); }
    @SafeVarargs public static <T> Matcher<Iterable<T>> hasItems(final Matcher<? super T>... itemMatchers) { return CoreMatchers.hasItems(itemMatchers); }
    @SafeVarargs public static <T> Matcher<Iterable<T>> hasItems(final T... items) { return CoreMatchers.hasItems(items); }
    public static <T> Matcher<T> equalTo(final T operand) { return IsEqual.equalTo(operand); }
    public static Matcher<Object> equalToObject(final Object operand) { return IsEqual.equalToObject(operand); }
    public static <T> Matcher<T> any(final Class<T> type) { return IsInstanceOf.any(type); }
    public static <T> Matcher<T> instanceOf(final Class<?> type) { return IsInstanceOf.instanceOf(type); }
    public static <T> Matcher<T> not(final Matcher<T> matcher) { return IsNot.not(matcher); }
    public static <T> Matcher<T> not(final T value) { return IsNot.not(value); }
    public static Matcher<Object> notNullValue() { return IsNull.notNullValue(); }
    public static <T> Matcher<T> notNullValue(final Class<T> type) { return IsNull.notNullValue(type); }
    public static Matcher<Object> nullValue() { return IsNull.nullValue(); }
    public static <T> Matcher<T> nullValue(final Class<T> type) { return IsNull.nullValue(type); }
    public static <T> Matcher<T> sameInstance(final T value) { return IsSame.sameInstance(value); }
    public static <T> Matcher<T> theInstance(final T value) { return IsSame.theInstance(value); }
    public static Matcher<String> containsString(final String substring) { return StringContains.containsString(substring); }
    public static Matcher<String> containsStringIgnoringCase(final String substring) { return StringContains.containsStringIgnoringCase(substring); }
    public static Matcher<String> startsWith(final String prefix) { return StringStartsWith.startsWith(prefix); }
    public static Matcher<String> startsWithIgnoringCase(final String prefix) { return StringStartsWith.startsWithIgnoringCase(prefix); }
    public static Matcher<String> endsWith(final String suffix) { return StringEndsWith.endsWith(suffix); }
    public static Matcher<String> endsWithIgnoringCase(final String suffix) { return StringEndsWith.endsWithIgnoringCase(suffix); }
    public static Matcher<String> matchesPattern(final java.util.regex.Pattern pattern) { return MatchesPattern.matchesPattern(pattern); }
    public static Matcher<String> matchesPattern(final String pattern) { return MatchesPattern.matchesPattern(pattern); }
    public static Matcher<CharSequence> hasLength(final int length) { return CharSequenceLength.hasLength(length); }
    public static <T> Matcher<T> hasToString(final Matcher<? super String> matcher) { return HasToString.hasToString(matcher); }
    public static <T> Matcher<T> hasToString(final String value) { return HasToString.hasToString(value); }
    public static <T> Matcher<T> hasProperty(final String propertyName) { return HasProperty.hasProperty(propertyName); }
    public static <T> Matcher<T> hasProperty(final String propertyName, final Matcher<?> valueMatcher) { return HasPropertyWithValue.hasProperty(propertyName, valueMatcher); }
    public static <B> Matcher<B> samePropertyValuesAs(final B bean, final String... ignoredProperties) { return SamePropertyValuesAs.samePropertyValuesAs(bean, ignoredProperties); }
    public static <K, V> Matcher<java.util.Map<? extends K, ? extends V>> hasEntry(final Matcher<? super K> keyMatcher, final Matcher<? super V> valueMatcher) { return IsMapContaining.hasEntry(keyMatcher, valueMatcher); }
    public static <K, V> Matcher<java.util.Map<? extends K, ? extends V>> hasEntry(final K key, final V value) { return IsMapContaining.hasEntry(key, value); }
    public static <K> Matcher<java.util.Map<? extends K, ?>> hasKey(final Matcher<? super K> keyMatcher) { return IsMapContaining.hasKey(keyMatcher); }
    public static <K> Matcher<java.util.Map<? extends K, ?>> hasKey(final K key) { return IsMapContaining.hasKey(key); }
    public static <V> Matcher<java.util.Map<?, ? extends V>> hasValue(final Matcher<? super V> valueMatcher) { return IsMapContaining.hasValue(valueMatcher); }
    public static <V> Matcher<java.util.Map<?, ? extends V>> hasValue(final V value) { return IsMapContaining.hasValue(value); }
    public static <E> Matcher<java.util.Collection<? extends E>> hasSize(final Matcher<? super Integer> size) { return IsCollectionWithSize.hasSize(size); }
    public static <E> Matcher<java.util.Collection<? extends E>> hasSize(final int size) { return IsCollectionWithSize.hasSize(size); }
    public static <E> Matcher<Iterable<E>> iterableWithSize(final Matcher<? super Integer> size) { return IsIterableWithSize.iterableWithSize(size); }
    public static <E> Matcher<Iterable<E>> iterableWithSize(final int size) { return IsIterableWithSize.iterableWithSize(size); }
    public static <E> Matcher<java.util.Collection<? extends E>> empty() { return IsEmptyCollection.empty(); }
    public static <E> Matcher<java.util.Collection<E>> emptyCollectionOf(final Class<E> type) { return IsEmptyCollection.emptyCollectionOf(type); }
    public static <E> Matcher<Iterable<? extends E>> emptyIterable() { return IsEmptyIterable.emptyIterable(); }
    public static <E> Matcher<Iterable<E>> emptyIterableOf(final Class<E> type) { return IsEmptyIterable.emptyIterableOf(type); }
    @SafeVarargs public static <E> Matcher<Iterable<? extends E>> contains(final E... items) { return IsIterableContainingInOrder.contains(items); }
    @SafeVarargs public static <E> Matcher<Iterable<? extends E>> contains(final Matcher<? super E>... matchers) { return IsIterableContainingInOrder.contains(matchers); }
    public static <E> Matcher<Iterable<? extends E>> contains(final java.util.List<Matcher<? super E>> matchers) { return IsIterableContainingInOrder.contains(matchers); }
    @SafeVarargs public static <T> Matcher<Iterable<? extends T>> containsInAnyOrder(final Matcher<? super T>... matchers) { return IsIterableContainingInAnyOrder.containsInAnyOrder(matchers); }
    @SafeVarargs public static <T> Matcher<Iterable<? extends T>> containsInAnyOrder(final T... items) { return IsIterableContainingInAnyOrder.containsInAnyOrder(items); }
    public static <T> Matcher<Iterable<? extends T>> containsInAnyOrder(final java.util.Collection<Matcher<? super T>> matchers) { return IsIterableContainingInAnyOrder.containsInAnyOrder(matchers); }
    @SafeVarargs public static <E> Matcher<Iterable<? extends E>> containsInRelativeOrder(final E... items) { return IsIterableContainingInRelativeOrder.containsInRelativeOrder(items); }
    @SafeVarargs public static <E> Matcher<Iterable<? extends E>> containsInRelativeOrder(final Matcher<? super E>... matchers) { return IsIterableContainingInRelativeOrder.containsInRelativeOrder(matchers); }
    public static <E> Matcher<Iterable<? extends E>> containsInRelativeOrder(final java.util.List<Matcher<? super E>> matchers) { return IsIterableContainingInRelativeOrder.containsInRelativeOrder(matchers); }
    public static <T> Matcher<T> in(final java.util.Collection<T> collection) { return IsIn.in(collection); }
    public static <T> Matcher<T> in(final T[] items) { return IsIn.in(items); }
    public static <T> Matcher<T> isIn(final java.util.Collection<T> collection) { return IsIn.isIn(collection); }
    public static <T> Matcher<T> isIn(final T[] items) { return IsIn.isIn(items); }
    @SafeVarargs public static <T> Matcher<T> isOneOf(final T... items) { return IsIn.isOneOf(items); }
    @SafeVarargs public static <T> Matcher<T> oneOf(final T... items) { return IsIn.oneOf(items); }
    public static Matcher<Double> closeTo(final double operand, final double error) { return IsCloseTo.closeTo(operand, error); }
    public static Matcher<java.math.BigDecimal> closeTo(final java.math.BigDecimal operand, final java.math.BigDecimal error) { return BigDecimalCloseTo.closeTo(operand, error); }
    public static Matcher<Double> notANumber() { return IsNaN.notANumber(); }
    public static <T extends Comparable<T>> Matcher<T> comparesEqualTo(final T value) { return OrderingComparison.comparesEqualTo(value); }
    public static <T extends Comparable<T>> Matcher<T> greaterThan(final T value) { return OrderingComparison.greaterThan(value); }
    public static <T extends Comparable<T>> Matcher<T> greaterThanOrEqualTo(final T value) { return OrderingComparison.greaterThanOrEqualTo(value); }
    public static <T extends Comparable<T>> Matcher<T> lessThan(final T value) { return OrderingComparison.lessThan(value); }
    public static <T extends Comparable<T>> Matcher<T> lessThanOrEqualTo(final T value) { return OrderingComparison.lessThanOrEqualTo(value); }
    public static Matcher<String> equalToIgnoringCase(final String value) { return IsEqualIgnoringCase.equalToIgnoringCase(value); }
    public static Matcher<String> equalToIgnoringWhiteSpace(final String value) { return IsEqualCompressingWhiteSpace.equalToIgnoringWhiteSpace(value); }
    public static Matcher<String> equalToCompressingWhiteSpace(final String value) { return IsEqualCompressingWhiteSpace.equalToCompressingWhiteSpace(value); }
    public static Matcher<String> emptyOrNullString() { return IsEmptyString.emptyOrNullString(); }
    public static Matcher<String> emptyString() { return IsEmptyString.emptyString(); }
    public static Matcher<String> isEmptyOrNullString() { return IsEmptyString.isEmptyOrNullString(); }
    public static Matcher<String> isEmptyString() { return IsEmptyString.isEmptyString(); }
    public static Matcher<String> blankOrNullString() { return IsBlankString.blankOrNullString(); }
    public static Matcher<String> blankString() { return IsBlankString.blankString(); }
    public static Matcher<String> matchesRegex(final java.util.regex.Pattern pattern) { return MatchesPattern.matchesPattern(pattern); }
    public static Matcher<String> matchesRegex(final String pattern) { return MatchesPattern.matchesPattern(pattern); }
    public static Matcher<String> stringContainsInOrder(final Iterable<String> values) { return StringContainsInOrder.stringContainsInOrder(values); }
    public static Matcher<String> stringContainsInOrder(final String... values) { return StringContainsInOrder.stringContainsInOrder(values); }
    public static Matcher<org.w3c.dom.Node> hasXPath(final String xpath, final Matcher<String> matcher) { return HasXPath.hasXPath(xpath, matcher); }
    public static Matcher<org.w3c.dom.Node> hasXPath(final String xpath, final javax.xml.namespace.NamespaceContext context, final Matcher<String> matcher) { return HasXPath.hasXPath(xpath, context, matcher); }
    public static Matcher<org.w3c.dom.Node> hasXPath(final String xpath) { return HasXPath.hasXPath(xpath); }
    public static Matcher<org.w3c.dom.Node> hasXPath(final String xpath, final javax.xml.namespace.NamespaceContext context) { return HasXPath.hasXPath(xpath, context); }
}
