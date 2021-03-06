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

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import com.landawn.abacus.util.function.BiConsumer;
import com.landawn.abacus.util.function.BiFunction;
import com.landawn.abacus.util.function.Consumer;
import com.landawn.abacus.util.function.DoubleConsumer;
import com.landawn.abacus.util.function.FloatConsumer;
import com.landawn.abacus.util.function.Function;
import com.landawn.abacus.util.function.IntConsumer;
import com.landawn.abacus.util.function.LongConsumer;
import com.landawn.abacus.util.stream.Stream;

/**
 * 
 * @since 0.8
 * 
 * @author Haiyang Li
 *
 * @param <L>
 * @param <R>
 */
public final class Pair<L, R> implements Map.Entry<L, R> {
    public volatile L left;
    public volatile R right;

    public Pair() {
    }

    Pair(final L l, final R r) {
        this.left = l;
        this.right = r;
    }

    public static <L, R> Pair<L, R> of(final L l, final R r) {
        return new Pair<>(l, r);
    }

    public static <T> Pair<T, T> from(T[] a) {
        if (N.isNullOrEmpty(a)) {
            return new Pair<>(null, null);
        } else if (a.length == 1) {
            return new Pair<>(a[0], null);
        } else {
            return new Pair<>(a[0], a[1]);
        }
    }

    public static <T> Pair<T, T> from(Collection<? extends T> c) {
        if (N.isNullOrEmpty(c)) {
            return new Pair<>(null, null);
        } else if (c.size() == 1) {
            return new Pair<T, T>(c.iterator().next(), null);
        } else {
            final Iterator<? extends T> iter = c.iterator();
            return new Pair<T, T>(iter.next(), iter.next());
        }
    }

    public L left() {
        return left;
    }

    public R right() {
        return right;
    }

    public L getLeft() {
        return left;
    }

    public Pair<L, R> setLeft(final L left) {
        this.left = left;

        return this;
    }

    public R getRight() {
        return right;
    }

    public Pair<L, R> setRight(final R right) {
        this.right = right;

        return this;
    }

    public Pair<L, R> set(final L left, final R right) {
        this.left = left;
        this.right = right;

        return this;
    }

    @Override
    public L getKey() {
        return left;
    }

    @Override
    public R getValue() {
        return right;
    }

    @Override
    public R setValue(R value) {
        R oldValue = this.right;
        this.right = value;

        return oldValue;
    }

    /**
     * 
     * @return a new instance of Pair&lt;R, L&gt;.
     */
    public Pair<R, L> reversed() {
        return new Pair<>(this.right, this.left);
    }

    public Pair<L, R> copy() {
        return new Pair<>(this.left, this.right);
    }

    public Object[] toArray() {
        return new Object[] { left, right };
    }

    public <A> A[] toArray(A[] a) {
        if (a.length < 2) {
            a = N.copyOf(a, 2);
        }

        a[0] = (A) left;
        a[1] = (A) right;

        return a;
    }

    public void forEach(Consumer<?> comsumer) {
        final Consumer<Object> objComsumer = (Consumer<Object>) comsumer;

        objComsumer.accept(left);
        objComsumer.accept(right);
    }

    public void accept(final BiConsumer<? super L, ? super R> action) {
        action.accept(left, right);
    }

    public void accept(final Consumer<Pair<L, R>> action) {
        action.accept(this);
    }

    public <U> U apply(final BiFunction<? super L, ? super R, U> action) {
        return action.apply(left, right);
    }

    public <U> U apply(final Function<Pair<L, R>, U> action) {
        return action.apply(this);
    }

    public Stream<Pair<L, R>> stream() {
        return Stream.of(this);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + N.hashCode(left);
        result = prime * result + N.hashCode(right);
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj instanceof Pair) {
            final Pair<L, R> other = (Pair<L, R>) obj;

            return N.equals(left, other.left) && N.equals(right, other.right);
        }

        return false;
    }

    @Override
    public String toString() {
        return "{left=" + N.toString(left) + ", right=" + N.toString(right) + "}";
    }

    public static final class IntPair {
        public final int _1;
        public final int _2;

        private IntPair(int _1, int _2) {
            this._1 = _1;
            this._2 = _2;
        }

        public static IntPair of(int _1, int _2) {
            return new IntPair(_1, _2);
        }

        public int min() {
            return N.min(_1, _2);
        }

        public int max() {
            return N.max(_1, _2);
        }

        public int sum() {
            return _1 + _2;
        }

        public double average() {
            return sum() / 2;
        }

        public IntPair reversed() {
            return new IntPair(_2, _1);
        }

        public int[] toArray() {
            return new int[] { _1, _2 };
        }

        public void forEach(IntConsumer comsumer) {
            comsumer.accept(this._1);
            comsumer.accept(this._2);
        }

        public void accept(Consumer<IntPair> action) {
            action.accept(this);
        }

        public <U> U apply(Function<IntPair, U> action) {
            return action.apply(this);
        }

        public Stream<IntPair> stream() {
            return Stream.of(this);
        }

        @Override
        public int hashCode() {
            return 31 * _1 + this._2;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            } else if (!(obj instanceof IntPair)) {
                return false;
            } else {
                IntPair other = (IntPair) obj;
                return this._1 == other._1 && this._2 == other._2;
            }
        }

        @Override
        public String toString() {
            return "[" + this._1 + ", " + this._2 + "]";
        }
    }

    public static final class LongPair {
        public final long _1;
        public final long _2;

        private LongPair(long _1, long _2) {
            this._1 = _1;
            this._2 = _2;
        }

        public static LongPair of(long _1, long _2) {
            return new LongPair(_1, _2);
        }

        public long min() {
            return N.min(_1, _2);
        }

        public long max() {
            return N.max(_1, _2);
        }

        public long sum() {
            return _1 + _2;
        }

        public double average() {
            return sum() / 2;
        }

        public LongPair reversed() {
            return new LongPair(_2, _1);
        }

        public long[] toArray() {
            return new long[] { _1, _2 };
        }

        public void forEach(LongConsumer comsumer) {
            comsumer.accept(this._1);
            comsumer.accept(this._2);
        }

        public void accept(Consumer<LongPair> action) {
            action.accept(this);
        }

        public <U> U apply(Function<LongPair, U> action) {
            return action.apply(this);
        }

        public Stream<LongPair> stream() {
            return Stream.of(this);
        }

        @Override
        public int hashCode() {
            return (int) (31 * _1 + this._2);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            } else if (!(obj instanceof LongPair)) {
                return false;
            } else {
                LongPair other = (LongPair) obj;
                return this._1 == other._1 && this._2 == other._2;
            }
        }

        @Override
        public String toString() {
            return "[" + this._1 + ", " + this._2 + "]";
        }
    }

    public static final class FloatPair {
        public final float _1;
        public final float _2;

        private FloatPair(float _1, float _2) {
            this._1 = _1;
            this._2 = _2;
        }

        public static FloatPair of(float _1, float _2) {
            return new FloatPair(_1, _2);
        }

        public float min() {
            return N.min(_1, _2);
        }

        public float max() {
            return N.max(_1, _2);
        }

        public float sum() {
            return _1 + _2;
        }

        public double average() {
            return sum() / 2;
        }

        public FloatPair reversed() {
            return new FloatPair(_2, _1);
        }

        public float[] toArray() {
            return new float[] { _1, _2 };
        }

        public void forEach(FloatConsumer comsumer) {
            comsumer.accept(this._1);
            comsumer.accept(this._2);
        }

        public void accept(Consumer<FloatPair> action) {
            action.accept(this);
        }

        public <U> U apply(Function<FloatPair, U> action) {
            return action.apply(this);
        }

        public Stream<FloatPair> stream() {
            return Stream.of(this);
        }

        @Override
        public int hashCode() {
            return (int) (31 * _1 + this._2);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            } else if (!(obj instanceof FloatPair)) {
                return false;
            } else {
                FloatPair other = (FloatPair) obj;
                return this._1 == other._1 && this._2 == other._2;
            }
        }

        @Override
        public String toString() {
            return "[" + this._1 + ", " + this._2 + "]";
        }
    }

    public static final class DoublePair {
        public final double _1;
        public final double _2;

        private DoublePair(double _1, double _2) {
            this._1 = _1;
            this._2 = _2;
        }

        public static DoublePair of(double _1, double _2) {
            return new DoublePair(_1, _2);
        }

        public double min() {
            return N.min(_1, _2);
        }

        public double max() {
            return N.max(_1, _2);
        }

        public double sum() {
            return _1 + _2;
        }

        public double average() {
            return sum() / 2;
        }

        public DoublePair reversed() {
            return new DoublePair(_2, _1);
        }

        public double[] toArray() {
            return new double[] { _1, _2 };
        }

        public void forEach(DoubleConsumer comsumer) {
            comsumer.accept(this._1);
            comsumer.accept(this._2);
        }

        public void accept(Consumer<DoublePair> action) {
            action.accept(this);
        }

        public <U> U apply(Function<DoublePair, U> action) {
            return action.apply(this);
        }

        public Stream<DoublePair> stream() {
            return Stream.of(this);
        }

        @Override
        public int hashCode() {
            return (int) (31 * _1 + this._2);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            } else if (!(obj instanceof DoublePair)) {
                return false;
            } else {
                DoublePair other = (DoublePair) obj;
                return this._1 == other._1 && this._2 == other._2;
            }
        }

        @Override
        public String toString() {
            return "[" + this._1 + ", " + this._2 + "]";
        }
    }
}
