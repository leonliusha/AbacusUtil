/*
 * Copyright (C) 2015 HaiYang Li
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

import com.landawn.abacus.util.function.Consumer;
import com.landawn.abacus.util.function.Function;
import com.landawn.abacus.util.function.Supplier;
import com.landawn.abacus.util.stream.Stream;

/**
 * 
 * @since 0.8
 * 
 * @author Haiyang Li
 */
public final class Output<T> {
    private volatile T value;

    public Output() {
    }

    Output(T value) {
        this.value = value;
    }

    public static <T> Output<T> of(T value) {
        return new Output<>(value);
    }

    public T value() {
        return value;
    }

    public T getValue() {
        return value;
    }

    public Output<T> setValue(final T value) {
        this.value = value;

        return this;
    }

    public T getAndSet(final T value) {
        final T result = this.value;
        this.value = value;
        return result;
    }

    public T setAndGet(final T value) {
        this.value = value;
        return this.value;
    }

    public boolean isNotNull() {
        return value != null;
    }

    public void accept(final Consumer<? super T> action) {
        action.accept(value);
    }

    public void acceptIfNotNull(final Consumer<? super T> action) {
        if (isNotNull()) {
            action.accept(value);
        }
    }

    public <U> U apply(final Function<? super T, U> action) {
        return action.apply(value);
    }

    /**
     * Execute the specified action if value is not null, otherwise return null directly.
     * 
     * @param action
     * @return
     */
    public <U> NullabLe<U> applyIfNotNull(final Function<? super T, U> action) {
        return isNotNull() ? NullabLe.of(action.apply(value)) : NullabLe.<U> empty();
    }

    public Stream<T> stream() {
        return Stream.of(value);
    }

    /**
     * 
     * @return an empty Stream if the value is null.
     */
    public Stream<T> streamIfNotNull() {
        return isNotNull() ? Stream.of(value) : Stream.<T> empty();
    }

    /**
     * Return the value is not null, otherwise return {@code other}.
     *
     * @param other the value to be returned if not present or null, may be null
     * @return the value, if not present or null, otherwise {@code other}
     */
    public T orIfNull(T other) {
        return isNotNull() ? value : other;
    }

    /**
     * Return the value is not null, otherwise invoke {@code other} and return the result of that invocation.
     *
     * @param other a {@code Supplier} whose result is returned if not present or null
     * @return the value if not present or null otherwise the result of {@code other.get()}
     * @throws NullPointerException if value is not present and {@code other} is null
     */
    public T orGetIfNull(Supplier<? extends T> other) {
        return isNotNull() ? value : other.get();
    }

    /**
     * Return the value is not null, otherwise throw an exception to be created by the provided supplier.
     *
     * @apiNote A method reference to the exception constructor with an empty
     * argument list can be used as the supplier. For example,
     * {@code IllegalStateException::new}
     *
     * @param <X> Type of the exception to be thrown
     * @param exceptionSupplier The supplier which will return the exception to be thrown
     * @return the present value
     * @throws X if not present or null
     * @throws NullPointerException if not present or null and
     * {@code exceptionSupplier} is null
     */
    public <X extends Throwable> T orThrowIfNull(Supplier<? extends X> exceptionSupplier) throws X {
        if (isNotNull()) {
            return value;
        } else {
            throw exceptionSupplier.get();
        }
    }

    @Override
    public int hashCode() {
        return (value == null) ? 0 : value.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        return this == obj || (obj instanceof Output && N.equals(((Output<T>) obj).value, value));
    }

    @Override
    public String toString() {
        return N.toString(value);
    }
}