/*
 * Copyright (C) 2016 HaiYang Li
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.landawn.abacus.util;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * 
 * @since 0.8
 * 
 * @author Haiyang Li
 */
public final class ImmutableMap<K, V> implements Map<K, V> {
    private final Map<K, V> map;

    ImmutableMap(final Map<? extends K, ? extends V> map) {
        this.map = Collections.unmodifiableMap(map);
    }

    public static <K, V, k extends K, v extends V> ImmutableMap<K, V> of(final k k1, final v v1) {
        final Map<k, v> map = N.asLinkedHashMap(k1, v1);
        return new ImmutableMap<K, V>(map);
    }

    public static <K, V, k extends K, v extends V> ImmutableMap<K, V> of(final k k1, final v v1, final k k2, final v v2) {
        final Map<k, v> map = N.asLinkedHashMap(k1, v1, k2, v2);
        return new ImmutableMap<K, V>(map);
    }

    public static <K, V, k extends K, v extends V> ImmutableMap<K, V> of(final k k1, final v v1, final k k2, final v v2, final k k3, final v v3) {
        final Map<k, v> map = N.asLinkedHashMap(k1, v1, k2, v2, k3, v3);
        return new ImmutableMap<K, V>(map);
    }

    public static <K, V, k extends K, v extends V> ImmutableMap<K, V> of(final k k1, final v v1, final k k2, final v v2, final k k3, final v v3, final k k4,
            final v v4) {
        final Map<k, v> map = N.asLinkedHashMap(k1, v1, k2, v2, k3, v3, k4, v4);
        return new ImmutableMap<K, V>(map);
    }

    public static <K, V, k extends K, v extends V> ImmutableMap<K, V> of(final k k1, final v v1, final k k2, final v v2, final k k3, final v v3, final k k4,
            final v v4, final k k5, final v v5) {
        final Map<k, v> map = N.asLinkedHashMap(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5);
        return new ImmutableMap<K, V>(map);
    }

    public static <K, V, k extends K, v extends V> ImmutableMap<K, V> of(final k k1, final v v1, final k k2, final v v2, final k k3, final v v3, final k k4,
            final v v4, final k k5, final v v5, final k k6, final v v6) {
        final Map<k, v> map = N.asLinkedHashMap(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, k6, v6);
        return new ImmutableMap<K, V>(map);
    }

    public static <K, V, k extends K, v extends V> ImmutableMap<K, V> of(final k k1, final v v1, final k k2, final v v2, final k k3, final v v3, final k k4,
            final v v4, final k k5, final v v5, final k k6, final v v6, final k k7, final v v7) {
        final Map<k, v> map = N.asLinkedHashMap(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, k6, v6, k7, v7);
        return new ImmutableMap<K, V>(map);
    }

    /**
     * 
     * @param map the elements in this <code>map</code> are shared by the returned ImmutableMap.
     * @return
     */
    public static <K, V> ImmutableMap<K, V> of(Map<? extends K, ? extends V> map) {
        return new ImmutableMap<>(map);
    }

    /**
     * @deprecated Unsupported operation.
     */
    @Deprecated
    @Override
    public final V put(K k, V v) {
        throw new UnsupportedOperationException();
    }

    /**
     * @deprecated Unsupported operation.
     */
    @Deprecated
    @Override
    public final V remove(Object o) {
        throw new UnsupportedOperationException();
    }

    /**
     * @deprecated Unsupported operation.
     */
    @Deprecated
    @Override
    public final void putAll(Map<? extends K, ? extends V> map) {
        throw new UnsupportedOperationException();
    }

    /**
     * @deprecated Unsupported operation.
     */
    @Deprecated
    @Override
    public V putIfAbsent(K key, V value) {
        throw new UnsupportedOperationException();
    }

    /**
     * @deprecated Unsupported operation.
     */
    @Deprecated
    @Override
    public boolean remove(Object key, Object value) {
        throw new UnsupportedOperationException();
    }

    /**
     * @deprecated Unsupported operation.
     */
    @Deprecated
    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        throw new UnsupportedOperationException();
    }

    /**
     * @deprecated Unsupported operation.
     */
    @Deprecated
    @Override
    public V replace(K key, V value) {
        throw new UnsupportedOperationException();
    }

    /**
     * @deprecated Unsupported operation.
     */
    @Deprecated
    @Override
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        throw new UnsupportedOperationException();
    }

    /**
     * @deprecated Unsupported operation.
     */
    @Deprecated
    @Override
    public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        throw new UnsupportedOperationException();
    }

    /**
     * @deprecated Unsupported operation.
     */
    @Deprecated
    @Override
    public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        throw new UnsupportedOperationException();
    }

    /**
     * @deprecated Unsupported operation.
     */
    @Deprecated
    @Override
    public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        throw new UnsupportedOperationException();
    }

    /**
     * @deprecated Unsupported operation.
     */
    @Deprecated
    @Override
    public final void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return map.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return map.containsValue(value);
    }

    @Override
    public V get(Object key) {
        return map.get(key);
    }

    @Override
    public Set<K> keySet() {
        return map.keySet();
    }

    @Override
    public Collection<V> values() {
        return map.values();
    }

    @Override
    public Set<java.util.Map.Entry<K, V>> entrySet() {
        return map.entrySet();
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ImmutableMap && ((ImmutableMap<K, V>) obj).map.equals(map);
    }

    @Override
    public int hashCode() {
        return map.hashCode();
    }

    @Override
    public String toString() {
        return map.toString();
    }
}
