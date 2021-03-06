/*
 * Copyright (c) 2015, Haiyang Li.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.landawn.abacus.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.landawn.abacus.annotation.Internal;
import com.landawn.abacus.util.function.BiFunction;
import com.landawn.abacus.util.function.BiPredicate;
import com.landawn.abacus.util.function.Function;
import com.landawn.abacus.util.function.IntFunction;
import com.landawn.abacus.util.function.ObjIntConsumer;
import com.landawn.abacus.util.function.Predicate;
import com.landawn.abacus.util.function.TriFunction;
import com.landawn.abacus.util.function.TriPredicate;
import com.landawn.abacus.util.stream.EntryStream;
import com.landawn.abacus.util.stream.Stream;

/**
 * A collection that supports order-independent equality, like {@link Set}, but
 * may have duplicate elements.
 *
 * <p>Elements of a Multiset that are equal to one another are referred to as
 * <i>occurrences</i> of the same single element. The total number of
 * occurrences of an element in a Multiset is called the <i>count</i> of that
 * element (the terms "frequency" and "multiplicity" are equivalent, but not
 * used in this API). Since the count of an element is represented as an {@code
 * int}, a Multiset may never contain more than {@link MutableInt#MAX_VALUE}
 * occurrences of any one element.
 *
 * @param <E>
 *
 * @since 0.8
 *
 * @author Haiyang Li
 */
public final class Multiset<E> implements Iterable<E> {
    private static final Comparator<Map.Entry<?, MutableInt>> cmpByCount = new Comparator<Map.Entry<?, MutableInt>>() {
        @Override
        public int compare(Entry<?, MutableInt> a, Entry<?, MutableInt> b) {
            return N.compare(a.getValue().value(), b.getValue().value());
        }
    };

    final Map<E, MutableInt> valueMap;

    public Multiset() {
        this(HashMap.class);
    }

    public Multiset(int initialCapacity) {
        this(new HashMap<E, MutableInt>(initialCapacity));
    }

    @SuppressWarnings("rawtypes")
    public Multiset(final Class<? extends Map> valueMapType) {
        this(N.newInstance(valueMapType));
    }

    public Multiset(final Collection<? extends E> c) {
        this();

        addAll(c);
    }

    /**
     *
     * @param valueMap The valueMap and this Multiset share the same data; any changes to one will appear in the other.
     */
    @Internal
    Multiset(final Map<E, MutableInt> valueMap) {
        this.valueMap = valueMap;
    }

    @SafeVarargs
    public static <T> Multiset<T> of(final T... a) {
        return N.asMultiset(a);
    }

    public static <T> Multiset<T> from(final Collection<? extends T> coll) {
        return new Multiset<>(coll);
    }

    public static <T> Multiset<T> from(final Map<? extends T, Integer> m) {
        if (N.isNullOrEmpty(m)) {
            return new Multiset<T>();
        }

        final Multiset<T> multiset = new Multiset<>(N.initHashCapacity(m.size()));

        multiset.setAll(m);

        return multiset;
    }

    /**
     *
     * @param e
     * @return the occurrences of the specified object. zero is returned if it's not in this set.
     */
    public int get(final Object e) {
        final MutableInt count = valueMap.get(e);

        return count == null ? 0 : count.value();
    }

    /**
     * 
     * @param e
     * @param defaultValue
     * @return the occurrences of the specified object. the specified defaultValue is returned if it's not in this set.
     */
    public int getOrDefault(final Object e, int defaultValue) {
        final MutableInt count = valueMap.get(e);

        return count == null ? defaultValue : count.value();
    }

    /**
     * The element will be removed if the specified count is 0.
     * 
     * @param e
     * @param occurrences
     * @return
     */
    public int getAndSet(final E e, final int occurrences) {
        checkOccurrences(occurrences);

        final MutableInt count = valueMap.get(e);
        int result = count == null ? 0 : count.value();

        if (occurrences == 0) {
            if (count != null) {
                valueMap.remove(e);
            }
        } else {
            if (count == null) {
                valueMap.put(e, MutableInt.of(occurrences));
            } else {
                count.setValue(occurrences);
            }
        }

        return result;
    }

    /**
     * The element will be removed if the specified count is 0.
     * 
     * @param e
     * @param occurrences
     * @return
     */
    public int setAndGet(final E e, final int occurrences) {
        checkOccurrences(occurrences);

        final MutableInt count = valueMap.get(e);

        if (occurrences == 0) {
            if (count != null) {
                valueMap.remove(e);
            }
        } else {
            if (count == null) {
                valueMap.put(e, MutableInt.of(occurrences));
            } else {
                count.setValue(occurrences);
            }
        }

        return occurrences;
    }

    /**
     * The element will be removed if the specified count is 0.
     *
     * @param e
     * @param occurrences
     * @return this Multiset.
     * @throws IllegalArgumentException if the occurrences of element is less than 0
     */
    public Multiset<E> set(final E e, final int occurrences) {
        checkOccurrences(occurrences);

        if (occurrences == 0) {
            valueMap.remove(e);
        } else {
            final MutableInt count = valueMap.get(e);

            if (count == null) {
                valueMap.put(e, MutableInt.of(occurrences));
            } else {
                count.setValue(occurrences);
            }
        }

        return this;
    }

    public Multiset<E> setAll(final Collection<? extends E> c, final int occurrences) {
        checkOccurrences(occurrences);

        if (N.notNullOrEmpty(c)) {
            for (E e : c) {
                set(e, occurrences);
            }
        }

        return this;
    }

    /**
     * 
     * @param m
     * @return this Multiset.
     * @throws IllegalArgumentException if the occurrences of element is less than 0.
     */
    public Multiset<E> setAll(final Map<? extends E, Integer> m) throws IllegalArgumentException {
        if (N.notNullOrEmpty(m)) {
            for (Map.Entry<? extends E, Integer> entry : m.entrySet()) {
                checkOccurrences(entry.getValue().intValue());
            }

            for (Map.Entry<? extends E, Integer> entry : m.entrySet()) {
                set(entry.getKey(), entry.getValue().intValue());
            }
        }

        return this;
    }

    /**
     * 
     * @param m
     * @return this Multiset.
     * @throws IllegalArgumentException if the occurrences of element is less than 0.
     */
    public Multiset<E> setAll(final Multiset<? extends E> multiset) throws IllegalArgumentException {
        if (N.notNullOrEmpty(multiset)) {
            for (Map.Entry<? extends E, MutableInt> entry : multiset.valueMap.entrySet()) {
                set(entry.getKey(), entry.getValue().value());
            }
        }

        return this;
    }

    public Optional<Pair<E, Integer>> minOccurrences() {
        if (size() == 0) {
            return Optional.empty();
        }

        final Iterator<Map.Entry<E, MutableInt>> it = valueMap.entrySet().iterator();
        Map.Entry<E, MutableInt> entry = it.next();
        E minCountElement = entry.getKey();
        int minCount = entry.getValue().value();

        while (it.hasNext()) {
            entry = it.next();

            if (entry.getValue().value() < minCount) {
                minCountElement = entry.getKey();
                minCount = entry.getValue().value();
            }
        }

        return Optional.of(Pair.of(minCountElement, minCount));
    }

    public Optional<Pair<E, Integer>> maxOccurrences() {
        if (size() == 0) {
            return Optional.empty();
        }

        final Iterator<Map.Entry<E, MutableInt>> it = valueMap.entrySet().iterator();
        Map.Entry<E, MutableInt> entry = it.next();
        E maxCountElement = entry.getKey();
        int maxCount = entry.getValue().value();

        while (it.hasNext()) {
            entry = it.next();

            if (entry.getValue().value() > maxCount) {
                maxCountElement = entry.getKey();
                maxCount = entry.getValue().value();
            }
        }

        return Optional.of(Pair.of(maxCountElement, maxCount));
    }

    /**
     * 
     * @return
     * @throws ArithmeticException if total occurrences overflows the maximum value of long.
     */
    public Long sumOfOccurrences() {
        long sum = 0;

        for (MutableInt count : valueMap.values()) {
            sum = Math2.addExact(sum, count.value());
        }

        return sum;
    }

    public OptionalDouble averageOfOccurrences() {
        if (size() == 0) {
            return OptionalDouble.empty();
        }

        final double sum = sumOfOccurrences();

        return OptionalDouble.of(sum / size());
    }

    /**
     *
     * @param e
     * @return always true
     * @throws IllegalArgumentException if the occurrences of element after this operation is bigger than Integer.MAX_VALUE.
     */
    public boolean add(final E e) throws IllegalArgumentException {
        return add(e, 1);
    }

    /**
     *
     * @param e
     * @param occurrences
     * @return true if the specified occurrences is bigger than 0.
     * @throws IllegalArgumentException if the occurrences of element after this operation is bigger than Integer.MAX_VALUE.
     */
    public boolean add(final E e, final int occurrences) throws IllegalArgumentException {
        checkOccurrences(occurrences);

        MutableInt count = valueMap.get(e);

        if (count != null && occurrences > (Integer.MAX_VALUE - count.value())) {
            throw new IllegalArgumentException("The total count is out of the bound of integer");
        }

        if (count == null) {
            if (occurrences > 0) {
                count = MutableInt.of(occurrences);
                valueMap.put(e, count);
            }
        } else {
            count.add(occurrences);
        }

        return occurrences > 0;
    }

    /**
     * 
     * @param e
     * @return true if the specified element is absent.
     * @throws IllegalArgumentException
     */
    public boolean addIfAbsent(final E e) throws IllegalArgumentException {
        return addIfAbsent(e, 1);
    }

    /**
     * 
     * @param e
     * @param occurrences
     * @return true if the specified element is absent and occurrences is bigger than 0.
     * @throws IllegalArgumentException 
     */
    public boolean addIfAbsent(final E e, final int occurrences) throws IllegalArgumentException {
        checkOccurrences(occurrences);

        MutableInt count = valueMap.get(e);

        if (count == null && occurrences > 0) {
            count = MutableInt.of(occurrences);
            valueMap.put(e, count);

            return true;
        }

        return false;
    }

    public int addAndGet(final E e) {
        return addAndGet(e, 1);
    }

    public int addAndGet(final E e, final int occurrences) {
        checkOccurrences(occurrences);

        MutableInt count = valueMap.get(e);

        if (count != null && occurrences > (Integer.MAX_VALUE - count.value())) {
            throw new IllegalArgumentException("The total count is out of the bound of integer");
        }

        if (count == null) {
            if (occurrences > 0) {
                count = MutableInt.of(occurrences);
                valueMap.put(e, count);
            }
        } else {
            count.add(occurrences);
        }

        return count == null ? 0 : count.value();
    }

    public int getAndAdd(final E e) {
        return getAndAdd(e, 1);
    }

    public int getAndAdd(final E e, final int occurrences) {
        checkOccurrences(occurrences);

        MutableInt count = valueMap.get(e);

        if (count != null && occurrences > (Integer.MAX_VALUE - count.value())) {
            throw new IllegalArgumentException("The total count is out of the bound of integer");
        }

        final int result = count == null ? 0 : count.value();

        if (count == null) {
            if (occurrences > 0) {
                count = MutableInt.of(occurrences);
                valueMap.put(e, count);
            }
        } else {
            count.add(occurrences);
        }

        return result;
    }

    /**
     * 
     * @param c
     * @throws IllegalArgumentException if the occurrences of element after this operation is bigger than Integer.MAX_VALUE.
     */
    public boolean addAll(final Collection<? extends E> c) throws IllegalArgumentException {
        if (N.isNullOrEmpty(c)) {
            return false;
        }

        return addAll(c, 1);
    }

    /**
     * 
     * @param c
     * @param occurrences
     * @throws IllegalArgumentException if the occurrences of element after this operation is bigger than Integer.MAX_VALUE.
     */
    public boolean addAll(final Collection<? extends E> c, final int occurrences) throws IllegalArgumentException {
        checkOccurrences(occurrences);

        if (N.isNullOrEmpty(c) || occurrences == 0) {
            return false;
        }

        for (E e : c) {
            add(e, occurrences);
        }

        return occurrences > 0;
    }

    /**
     * 
     * @param m
     * @throws IllegalArgumentException if the occurrences of element after this operation is bigger than Integer.MAX_VALUE.
     */
    public boolean addAll(final Map<? extends E, Integer> m) throws IllegalArgumentException {
        if (N.isNullOrEmpty(m)) {
            return false;
        }

        for (Map.Entry<? extends E, Integer> entry : m.entrySet()) {
            checkOccurrences(entry.getValue().intValue());
        }

        boolean result = false;

        for (Map.Entry<? extends E, Integer> entry : m.entrySet()) {
            if (result == false) {
                result = add(entry.getKey(), entry.getValue().intValue());
            } else {
                add(entry.getKey(), entry.getValue().intValue());
            }
        }

        return result;
    }

    /**
     * 
     * @param m
     * @throws IllegalArgumentException if the occurrences of element is less than 0.
     */
    public boolean addAll(final Multiset<? extends E> multiset) throws IllegalArgumentException {
        if (N.isNullOrEmpty(multiset)) {
            return false;
        }

        for (Map.Entry<? extends E, MutableInt> entry : multiset.valueMap.entrySet()) {
            add(entry.getKey(), entry.getValue().value());
        }

        return true;
    }

    public boolean contains(final Object o) {
        return valueMap.containsKey(o);
    }

    public boolean containsAll(final Collection<?> c) {
        return valueMap.keySet().containsAll(c);
    }

    /**
     * Remove one occurrences from the specified element. 
     * The elements will be removed from this set if the occurrences equals to or less than 0 after the operation.
     *
     * @param e
     * @param occurrences
     * @return
     */
    public boolean remove(final Object e) throws IllegalArgumentException {
        return remove(e, 1);
    }

    /**
     * Remove the specified occurrences from the specified element. 
     * The elements will be removed from this set if the occurrences equals to or less than 0 after the operation.
     *
     *
     * @param e
     * @param occurrences
     * @return
     */
    public boolean remove(final Object e, final int occurrences) throws IllegalArgumentException {
        checkOccurrences(occurrences);

        final MutableInt count = valueMap.get(e);

        if (count == null) {
            return false;
        } else {
            count.subtract(occurrences);

            if (count.value() <= 0) {
                valueMap.remove(e);
            }

            return occurrences > 0;
        }
    }

    public int removeAndGet(final Object e) {
        return removeAndGet(e, 1);
    }

    public int removeAndGet(final Object e, final int occurrences) {
        checkOccurrences(occurrences);

        final MutableInt count = valueMap.get(e);

        if (count == null) {
            return 0;
        } else {
            count.subtract(occurrences);

            if (count.value() <= 0) {
                valueMap.remove(e);
            }

            return count.value() > 0 ? count.value() : 0;
        }
    }

    public int getAndRemove(final Object e) {
        return getAndRemove(e, 1);
    }

    public int getAndRemove(final Object e, final int occurrences) {
        checkOccurrences(occurrences);

        final MutableInt count = valueMap.get(e);
        final int result = count == null ? 0 : count.value();

        if (count != null) {
            count.subtract(occurrences);

            if (count.value() <= 0) {
                valueMap.remove(e);
            }
        }

        return result;
    }

    /**
     * 
     * @param e
     * @return the occurrences of the specified element before it's removed.
     */
    public int removeAllOccurrences(final Object e) {
        final MutableInt count = valueMap.remove(e);

        return count == null ? 0 : count.value();
    }

    public boolean removeAllOccurrencesIf(Predicate<? super E> predicate) {
        Set<E> removingKeys = null;

        for (E key : this.valueMap.keySet()) {
            if (predicate.test(key)) {
                if (removingKeys == null) {
                    removingKeys = new HashSet<>();
                }

                removingKeys.add(key);
            }
        }

        if (N.isNullOrEmpty(removingKeys)) {
            return false;
        }

        removeAll(removingKeys);

        return true;
    }

    public boolean removeAllOccurrencesIf(BiPredicate<? super E, ? super Integer> predicate) {
        Set<E> removingKeys = null;

        for (Map.Entry<E, MutableInt> entry : this.valueMap.entrySet()) {
            if (predicate.test(entry.getKey(), entry.getValue().value())) {
                if (removingKeys == null) {
                    removingKeys = new HashSet<>();
                }

                removingKeys.add(entry.getKey());
            }
        }

        if (N.isNullOrEmpty(removingKeys)) {
            return false;
        }

        removeAll(removingKeys);

        return true;
    }

    public boolean removeIf(final int occurrences, Predicate<? super E> predicate) {
        checkOccurrences(occurrences);

        Set<E> removingKeys = null;

        for (E key : this.valueMap.keySet()) {
            if (predicate.test(key)) {
                if (removingKeys == null) {
                    removingKeys = new HashSet<>();
                }

                removingKeys.add(key);
            }
        }

        if (N.isNullOrEmpty(removingKeys)) {
            return false;
        }

        removeAll(removingKeys, occurrences);

        return true;
    }

    public boolean removeIf(final int occurrences, BiPredicate<? super E, ? super Integer> predicate) {
        checkOccurrences(occurrences);

        Set<E> removingKeys = null;

        for (Map.Entry<E, MutableInt> entry : this.valueMap.entrySet()) {
            if (predicate.test(entry.getKey(), entry.getValue().value())) {
                if (removingKeys == null) {
                    removingKeys = new HashSet<>();
                }

                removingKeys.add(entry.getKey());
            }
        }

        if (N.isNullOrEmpty(removingKeys)) {
            return false;
        }

        removeAll(removingKeys, occurrences);

        return true;
    }

    /**
     * Removes all of this Multiset's elements that are also contained in the
     * specified collection (optional operation).  After this call returns,
     * this Multiset will contain no elements in common with the specified
     * collection. This method ignores how often any element might appear in
     * {@code c}, and only cares whether or not an element appears at all.
     *
     * @param c
     * @return <tt>true</tt> if this set changed as a result of the call
     * @see Collection#removeAll(Collection)
     */
    public boolean removeAll(final Collection<?> c) {
        if (N.isNullOrEmpty(c)) {
            return false;
        }

        boolean result = false;

        for (Object e : c) {
            if (result == false) {
                result = valueMap.remove(e) != null;
            } else {
                valueMap.remove(e);
            }
        }

        return result;
    }

    /**
     * Remove the specified occurrences from the specified elements. 
     * The elements will be removed from this set if the occurrences equals to or less than 0 after the operation.
     *
     * @param c
     * @param occurrences
     *            the occurrences to remove if the element is in the specified collection <code>c</code>.
     * @return <tt>true</tt> if this set changed as a result of the call
     */
    public boolean removeAll(final Collection<?> c, final int occurrences) throws IllegalArgumentException {
        checkOccurrences(occurrences);

        if (N.isNullOrEmpty(c) || occurrences == 0) {
            return false;
        }

        boolean result = false;

        for (Object e : c) {
            if (result == false) {
                result = remove(e, occurrences);
            } else {
                remove(e, occurrences);
            }
        }

        return result;
    }

    /**
     * 
     * @param m
     * @return
     */
    public boolean removeAll(final Map<?, Integer> m) throws IllegalArgumentException {
        if (N.isNullOrEmpty(m)) {
            return false;
        }

        for (Map.Entry<?, Integer> entry : m.entrySet()) {
            checkOccurrences(entry.getValue().intValue());
        }

        boolean result = false;

        for (Map.Entry<?, Integer> entry : m.entrySet()) {
            if (result == false) {
                result = remove(entry.getKey(), entry.getValue().intValue());
            } else {
                remove(entry.getKey(), entry.getValue().intValue());
            }
        }

        return result;
    }

    /**
     * 
     * @param m
     */
    public boolean removeAll(final Multiset<?> multiset) throws IllegalArgumentException {
        if (N.isNullOrEmpty(multiset)) {
            return false;
        }

        for (Map.Entry<?, MutableInt> entry : multiset.valueMap.entrySet()) {
            remove(entry.getKey(), entry.getValue().value());
        }

        return true;
    }

    /**
     * The associated elements will be removed if zero or negative occurrences are returned by the specified <code>function</code>.
     * 
     * @param function
     */
    public void replaceAll(BiFunction<? super E, ? super Integer, Integer> function) {
        List<E> keyToRemove = null;
        Integer newVal = null;

        for (Map.Entry<E, MutableInt> entry : this.valueMap.entrySet()) {
            newVal = function.apply(entry.getKey(), entry.getValue().value());

            if (newVal == null || newVal.intValue() <= 0) {
                if (keyToRemove == null) {
                    keyToRemove = new ArrayList<>();
                }

                keyToRemove.add(entry.getKey());
            } else {
                entry.getValue().setValue(newVal);
            }
        }

        if (N.notNullOrEmpty(keyToRemove)) {
            for (E key : keyToRemove) {
                valueMap.remove(key);
            }
        }
    }

    public boolean replaceIf(final int newOccurrences, Predicate<? super E> predicate) {
        checkNewOccurrences(newOccurrences);

        boolean modified = false;

        for (Map.Entry<E, MutableInt> entry : this.valueMap.entrySet()) {
            if (predicate.test(entry.getKey())) {
                entry.getValue().setValue(newOccurrences);

                modified = true;
            }
        }

        return modified;
    }

    public boolean replaceIf(final int newOccurrences, BiPredicate<? super E, ? super Integer> predicate) {
        checkNewOccurrences(newOccurrences);

        boolean modified = false;

        for (Map.Entry<E, MutableInt> entry : this.valueMap.entrySet()) {
            if (predicate.test(entry.getKey(), entry.getValue().value())) {
                entry.getValue().setValue(newOccurrences);

                modified = true;
            }
        }

        return modified;
    }

    /**
     * Retains only the elements in this collection that are contained in the
     * specified collection (optional operation).  In other words, removes from
     * this collection all of its elements that are not contained in the
     * specified collection.
     *
     * @param c
     * @return <tt>true</tt> if this set changed as a result of the call
     */
    public boolean retainAll(final Collection<?> c) {
        if (N.isNullOrEmpty(c)) {
            boolean result = size() > 0;
            clear();
            return result;
        }

        Set<E> others = null;

        for (E e : valueMap.keySet()) {
            if (!c.contains(e)) {
                if (others == null) {
                    others = new HashSet<>(valueMap.size());
                }

                others.add(e);
            }
        }

        return N.isNullOrEmpty(others) ? false : removeAll(others, Integer.MAX_VALUE);
    }

    public Set<E> elements() {
        return valueMap.keySet();
    }

    public int size() {
        return valueMap.size();
    }

    public boolean isEmpty() {
        return valueMap.isEmpty();
    }

    public void clear() {
        valueMap.clear();
    }

    @Override
    public Iterator<E> iterator() {
        return valueMap.keySet().iterator();
    }

    //    public Set<Map.Entry<E, MutableInt>> entrySet() {
    //        return valueMap.entrySet();
    //    }

    public Object[] toArray() {
        return valueMap.keySet().toArray();
    }

    public <T> T[] toArray(final T[] a) {
        return valueMap.keySet().toArray(a);
    }

    public Map<E, Integer> toMap() {
        final Map<E, Integer> result = valueMap instanceof IdentityHashMap ? new IdentityHashMap<E, Integer>(N.initHashCapacity(size()))
                : new HashMap<E, Integer>(N.initHashCapacity(size()));

        for (Map.Entry<E, MutableInt> entry : valueMap.entrySet()) {
            result.put(entry.getKey(), entry.getValue().value());
        }

        return result;
    }

    public <M extends Map<E, Integer>> M toMap(final IntFunction<M> supplier) {
        final M result = supplier.apply(size());

        for (Map.Entry<E, MutableInt> entry : valueMap.entrySet()) {
            result.put(entry.getKey(), entry.getValue().value());
        }

        return result;
    }

    @SuppressWarnings("rawtypes")
    public Map<E, Integer> toMapSortedByOccurrences() {
        return toMapSortedBy((Comparator) cmpByCount);
    }

    public Map<E, Integer> toMapSortedByOccurrences(final Comparator<? super Integer> cmp) {
        return toMapSortedBy(new Comparator<Map.Entry<E, MutableInt>>() {
            @Override
            public int compare(Entry<E, MutableInt> o1, Entry<E, MutableInt> o2) {
                return cmp.compare(o1.getValue().value(), o2.getValue().value());
            }
        });
    }

    public Map<E, Integer> toMapSortedByKey(final Comparator<? super E> cmp) {
        return toMapSortedBy(new Comparator<Map.Entry<E, MutableInt>>() {
            @Override
            public int compare(Entry<E, MutableInt> o1, Entry<E, MutableInt> o2) {
                return cmp.compare(o1.getKey(), o2.getKey());
            }
        });
    }

    Map<E, Integer> toMapSortedBy(final Comparator<Map.Entry<E, MutableInt>> cmp) {
        if (N.isNullOrEmpty(valueMap)) {
            return new LinkedHashMap<>();
        }

        final Map.Entry<E, MutableInt>[] entries = valueMap.entrySet().toArray(new Map.Entry[size()]);
        Arrays.sort(entries, cmp);

        final Map<E, Integer> sortedValues = new LinkedHashMap<>(N.initHashCapacity(size()));

        for (Map.Entry<E, MutableInt> entry : entries) {
            sortedValues.put(entry.getKey(), entry.getValue().value());
        }

        return sortedValues;
    }

    /**
     * 
     * @return a list with all elements, each of them is repeated with the occurrences in this <code>Multiset</code>   
     */
    public List<E> flatten() {
        final long totalOccurrences = sumOfOccurrences().longValue();

        if (totalOccurrences > Integer.MAX_VALUE) {
            throw new RuntimeException("The total occurrences(" + totalOccurrences + ") is bigger than the max value of int.");
        }

        final Object[] a = new Object[sumOfOccurrences().intValue()];

        int fromIndex = 0;
        int toIndex = 0;

        for (Map.Entry<E, MutableInt> entry : valueMap.entrySet()) {
            toIndex = fromIndex + entry.getValue().value();

            Arrays.fill(a, fromIndex, toIndex, entry.getKey());
            fromIndex = toIndex;
        }

        return N.asList((E[]) a);
    }

    public Multiset<E> filter(Predicate<? super E> filter) {
        final Multiset<E> result = new Multiset<>(valueMap instanceof IdentityHashMap ? IdentityHashMap.class : LinkedHashMap.class);

        for (Map.Entry<E, MutableInt> entry : valueMap.entrySet()) {
            if (filter.test(entry.getKey())) {
                result.add(entry.getKey(), entry.getValue().intValue());
            }
        }

        return result;
    }

    public Multiset<E> filter(BiPredicate<? super E, Integer> filter) {
        final Multiset<E> result = new Multiset<>(valueMap instanceof IdentityHashMap ? IdentityHashMap.class : LinkedHashMap.class);

        for (Map.Entry<E, MutableInt> entry : valueMap.entrySet()) {
            if (filter.test(entry.getKey(), entry.getValue().intValue())) {
                result.add(entry.getKey(), entry.getValue().intValue());
            }
        }

        return result;
    }

    public void forEach(final ObjIntConsumer<? super E> action) {
        N.requireNonNull(action);

        for (Map.Entry<E, MutableInt> entry : valueMap.entrySet()) {
            action.accept(entry.getKey(), entry.getValue().value());
        }
    }

    /**
     * Execute <code>accumulator</code> on each element till <code>true</code> is returned by <code>conditionToBreak</code>
     * 
     * @param seed The seed element is both the initial value of the reduction and the default result if there are no elements.
     * @param accumulator
     * @param conditionToBreak break if <code>true</code> is return.
     * @return
     */
    public <R> R forEach(final R seed, TriFunction<R, ? super E, Integer, R> accumulator, final TriPredicate<? super E, Integer, ? super R> conditionToBreak) {
        N.requireNonNull(accumulator);
        N.requireNonNull(conditionToBreak);

        R result = seed;

        for (Map.Entry<E, MutableInt> entry : valueMap.entrySet()) {
            result = accumulator.apply(result, entry.getKey(), entry.getValue().value());

            if (conditionToBreak.test(entry.getKey(), entry.getValue().value(), result)) {
                break;
            }
        }

        return result;
    }

    /**
     * The implementation is equivalent to performing the following steps for this Multiset:
     * 
     * <pre>
     * final int oldValue = get(e);
     * 
     * if (oldValue > 0) {
     *     return oldValue;
     * }
     * 
     * final int newValue = mappingFunction.apply(e);
     * 
     * if (newValue > 0) {
     *     set(e, newValue);
     * }
     * 
     * return newValue;
     * </pre>
     * 
     * @param e
     * @param mappingFunction
     * @return
     */
    public int computeIfAbsent(E e, Function<? super E, Integer> mappingFunction) {
        N.requireNonNull(mappingFunction);

        final int oldValue = get(e);

        if (oldValue > 0) {
            return oldValue;
        }

        final int newValue = mappingFunction.apply(e);

        if (newValue > 0) {
            set(e, newValue);
        }

        return newValue;
    }

    /**
     * The implementation is equivalent to performing the following steps for this Multiset:
     * 
     * <pre> 
     * final int oldValue = get(e);
     * 
     * if (oldValue == 0) {
     *     return oldValue;
     * }
     * 
     * final int newValue = remappingFunction.apply(e, oldValue);
     * 
     * if (newValue > 0) {
     *     set(e, newValue);
     * } else {
     *     remove(e);
     * }
     * 
     * return newValue;
     * </pre>
     * 
     * @param e
     * @param remappingFunction
     * @return
     */
    public int computeIfPresent(E e, BiFunction<? super E, Integer, Integer> remappingFunction) {
        N.requireNonNull(remappingFunction);

        final int oldValue = get(e);

        if (oldValue == 0) {
            return oldValue;
        }

        final int newValue = remappingFunction.apply(e, oldValue);

        if (newValue > 0) {
            set(e, newValue);
        } else {
            remove(e);
        }

        return newValue;
    }

    /**
     * The implementation is equivalent to performing the following steps for this Multiset:
     * 
     * <pre>
     * final int oldValue = get(key);
     * final int newValue = remappingFunction.apply(key, oldValue);
     * 
     * if (newValue > 0) {
     *     set(key, newValue);
     * } else {
     *     if (oldValue > 0) {
     *         remove(key);
     *     }
     * }
     * 
     * return newValue;
     * </pre>
     * 
     * @param key
     * @param remappingFunction
     * @return
     */
    public int compute(E key, BiFunction<? super E, Integer, Integer> remappingFunction) {
        N.requireNonNull(remappingFunction);

        final int oldValue = get(key);
        final int newValue = remappingFunction.apply(key, oldValue);

        if (newValue > 0) {
            set(key, newValue);
        } else {
            if (oldValue > 0) {
                remove(key);
            }
        }

        return newValue;
    }

    /**
     * The implementation is equivalent to performing the following steps for this Multiset:
     * 
     * <pre>
     * int oldValue = get(key);
     * int newValue = (oldValue == 0) ? value : remappingFunction.apply(oldValue, value);
     * 
     * if (newValue > 0) {
     *     set(key, newValue);
     * } else {
     *     if (oldValue > 0) {
     *         remove(key);
     *     }
     * }
     * 
     * return newValue;
     * </pre>
     * 
     * @param key
     * @param value
     * @param remappingFunction
     * @return
     */
    public int merge(E key, int value, BiFunction<Integer, Integer, Integer> remappingFunction) {
        N.requireNonNull(remappingFunction);
        N.requireNonNull(value);

        int oldValue = get(key);
        int newValue = (oldValue == 0) ? value : remappingFunction.apply(oldValue, value);

        if (newValue > 0) {
            set(key, newValue);
        } else {
            if (oldValue > 0) {
                remove(key);
            }
        }

        return newValue;
    }

    public Stream<Entry<E, Integer>> stream() {
        return Stream.of(valueMap.entrySet()).map(new Function<Map.Entry<E, MutableInt>, Map.Entry<E, Integer>>() {
            @Override
            public Entry<E, Integer> apply(Entry<E, MutableInt> t) {
                return Pair.of(t.getKey(), t.getValue().value());
            }
        });
    }

    public EntryStream<E, Integer> entryStream() {
        return EntryStream.of(this);
    }

    @Override
    public int hashCode() {
        return valueMap.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        return obj == this || (obj instanceof Multiset && valueMap.equals(((Multiset<E>) obj).valueMap));
    }

    @Override
    public String toString() {
        return valueMap.toString();
    }

    private static void checkOccurrences(final int occurrences) {
        if (occurrences < 0) {
            throw new IllegalArgumentException("The specified 'occurrences' can not be less than 0");
        }
    }

    private static void checkNewOccurrences(final int newOccurrences) {
        if (newOccurrences < 1) {
            throw new IllegalArgumentException("The specified 'newOccurrences' can not be less than 1");
        }
    }
}
