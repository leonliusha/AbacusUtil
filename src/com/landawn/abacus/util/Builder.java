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
import java.util.Map;

import com.landawn.abacus.util.function.Consumer;
import com.landawn.abacus.util.function.Function;
import com.landawn.abacus.util.function.Predicate;
import com.landawn.abacus.util.function.Supplier;

/**
 * 
 * @param <T>
 * @since 0.8
 * 
 * @author haiyangl
 */
public class Builder<T> {
    final T val;

    Builder(T val) {
        N.requireNonNull(val);

        this.val = val;
    }

    public static final BooleanListBuilder of(BooleanList val) {
        return new BooleanListBuilder(val);
    }

    public static final CharListBuilder of(CharList val) {
        return new CharListBuilder(val);
    }

    public static final ByteListBuilder of(ByteList val) {
        return new ByteListBuilder(val);
    }

    public static final ShortListBuilder of(ShortList val) {
        return new ShortListBuilder(val);
    }

    public static final IntListBuilder of(IntList val) {
        return new IntListBuilder(val);
    }

    public static final LongListBuilder of(LongList val) {
        return new LongListBuilder(val);
    }

    public static final FloatListBuilder of(FloatList val) {
        return new FloatListBuilder(val);
    }

    public static final DoubleListBuilder of(DoubleList val) {
        return new DoubleListBuilder(val);
    }

    public static final <T, C extends Collection<T>> CollectionBuilder<T, C> of(C val) {
        return new CollectionBuilder<>(val);
    }

    public static final <K, V, M extends Map<K, V>> MapBuilder<K, V, M> of(M val) {
        return new MapBuilder<>(val);
    }

    public static final <T> MultisetBuilder<T> of(Multiset<T> val) {
        return new MultisetBuilder<>(val);
    }

    public static final <T> LongMultisetBuilder<T> of(LongMultiset<T> val) {
        return new LongMultisetBuilder<>(val);
    }

    public static final <K, E, V extends Collection<E>> MultimapBuilder<K, E, V> of(Multimap<K, E, V> val) {
        return new MultimapBuilder<>(val);
    }

    public static final <T> Builder<T> of(T val) {
        return new Builder<>(val);
    }

    public static <T> Builder<T> get(final Supplier<T> supplier) {
        return new Builder<>(supplier.get());
    }

    public Builder<T> accept(final Consumer<? super T> consumer) {
        consumer.accept(val);

        return this;
    }

    public <R> Builder<R> apply(final Function<? super T, R> func) {
        return of(func.apply(val));
    }

    /**
     * 
     * @param predicate
     * @return <code>Optional</code> with the value if <code>predicate</code> returns true, 
     * otherwise, return an empty <code>Optional</code>
     */
    public Optional<T> filter(final Predicate<? super T> predicate) {
        return predicate.test(val) ? Optional.of(val) : Optional.<T> empty();
    }

    public T val() {
        return val;
    }

    public static final class BooleanListBuilder extends Builder<BooleanList> {
        BooleanListBuilder(BooleanList val) {
            super(val);
        }

        public BooleanListBuilder set(int index, boolean e) {
            val.set(index, e);

            return this;
        }

        public BooleanListBuilder add(boolean e) {
            val.add(e);

            return this;
        }

        public BooleanListBuilder add(int index, boolean e) {
            val.add(index, e);

            return this;
        }

        public BooleanListBuilder addAll(BooleanList c) {
            val.addAll(c);

            return this;
        }

        public BooleanListBuilder addAll(int index, BooleanList c) {
            val.addAll(index, c);

            return this;
        }

        public BooleanListBuilder remove(boolean e) {
            val.remove(e);

            return this;
        }

        //        public BooleanListBuilder removeAllOccurrences(double e) {
        //            value.removeAllOccurrences(e);
        //
        //            return this;
        //        }

        public BooleanListBuilder removeAll(BooleanList c) {
            val.removeAll(c);

            return this;
        }
    }

    public static final class CharListBuilder extends Builder<CharList> {
        CharListBuilder(CharList val) {
            super(val);
        }

        public CharListBuilder set(int index, char e) {
            val.set(index, e);

            return this;
        }

        public CharListBuilder add(char e) {
            val.add(e);

            return this;
        }

        public CharListBuilder add(int index, char e) {
            val.add(index, e);

            return this;
        }

        public CharListBuilder addAll(CharList c) {
            val.addAll(c);

            return this;
        }

        public CharListBuilder addAll(int index, CharList c) {
            val.addAll(index, c);

            return this;
        }

        public CharListBuilder remove(char e) {
            val.remove(e);

            return this;
        }

        //        public CharListBuilder removeAllOccurrences(double e) {
        //            value.removeAllOccurrences(e);
        //
        //            return this;
        //        }

        public CharListBuilder removeAll(CharList c) {
            val.removeAll(c);

            return this;
        }
    }

    public static final class ByteListBuilder extends Builder<ByteList> {
        ByteListBuilder(ByteList val) {
            super(val);
        }

        public ByteListBuilder set(int index, byte e) {
            val.set(index, e);

            return this;
        }

        public ByteListBuilder add(byte e) {
            val.add(e);

            return this;
        }

        public ByteListBuilder add(int index, byte e) {
            val.add(index, e);

            return this;
        }

        public ByteListBuilder addAll(ByteList c) {
            val.addAll(c);

            return this;
        }

        public ByteListBuilder addAll(int index, ByteList c) {
            val.addAll(index, c);

            return this;
        }

        public ByteListBuilder remove(byte e) {
            val.remove(e);

            return this;
        }

        //        public ByteListBuilder removeAllOccurrences(double e) {
        //            value.removeAllOccurrences(e);
        //
        //            return this;
        //        }

        public ByteListBuilder removeAll(ByteList c) {
            val.removeAll(c);

            return this;
        }
    }

    public static final class ShortListBuilder extends Builder<ShortList> {
        ShortListBuilder(ShortList val) {
            super(val);
        }

        public ShortListBuilder set(int index, short e) {
            val.set(index, e);

            return this;
        }

        public ShortListBuilder add(short e) {
            val.add(e);

            return this;
        }

        public ShortListBuilder add(int index, short e) {
            val.add(index, e);

            return this;
        }

        public ShortListBuilder addAll(ShortList c) {
            val.addAll(c);

            return this;
        }

        public ShortListBuilder addAll(int index, ShortList c) {
            val.addAll(index, c);

            return this;
        }

        public ShortListBuilder remove(short e) {
            val.remove(e);

            return this;
        }

        //        public ShortListBuilder removeAllOccurrences(double e) {
        //            value.removeAllOccurrences(e);
        //
        //            return this;
        //        }

        public ShortListBuilder removeAll(ShortList c) {
            val.removeAll(c);

            return this;
        }
    }

    public static final class IntListBuilder extends Builder<IntList> {
        IntListBuilder(IntList val) {
            super(val);
        }

        public IntListBuilder set(int index, int e) {
            val.set(index, e);

            return this;
        }

        public IntListBuilder add(int e) {
            val.add(e);

            return this;
        }

        public IntListBuilder add(int index, int e) {
            val.add(index, e);

            return this;
        }

        public IntListBuilder addAll(IntList c) {
            val.addAll(c);

            return this;
        }

        public IntListBuilder addAll(int index, IntList c) {
            val.addAll(index, c);

            return this;
        }

        public IntListBuilder remove(int e) {
            val.remove(e);

            return this;
        }

        //        public IntListBuilder removeAllOccurrences(double e) {
        //            value.removeAllOccurrences(e);
        //
        //            return this;
        //        }

        public IntListBuilder removeAll(IntList c) {
            val.removeAll(c);

            return this;
        }
    }

    public static final class LongListBuilder extends Builder<LongList> {
        LongListBuilder(LongList val) {
            super(val);
        }

        public LongListBuilder set(int index, long e) {
            val.set(index, e);

            return this;
        }

        public LongListBuilder add(long e) {
            val.add(e);

            return this;
        }

        public LongListBuilder add(int index, long e) {
            val.add(index, e);

            return this;
        }

        public LongListBuilder addAll(LongList c) {
            val.addAll(c);

            return this;
        }

        public LongListBuilder addAll(int index, LongList c) {
            val.addAll(index, c);

            return this;
        }

        public LongListBuilder remove(long e) {
            val.remove(e);

            return this;
        }

        //        public LongListBuilder removeAllOccurrences(double e) {
        //            value.removeAllOccurrences(e);
        //
        //            return this;
        //        }

        public LongListBuilder removeAll(LongList c) {
            val.removeAll(c);

            return this;
        }
    }

    public static final class FloatListBuilder extends Builder<FloatList> {
        FloatListBuilder(FloatList val) {
            super(val);
        }

        public FloatListBuilder set(int index, float e) {
            val.set(index, e);

            return this;
        }

        public FloatListBuilder add(float e) {
            val.add(e);

            return this;
        }

        public FloatListBuilder add(int index, float e) {
            val.add(index, e);

            return this;
        }

        public FloatListBuilder addAll(FloatList c) {
            val.addAll(c);

            return this;
        }

        public FloatListBuilder addAll(int index, FloatList c) {
            val.addAll(index, c);

            return this;
        }

        public FloatListBuilder remove(float e) {
            val.remove(e);

            return this;
        }

        //        public FloatListBuilder removeAllOccurrences(double e) {
        //            value.removeAllOccurrences(e);
        //
        //            return this;
        //        }

        public FloatListBuilder removeAll(FloatList c) {
            val.removeAll(c);

            return this;
        }
    }

    public static final class DoubleListBuilder extends Builder<DoubleList> {
        DoubleListBuilder(DoubleList val) {
            super(val);
        }

        public DoubleListBuilder set(int index, double e) {
            val.set(index, e);

            return this;
        }

        public DoubleListBuilder add(double e) {
            val.add(e);

            return this;
        }

        public DoubleListBuilder add(int index, double e) {
            val.add(index, e);

            return this;
        }

        public DoubleListBuilder addAll(DoubleList c) {
            val.addAll(c);

            return this;
        }

        public DoubleListBuilder addAll(int index, DoubleList c) {
            val.addAll(index, c);

            return this;
        }

        public DoubleListBuilder remove(double e) {
            val.remove(e);

            return this;
        }

        //        public DoubleListBuilder removeAllOccurrences(double e) {
        //            value.removeAllOccurrences(e);
        //
        //            return this;
        //        }

        public DoubleListBuilder removeAll(DoubleList c) {
            val.removeAll(c);

            return this;
        }
    }

    public static final class CollectionBuilder<T, C extends Collection<T>> extends Builder<C> {
        CollectionBuilder(C c) {
            super(c);
        }

        public CollectionBuilder<T, C> add(T e) {
            val.add(e);

            return this;
        }

        public CollectionBuilder<T, C> addAll(Collection<? extends T> c) {
            @SuppressWarnings("rawtypes")
            final Collection tmp = c;

            val.addAll(tmp);

            return this;
        }

        public CollectionBuilder<T, C> remove(Object e) {
            val.remove(e);

            return this;
        }

        public CollectionBuilder<T, C> removeAll(Collection<?> c) {
            val.removeAll(c);

            return this;
        }
    }

    public static final class MultisetBuilder<T> extends Builder<Multiset<T>> {
        MultisetBuilder(Multiset<T> c) {
            super(c);
        }

        public MultisetBuilder<T> add(T e) {
            val.add(e);

            return this;
        }

        public MultisetBuilder<T> addAll(Collection<? extends T> c) {
            @SuppressWarnings("rawtypes")
            final Collection tmp = c;

            val.addAll(tmp);

            return this;
        }

        public MultisetBuilder<T> addAll(final Map<? extends T, Integer> m) {
            val.addAll(m);

            return this;
        }

        public MultisetBuilder<T> addAll(Multiset<? extends T> multiset) {
            val.addAll(multiset);

            return this;
        }

        public MultisetBuilder<T> remove(Object e) {
            val.remove(e);

            return this;
        }

        public MultisetBuilder<T> removeAll(Collection<?> c) {
            val.removeAll(c);

            return this;
        }

        public MultisetBuilder<T> removeAll(final Map<? extends T, Integer> m) {
            val.removeAll(m);

            return this;
        }

        public MultisetBuilder<T> removeAll(Multiset<? extends T> multiset) {
            val.removeAll(multiset);

            return this;
        }
    }

    public static final class LongMultisetBuilder<T> extends Builder<LongMultiset<T>> {
        LongMultisetBuilder(LongMultiset<T> c) {
            super(c);
        }

        public LongMultisetBuilder<T> add(T e) {
            val.add(e);

            return this;
        }

        public LongMultisetBuilder<T> addAll(Collection<? extends T> c) {
            @SuppressWarnings("rawtypes")
            final Collection tmp = c;

            val.addAll(tmp);

            return this;
        }

        public LongMultisetBuilder<T> addAll(final Map<? extends T, Long> m) {
            val.addAll(m);

            return this;
        }

        public LongMultisetBuilder<T> addAll(LongMultiset<? extends T> multiset) {
            val.addAll(multiset);

            return this;
        }

        public LongMultisetBuilder<T> remove(Object e) {
            val.remove(e);

            return this;
        }

        public LongMultisetBuilder<T> removeAll(Collection<?> c) {
            val.removeAll(c);

            return this;
        }

        public LongMultisetBuilder<T> removeAll(final Map<? extends T, Long> m) {
            val.removeAll(m);

            return this;
        }

        public LongMultisetBuilder<T> removeAll(LongMultiset<? extends T> multiset) {
            val.removeAll(multiset);

            return this;
        }
    }

    public static final class MapBuilder<K, V, M extends Map<K, V>> extends Builder<M> {
        MapBuilder(M m) {
            super(m);
        }

        public MapBuilder<K, V, M> put(K k, V v) {
            val.put(k, v);

            return this;
        }

        public MapBuilder<K, V, M> putAll(Map<? extends K, ? extends V> m) {
            val.putAll(m);

            return this;
        }

        public MapBuilder<K, V, M> remove(Object k) {
            val.remove(k);

            return this;
        }

        public MapBuilder<K, V, M> removeAll(Collection<?> c) {
            for (Object k : c) {
                val.remove(k);
            }

            return this;
        }
    }

    public static final class MultimapBuilder<K, E, V extends Collection<E>> extends Builder<Multimap<K, E, V>> {
        MultimapBuilder(Multimap<K, E, V> m) {
            super(m);
        }

        public MultimapBuilder<K, E, V> put(K key, E e) {
            val.put(key, e);

            return this;
        }

        public MultimapBuilder<K, E, V> putAll(final K k, final Collection<? extends E> c) {
            val.putAll(k, c);

            return this;
        }

        public MultimapBuilder<K, E, V> putAll(Map<? extends K, ? extends E> m) {
            val.putAll(m);

            return this;
        }

        public MultimapBuilder<K, E, V> putAll(Multimap<? extends K, ? extends E, ? extends V> m) {
            val.putAll(m);

            return this;
        }

        public MultimapBuilder<K, E, V> remove(Object k, Object e) {
            val.remove(k, e);

            return this;
        }

        public MultimapBuilder<K, E, V> removeAll(K k) {
            val.removeAll(k);

            return this;
        }

        public MultimapBuilder<K, E, V> removeAll(Collection<? extends K> c) {
            for (Object k : c) {
                val.removeAll(k);
            }

            return this;
        }

        public MultimapBuilder<K, E, V> removeAll(Map<? extends K, ? extends E> m) {
            val.removeAll(m);

            return this;
        }

        public MultimapBuilder<K, E, V> removeAll(Multimap<? extends K, ? extends E, ? extends V> m) {
            val.removeAll(m);

            return this;
        }
    }

    public static final class X<T> extends Builder<T> {
        private X(T val) {
            super(val);
        }
    }
}
