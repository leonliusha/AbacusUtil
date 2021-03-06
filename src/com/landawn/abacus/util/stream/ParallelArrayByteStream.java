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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.Callable;

import com.landawn.abacus.util.ByteList;
import com.landawn.abacus.util.ByteSummaryStatistics;
import com.landawn.abacus.util.CompletableFuture;
import com.landawn.abacus.util.IndexedByte;
import com.landawn.abacus.util.LongMultiset;
import com.landawn.abacus.util.Multimap;
import com.landawn.abacus.util.Multiset;
import com.landawn.abacus.util.MutableBoolean;
import com.landawn.abacus.util.MutableInt;
import com.landawn.abacus.util.N;
import com.landawn.abacus.util.Nth;
import com.landawn.abacus.util.NullabLe;
import com.landawn.abacus.util.OptionalByte;
import com.landawn.abacus.util.OptionalDouble;
import com.landawn.abacus.util.Output;
import com.landawn.abacus.util.Pair;
import com.landawn.abacus.util.function.BiConsumer;
import com.landawn.abacus.util.function.BiFunction;
import com.landawn.abacus.util.function.BinaryOperator;
import com.landawn.abacus.util.function.ByteBiFunction;
import com.landawn.abacus.util.function.ByteBinaryOperator;
import com.landawn.abacus.util.function.ByteConsumer;
import com.landawn.abacus.util.function.ByteFunction;
import com.landawn.abacus.util.function.BytePredicate;
import com.landawn.abacus.util.function.ByteToIntFunction;
import com.landawn.abacus.util.function.ByteTriFunction;
import com.landawn.abacus.util.function.ByteUnaryOperator;
import com.landawn.abacus.util.function.Consumer;
import com.landawn.abacus.util.function.Function;
import com.landawn.abacus.util.function.ObjByteConsumer;
import com.landawn.abacus.util.function.Predicate;
import com.landawn.abacus.util.function.Supplier;
import com.landawn.abacus.util.function.ToByteFunction;
import com.landawn.abacus.util.function.ToIntFunction;

/**
 * This class is a sequential, stateful and immutable stream implementation.
 *
 * @since 0.8
 * 
 * @author Haiyang Li
 */
final class ParallelArrayByteStream extends ArrayByteStream {
    private final int maxThreadNum;
    private final Splitor splitor;
    private volatile ArrayByteStream sequential;
    private volatile Stream<Byte> boxed;

    ParallelArrayByteStream(final byte[] values, final int fromIndex, final int toIndex, final Collection<Runnable> closeHandlers, final boolean sorted,
            int maxThreadNum, Splitor splitor) {
        super(values, fromIndex, toIndex, closeHandlers, sorted);

        this.maxThreadNum = fromIndex >= toIndex ? 1 : N.min(maxThreadNum, MAX_THREAD_NUM_PER_OPERATION, toIndex - fromIndex);
        this.splitor = splitor == null ? DEFAULT_SPLITOR : splitor;
    }

    @Override
    public ByteStream filter(final BytePredicate predicate) {
        if (maxThreadNum <= 1) {
            return new ParallelIteratorByteStream(sequential().filter(predicate).exIterator(), closeHandlers, sorted, maxThreadNum, splitor);
        }

        final Stream<Byte> stream = boxed().filter(new Predicate<Byte>() {
            @Override
            public boolean test(Byte value) {
                return predicate.test(value.byteValue());
            }
        });

        return new ParallelIteratorByteStream(stream, closeHandlers, false, maxThreadNum, splitor);
    }

    @Override
    public ByteStream takeWhile(final BytePredicate predicate) {
        if (maxThreadNum <= 1) {
            return new ParallelIteratorByteStream(sequential().takeWhile(predicate).exIterator(), closeHandlers, sorted, maxThreadNum, splitor);
        }

        final Stream<Byte> stream = boxed().takeWhile(new Predicate<Byte>() {
            @Override
            public boolean test(Byte value) {
                return predicate.test(value.byteValue());
            }
        });

        return new ParallelIteratorByteStream(stream, closeHandlers, false, maxThreadNum, splitor);
    }

    @Override
    public ByteStream dropWhile(final BytePredicate predicate) {
        if (maxThreadNum <= 1) {
            return new ParallelIteratorByteStream(sequential().dropWhile(predicate).exIterator(), closeHandlers, sorted, maxThreadNum, splitor);
        }

        final Stream<Byte> stream = boxed().dropWhile(new Predicate<Byte>() {
            @Override
            public boolean test(Byte value) {
                return predicate.test(value.byteValue());
            }
        });

        return new ParallelIteratorByteStream(stream, closeHandlers, false, maxThreadNum, splitor);
    }

    @Override
    public ByteStream map(final ByteUnaryOperator mapper) {
        if (maxThreadNum <= 1) {
            return new ParallelIteratorByteStream(sequential().map(mapper).exIterator(), closeHandlers, false, maxThreadNum, splitor);
        }

        final ByteStream stream = boxed().mapToByte(new ToByteFunction<Byte>() {
            @Override
            public byte applyAsByte(Byte value) {
                return mapper.applyAsByte(value.byteValue());
            }
        });

        return new ParallelIteratorByteStream(stream, closeHandlers, false, maxThreadNum, splitor);
    }

    @Override
    public IntStream mapToInt(final ByteToIntFunction mapper) {
        if (maxThreadNum <= 1) {
            return new ParallelIteratorIntStream(sequential().mapToInt(mapper).exIterator(), closeHandlers, false, maxThreadNum, splitor);
        }

        final IntStream stream = boxed().mapToInt(new ToIntFunction<Byte>() {
            @Override
            public int applyAsInt(Byte value) {
                return mapper.applyAsInt(value);
            }
        });

        return new ParallelIteratorIntStream(stream, closeHandlers, false, maxThreadNum, splitor);
    }

    @Override
    public <U> Stream<U> mapToObj(final ByteFunction<? extends U> mapper) {
        if (maxThreadNum <= 1) {
            return new ParallelIteratorStream<U>(sequential().mapToObj(mapper).iterator(), closeHandlers, false, null, maxThreadNum, splitor);
        }

        return boxed().map(new Function<Byte, U>() {
            @Override
            public U apply(Byte value) {
                return mapper.apply(value);
            }
        });
    }

    @Override
    public ByteStream flatMap(final ByteFunction<? extends ByteStream> mapper) {
        if (maxThreadNum <= 1) {
            return new ParallelIteratorByteStream(sequential().flatMap(mapper).exIterator(), closeHandlers, false, maxThreadNum, splitor);
        }

        final ByteStream stream = boxed().flatMapToByte(new Function<Byte, ByteStream>() {
            @Override
            public ByteStream apply(Byte value) {
                return mapper.apply(value);
            }
        });

        return new ParallelIteratorByteStream(stream, closeHandlers, false, maxThreadNum, splitor);
    }

    @Override
    public IntStream flatMapToInt(final ByteFunction<? extends IntStream> mapper) {
        if (maxThreadNum <= 1) {
            return new ParallelIteratorIntStream(sequential().flatMapToInt(mapper).exIterator(), closeHandlers, false, maxThreadNum, splitor);
        }

        final IntStream stream = boxed().flatMapToInt(new Function<Byte, IntStream>() {
            @Override
            public IntStream apply(Byte value) {
                return mapper.apply(value);
            }
        });

        return new ParallelIteratorIntStream(stream, closeHandlers, false, maxThreadNum, splitor);
    }

    @Override
    public <T> Stream<T> flatMapToObj(final ByteFunction<? extends Stream<T>> mapper) {
        if (maxThreadNum <= 1) {
            return new ParallelIteratorStream<>(sequential().flatMapToObj(mapper).iterator(), closeHandlers, false, null, maxThreadNum, splitor);
        }

        return boxed().flatMap(new Function<Byte, Stream<T>>() {
            @Override
            public Stream<T> apply(Byte value) {
                return mapper.apply(value);
            }
        });
    }

    @Override
    public Stream<ByteStream> split(final int size) {
        return new ParallelIteratorStream<ByteStream>(sequential().split(size).iterator(), closeHandlers, false, null, maxThreadNum, splitor);
    }

    @Override
    public Stream<ByteList> splitToList(final int size) {
        return new ParallelIteratorStream<ByteList>(sequential().splitToList(size).iterator(), closeHandlers, false, null, maxThreadNum, splitor);
    }

    @Override
    public <U> Stream<ByteStream> split(final U identity, final BiFunction<? super Byte, ? super U, Boolean> predicate,
            final Consumer<? super U> identityUpdate) {
        return new ParallelIteratorStream<ByteStream>(sequential().split(identity, predicate, identityUpdate).iterator(), closeHandlers, false, null,
                maxThreadNum, splitor);
    }

    @Override
    public <U> Stream<ByteList> splitToList(final U identity, final BiFunction<? super Byte, ? super U, Boolean> predicate,
            final Consumer<? super U> identityUpdate) {
        return new ParallelIteratorStream<ByteList>(sequential().splitToList(identity, predicate, identityUpdate).iterator(), closeHandlers, false, null,
                maxThreadNum, splitor);
    }

    @Override
    public Stream<ByteStream> splitAt(final int n) {
        if (n < 0) {
            throw new IllegalArgumentException("'n' can't be negative");
        }

        final ByteStream[] a = new ByteStream[2];
        final int middleIndex = n < toIndex - fromIndex ? fromIndex + n : toIndex;
        a[0] = middleIndex == fromIndex ? ByteStream.empty() : new ArrayByteStream(elements, fromIndex, middleIndex, null, sorted);
        a[1] = middleIndex == toIndex ? ByteStream.empty() : new ArrayByteStream(elements, middleIndex, toIndex, null, sorted);

        return new ParallelArrayStream<>(a, 0, a.length, closeHandlers, false, null, maxThreadNum, splitor);
    }

    @Override
    public Stream<ByteStream> splitBy(final BytePredicate where) {
        N.requireNonNull(where);

        final NullabLe<IndexedByte> first = indexed().findFirst(new Predicate<IndexedByte>() {
            @Override
            public boolean test(IndexedByte indexed) {
                return !where.test(indexed.value());
            }
        });

        return splitAt(first.isPresent() ? (int) first.get().index() : toIndex - fromIndex);
    }

    @Override
    public Stream<ByteStream> sliding(final int windowSize, final int increment) {
        return new ParallelIteratorStream<ByteStream>(sequential().sliding(windowSize, increment).iterator(), closeHandlers, false, null, maxThreadNum,
                splitor);
    }

    @Override
    public Stream<ByteList> slidingToList(final int windowSize, final int increment) {
        return new ParallelIteratorStream<ByteList>(sequential().slidingToList(windowSize, increment).iterator(), closeHandlers, false, null, maxThreadNum, splitor);
    }

    @Override
    public ByteStream sorted() {
        if (sorted) {
            return this;
        }

        final byte[] a = N.copyOfRange(elements, fromIndex, toIndex);
        N.parallelSort(a);
        return new ParallelArrayByteStream(a, 0, a.length, closeHandlers, true, maxThreadNum, splitor);
    }

    @Override
    public ByteStream peek(final ByteConsumer action) {
        if (maxThreadNum <= 1) {
            return new ParallelIteratorByteStream(sequential().peek(action).exIterator(), closeHandlers, false, maxThreadNum, splitor);
        }

        final ByteStream stream = boxed().peek(new Consumer<Byte>() {
            @Override
            public void accept(Byte t) {
                action.accept(t);
            }
        }).sequential().mapToByte(ToByteFunction.UNBOX);

        return new ParallelIteratorByteStream(stream, closeHandlers, false, maxThreadNum, splitor);
    }

    @Override
    public ByteStream limit(long maxSize) {
        if (maxSize < 0) {
            throw new IllegalArgumentException("'maxSize' can't be negative: " + maxSize);
        } else if (maxSize >= toIndex - fromIndex) {
            return this;
        }

        return new ParallelArrayByteStream(elements, fromIndex, (int) (fromIndex + maxSize), closeHandlers, sorted, maxThreadNum, splitor);
    }

    @Override
    public ByteStream skip(long n) {
        if (n < 0) {
            throw new IllegalArgumentException("The skipped number can't be negative: " + n);
        } else if (n == 0) {
            return this;
        }

        if (n >= toIndex - fromIndex) {
            return new ParallelArrayByteStream(elements, toIndex, toIndex, closeHandlers, sorted, maxThreadNum, splitor);
        } else {
            return new ParallelArrayByteStream(elements, (int) (fromIndex + n), toIndex, closeHandlers, sorted, maxThreadNum, splitor);
        }
    }

    @Override
    public void forEach(final ByteConsumer action) {
        if (maxThreadNum <= 1) {
            sequential().forEach(action);
            return;
        }

        final List<CompletableFuture<Void>> futureList = new ArrayList<>(maxThreadNum);
        final Output<Throwable> eHolder = new Output<>();

        if (splitor == Splitor.ARRAY) {
            final int sliceSize = (toIndex - fromIndex) / maxThreadNum + ((toIndex - fromIndex) % maxThreadNum == 0 ? 0 : 1);

            for (int i = 0; i < maxThreadNum; i++) {
                final int sliceIndex = i;

                futureList.add(asyncExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        int cursor = fromIndex + sliceIndex * sliceSize;
                        final int to = toIndex - cursor > sliceSize ? cursor + sliceSize : toIndex;

                        try {
                            while (cursor < to && eHolder.value() == null) {
                                action.accept(elements[cursor++]);
                            }
                        } catch (Throwable e) {
                            setError(eHolder, e);
                        }
                    }
                }));
            }
        } else {
            final MutableInt cursor = MutableInt.of(fromIndex);

            for (int i = 0; i < maxThreadNum; i++) {
                futureList.add(asyncExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        byte next = 0;

                        try {
                            while (eHolder.value() == null) {
                                synchronized (elements) {
                                    if (cursor.intValue() < toIndex) {
                                        next = elements[cursor.getAndIncrement()];
                                    } else {
                                        break;
                                    }
                                }

                                action.accept(next);
                            }
                        } catch (Throwable e) {
                            setError(eHolder, e);
                        }
                    }
                }));
            }
        }

        complete(futureList, eHolder);
    }

    @Override
    public byte[] toArray() {
        return N.copyOfRange(elements, fromIndex, toIndex);
    }

    @Override
    public ByteList toByteList() {
        return ByteList.of(N.copyOfRange(elements, fromIndex, toIndex));
    }

    @Override
    public List<Byte> toList() {
        final List<Byte> result = new ArrayList<>(toIndex - fromIndex);

        for (int i = fromIndex; i < toIndex; i++) {
            result.add(elements[i]);
        }

        return result;
    }

    @Override
    public <R extends List<Byte>> R toList(Supplier<R> supplier) {
        final R result = supplier.get();

        for (int i = fromIndex; i < toIndex; i++) {
            result.add(elements[i]);
        }

        return result;
    }

    @Override
    public Set<Byte> toSet() {
        final Set<Byte> result = new HashSet<>(N.min(9, N.initHashCapacity(toIndex - fromIndex)));

        for (int i = fromIndex; i < toIndex; i++) {
            result.add(elements[i]);
        }

        return result;
    }

    @Override
    public <R extends Set<Byte>> R toSet(Supplier<R> supplier) {
        final R result = supplier.get();

        for (int i = fromIndex; i < toIndex; i++) {
            result.add(elements[i]);
        }

        return result;
    }

    @Override
    public Multiset<Byte> toMultiset() {
        final Multiset<Byte> result = new Multiset<>(N.min(9, N.initHashCapacity(toIndex - fromIndex)));

        for (int i = fromIndex; i < toIndex; i++) {
            result.add(elements[i]);
        }

        return result;
    }

    @Override
    public Multiset<Byte> toMultiset(Supplier<? extends Multiset<Byte>> supplier) {
        final Multiset<Byte> result = supplier.get();

        for (int i = fromIndex; i < toIndex; i++) {
            result.add(elements[i]);
        }

        return result;
    }

    @Override
    public LongMultiset<Byte> toLongMultiset() {
        final LongMultiset<Byte> result = new LongMultiset<>(N.min(9, N.initHashCapacity(toIndex - fromIndex)));

        for (int i = fromIndex; i < toIndex; i++) {
            result.add(elements[i]);
        }

        return result;
    }

    @Override
    public LongMultiset<Byte> toLongMultiset(Supplier<? extends LongMultiset<Byte>> supplier) {
        final LongMultiset<Byte> result = supplier.get();

        for (int i = fromIndex; i < toIndex; i++) {
            result.add(elements[i]);
        }

        return result;
    }

    @Override
    public <K, U, M extends Map<K, U>> M toMap(final ByteFunction<? extends K> keyExtractor, final ByteFunction<? extends U> valueMapper,
            final BinaryOperator<U> mergeFunction, final Supplier<M> mapFactory) {
        if (maxThreadNum <= 1) {
            return sequential().toMap(keyExtractor, valueMapper, mergeFunction, mapFactory);
        }

        final Function<? super Byte, ? extends K> keyExtractor2 = new Function<Byte, K>() {
            @Override
            public K apply(Byte value) {
                return keyExtractor.apply(value);
            }
        };

        final Function<? super Byte, ? extends U> valueMapper2 = new Function<Byte, U>() {
            @Override
            public U apply(Byte value) {
                return valueMapper.apply(value);
            }
        };

        return boxed().toMap(keyExtractor2, valueMapper2, mergeFunction, mapFactory);
    }

    @Override
    public <K, A, D, M extends Map<K, D>> M toMap(final ByteFunction<? extends K> classifier, final Collector<Byte, A, D> downstream,
            final Supplier<M> mapFactory) {
        if (maxThreadNum <= 1) {
            return sequential().toMap(classifier, downstream, mapFactory);
        }

        final Function<? super Byte, ? extends K> classifier2 = new Function<Byte, K>() {
            @Override
            public K apply(Byte value) {
                return classifier.apply(value);
            }
        };

        return boxed().toMap(classifier2, downstream, mapFactory);
    }

    @Override
    public <K, U, V extends Collection<U>> Multimap<K, U, V> toMultimap(final ByteFunction<? extends K> keyExtractor, final ByteFunction<? extends U> valueMapper,
            final Supplier<Multimap<K, U, V>> mapFactory) {
        if (maxThreadNum <= 1) {
            return sequential().toMultimap(keyExtractor, valueMapper, mapFactory);
        }

        final Function<? super Byte, ? extends K> keyExtractor2 = new Function<Byte, K>() {
            @Override
            public K apply(Byte value) {
                return keyExtractor.apply(value);
            }
        };

        final Function<? super Byte, ? extends U> valueMapper2 = new Function<Byte, U>() {
            @Override
            public U apply(Byte value) {
                return valueMapper.apply(value);
            }
        };

        return boxed().toMultimap(keyExtractor2, valueMapper2, mapFactory);
    }

    @Override
    public OptionalByte first() {
        return fromIndex < toIndex ? OptionalByte.of(elements[fromIndex]) : OptionalByte.empty();
    }

    @Override
    public OptionalByte last() {
        return fromIndex < toIndex ? OptionalByte.of(elements[toIndex - 1]) : OptionalByte.empty();
    }

    @Override
    public byte reduce(final byte identity, final ByteBinaryOperator op) {
        if (maxThreadNum <= 1) {
            return sequential().reduce(identity, op);
        }

        final List<CompletableFuture<Byte>> futureList = new ArrayList<>(maxThreadNum);
        final Output<Throwable> eHolder = new Output<>();

        if (splitor == Splitor.ARRAY) {
            final int sliceSize = (toIndex - fromIndex) / maxThreadNum + ((toIndex - fromIndex) % maxThreadNum == 0 ? 0 : 1);

            for (int i = 0; i < maxThreadNum; i++) {
                final int sliceIndex = i;

                futureList.add(asyncExecutor.execute(new Callable<Byte>() {
                    @Override
                    public Byte call() {
                        int cursor = fromIndex + sliceIndex * sliceSize;
                        final int to = toIndex - cursor > sliceSize ? cursor + sliceSize : toIndex;

                        byte result = identity;

                        try {
                            while (cursor < to && eHolder.value() == null) {
                                result = op.applyAsByte(result, elements[cursor++]);
                            }
                        } catch (Throwable e) {
                            setError(eHolder, e);
                        }

                        return result;
                    }
                }));
            }
        } else {
            final MutableInt cursor = MutableInt.of(fromIndex);

            for (int i = 0; i < maxThreadNum; i++) {
                futureList.add(asyncExecutor.execute(new Callable<Byte>() {
                    @Override
                    public Byte call() {
                        byte result = identity;
                        byte next = 0;

                        try {
                            while (eHolder.value() == null) {
                                synchronized (elements) {
                                    if (cursor.intValue() < toIndex) {
                                        next = elements[cursor.getAndIncrement()];
                                    } else {
                                        break;
                                    }
                                }

                                result = op.applyAsByte(result, next);
                            }
                        } catch (Throwable e) {
                            setError(eHolder, e);
                        }

                        return result;
                    }
                }));
            }
        }

        if (eHolder.value() != null) {
            throw N.toRuntimeException(eHolder.value());
        }

        Byte result = null;

        try {
            for (CompletableFuture<Byte> future : futureList) {
                if (result == null) {
                    result = future.get();
                } else {
                    result = op.applyAsByte(result, future.get());
                }
            }
        } catch (Exception e) {
            throw N.toRuntimeException(e);
        }

        return result == null ? identity : result;
    }

    @Override
    public OptionalByte reduce(final ByteBinaryOperator accumulator) {
        if (maxThreadNum <= 1) {
            return sequential().reduce(accumulator);
        }

        final List<CompletableFuture<Byte>> futureList = new ArrayList<>(maxThreadNum);
        final Output<Throwable> eHolder = new Output<>();

        if (splitor == Splitor.ARRAY) {
            final int sliceSize = (toIndex - fromIndex) / maxThreadNum + ((toIndex - fromIndex) % maxThreadNum == 0 ? 0 : 1);

            for (int i = 0; i < maxThreadNum; i++) {
                final int sliceIndex = i;

                futureList.add(asyncExecutor.execute(new Callable<Byte>() {
                    @Override
                    public Byte call() {
                        int cursor = fromIndex + sliceIndex * sliceSize;
                        final int to = toIndex - cursor > sliceSize ? cursor + sliceSize : toIndex;

                        if (cursor >= to) {
                            return null;
                        }

                        byte result = elements[cursor++];

                        try {
                            while (cursor < to && eHolder.value() == null) {
                                result = accumulator.applyAsByte(result, elements[cursor++]);
                            }
                        } catch (Throwable e) {
                            setError(eHolder, e);
                        }

                        return result;
                    }
                }));
            }
        } else {
            final MutableInt cursor = MutableInt.of(fromIndex);

            for (int i = 0; i < maxThreadNum; i++) {
                futureList.add(asyncExecutor.execute(new Callable<Byte>() {
                    @Override
                    public Byte call() {
                        byte result = 0;

                        synchronized (elements) {
                            if (cursor.intValue() < toIndex) {
                                result = elements[cursor.getAndIncrement()];
                            } else {
                                return null;
                            }
                        }

                        byte next = 0;

                        try {
                            while (eHolder.value() == null) {
                                synchronized (elements) {
                                    if (cursor.intValue() < toIndex) {
                                        next = elements[cursor.getAndIncrement()];
                                    } else {
                                        break;
                                    }
                                }

                                result = accumulator.applyAsByte(result, next);
                            }
                        } catch (Throwable e) {
                            setError(eHolder, e);
                        }

                        return result;
                    }
                }));
            }
        }

        if (eHolder.value() != null) {
            throw N.toRuntimeException(eHolder.value());
        }

        Byte result = null;

        try {
            for (CompletableFuture<Byte> future : futureList) {
                final Byte tmp = future.get();

                if (tmp == null) {
                    continue;
                } else if (result == null) {
                    result = tmp;
                } else {
                    result = accumulator.applyAsByte(result, tmp);
                }
            }
        } catch (Exception e) {
            throw N.toRuntimeException(e);
        }

        return result == null ? OptionalByte.empty() : OptionalByte.of(result);
    }

    @Override
    public ByteStream tail() {
        if (fromIndex == toIndex) {
            return this;
        }

        return new ParallelArrayByteStream(elements, fromIndex + 1, toIndex, closeHandlers, sorted, maxThreadNum, splitor);
    }

    @Override
    public ByteStream head2() {
        if (fromIndex == toIndex) {
            return this;
        }

        return new ParallelArrayByteStream(elements, fromIndex, toIndex - 1, closeHandlers, sorted, maxThreadNum, splitor);
    }

    @Override
    public <R> R collect(final Supplier<R> supplier, final ObjByteConsumer<R> accumulator, final BiConsumer<R, R> combiner) {
        if (maxThreadNum <= 1) {
            return sequential().collect(supplier, accumulator, combiner);
        }

        final List<CompletableFuture<R>> futureList = new ArrayList<>(maxThreadNum);
        final Output<Throwable> eHolder = new Output<>();

        if (splitor == Splitor.ARRAY) {
            final int sliceSize = (toIndex - fromIndex) / maxThreadNum + ((toIndex - fromIndex) % maxThreadNum == 0 ? 0 : 1);

            for (int i = 0; i < maxThreadNum; i++) {
                final int sliceIndex = i;

                futureList.add(asyncExecutor.execute(new Callable<R>() {
                    @Override
                    public R call() {
                        int cursor = fromIndex + sliceIndex * sliceSize;
                        final int to = toIndex - cursor > sliceSize ? cursor + sliceSize : toIndex;

                        final R container = supplier.get();

                        try {
                            while (cursor < to && eHolder.value() == null) {
                                accumulator.accept(container, elements[cursor++]);
                            }
                        } catch (Throwable e) {
                            setError(eHolder, e);
                        }

                        return container;
                    }
                }));
            }
        } else {
            final MutableInt cursor = MutableInt.of(fromIndex);

            for (int i = 0; i < maxThreadNum; i++) {
                futureList.add(asyncExecutor.execute(new Callable<R>() {
                    @Override
                    public R call() {
                        final R container = supplier.get();
                        byte next = 0;

                        try {
                            while (eHolder.value() == null) {
                                synchronized (elements) {
                                    if (cursor.intValue() < toIndex) {
                                        next = elements[cursor.getAndIncrement()];
                                    } else {
                                        break;
                                    }
                                }

                                accumulator.accept(container, next);
                            }
                        } catch (Throwable e) {
                            setError(eHolder, e);
                        }

                        return container;
                    }
                }));
            }
        }

        if (eHolder.value() != null) {
            throw N.toRuntimeException(eHolder.value());
        }

        R container = (R) NONE;

        try {
            for (CompletableFuture<R> future : futureList) {
                if (container == NONE) {
                    container = future.get();
                } else {
                    combiner.accept(container, future.get());
                }
            }
        } catch (Exception e) {
            throw N.toRuntimeException(e);
        }

        return container == NONE ? supplier.get() : container;
    }

    @Override
    public OptionalByte min() {
        if (fromIndex == toIndex) {
            return OptionalByte.empty();
        } else if (sorted) {
            return OptionalByte.of(elements[fromIndex]);
        } else if (maxThreadNum <= 1) {
            return OptionalByte.of(N.min(elements, fromIndex, toIndex));
        }

        final List<CompletableFuture<Byte>> futureList = new ArrayList<>(maxThreadNum);
        final int sliceSize = (toIndex - fromIndex) / maxThreadNum + ((toIndex - fromIndex) % maxThreadNum == 0 ? 0 : 1);

        for (int i = 0; i < maxThreadNum; i++) {
            final int sliceIndex = i;

            futureList.add(asyncExecutor.execute(new Callable<Byte>() {
                @Override
                public Byte call() {
                    int cursor = fromIndex + sliceIndex * sliceSize;
                    final int to = toIndex - cursor > sliceSize ? cursor + sliceSize : toIndex;

                    return cursor >= to ? null : N.min(elements, cursor, to);
                }
            }));
        }

        Byte candidate = null;

        try {
            for (CompletableFuture<Byte> future : futureList) {
                final Byte tmp = future.get();

                if (tmp == null) {
                    continue;
                } else if (candidate == null || tmp.byteValue() < candidate.byteValue()) {
                    candidate = tmp;
                }
            }
        } catch (Exception e) {
            throw N.toRuntimeException(e);
        }

        return candidate == null ? OptionalByte.empty() : OptionalByte.of(candidate);
    }

    @Override
    public OptionalByte max() {
        if (fromIndex == toIndex) {
            return OptionalByte.empty();
        } else if (sorted) {
            return OptionalByte.of(elements[toIndex - 1]);
        } else if (maxThreadNum <= 1) {
            return OptionalByte.of(N.max(elements, fromIndex, toIndex));
        }

        final List<CompletableFuture<Byte>> futureList = new ArrayList<>(maxThreadNum);
        final int sliceSize = (toIndex - fromIndex) / maxThreadNum + ((toIndex - fromIndex) % maxThreadNum == 0 ? 0 : 1);

        for (int i = 0; i < maxThreadNum; i++) {
            final int sliceIndex = i;

            futureList.add(asyncExecutor.execute(new Callable<Byte>() {
                @Override
                public Byte call() {
                    int cursor = fromIndex + sliceIndex * sliceSize;
                    final int to = toIndex - cursor > sliceSize ? cursor + sliceSize : toIndex;
                    return cursor >= to ? null : N.max(elements, cursor, to);
                }
            }));
        }

        Byte candidate = null;

        try {
            for (CompletableFuture<Byte> future : futureList) {
                final Byte tmp = future.get();

                if (tmp == null) {
                    continue;
                } else if (candidate == null || tmp.byteValue() > candidate.byteValue()) {
                    candidate = tmp;
                }
            }
        } catch (Exception e) {
            throw N.toRuntimeException(e);
        }

        return candidate == null ? OptionalByte.empty() : OptionalByte.of(candidate);
    }

    @Override
    public OptionalByte kthLargest(int k) {
        N.checkArgument(k > 0, "'k' must be bigger than 0");

        if (k > toIndex - fromIndex) {
            return OptionalByte.empty();
        } else if (sorted) {
            return OptionalByte.of(elements[toIndex - k]);
        }

        return OptionalByte.of(N.kthLargest(elements, fromIndex, toIndex, k));
    }

    @Override
    public Long sum() {
        if (fromIndex == toIndex) {
            return 0L;
        } else if (maxThreadNum <= 1) {
            return N.sum(elements, fromIndex, toIndex);
        }

        final List<CompletableFuture<Long>> futureList = new ArrayList<>(maxThreadNum);
        final int sliceSize = (toIndex - fromIndex) / maxThreadNum + ((toIndex - fromIndex) % maxThreadNum == 0 ? 0 : 1);

        for (int i = 0; i < maxThreadNum; i++) {
            final int sliceIndex = i;

            futureList.add(asyncExecutor.execute(new Callable<Long>() {
                @Override
                public Long call() {
                    int cursor = fromIndex + sliceIndex * sliceSize;
                    final int to = toIndex - cursor > sliceSize ? cursor + sliceSize : toIndex;

                    return cursor >= to ? null : N.sum(elements, cursor, to);
                }
            }));
        }

        long result = 0;

        try {
            for (CompletableFuture<Long> future : futureList) {
                final Long tmp = future.get();

                if (tmp == null) {
                    continue;
                } else {
                    result += tmp.longValue();
                }
            }
        } catch (Exception e) {
            throw N.toRuntimeException(e);
        }

        return result;
    }

    @Override
    public OptionalDouble average() {
        if (fromIndex == toIndex) {
            return OptionalDouble.empty();
        }

        return OptionalDouble.of(sum() / toIndex - fromIndex);
    }

    @Override
    public long count() {
        return toIndex - fromIndex;
    }

    @Override
    public ByteStream reversed() {
        return new ParallelIteratorByteStream(sequential().reversed().exIterator(), closeHandlers, false, maxThreadNum, splitor);
    }

    @Override
    public ByteSummaryStatistics summarize() {
        if (fromIndex == toIndex) {
            return new ByteSummaryStatistics();
        } else if (maxThreadNum <= 1) {
            return sequential().summarize();
        }

        final List<CompletableFuture<ByteSummaryStatistics>> futureList = new ArrayList<>(maxThreadNum);
        final int sliceSize = (toIndex - fromIndex) / maxThreadNum + ((toIndex - fromIndex) % maxThreadNum == 0 ? 0 : 1);

        for (int i = 0; i < maxThreadNum; i++) {
            final int sliceIndex = i;

            futureList.add(asyncExecutor.execute(new Callable<ByteSummaryStatistics>() {
                @Override
                public ByteSummaryStatistics call() {
                    int cursor = fromIndex + sliceIndex * sliceSize;
                    final int to = toIndex - cursor > sliceSize ? cursor + sliceSize : toIndex;
                    final ByteSummaryStatistics result = new ByteSummaryStatistics();

                    for (int i = cursor; i < to; i++) {
                        result.accept(elements[i]);
                    }

                    return result;
                }
            }));
        }

        ByteSummaryStatistics result = null;

        try {
            for (CompletableFuture<ByteSummaryStatistics> future : futureList) {
                final ByteSummaryStatistics tmp = future.get();

                if (tmp == null) {
                    continue;
                } else if (result == null) {
                    result = tmp;
                } else {
                    result.combine(tmp);
                }
            }
        } catch (Exception e) {
            throw N.toRuntimeException(e);
        }

        return result;
    }

    @Override
    public boolean anyMatch(final BytePredicate predicate) {
        if (maxThreadNum <= 1) {
            return sequential().anyMatch(predicate);
        }

        final List<CompletableFuture<Void>> futureList = new ArrayList<>(maxThreadNum);
        final Output<Throwable> eHolder = new Output<>();
        final MutableBoolean result = MutableBoolean.of(false);

        if (splitor == Splitor.ARRAY) {
            final int sliceSize = (toIndex - fromIndex) / maxThreadNum + ((toIndex - fromIndex) % maxThreadNum == 0 ? 0 : 1);

            for (int i = 0; i < maxThreadNum; i++) {
                final int sliceIndex = i;

                futureList.add(asyncExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        int cursor = fromIndex + sliceIndex * sliceSize;
                        final int to = toIndex - cursor > sliceSize ? cursor + sliceSize : toIndex;

                        try {
                            while (cursor < to && result.isFalse() && eHolder.value() == null) {
                                if (predicate.test(elements[cursor++])) {
                                    result.setTrue();
                                    break;
                                }
                            }
                        } catch (Throwable e) {
                            setError(eHolder, e);
                        }
                    }
                }));
            }
        } else {
            final MutableInt cursor = MutableInt.of(fromIndex);

            for (int i = 0; i < maxThreadNum; i++) {
                futureList.add(asyncExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        byte next = 0;

                        try {
                            while (result.isFalse() && eHolder.value() == null) {
                                synchronized (elements) {
                                    if (cursor.intValue() < toIndex) {
                                        next = elements[cursor.getAndIncrement()];
                                    } else {
                                        break;
                                    }
                                }

                                if (predicate.test(next)) {
                                    result.setTrue();
                                    break;
                                }
                            }
                        } catch (Throwable e) {
                            setError(eHolder, e);
                        }
                    }
                }));
            }
        }

        complete(futureList, eHolder);

        return result.value();
    }

    @Override
    public boolean allMatch(final BytePredicate predicate) {
        if (maxThreadNum <= 1) {
            return sequential().allMatch(predicate);
        }

        final List<CompletableFuture<Void>> futureList = new ArrayList<>(maxThreadNum);
        final Output<Throwable> eHolder = new Output<>();
        final MutableBoolean result = MutableBoolean.of(true);

        if (splitor == Splitor.ARRAY) {
            final int sliceSize = (toIndex - fromIndex) / maxThreadNum + ((toIndex - fromIndex) % maxThreadNum == 0 ? 0 : 1);

            for (int i = 0; i < maxThreadNum; i++) {
                final int sliceIndex = i;

                futureList.add(asyncExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        int cursor = fromIndex + sliceIndex * sliceSize;
                        final int to = toIndex - cursor > sliceSize ? cursor + sliceSize : toIndex;

                        try {
                            while (cursor < to && result.isTrue() && eHolder.value() == null) {
                                if (predicate.test(elements[cursor++]) == false) {
                                    result.setFalse();
                                    break;
                                }
                            }
                        } catch (Throwable e) {
                            setError(eHolder, e);
                        }
                    }
                }));
            }
        } else {
            final MutableInt cursor = MutableInt.of(fromIndex);

            for (int i = 0; i < maxThreadNum; i++) {
                futureList.add(asyncExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        byte next = 0;

                        try {
                            while (result.isTrue() && eHolder.value() == null) {
                                synchronized (elements) {
                                    if (cursor.intValue() < toIndex) {
                                        next = elements[cursor.getAndIncrement()];
                                    } else {
                                        break;
                                    }
                                }

                                if (predicate.test(next) == false) {
                                    result.setFalse();
                                    break;
                                }
                            }
                        } catch (Throwable e) {
                            setError(eHolder, e);
                        }
                    }
                }));
            }
        }

        complete(futureList, eHolder);

        return result.value();
    }

    @Override
    public boolean noneMatch(final BytePredicate predicate) {
        if (maxThreadNum <= 1) {
            return sequential().noneMatch(predicate);
        }

        final List<CompletableFuture<Void>> futureList = new ArrayList<>(maxThreadNum);
        final Output<Throwable> eHolder = new Output<>();
        final MutableBoolean result = MutableBoolean.of(true);

        if (splitor == Splitor.ARRAY) {
            final int sliceSize = (toIndex - fromIndex) / maxThreadNum + ((toIndex - fromIndex) % maxThreadNum == 0 ? 0 : 1);

            for (int i = 0; i < maxThreadNum; i++) {
                final int sliceIndex = i;

                futureList.add(asyncExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        int cursor = fromIndex + sliceIndex * sliceSize;
                        final int to = toIndex - cursor > sliceSize ? cursor + sliceSize : toIndex;

                        try {
                            while (cursor < to && result.isTrue() && eHolder.value() == null) {
                                if (predicate.test(elements[cursor++])) {
                                    result.setFalse();
                                    break;
                                }
                            }
                        } catch (Throwable e) {
                            setError(eHolder, e);
                        }
                    }
                }));
            }
        } else {
            final MutableInt cursor = MutableInt.of(fromIndex);

            for (int i = 0; i < maxThreadNum; i++) {
                futureList.add(asyncExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        byte next = 0;

                        try {
                            while (result.isTrue() && eHolder.value() == null) {
                                synchronized (elements) {
                                    if (cursor.intValue() < toIndex) {
                                        next = elements[cursor.getAndIncrement()];
                                    } else {
                                        break;
                                    }
                                }

                                if (predicate.test(next)) {
                                    result.setFalse();
                                    break;
                                }
                            }
                        } catch (Throwable e) {
                            setError(eHolder, e);
                        }
                    }
                }));
            }
        }

        complete(futureList, eHolder);

        return result.value();
    }

    @Override
    public OptionalByte findFirst(final BytePredicate predicate) {
        if (maxThreadNum <= 1) {
            return sequential().findFirst(predicate);
        }

        final List<CompletableFuture<Void>> futureList = new ArrayList<>(maxThreadNum);
        final Output<Throwable> eHolder = new Output<>();
        final Output<Pair<Integer, Byte>> resultHolder = new Output<>();

        if (splitor == Splitor.ARRAY) {
            final int sliceSize = (toIndex - fromIndex) / maxThreadNum + ((toIndex - fromIndex) % maxThreadNum == 0 ? 0 : 1);

            for (int i = 0; i < maxThreadNum; i++) {
                final int sliceIndex = i;

                futureList.add(asyncExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        int cursor = fromIndex + sliceIndex * sliceSize;
                        final int to = toIndex - cursor > sliceSize ? cursor + sliceSize : toIndex;
                        final Pair<Integer, Byte> pair = new Pair<>();

                        try {
                            while (cursor < to && (resultHolder.value() == null || cursor < resultHolder.value().left) && eHolder.value() == null) {
                                pair.left = cursor;
                                pair.right = elements[cursor++];

                                if (predicate.test(pair.right)) {
                                    synchronized (resultHolder) {
                                        if (resultHolder.value() == null || pair.left < resultHolder.value().left) {
                                            resultHolder.setValue(pair.copy());
                                        }
                                    }

                                    break;
                                }
                            }
                        } catch (Throwable e) {
                            setError(eHolder, e);
                        }
                    }
                }));
            }
        } else {
            final MutableInt cursor = MutableInt.of(fromIndex);

            for (int i = 0; i < maxThreadNum; i++) {
                futureList.add(asyncExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        final Pair<Integer, Byte> pair = new Pair<>();

                        try {
                            while (resultHolder.value() == null && eHolder.value() == null) {
                                synchronized (elements) {
                                    if (cursor.intValue() < toIndex) {
                                        pair.left = cursor.intValue();
                                        pair.right = elements[cursor.getAndIncrement()];
                                    } else {
                                        break;
                                    }
                                }

                                if (predicate.test(pair.right)) {
                                    synchronized (resultHolder) {
                                        if (resultHolder.value() == null || pair.left < resultHolder.value().left) {
                                            resultHolder.setValue(pair.copy());
                                        }
                                    }

                                    break;
                                }
                            }
                        } catch (Throwable e) {
                            setError(eHolder, e);
                        }
                    }
                }));
            }
        }

        complete(futureList, eHolder);

        return resultHolder.value() == null ? OptionalByte.empty() : OptionalByte.of(resultHolder.value().right);
    }

    @Override
    public OptionalByte findLast(final BytePredicate predicate) {
        if (maxThreadNum <= 1) {
            return sequential().findLast(predicate);
        }

        final List<CompletableFuture<Void>> futureList = new ArrayList<>(maxThreadNum);
        final Output<Throwable> eHolder = new Output<>();
        final Output<Pair<Integer, Byte>> resultHolder = new Output<>();

        if (splitor == Splitor.ARRAY) {
            final int sliceSize = (toIndex - fromIndex) / maxThreadNum + ((toIndex - fromIndex) % maxThreadNum == 0 ? 0 : 1);

            for (int i = 0; i < maxThreadNum; i++) {
                final int sliceIndex = i;

                futureList.add(asyncExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        final int from = fromIndex + sliceIndex * sliceSize;
                        int cursor = toIndex - from > sliceSize ? from + sliceSize : toIndex;
                        final Pair<Integer, Byte> pair = new Pair<>();

                        try {
                            while (cursor > from && (resultHolder.value() == null || cursor > resultHolder.value().left) && eHolder.value() == null) {
                                pair.left = cursor;
                                pair.right = elements[--cursor];

                                if (predicate.test(pair.right)) {
                                    synchronized (resultHolder) {
                                        if (resultHolder.value() == null || pair.left > resultHolder.value().left) {
                                            resultHolder.setValue(pair.copy());
                                        }
                                    }

                                    break;
                                }
                            }
                        } catch (Throwable e) {
                            setError(eHolder, e);
                        }
                    }
                }));
            }
        } else {
            final MutableInt cursor = MutableInt.of(toIndex);

            for (int i = 0; i < maxThreadNum; i++) {
                futureList.add(asyncExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        final Pair<Integer, Byte> pair = new Pair<>();

                        try {
                            while (resultHolder.value() == null && eHolder.value() == null) {
                                synchronized (elements) {
                                    if (cursor.intValue() > fromIndex) {
                                        pair.left = cursor.intValue();
                                        pair.right = elements[cursor.decrementAndGet()];
                                    } else {
                                        break;
                                    }
                                }

                                if (predicate.test(pair.right)) {
                                    synchronized (resultHolder) {
                                        if (resultHolder.value() == null || pair.left > resultHolder.value().left) {
                                            resultHolder.setValue(pair.copy());
                                        }
                                    }

                                    break;
                                }
                            }
                        } catch (Throwable e) {
                            setError(eHolder, e);
                        }
                    }
                }));
            }
        }

        complete(futureList, eHolder);

        return resultHolder.value() == null ? OptionalByte.empty() : OptionalByte.of(resultHolder.value().right);
    }

    @Override
    public OptionalByte findAny(final BytePredicate predicate) {
        if (maxThreadNum <= 1) {
            return sequential().findAny(predicate);
        }

        final List<CompletableFuture<Void>> futureList = new ArrayList<>(maxThreadNum);
        final Output<Throwable> eHolder = new Output<>();
        final Output<Object> resultHolder = Output.of(NONE);

        if (splitor == Splitor.ARRAY) {
            final int sliceSize = (toIndex - fromIndex) / maxThreadNum + ((toIndex - fromIndex) % maxThreadNum == 0 ? 0 : 1);

            for (int i = 0; i < maxThreadNum; i++) {
                final int sliceIndex = i;

                futureList.add(asyncExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        int cursor = fromIndex + sliceIndex * sliceSize;
                        final int to = toIndex - cursor > sliceSize ? cursor + sliceSize : toIndex;
                        byte next = 0;

                        try {
                            while (cursor < to && resultHolder.value() == NONE && eHolder.value() == null) {
                                next = elements[cursor++];

                                if (predicate.test(next)) {
                                    synchronized (resultHolder) {
                                        if (resultHolder.value() == NONE) {
                                            resultHolder.setValue(next);
                                        }
                                    }

                                    break;
                                }
                            }
                        } catch (Throwable e) {
                            setError(eHolder, e);
                        }
                    }
                }));
            }
        } else {
            final MutableInt cursor = MutableInt.of(fromIndex);

            for (int i = 0; i < maxThreadNum; i++) {
                futureList.add(asyncExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        byte next = 0;

                        try {
                            while (resultHolder.value() == NONE && eHolder.value() == null) {
                                synchronized (elements) {
                                    if (cursor.intValue() < toIndex) {
                                        next = elements[cursor.getAndIncrement()];
                                    } else {
                                        break;
                                    }
                                }

                                if (predicate.test(next)) {
                                    synchronized (resultHolder) {
                                        if (resultHolder.value() == NONE) {
                                            resultHolder.setValue(next);
                                        }
                                    }

                                    break;
                                }
                            }
                        } catch (Throwable e) {
                            setError(eHolder, e);
                        }
                    }
                }));
            }
        }

        complete(futureList, eHolder);

        return resultHolder.value() == NONE ? OptionalByte.empty() : OptionalByte.of((Byte) resultHolder.value());
    }

    @Override
    public IntStream asIntStream() {
        return new ParallelIteratorIntStream(new ExIntIterator() {
            private int cursor = fromIndex;

            @Override
            public boolean hasNext() {
                return cursor < toIndex;
            }

            @Override
            public int nextInt() {
                if (cursor >= toIndex) {
                    throw new NoSuchElementException();
                }

                return elements[cursor++];
            }

            @Override
            public long count() {
                return toIndex - cursor;
            }

            @Override
            public void skip(long n) {
                cursor = n < toIndex - cursor ? cursor + (int) n : toIndex;
            }

            @Override
            public int[] toArray() {
                final int[] a = new int[toIndex - cursor];

                for (int i = 0, len = toIndex - cursor; i < len; i++) {
                    a[i] = elements[cursor++];
                }

                return a;
            }
        }, closeHandlers, sorted, maxThreadNum, splitor);
    }

    @Override
    public Stream<Byte> boxed() {
        Stream<Byte> tmp = boxed;

        if (tmp == null) {
            tmp = new ParallelIteratorStream<Byte>(iterator(), closeHandlers, sorted, sorted ? BYTE_COMPARATOR : null, maxThreadNum, splitor);
            boxed = tmp;
        }

        return tmp;
    }

    @Override
    public ByteStream cached() {
        return this;
    }

    @Override
    public ByteStream append(ByteStream stream) {
        return new ParallelIteratorByteStream(ByteStream.concat(this, stream), closeHandlers, false, maxThreadNum, splitor);
    }

    @Override
    public ByteStream prepend(ByteStream stream) {
        return new ParallelIteratorByteStream(ByteStream.concat(stream, this), closeHandlers, false, maxThreadNum, splitor);
    }

    @Override
    public ByteStream merge(final ByteStream b, final ByteBiFunction<Nth> nextSelector) {
        return new ParallelIteratorByteStream(ByteStream.merge(this, b, nextSelector), closeHandlers, false, maxThreadNum, splitor);
    }

    @Override
    public ByteStream zipWith(ByteStream b, ByteBiFunction<Byte> zipFunction) {
        return new ParallelIteratorByteStream(ByteStream.zip(this, b, zipFunction), closeHandlers, false, maxThreadNum, splitor);
    }

    @Override
    public ByteStream zipWith(ByteStream b, ByteStream c, ByteTriFunction<Byte> zipFunction) {
        return new ParallelIteratorByteStream(ByteStream.zip(this, b, c, zipFunction), closeHandlers, false, maxThreadNum, splitor);
    }

    @Override
    public ByteStream zipWith(ByteStream b, byte valueForNoneA, byte valueForNoneB, ByteBiFunction<Byte> zipFunction) {
        return new ParallelIteratorByteStream(ByteStream.zip(this, b, valueForNoneA, valueForNoneB, zipFunction), closeHandlers, false, maxThreadNum, splitor);
    }

    @Override
    public ByteStream zipWith(ByteStream b, ByteStream c, byte valueForNoneA, byte valueForNoneB, byte valueForNoneC, ByteTriFunction<Byte> zipFunction) {
        return new ParallelIteratorByteStream(ByteStream.zip(this, b, c, valueForNoneA, valueForNoneB, valueForNoneC, zipFunction), closeHandlers, false,
                maxThreadNum, splitor);
    }

    @Override
    public boolean isParallel() {
        return true;
    }

    @Override
    public ByteStream sequential() {
        ArrayByteStream tmp = sequential;

        if (tmp == null) {
            tmp = new ArrayByteStream(elements, fromIndex, toIndex, closeHandlers, sorted);
            sequential = tmp;
        }

        return tmp;
    }

    @Override
    public ByteStream parallel(int maxThreadNum, Splitor splitor) {
        if (maxThreadNum < 1 || maxThreadNum > MAX_THREAD_NUM_PER_OPERATION) {
            throw new IllegalArgumentException("'maxThreadNum' must not less than 1 or exceeded: " + MAX_THREAD_NUM_PER_OPERATION);
        }

        if (this.maxThreadNum == maxThreadNum && this.splitor == splitor) {
            return this;
        }

        return new ParallelArrayByteStream(elements, fromIndex, toIndex, closeHandlers, sorted, maxThreadNum, splitor);
    }

    @Override
    public int maxThreadNum() {
        return maxThreadNum;
    }

    @Override
    public ByteStream maxThreadNum(int maxThreadNum) {
        if (maxThreadNum < 1 || maxThreadNum > MAX_THREAD_NUM_PER_OPERATION) {
            throw new IllegalArgumentException("'maxThreadNum' must not less than 1 or exceeded: " + MAX_THREAD_NUM_PER_OPERATION);
        }

        if (this.maxThreadNum == maxThreadNum) {
            return this;
        }

        return new ParallelArrayByteStream(elements, fromIndex, toIndex, closeHandlers, sorted, maxThreadNum, splitor);
    }

    @Override
    public BaseStream.Splitor splitor() {
        return splitor;
    }

    @Override
    public ByteStream splitor(BaseStream.Splitor splitor) {
        if (this.splitor == splitor) {
            return this;
        }

        return new ParallelArrayByteStream(elements, fromIndex, toIndex, closeHandlers, sorted, maxThreadNum, splitor);
    }

    @Override
    public ByteStream onClose(Runnable closeHandler) {
        final Set<Runnable> newCloseHandlers = new AbstractStream.LocalLinkedHashSet<>(N.isNullOrEmpty(this.closeHandlers) ? 1 : this.closeHandlers.size() + 1);

        if (N.notNullOrEmpty(this.closeHandlers)) {
            newCloseHandlers.addAll(this.closeHandlers);
        }

        newCloseHandlers.add(closeHandler);

        return new ParallelArrayByteStream(elements, fromIndex, toIndex, newCloseHandlers, sorted, maxThreadNum, splitor);
    }
}
