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

package com.landawn.abacus.util.stream;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import com.landawn.abacus.util.Fn;
import com.landawn.abacus.util.IndexedShort;
import com.landawn.abacus.util.Joiner;
import com.landawn.abacus.util.Multimap;
import com.landawn.abacus.util.Multiset;
import com.landawn.abacus.util.MutableLong;
import com.landawn.abacus.util.MutableShort;
import com.landawn.abacus.util.N;
import com.landawn.abacus.util.Nth;
import com.landawn.abacus.util.Optional;
import com.landawn.abacus.util.OptionalShort;
import com.landawn.abacus.util.Pair;
import com.landawn.abacus.util.Percentage;
import com.landawn.abacus.util.ShortIterator;
import com.landawn.abacus.util.ShortList;
import com.landawn.abacus.util.ShortMatrix;
import com.landawn.abacus.util.ShortSummaryStatistics;
import com.landawn.abacus.util.function.BiConsumer;
import com.landawn.abacus.util.function.BiFunction;
import com.landawn.abacus.util.function.BinaryOperator;
import com.landawn.abacus.util.function.Consumer;
import com.landawn.abacus.util.function.Function;
import com.landawn.abacus.util.function.ObjShortConsumer;
import com.landawn.abacus.util.function.Predicate;
import com.landawn.abacus.util.function.ShortBiFunction;
import com.landawn.abacus.util.function.ShortBiPredicate;
import com.landawn.abacus.util.function.ShortConsumer;
import com.landawn.abacus.util.function.ShortFunction;
import com.landawn.abacus.util.function.ShortPredicate;
import com.landawn.abacus.util.function.ShortTriFunction;
import com.landawn.abacus.util.function.Supplier;
import com.landawn.abacus.util.function.ToShortFunction;

/**
 * This class is a sequential, stateful and immutable stream implementation.
 *
 * @since 0.8
 * 
 * @author Haiyang Li
 */
abstract class AbstractShortStream extends ShortStream {

    AbstractShortStream(final Collection<Runnable> closeHandlers, final boolean sorted) {
        super(closeHandlers, sorted);
    }

    @Override
    public ShortStream remove(final long n, final ShortConsumer action) {
        if (n < 0) {
            throw new IllegalArgumentException("'n' can't be less than 0");
        } else if (n == 0) {
            return this;
        }

        if (this.isParallel()) {
            final AtomicLong cnt = new AtomicLong(n);

            return removeWhile(new ShortPredicate() {
                @Override
                public boolean test(short value) {
                    return cnt.getAndDecrement() > 0;
                }
            }, action);
        } else {
            final MutableLong cnt = MutableLong.of(n);

            return removeWhile(new ShortPredicate() {
                @Override
                public boolean test(short value) {
                    return cnt.getAndDecrement() > 0;
                }
            }, action);
        }
    }

    @Override
    public ShortStream removeIf(final ShortPredicate predicate) {
        N.requireNonNull(predicate);

        return filter(new ShortPredicate() {
            @Override
            public boolean test(short value) {
                return predicate.test(value) == false;
            }
        });
    }

    @Override
    public ShortStream removeIf(final ShortPredicate predicate, final ShortConsumer action) {
        N.requireNonNull(predicate);
        N.requireNonNull(predicate);

        return filter(new ShortPredicate() {
            @Override
            public boolean test(short value) {
                if (predicate.test(value)) {
                    action.accept(value);
                    return false;
                }

                return true;
            }
        });
    }

    @Override
    public ShortStream removeWhile(final ShortPredicate predicate, final ShortConsumer action) {
        N.requireNonNull(predicate);
        N.requireNonNull(action);

        return dropWhile(new ShortPredicate() {
            @Override
            public boolean test(short value) {
                if (predicate.test(value)) {
                    action.accept(value);
                    return true;
                }

                return false;
            }
        });
    }

    @Override
    public ShortStream step(final long step) {
        N.checkArgument(step > 0, "'step' can't be 0 or negative: %s", step);

        if (step == 1) {
            return this;
        }

        final long skip = step - 1;
        final ExShortIterator iter = this.exIterator();

        final ShortIterator shortIterator = new ExShortIterator() {
            @Override
            public boolean hasNext() {
                return iter.hasNext();
            }

            @Override
            public short nextShort() {
                final short next = iter.nextShort();
                iter.skip(skip);
                return next;
            }
        };

        return newStream(shortIterator, sorted);
    }

    @Override
    public Stream<ShortStream> split(final int size) {
        return splitToList(size).map(new Function<ShortList, ShortStream>() {
            @Override
            public ShortStream apply(ShortList t) {
                return new ArrayShortStream(t.array(), 0, t.size(), null, sorted);
            }
        });
    }

    @Override
    public <U> Stream<ShortStream> split(final U identity, final BiFunction<? super Short, ? super U, Boolean> predicate,
            final Consumer<? super U> identityUpdate) {
        return splitToList(identity, predicate, identityUpdate).map(new Function<ShortList, ShortStream>() {
            @Override
            public ShortStream apply(ShortList t) {
                return new ArrayShortStream(t.array(), 0, t.size(), null, sorted);
            }
        });
    }

    @Override
    public Stream<ShortStream> sliding(final int windowSize, final int increment) {
        return slidingToList(windowSize, increment).map(new Function<ShortList, ShortStream>() {
            @Override
            public ShortStream apply(ShortList t) {
                return new ArrayShortStream(t.array(), 0, t.size(), null, sorted);
            }
        });
    }

    @Override
    public ShortStream collapse(final ShortBiPredicate collapsible, final ShortBiFunction<Short> mergeFunction) {
        final ExShortIterator iter = exIterator();

        return this.newStream(new ExShortIterator() {
            private boolean hasNext = false;
            private short next = 0;

            @Override
            public boolean hasNext() {
                return hasNext || iter.hasNext();
            }

            @Override
            public short nextShort() {
                short res = hasNext ? next : (next = iter.nextShort());

                while ((hasNext = iter.hasNext())) {
                    if (collapsible.test(next, (next = iter.nextShort()))) {
                        res = mergeFunction.apply(res, next);
                    } else {
                        break;
                    }
                }

                return res;
            }
        }, false);
    }

    @Override
    public ShortStream scan(final ShortBiFunction<Short> accumulator) {
        final ExShortIterator iter = exIterator();

        return this.newStream(new ExShortIterator() {
            private short res = 0;
            private boolean isFirst = true;

            @Override
            public boolean hasNext() {
                return iter.hasNext();
            }

            @Override
            public short nextShort() {
                if (isFirst) {
                    isFirst = false;
                    return (res = iter.nextShort());
                } else {
                    return (res = accumulator.apply(res, iter.nextShort()));
                }
            }
        }, false);
    }

    @Override
    public ShortStream scan(final short seed, final ShortBiFunction<Short> accumulator) {
        final ExShortIterator iter = exIterator();

        return this.newStream(new ExShortIterator() {
            private short res = seed;

            @Override
            public boolean hasNext() {
                return iter.hasNext();
            }

            @Override
            public short nextShort() {
                return (res = accumulator.apply(res, iter.nextShort()));
            }
        }, false);
    }

    @Override
    public ShortStream reverseSorted() {
        return sorted().reversed();
    }

    @Override
    public <K, U> Map<K, U> toMap(ShortFunction<? extends K> keyExtractor, ShortFunction<? extends U> valueMapper) {
        final Supplier<Map<K, U>> mapFactory = Fn.Suppliers.ofMap();

        return toMap(keyExtractor, valueMapper, mapFactory);
    }

    @Override
    public <K, U, M extends Map<K, U>> M toMap(ShortFunction<? extends K> keyExtractor, ShortFunction<? extends U> valueMapper, Supplier<M> mapFactory) {
        final BinaryOperator<U> mergeFunction = Fn.throwingMerger();

        return toMap(keyExtractor, valueMapper, mergeFunction, mapFactory);
    }

    @Override
    public <K, U> Map<K, U> toMap(ShortFunction<? extends K> keyExtractor, ShortFunction<? extends U> valueMapper, BinaryOperator<U> mergeFunction) {
        final Supplier<Map<K, U>> mapFactory = Fn.Suppliers.ofMap();

        return toMap(keyExtractor, valueMapper, mergeFunction, mapFactory);
    }

    @Override
    public <K, A, D> Map<K, D> toMap(ShortFunction<? extends K> classifier, Collector<Short, A, D> downstream) {
        final Supplier<Map<K, D>> mapFactory = Fn.Suppliers.ofMap();

        return toMap(classifier, downstream, mapFactory);
    }

    @Override
    public <K> Multimap<K, Short, List<Short>> toMultimap(ShortFunction<? extends K> keyExtractor) {
        return toMultimap(keyExtractor, ShortFunction.BOX);
    }

    @Override
    public <K, V extends Collection<Short>> Multimap<K, Short, V> toMultimap(ShortFunction<? extends K> keyExtractor,
            Supplier<Multimap<K, Short, V>> mapFactory) {
        return toMultimap(keyExtractor, ShortFunction.BOX, mapFactory);
    }

    @Override
    public <K, U> Multimap<K, U, List<U>> toMultimap(ShortFunction<? extends K> keyExtractor, ShortFunction<? extends U> valueMapper) {
        return toMultimap(keyExtractor, valueMapper, new Supplier<Multimap<K, U, List<U>>>() {
            @Override
            public Multimap<K, U, List<U>> get() {
                return N.newListMultimap();
            }
        });
    }

    @Override
    public ShortMatrix toMatrix() {
        return ShortMatrix.of(toArray());
    }

    @Override
    public ShortStream distinct() {
        final Set<Object> set = new HashSet<>();

        return newStream(this.sequential().filter(new ShortPredicate() {
            @Override
            public boolean test(short value) {
                return set.add(value);
            }
        }).exIterator(), sorted);
    }

    @Override
    public OptionalShort first() {
        final ShortIterator iter = this.exIterator();

        return iter.hasNext() ? OptionalShort.of(iter.nextShort()) : OptionalShort.empty();
    }

    @Override
    public OptionalShort last() {
        final ShortIterator iter = this.exIterator();

        if (iter.hasNext() == false) {
            return OptionalShort.empty();
        }

        short next = iter.nextShort();

        while (iter.hasNext()) {
            next = iter.nextShort();
        }

        return OptionalShort.of(next);
    }

    @Override
    public OptionalShort findFirstOrLast(ShortPredicate predicateForFirst, ShortPredicate predicateForLast) {
        final ExShortIterator iter = exIterator();
        MutableShort last = null;
        short next = 0;

        while (iter.hasNext()) {
            next = iter.nextShort();

            if (predicateForFirst.test(next)) {
                return OptionalShort.of(next);
            } else if (predicateForLast.test(next)) {
                if (last == null) {
                    last = MutableShort.of(next);
                } else {
                    last.setValue(next);
                }
            }
        }

        return last == null ? OptionalShort.empty() : OptionalShort.of(last.value());
    }

    @Override
    public Pair<OptionalShort, OptionalShort> findFirstAndLast(ShortPredicate predicateForFirst, ShortPredicate predicateForLast) {
        final Pair<OptionalShort, OptionalShort> result = new Pair<>();
        final ExShortIterator iter = exIterator();
        MutableShort last = null;
        short next = 0;

        while (iter.hasNext()) {
            next = iter.nextShort();

            if (result.left == null && predicateForFirst.test(next)) {
                result.left = OptionalShort.of(next);
            }

            if (predicateForLast.test(next)) {
                if (last == null) {
                    last = MutableShort.of(next);
                } else {
                    last.setValue(next);
                }
            }
        }

        if (result.left == null) {
            result.left = OptionalShort.empty();
        }

        result.right = last == null ? OptionalShort.empty() : OptionalShort.of(last.value());

        return result;
    }

    @Override
    public ShortStream intersection(final Collection<?> c) {
        final Multiset<?> multiset = Multiset.from(c);

        return newStream(this.sequential().filter(new ShortPredicate() {
            @Override
            public boolean test(short value) {
                return multiset.getAndRemove(value) > 0;
            }
        }).exIterator(), sorted);
    }

    @Override
    public ShortStream difference(final Collection<?> c) {
        final Multiset<?> multiset = Multiset.from(c);

        return newStream(this.sequential().filter(new ShortPredicate() {
            @Override
            public boolean test(short value) {
                return multiset.getAndRemove(value) < 1;
            }
        }).exIterator(), sorted);
    }

    @Override
    public ShortStream symmetricDifference(final Collection<Short> c) {
        final Multiset<?> multiset = Multiset.from(c);

        return newStream(this.sequential().filter(new ShortPredicate() {
            @Override
            public boolean test(short value) {
                return multiset.getAndRemove(value) < 1;
            }
        }).append(Stream.of(c).filter(new Predicate<Short>() {
            @Override
            public boolean test(Short value) {
                return multiset.getAndRemove(value) > 0;
            }
        }).mapToShort(ToShortFunction.UNBOX)).exIterator(), false);
    }

    @Override
    public Stream<ShortStream> splitAt(final int n) {
        if (n < 0) {
            throw new IllegalArgumentException("'n' can't be negative");
        }

        final ShortIterator iter = this.exIterator();
        final ShortList list = new ShortList();

        while (list.size() < n && iter.hasNext()) {
            list.add(iter.nextShort());
        }

        final ShortStream[] a = { new ArrayShortStream(list.array(), 0, list.size(), null, sorted), new IteratorShortStream(iter, null, sorted) };

        return this.newStream(a, false, null);
    }

    @Override
    public Stream<ShortStream> splitBy(ShortPredicate where) {
        N.requireNonNull(where);

        final ShortIterator iter = this.exIterator();
        final ShortList list = new ShortList();
        short next = 0;
        ShortStream s = null;

        while (iter.hasNext()) {
            next = iter.nextShort();

            if (where.test(next)) {
                list.add(next);
            } else {
                s = ShortStream.of(next);

                break;
            }
        }

        final ShortStream[] a = { new ArrayShortStream(list.array(), 0, list.size(), null, sorted), new IteratorShortStream(iter, null, sorted) };

        if (s != null) {
            if (sorted) {
                a[1] = new IteratorShortStream(a[1].prepend(s).exIterator(), null, sorted);
            } else {
                a[1] = a[1].prepend(s);
            }
        }

        return this.newStream(a, false, null);
    }

    @Override
    public ShortStream reversed() {
        final short[] tmp = toArray();

        return newStream(new ExShortIterator() {
            private int cursor = tmp.length;

            @Override
            public boolean hasNext() {
                return cursor > 0;
            }

            @Override
            public short nextShort() {
                if (cursor <= 0) {
                    throw new NoSuchElementException();
                }

                return tmp[--cursor];
            }

            @Override
            public long count() {
                return cursor;
            }

            @Override
            public void skip(long n) {
                cursor = n < cursor ? cursor - (int) n : 0;
            }

            @Override
            public short[] toArray() {
                final short[] a = new short[cursor];

                for (int i = 0, len = tmp.length; i < len; i++) {
                    a[i] = tmp[cursor - i - 1];
                }

                return a;
            }
        }, false);
    }

    @Override
    public ShortStream shuffled() {
        final short[] a = toArray();

        N.shuffle(a);

        return newStream(a, false);
    }

    @Override
    public ShortStream shuffled(final Random rnd) {
        final short[] a = toArray();

        N.shuffle(a, rnd);

        return newStream(a, false);
    }

    @Override
    public ShortStream rotated(int distance) {
        final short[] a = toArray();

        N.rotate(a, distance);

        return newStream(a, false);
    }

    @Override
    public Optional<Map<Percentage, Short>> distribution() {
        final short[] a = sorted().toArray();

        if (a.length == 0) {
            return Optional.empty();
        }

        return Optional.of(N.distribution(a));
    }

    @Override
    public Pair<ShortSummaryStatistics, Optional<Map<Percentage, Short>>> summarize2() {
        final short[] a = sorted().toArray();

        if (N.isNullOrEmpty(a)) {
            return Pair.of(new ShortSummaryStatistics(), Optional.<Map<Percentage, Short>> empty());
        } else {
            return Pair.of(new ShortSummaryStatistics(a.length, N.sum(a), a[0], a[a.length - 1]), Optional.of(N.distribution(a)));
        }
    }

    @Override
    public String join(final CharSequence delimiter) {
        return join(delimiter, "", "");
    }

    @Override
    public String join(final CharSequence delimiter, final CharSequence prefix, final CharSequence suffix) {
        final Supplier<Joiner> supplier = new Supplier<Joiner>() {
            @Override
            public Joiner get() {
                return Joiner.with(delimiter, prefix, suffix);
            }
        };

        final ObjShortConsumer<Joiner> accumulator = new ObjShortConsumer<Joiner>() {
            @Override
            public void accept(Joiner a, short t) {
                a.add(t);
            }
        };

        final BiConsumer<Joiner, Joiner> combiner = new BiConsumer<Joiner, Joiner>() {
            @Override
            public void accept(Joiner a, Joiner b) {
                a.merge(b);
            }
        };

        final Joiner joiner = collect(supplier, accumulator, combiner);

        return joiner.toString();
    }

    @Override
    public <R> R collect(Supplier<R> supplier, ObjShortConsumer<R> accumulator) {
        final BiConsumer<R, R> combiner = collectingCombiner;

        return collect(supplier, accumulator, combiner);
    }

    @Override
    public Pair<OptionalShort, ShortStream> headAndTail() {
        return Pair.of(head(), tail());
    }

    @Override
    public Pair<ShortStream, OptionalShort> headAndTail2() {
        return Pair.of(head2(), tail2());
    }

    @Override
    public Stream<IndexedShort> indexed() {
        return newStream(this.sequential().mapToObj(new ShortFunction<IndexedShort>() {
            final MutableLong idx = MutableLong.of(0);

            @Override
            public IndexedShort apply(short t) {
                return IndexedShort.of(t, idx.getAndIncrement());
            }
        }).iterator(), true, INDEXED_SHORT_COMPARATOR);
    }

    @Override
    public ShortStream append(ShortStream stream) {
        return ShortStream.concat(this, stream);
    }

    @Override
    public ShortStream prepend(ShortStream stream) {
        return ShortStream.concat(stream, this);
    }

    @Override
    public ShortStream merge(ShortStream b, ShortBiFunction<Nth> nextSelector) {
        return ShortStream.merge(this, b, nextSelector);
    }

    @Override
    public ShortStream zipWith(ShortStream b, ShortBiFunction<Short> zipFunction) {
        return ShortStream.zip(this, b, zipFunction);
    }

    @Override
    public ShortStream zipWith(ShortStream b, ShortStream c, ShortTriFunction<Short> zipFunction) {
        return ShortStream.zip(this, b, c, zipFunction);
    }

    @Override
    public ShortStream zipWith(ShortStream b, short valueForNoneA, short valueForNoneB, ShortBiFunction<Short> zipFunction) {
        return ShortStream.zip(this, b, valueForNoneA, valueForNoneB, zipFunction);
    }

    @Override
    public ShortStream zipWith(ShortStream b, ShortStream c, short valueForNoneA, short valueForNoneB, short valueForNoneC,
            ShortTriFunction<Short> zipFunction) {
        return ShortStream.zip(this, b, c, valueForNoneA, valueForNoneB, valueForNoneC, zipFunction);
    }

    @Override
    public ShortStream cached() {
        return this.newStream(toArray(), sorted);
    }
}
