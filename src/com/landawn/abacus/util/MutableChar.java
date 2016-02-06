/*
 * Copyright (c) 2015, Haiyang Li. All rights reserved.
 */

package com.landawn.abacus.util;

/**
 * <p>
 * Note: it's copied from Apache Commons Lang developed at The Apache Software Foundation (http://www.apache.org/), or
 * under the Apache License 2.0. The methods copied from other products/frameworks may be modified in this class.
 * </p>
 * 
 * A mutable <code>char</code> wrapper.
 * 
 * @since 0.8
 * 
 * @author Haiyang Li
 */
public final class MutableChar implements Comparable<MutableChar>, Mutable<Character> {
    /** The mutable value. */
    private volatile char value;

    /**
     * Constructs a new MutableChar with the default value of zero.
     */
    public MutableChar() {
        super();
    }

    /**
     * Constructs a new MutableChar with the specified value.
     * 
     * @param value  the initial value to store
     */
    public MutableChar(char value) {
        super();
        this.value = value;
    }

    /**
     * Constructs a new MutableChar with the specified value.
     * 
     * @param value  the initial value to store
     */
    public MutableChar(Character value) {
        super();
        this.value = value;
    }

    public static MutableChar of(final char value) {
        return new MutableChar(value);
    }

    //-----------------------------------------------------------------------
    /**
     * Gets the value as a Character instance.
     * 
     * @return the value as a Character, never null
     */
    @Override
    public Character getValue() {
        return Character.valueOf(this.value);
    }

    /**
     * Sets the value from any Number instance.
     * 
     * @param value  the value to set, not null
     * @throws NullPointerException if the object is null
     */
    @Override
    public void setValue(final Character value) {
        this.value = value.charValue();
    }

    /**
     * Sets the value.
     * 
     * @param value  the value to set
     */
    public void setValue(final char value) {
        this.value = value;
    }

    public char charValue() {
        return value;
    }

    //-----------------------------------------------------------------------
    /**
     * Gets this mutable as an instance of Character.
     *
     * @return a Character instance containing the value from this mutable
     */
    public Character toCharacter() {
        return Character.valueOf(value);
    }

    //-----------------------------------------------------------------------
    /**
     * Compares this mutable to another in ascending order.
     * 
     * @param other  the other mutable to compare to, not null
     * @return negative if this is less, zero if equal, positive if greater
     */
    @Override
    public int compareTo(final MutableChar other) {
        return (this.value > other.value) ? 1 : ((this.value == other.value) ? 0 : -1);
    }

    //-----------------------------------------------------------------------
    /**
     * Compares this object to the specified object. The result is <code>true</code> if and only if the argument is
     * not <code>null</code> and is a <code>MutableChar</code> object that contains the same <code>char</code> value
     * as this object.
     * 
     * @param obj  the object to compare with, null returns false
     * @return <code>true</code> if the objects are the same; <code>false</code> otherwise.
     */
    @Override
    public boolean equals(final Object obj) {
        if (obj instanceof MutableChar) {
            return value == ((MutableChar) obj).value;
        }
        return false;
    }

    /**
     * Returns a suitable hash code for this mutable.
     * 
     * @return a suitable hash code
     */
    @Override
    public int hashCode() {
        return value;
    }

    //-----------------------------------------------------------------------
    /**
     * Returns the String value of this mutable.
     * 
     * @return the mutable value as a string
     */
    @Override
    public String toString() {
        return String.valueOf(value);
    }
}