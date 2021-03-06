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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

import com.landawn.abacus.util.stream.Stream;

/**
 * Catch checked exception and convert it to <code>RuntimeException</code>.
 *
 * @since 0.8
 * 
 * @author Haiyang Li
 */
public final class Try<T extends AutoCloseable> {
    private final T t;

    Try(final T t) {
        this.t = t;
    }

    public static <T extends AutoCloseable> Try<T> of(final T t) {
        return new Try<>(t);
    }

    public static <T extends AutoCloseable> Try<T> of(final Supplier<T, ? extends Throwable> supplier) {
        try {
            return new Try<>(supplier.get());
        } catch (Throwable e) {
            throw N.toRuntimeException(e);
        }
    }

    public static Try<Reader> reader(final File file) {
        try {
            return of((Reader) new FileReader(file));
        } catch (FileNotFoundException e) {
            throw N.toRuntimeException(e);
        }
    }

    public static Try<Writer> writer(final File file) {
        try {
            return of((Writer) new FileWriter(file));
        } catch (IOException e) {
            throw N.toRuntimeException(e);
        }
    }

    public static Try<Stream<String>> stream(final File file) {
        final Reader reader = IOUtil.newBufferedReader(file);

        return new Try<>(Stream.of(reader).onClose(new java.lang.Runnable() {
            @Override
            public void run() {
                IOUtil.close(reader);
            }
        }));
    }

    //    public static java.lang.Runnable of(final Try.Runnable run) {
    //        return new java.lang.Runnable() {
    //            @Override
    //            public void run() {
    //                try {
    //                    run.run();
    //                } catch (Exception e) {
    //                    throw N.toRuntimeException(e);
    //                }
    //            }
    //        };
    //    }
    //
    //    public static <R> Try.Callable<R> of(final java.util.concurrent.Callable<R> call) {
    //        return new Try.Callable<R>() {
    //            @Override
    //            public R call() {
    //                try {
    //                    return call.call();
    //                } catch (Exception e) {
    //                    throw N.toRuntimeException(e);
    //                }
    //            }
    //        };
    //    }

    public static void run(final Try.Runnable<? extends Throwable> cmd) {
        run(cmd, null);
    }

    public static void run(final Try.Runnable<? extends Throwable> cmd, final com.landawn.abacus.util.function.Consumer<? super Throwable> actionOnError) {
        try {
            cmd.run();
        } catch (Throwable e) {
            if (actionOnError == null) {
                throw N.toRuntimeException(e);
            } else {
                actionOnError.accept(e);
            }
        }
    }

    public static <R> R call(final java.util.concurrent.Callable<R> cmd) {
        return call(cmd, null);
    }

    public static <R> R call(final java.util.concurrent.Callable<R> cmd, final com.landawn.abacus.util.function.Function<? super Throwable, R> actionOnError) {
        try {
            return cmd.call();
        } catch (Throwable e) {
            if (actionOnError == null) {
                throw N.toRuntimeException(e);
            } else {
                return actionOnError.apply(e);
            }
        }
    }

    public void run(final Try.Consumer<? super T, ? extends Throwable> cmd) {
        run(cmd, null);
    }

    public void run(final Try.Consumer<? super T, ? extends Throwable> cmd, final com.landawn.abacus.util.function.Consumer<? super Throwable> actionOnError) {
        try {
            cmd.accept(t);
        } catch (Throwable e) {
            if (actionOnError == null) {
                throw N.toRuntimeException(e);
            } else {
                actionOnError.accept(e);
            }
        } finally {
            IOUtil.close(t);
        }
    }

    public <R> R call(final Try.Function<? super T, R, ? extends Throwable> cmd) {
        return call(cmd, null);
    }

    public <R> R call(final Try.Function<? super T, R, ? extends Throwable> cmd,
            final com.landawn.abacus.util.function.Function<? super Throwable, R> actionOnError) {
        try {
            return cmd.apply(t);
        } catch (Throwable e) {
            if (actionOnError == null) {
                throw N.toRuntimeException(e);
            } else {
                return actionOnError.apply(e);
            }
        } finally {
            IOUtil.close(t);
        }
    }

    public static final class Try0<T extends AutoCloseable> {
        private final T t;

        Try0(final T t) {
            this.t = t;
        }

        public static <T extends AutoCloseable> Try0<T> of(final T t) {
            return new Try0<>(t);
        }

        public static <T extends AutoCloseable> Try0<T> of(final Supplier<T, ? extends Throwable> supplier) {
            try {
                return new Try0<>(supplier.get());
            } catch (Throwable e) {
                throw N.toRuntimeException(e);
            }
        }

        //    public static Try<Reader> reader(final File file) {
        //        return of((Reader) IOUtil.createBufferedReader(file));
        //    }
        //
        //    public static Try<Writer> writer(final File file) {
        //        return of((Writer) IOUtil.createBufferedWriter(file));
        //    }
        //
        //    public static Try<Stream<String>> stream(final File file) {
        //        final Reader reader = IOUtil.createBufferedReader(file);
        //
        //        return new Try<Stream<String>>(Stream.of(reader).onClose(new java.lang.Runnable() {
        //            @Override
        //            public void run() {
        //                IOUtil.close(reader);
        //            }
        //        }));
        //    }

        //    public static java.lang.Runnable of(final Try.Runnable run) {
        //        return new java.lang.Runnable() {
        //            @Override
        //            public void run() {
        //                try {
        //                    run.run();
        //                } catch (Exception e) {
        //                    throw N.toRuntimeException(e);
        //                }
        //            }
        //        };
        //    }
        //
        //    public static <R> Try.Callable<R> of(final java.util.concurrent.Callable<R> call) {
        //        return new Try.Callable<R>() {
        //            @Override
        //            public R call() {
        //                try {
        //                    return call.call();
        //                } catch (Exception e) {
        //                    throw N.toRuntimeException(e);
        //                }
        //            }
        //        };
        //    }

        public void run(final Try.Runnable<? extends Throwable> cmd) {
            run(cmd, null);
        }

        public void run(final Try.Runnable<? extends Throwable> cmd, final com.landawn.abacus.util.function.Consumer<? super Throwable> actionOnError) {
            try {
                cmd.run();
            } catch (Throwable e) {
                if (actionOnError == null) {
                    throw N.toRuntimeException(e);
                } else {
                    actionOnError.accept(e);
                }
            } finally {
                IOUtil.close(t);
            }
        }

        public <R> R call(final java.util.concurrent.Callable<R> cmd) {
            return call(cmd, null);
        }

        public <R> R call(final java.util.concurrent.Callable<R> cmd, final com.landawn.abacus.util.function.Function<? super Throwable, R> actionOnError) {
            try {
                return cmd.call();
            } catch (Exception e) {
                if (actionOnError == null) {
                    throw N.toRuntimeException(e);
                } else {
                    return actionOnError.apply(e);
                }
            } finally {
                IOUtil.close(t);
            }
        }
    }

    public static interface Callable<R> extends java.util.concurrent.Callable<R> {
        @Override
        R call();
    }

    public static interface Runnable<E extends Throwable> {
        void run() throws E;
    }

    public static interface Supplier<T, E extends Throwable> {
        T get() throws E;
    }

    public static interface Predicate<T, E extends Throwable> {
        boolean test(T t) throws E;

        default Predicate<T, E> negate() throws E {
            return (t) -> !test(t);
        }
    }

    public static interface BiPredicate<T, U, E extends Throwable> {
        boolean test(T t, U u) throws E;

        default BiPredicate<T, U, E> negate() throws E {
            return (T t, U u) -> !test(t, u);
        }
    }

    public static interface TriPredicate<A, B, C, E extends Throwable> {
        boolean test(A a, B b, C c) throws E;
    }

    public static interface Function<T, R, E extends Throwable> {
        R apply(T t) throws E;
    }

    public static interface BiFunction<T, U, R, E extends Throwable> {
        R apply(T t, U u) throws E;
    }

    public static interface TriFunction<A, B, C, R, E extends Throwable> {
        R apply(A a, B b, C c) throws E;
    }

    public static interface Consumer<T, E extends Throwable> {
        void accept(T t) throws E;
    }

    public static interface BiConsumer<T, U, E extends Throwable> {
        void accept(T t, U u) throws E;
    }

    public static interface TriConsumer<A, B, C, E extends Throwable> {
        void accept(A a, B b, C c) throws E;
    }
}
