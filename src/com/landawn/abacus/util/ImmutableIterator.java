/*
 * Copyright (c) 2017, Haiyang Li.
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

import java.util.NoSuchElementException;

/**
 * 
 * @since 0.9
 * 
 * @author Haiyang Li
 */
public abstract class ImmutableIterator<E> implements java.util.Iterator<E> {
    @SuppressWarnings("rawtypes")
    public static final ImmutableIterator EMPTY = new ImmutableIterator() {
        @Override
        public boolean hasNext() {
            return false;
        }

        @Override
        public Object next() {
            throw new NoSuchElementException();
        }
    };

    public static <T> ImmutableIterator<T> empty() {
        return EMPTY;
    }

    public static <T> ImmutableIterator<T> of(final T[] a) {
        return N.isNullOrEmpty(a) ? EMPTY : of(a, 0, a.length);
    }

    public static <T> ImmutableIterator<T> of(final T[] a, final int fromIndex, final int toIndex) {
        N.checkFromToIndex(fromIndex, toIndex, a == null ? 0 : a.length);

        if (fromIndex == toIndex) {
            return EMPTY;
        }

        return new ImmutableIterator<T>() {
            private int cursor = fromIndex;

            @Override
            public boolean hasNext() {
                return cursor < toIndex;
            }

            @Override
            public T next() {
                if (cursor >= toIndex) {
                    throw new NoSuchElementException();
                }

                return a[cursor++];
            }
        };
    }

    /**
     * @deprecated - UnsupportedOperationException
     */
    @Deprecated
    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }
}
