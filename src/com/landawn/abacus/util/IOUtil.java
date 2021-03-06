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

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.charset.Charset;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import com.landawn.abacus.exception.AbacusIOException;
import com.landawn.abacus.logging.Logger;
import com.landawn.abacus.logging.LoggerFactory;
import com.landawn.abacus.util.function.Consumer;

/**
 *
 * @author Haiyang Li
 *
 * @version $Revision: 0.8 $
 */
public final class IOUtil {
    private static final Logger logger = LoggerFactory.getLogger(IOUtil.class);

    static final int DEFAULT_QUEUE_SIZE_FOR_ROW_PARSER = 1024;

    // ...
    public static final String PATH_SEPARATOR = N.PATH_SEPARATOR;
    public static final String FILE_SEPARATOR = N.FILE_SEPARATOR;
    public static final String LINE_SEPARATOR = N.LINE_SEPARATOR;

    // ... 
    public static final int EOF = -1;

    // ...
    private static final String ZIP = ".zip";
    private static final String GZ = ".gz";

    /**
     * The file copy buffer size (32 MB)
     */
    private static final long FILE_COPY_BUFFER_SIZE = (1024 * 1024) * 32;

    static final Method stringEncodeMethod;
    static final Method stringDecodeMethod;

    static {
        Method encodeMethod = null;
        Method decodeMethod = null;

        try {
            Class<?> cls = RefUtil.forClass("java.lang.StringCoding");
            Method enMethod = RefUtil.getDeclaredMethod(cls, "encode", Charset.class, char[].class, int.class, int.class);
            Method deMethod = RefUtil.getDeclaredMethod(cls, "decode", Charset.class, byte[].class, int.class, int.class);

            if (enMethod != null && deMethod != null) {
                enMethod.setAccessible(true);
                deMethod.setAccessible(true);

                char[] chars = "abc".toCharArray();
                byte[] bytes = RefUtil.invokeMethod(enMethod, Charsets.DEFAULT, chars, 1, 1);
                char[] chars2 = RefUtil.invokeMethod(deMethod, Charsets.DEFAULT, bytes, 0, bytes.length);

                if (chars2.length == 1 && chars2[0] == 'b') {
                    encodeMethod = enMethod;
                    decodeMethod = deMethod;
                }
            }

        } catch (Throwable e) {
            // e.printStackTrace();
            // ignore
        }

        stringEncodeMethod = encodeMethod;
        stringDecodeMethod = decodeMethod;
    }

    /**
     * Constructor for FileUtil.
     */
    private IOUtil() {
        // no instance;
    }

    /**
     * Returns the disk size of the volume which holds the working directory.
     * <p>
     * Identical to:
     * <pre>
     * freeSpaceKb(new File(".").getAbsolutePath())
     * </pre>
     * @return the amount of free drive space on the drive or volume in kilobytes
     * @throws IllegalStateException if an error occurred in initialisation
     * @throws AbacusIOException if an error occurs when finding the free space
     */
    public static long freeDiskSpaceKb() {
        try {
            return FileSystemUtil.freeSpaceKb();
        } catch (IOException e) {
            throw new AbacusIOException(e);
        }
    }

    /**
     * Returns the disk size of the volume which holds the working directory.
     * <p>
     * Identical to:
     * <pre>
     * freeSpaceKb(new File(".").getAbsolutePath())
     * </pre>
     * @param timeout The timeout amount in milliseconds or no timeout if the value
     *  is zero or less
     * @return the amount of free drive space on the drive or volume in kilobytes
     * @throws IllegalStateException if an error occurred in initialisation
     * @throws AbacusIOException if an error occurs when finding the free space
     */
    public static long freeDiskSpaceKb(final long timeout) {
        try {
            return FileSystemUtil.freeSpaceKb(timeout);
        } catch (IOException e) {
            throw new AbacusIOException(e);
        }
    }

    //-----------------------------------------------------------------------
    /**
     * Returns the free space on a drive or volume in kilobytes by invoking
     * the command line.
     * <pre>
     * FileSystemUtils.freeSpaceKb("C:");       // Windows
     * FileSystemUtils.freeSpaceKb("/volume");  // *nix
     * </pre>
     * The free space is calculated via the command line.
     * It uses 'dir /-c' on Windows, 'df -kP' on AIX/HP-UX and 'df -k' on other Unix.
     * <p>
     * In order to work, you must be running Windows, or have a implementation of
     * Unix df that supports GNU format when passed -k (or -kP). If you are going
     * to rely on this code, please check that it works on your OS by running
     * some simple tests to compare the command line with the output from this class.
     * If your operating system isn't supported, please raise a JIRA call detailing
     * the exact result from df -k and as much other detail as possible, thanks.
     *
     * @param path  the path to get free space for, not null, not empty on Unix
     * @return the amount of free drive space on the drive or volume in kilobytes
     * @throws IllegalArgumentException if the path is invalid
     * @throws IllegalStateException if an error occurred in initialisation
     * @throws AbacusIOException if an error occurs when finding the free space
     */
    public static long freeDiskSpaceKb(final String path) {
        try {
            return FileSystemUtil.freeSpaceKb(path);
        } catch (IOException e) {
            throw new AbacusIOException(e);
        }
    }

    /**
     * Returns the free space on a drive or volume in kilobytes by invoking
     * the command line.
     * <pre>
     * FileSystemUtils.freeSpaceKb("C:");       // Windows
     * FileSystemUtils.freeSpaceKb("/volume");  // *nix
     * </pre>
     * The free space is calculated via the command line.
     * It uses 'dir /-c' on Windows, 'df -kP' on AIX/HP-UX and 'df -k' on other Unix.
     * <p>
     * In order to work, you must be running Windows, or have a implementation of
     * Unix df that supports GNU format when passed -k (or -kP). If you are going
     * to rely on this code, please check that it works on your OS by running
     * some simple tests to compare the command line with the output from this class.
     * If your operating system isn't supported, please raise a JIRA call detailing
     * the exact result from df -k and as much other detail as possible, thanks.
     *
     * @param path  the path to get free space for, not null, not empty on Unix
     * @param timeout The timeout amount in milliseconds or no timeout if the value
     *  is zero or less
     * @return the amount of free drive space on the drive or volume in kilobytes
     * @throws IllegalArgumentException if the path is invalid
     * @throws IllegalStateException if an error occurred in initialisation
     * @throws AbacusIOException if an error occurs when finding the free space
     */
    public static long freeDiskSpaceKb(final String path, final long timeout) {
        try {
            return FileSystemUtil.freeSpaceKb(path, timeout);
        } catch (IOException e) {
            throw new AbacusIOException(e);
        }
    }

    public static int parseInt(final char[] cbuf, final int offset, final int len) {
        return parseInt(cbuf, offset, len, 10);
    }

    /**
     * Refer to Integer.parseInt(String);
     *
     * @param cbuf
     * @param offset
     * @param len
     * @param radix
     * @return
     * @throws NumberFormatException
     */
    // It's copied from OpenJDK 1.7 on 3/9/205
    /*
     * Copyright (c) 1994, 2009, Oracle and/or its affiliates. All rights reserved.
     * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
     *
     * This code is free software; you can redistribute it and/or modify it
     * under the terms of the GNU General Public License version 2 only, as
     * published by the Free Software Foundation.  Oracle designates this
     * particular file as subject to the "Classpath" exception as provided
     * by Oracle in the LICENSE file that accompanied this code.
     *
     * This code is distributed in the hope that it will be useful, but WITHOUT
     * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
     * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
     * version 2 for more details (a copy is included in the LICENSE file that
     * accompanied this code).
     *
     * You should have received a copy of the GNU General Public License version
     * 2 along with this work; if not, write to the Free Software Foundation,
     * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
     *
     * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
     * or visit www.oracle.com if you need additional information or have any
     * questions.
     */
    public static int parseInt(final char[] cbuf, final int offset, final int len, final int radix) throws NumberFormatException {
        if (offset < 0 || len < 0) {
            throw new IllegalArgumentException("'offset' and 'len' can't be negative");
        }

        if (radix < Character.MIN_RADIX) {
            throw new NumberFormatException("radix " + radix + " less than Character.MIN_RADIX");
        }

        if (radix > Character.MAX_RADIX) {
            throw new NumberFormatException("radix " + radix + " greater than Character.MAX_RADIX");
        }

        if (len == 0 || (N.isNullOrEmpty(cbuf) && offset == 0)) {
            return 0;
        }

        int result = 0;
        boolean negative = false;
        int limit = -Integer.MAX_VALUE;
        int multmin;
        int digit;

        int endIndex = offset + len;
        int i = offset;

        final char firstChar = cbuf[offset];
        if (firstChar < '0') { // Possible leading "+" or "-"
            if (firstChar == '-') {
                negative = true;
                limit = Integer.MIN_VALUE;
            } else if (firstChar != '+') {
                throw new NumberFormatException(String.valueOf(cbuf, offset, len));
            }

            if (len == 1) {
                throw new NumberFormatException(String.valueOf(cbuf, offset, len));
            }

            i++;
        }

        multmin = limit / radix;

        while (i < endIndex) {
            // Accumulating negatively avoids surprises near MAX_VALUE
            digit = Character.digit(cbuf[i++], radix);

            if (digit < 0) {
                throw new NumberFormatException(String.valueOf(cbuf, offset, len));
            }

            if (result < multmin) {
                throw new NumberFormatException(String.valueOf(cbuf, offset, len));
            }

            result *= radix;

            if (result < limit + digit) {
                throw new NumberFormatException(String.valueOf(cbuf, offset, len));
            }

            result -= digit;
        }

        return negative ? result : -result;
    }

    public static long parseLong(final char[] cbuf, final int offset, final int len) {
        return parseLong(cbuf, offset, len, 10);
    }

    /**
     * Refer to Long.parseLong(String);
     *
     * @param cbuf
     * @param offset
     * @param len
     * @param radix
     * @return
     * @throws NumberFormatException
     */
    // It's copied from OpenJDK 1.7 on 3/9/205
    /*
     * Copyright (c) 1994, 2009, Oracle and/or its affiliates. All rights reserved.
     * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
     *
     * This code is free software; you can redistribute it and/or modify it
     * under the terms of the GNU General Public License version 2 only, as
     * published by the Free Software Foundation.  Oracle designates this
     * particular file as subject to the "Classpath" exception as provided
     * by Oracle in the LICENSE file that accompanied this code.
     *
     * This code is distributed in the hope that it will be useful, but WITHOUT
     * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
     * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
     * version 2 for more details (a copy is included in the LICENSE file that
     * accompanied this code).
     *
     * You should have received a copy of the GNU General Public License version
     * 2 along with this work; if not, write to the Free Software Foundation,
     * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
     *
     * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
     * or visit www.oracle.com if you need additional information or have any
     * questions.
     */
    public static long parseLong(final char[] cbuf, final int offset, final int len, final int radix) throws NumberFormatException {
        if (offset < 0 || len < 0) {
            throw new IllegalArgumentException("'offset' and 'len' can't be negative");
        }

        if (radix < Character.MIN_RADIX) {
            throw new NumberFormatException("radix " + radix + " less than Character.MIN_RADIX");
        }

        if (radix > Character.MAX_RADIX) {
            throw new NumberFormatException("radix " + radix + " greater than Character.MAX_RADIX");
        }

        if (len == 0 || (N.isNullOrEmpty(cbuf) && offset == 0)) {
            return 0;
        }

        long result = 0;
        boolean negative = false;
        long limit = -Long.MAX_VALUE;
        long multmin;
        int digit;

        int endIndex = offset + len;
        int i = offset;

        final char lastChar = cbuf[offset + len - 1];
        if (lastChar == 'l' || lastChar == 'L') {
            if (len == 1) {
                throw new NumberFormatException(String.valueOf(cbuf, offset, len));
            }

            endIndex = endIndex - 1;
        }

        final char firstChar = cbuf[offset];
        if (firstChar < '0') { // Possible leading "+" or "-"
            if (firstChar == '-') {
                negative = true;
                limit = Long.MIN_VALUE;
            } else if (firstChar != '+') {
                throw new NumberFormatException(String.valueOf(cbuf, offset, len));
            }

            if (len == 1) {
                throw new NumberFormatException(String.valueOf(cbuf, offset, len));
            }

            i++;
        }

        multmin = limit / radix;

        while (i < endIndex) {
            // Accumulating negatively avoids surprises near MAX_VALUE
            digit = Character.digit(cbuf[i++], radix);

            if (digit < 0) {
                throw new NumberFormatException(String.valueOf(cbuf, offset, len));
            }

            if (result < multmin) {
                throw new NumberFormatException(String.valueOf(cbuf, offset, len));
            }

            result *= radix;

            if (result < limit + digit) {
                throw new NumberFormatException(String.valueOf(cbuf, offset, len));
            }

            result -= digit;
        }

        return negative ? result : -result;
    }

    public static byte[] chars2Bytes(final char[] chars) {
        return chars2Bytes(chars, Charsets.DEFAULT);
    }

    public static byte[] chars2Bytes(final char[] chars, final Charset charset) {
        return chars2Bytes(chars, 0, chars.length, charset);
    }

    public static byte[] chars2Bytes(final char[] chars, final int offset, final int len, Charset charset) {
        charset = charset == null ? Charsets.DEFAULT : charset;

        if (stringEncodeMethod == null) {
            return new String(chars, offset, len).getBytes(charset);
        } else {
            return RefUtil.invokeMethod(stringEncodeMethod, charset, chars, offset, len);
        }
    }

    public static char[] bytes2Chars(final byte[] bytes) {
        return bytes2Chars(bytes, Charsets.DEFAULT);
    }

    public static char[] bytes2Chars(final byte[] bytes, final Charset charset) {
        return bytes2Chars(bytes, 0, bytes.length, charset);
    }

    public static char[] bytes2Chars(final byte bytes[], final int offset, final int len, Charset charset) {
        charset = charset == null ? Charsets.DEFAULT : charset;

        if (stringDecodeMethod == null) {
            return new String(bytes, offset, len, charset).toCharArray();
        } else {
            return RefUtil.invokeMethod(stringDecodeMethod, charset, bytes, offset, len);
        }
    }

    public static InputStream string2InputStream(final String str) {
        return string2InputStream(str, Charsets.DEFAULT);
    }

    public static InputStream string2InputStream(final String str, Charset charset) {
        if (str == null) {
            throw new IllegalArgumentException("The input String can't be null.");
        }

        charset = charset == null ? Charsets.DEFAULT : charset;

        return new ByteArrayInputStream(str.getBytes(charset));
    }

    public static Reader string2Reader(final String str) {
        if (str == null) {
            throw new IllegalArgumentException("The input String can't be null.");
        }

        return new StringReader(str);
    }

    public static Writer stringBuilder2Writer(final StringBuilder sb) {
        if (sb == null) {
            throw new IllegalArgumentException("The input StringBuilder can't be null.");
        }

        return new StringWriter(sb);
    }

    public static byte[] readBytes(final File file) {
        return readBytes(file, 0, Integer.MAX_VALUE);
    }

    public static byte[] readBytes(final File file, final long offset, final int maxLen) {
        final Output<ZipFile> outputZipFile = new Output<>();
        InputStream is = null;

        try {
            is = openFile(outputZipFile, file);

            return readBytes(is, offset, maxLen);
        } catch (IOException e) {
            throw new AbacusIOException(e);
        } finally {
            close(is);
            close(outputZipFile.value());
        }
    }

    public static byte[] readBytes(final InputStream is) {
        return readBytes(is, 0, Integer.MAX_VALUE);
    }

    public static byte[] readBytes(final InputStream is, final long offset, final int maxLen) {
        if (maxLen == 0) {
            return N.EMPTY_BYTE_ARRAY;
        }

        if (offset > 0) {
            if (skip(is, offset) < offset) {
                return N.EMPTY_BYTE_ARRAY;
            }
        }

        ByteArrayOutputStream os = null;
        final byte[] buf = ObjectFactory.createByteArrayBuffer();
        final int bufLength = buf.length;

        int totalCount = 0;
        int count = 0;

        try {
            while (totalCount < maxLen && EOF != (count = read(is, buf, 0, Math.min(maxLen - totalCount, bufLength)))) {
                if (count == bufLength && count < maxLen) {
                    if (os == null) {
                        os = ObjectFactory.createByteArrayOutputStream();
                    }
                }

                if (os != null) {
                    os.write(buf, 0, count);
                }

                totalCount += count;
            }

            return os == null ? (totalCount <= 0 ? N.EMPTY_BYTE_ARRAY : N.copyOfRange(buf, 0, totalCount)) : os.toByteArray();

        } catch (IOException e) {
            throw new AbacusIOException(e);
        } finally {
            ObjectFactory.recycle(buf);
            ObjectFactory.recycle(os);
        }
    }

    public static char[] readChars(final File file) {
        return readChars(file, 0, Integer.MAX_VALUE);
    }

    public static char[] readChars(final File file, final long offset, final int maxLen) {
        return readChars(file, 0, maxLen, Charsets.DEFAULT);
    }

    public static char[] readChars(final File file, final long offset, final int maxLen, final Charset encoding) {
        final Output<ZipFile> outputZipFile = new Output<>();
        InputStream is = null;

        try {
            is = openFile(outputZipFile, file);

            return readChars(is, offset, maxLen, encoding);
        } catch (IOException e) {
            throw new AbacusIOException(e);
        } finally {
            close(is);
            close(outputZipFile.value());
        }
    }

    public static char[] readChars(final InputStream is) {
        return readChars(is, 0, Integer.MAX_VALUE);
    }

    public static char[] readChars(final InputStream is, final long offset, final int maxLen) {
        return readChars(is, 0, maxLen, Charsets.DEFAULT);
    }

    public static char[] readChars(final InputStream is, final long offset, final int maxLen, Charset encoding) {
        encoding = encoding == null ? Charsets.DEFAULT : encoding;

        Reader reader = null;

        // try {
        reader = createReader(is, encoding);

        return readChars(reader, offset, maxLen);

        // } finally {
        // // close(reader);
        // }
    }

    public static char[] readChars(final Reader reader) {
        return readChars(reader, 0, Integer.MAX_VALUE);
    }

    public static char[] readChars(final Reader reader, final long offset, final int maxLen) {
        if (maxLen == 0) {
            return N.EMPTY_CHAR_ARRAY;
        }

        if (offset > 0) {
            if (skip(reader, offset) < offset) {
                return N.EMPTY_CHAR_ARRAY;
            }
        }

        StringBuilder sb = null;
        final char[] buf = ObjectFactory.createCharArrayBuffer();
        final int bufLength = buf.length;

        int totalCount = 0;
        int count = 0;

        try {
            while (totalCount < maxLen && EOF != (count = read(reader, buf, 0, Math.min(maxLen - totalCount, bufLength)))) {
                if (count == bufLength && count < maxLen) {
                    if (sb == null) {
                        sb = ObjectFactory.createStringBuilder();
                    }
                }

                if (sb != null) {
                    sb.append(buf, 0, count);
                }

                totalCount += count;
            }

            if (sb == null) {
                return totalCount <= 0 ? N.EMPTY_CHAR_ARRAY : N.copyOfRange(buf, 0, totalCount);
            } else {
                final char[] a = new char[totalCount];
                sb.getChars(0, totalCount, a, 0);

                return a;
            }
        } catch (IOException e) {
            throw new AbacusIOException(e);
        } finally {
            ObjectFactory.recycle(buf);
            ObjectFactory.recycle(sb);
        }
    }

    public static String readString(final File file) {
        return readString(file, 0, Integer.MAX_VALUE);
    }

    public static String readString(final File file, final long offset, final int maxLen) {
        return readString(file, offset, maxLen, Charsets.DEFAULT);
    }

    public static String readString(final File file, final long offset, final int maxLen, final Charset encoding) {
        final char[] chs = readChars(file, offset, maxLen, encoding);

        return N.isNullOrEmpty(chs) ? N.EMPTY_STRING : N.newString(chs, true);
    }

    public static String readString(final InputStream is) {
        return readString(is, 0, Integer.MAX_VALUE);
    }

    public static String readString(final InputStream is, final long offset, final int maxLen) {
        return readString(is, offset, maxLen, Charsets.DEFAULT);
    }

    public static String readString(final InputStream is, final long offset, final int maxLen, final Charset encoding) {
        final char[] chs = readChars(is, offset, maxLen, encoding);

        return N.isNullOrEmpty(chs) ? N.EMPTY_STRING : N.newString(chs, true);
    }

    public static String readString(final Reader reader) {
        return readString(reader, 0, Integer.MAX_VALUE);
    }

    public static String readString(final Reader reader, final long offset, final int maxLen) {
        final char[] chs = readChars(reader, offset, maxLen);

        return N.isNullOrEmpty(chs) ? N.EMPTY_STRING : N.newString(chs, true);
    }

    public static String readLine(final File file) {
        return readLine(file, 0);
    }

    public static String readLine(final File file, final int lineIndex) {
        return readLine(file, lineIndex, Charsets.DEFAULT);
    }

    public static String readLine(final File file, final int lineIndex, final Charset encoding) {
        final Output<ZipFile> outputZipFile = new Output<>();
        InputStream is = null;

        try {
            is = openFile(outputZipFile, file);

            return readLine(is, lineIndex, encoding);
        } catch (IOException e) {
            throw new AbacusIOException(e);
        } finally {
            close(is);
            close(outputZipFile.value());
        }
    }

    public static String readLine(final InputStream is) {
        return readLine(is, 0);
    }

    public static String readLine(final InputStream is, final int lineIndex) {
        return readLine(is, lineIndex, Charsets.DEFAULT);
    }

    public static String readLine(final InputStream is, final int lineIndex, final Charset encoding) {
        return readLine(createReader(is, encoding), lineIndex);
    }

    public static String readLine(final Reader reader) {
        return readLine(reader, 0);
    }

    public static String readLine(final Reader reader, int lineIndex) {
        final BufferedReader br = reader instanceof BufferedReader ? (BufferedReader) reader : ObjectFactory.createBufferedReader(reader);

        try {
            if (lineIndex == 0) {
                return br.readLine();
            } else {
                while (lineIndex-- > 0 && br.readLine() != null) {
                }

                return br.readLine();
            }
        } catch (IOException e) {
            throw new AbacusIOException(e);
        } finally {
            if (br != reader) {
                ObjectFactory.recycle(br);
            }
        }
    }

    public static List<String> readLines(final File file) {
        return readLines(file, 0, Integer.MAX_VALUE);
    }

    public static List<String> readLines(final File file, final int offset, final int count) {
        return readLines(file, offset, count, Charsets.DEFAULT);
    }

    public static List<String> readLines(final File file, final int offset, final int count, final Charset encoding) {
        final Output<ZipFile> outputZipFile = new Output<>();
        InputStream is = null;

        try {
            is = openFile(outputZipFile, file);

            return readLines(is, offset, count, encoding);
        } catch (IOException e) {
            throw new AbacusIOException(e);
        } finally {
            close(is);
            close(outputZipFile.value());
        }
    }

    public static List<String> readLines(final InputStream is) {
        return readLines(is, 0, Integer.MAX_VALUE);
    }

    public static List<String> readLines(final InputStream is, final int offset, final int count) {
        return readLines(is, offset, count, Charsets.DEFAULT);
    }

    public static List<String> readLines(final InputStream is, final int offset, final int count, final Charset encoding) {
        return readLines(createReader(is, encoding), offset, count);
    }

    private static InputStreamReader createReader(final InputStream is, final Charset encoding) {
        return encoding == null ? new InputStreamReader(is, Charsets.DEFAULT) : new InputStreamReader(is, encoding);
    }

    public static List<String> readLines(final Reader reader) {
        return readLines(reader, 0, Integer.MAX_VALUE);
    }

    public static List<String> readLines(final Reader reader, int offset, int count) {
        final List<String> res = new ArrayList<>();
        final BufferedReader br = reader instanceof BufferedReader ? (BufferedReader) reader : ObjectFactory.createBufferedReader(reader);

        try {
            while (offset-- > 0 && br.readLine() != null) {
            }

            String line = null;

            while (count-- > 0 && (line = br.readLine()) != null) {
                res.add(line);
            }

        } catch (IOException e) {
            throw new AbacusIOException(e);
        } finally {
            if (br != reader) {
                ObjectFactory.recycle(br);
            }
        }

        return res;
    }

    /**
     * Returns an Iterator for the lines in a <code>File</code> using the default encoding for the VM.
     *
     * @param file  the file to open for input, must not be {@code null}
     * @return an Iterator of the lines in the file, never {@code null}
     * @throws AbacusIOException in case of an I/O error (file closed)
     * @see #lineIterator(File, Charset)
     */
    public static LineIterator lineIterator(final File file) {
        return lineIterator(file, Charsets.DEFAULT);
    }

    /**
     * Returns an Iterator for the lines in a <code>File</code>.
     * <p>
     * This method opens an <code>InputStream</code> for the file.
     * When you have finished with the iterator you should close the stream
     * to free internal resources. This can be done by calling the
     * {@link LineIterator#close()} or
     * {@link IOUtil#closeQuietly(LineIterator)} method.
     * <p>
     * The recommended usage pattern is:
     * <pre>
     * LineIterator it = FileUtils.lineIterator(file, "UTF-8");
     * try {
     *   while (it.hasNext()) {
     *     String line = it.nextLine();
     *     /// do something with line
     *   }
     * } finally {
     *   closeQuietly(iterator);
     * }
     * </pre>
     * <p>
     * If an exception occurs during the creation of the iterator, the
     * underlying stream is closed.
     *
     * @param file  the file to open for input, must not be {@code null}
     * @param encoding  the encoding to use, {@code null} means platform default
     * @return an Iterator of the lines in the file, never {@code null}
     * @throws AbacusIOException in case of an I/O error (file closed)
     */
    public static LineIterator lineIterator(final File file, final Charset encoding) {
        InputStream in = null;

        try {
            in = new FileInputStream(file);

            return lineIterator(in, encoding);
        } catch (final IOException ex) {
            closeQuietly(in);
            throw new AbacusIOException(ex);
        } catch (final RuntimeException ex) {
            closeQuietly(in);
            throw ex;
        }
    }

    public static LineIterator lineIterator(final InputStream input) {
        return lineIterator(input, null);
    }

    /**
     * Returns an Iterator for the lines in an <code>InputStream</code>, using
     * the character encoding specified (or default encoding if null).
     * <p>
     * <code>LineIterator</code> holds a reference to the open
     * <code>InputStream</code> specified here. When you have finished with
     * the iterator you should close the stream to free internal resources.
     * This can be done by closing the stream directly, or by calling
     * {@link LineIterator#close()} or {@link IOUtil#closeQuietly(LineIterator)}.
     * <p>
     * The recommended usage pattern is:
     * <pre>
     * try {
     *   LineIterator it = lineIterator(stream, charset);
     *   while (it.hasNext()) {
     *     String line = it.nextLine();
     *     /// do something with line
     *   }
     * } finally {
     *   closeQuietly(stream);
     * }
     * </pre>
     *
     * @param input  the <code>InputStream</code> to read from, not null
     * @param encoding  the encoding to use, null means platform default
     * @return an Iterator of the lines in the reader, never null
     * @throws IllegalArgumentException if the input is null
     * @throws AbacusIOException if an I/O error occurs, such as if the encoding is invalid
     */
    public static LineIterator lineIterator(final InputStream input, final Charset encoding) {
        return new LineIterator(createReader(input, encoding));
    }

    // lineIterator
    //-----------------------------------------------------------------------
    /**
     * Returns an Iterator for the lines in a <code>Reader</code>.
     * <p>
     * <code>LineIterator</code> holds a reference to the open
     * <code>Reader</code> specified here. When you have finished with the
     * iterator you should close the reader to free internal resources.
     * This can be done by closing the reader directly, or by calling
     * {@link LineIterator#close()} or {@link IOUtil#closeQuietly(LineIterator)}.
     * <p>
     * The recommended usage pattern is:
     * <pre>
     * try {
     *   LineIterator it = lineIterator(reader);
     *   while (it.hasNext()) {
     *     String line = it.nextLine();
     *     /// do something with line
     *   }
     * } finally {
     *   closeQuietly(reader);
     * }
     * </pre>
     *
     * @param reader  the <code>Reader</code> to read from, not null
     * @return an Iterator of the lines in the reader, never null
     * @throws IllegalArgumentException if the reader is null
     */
    public static LineIterator lineIterator(final Reader reader) {
        return new LineIterator(reader);
    }

    /**
     *
     * @param file
     * @param buf
     * @param off the start offset in array <code>b</code> at which the data is written.
     * @param len
     * @return
     */
    public static int read(final File file, final byte[] buf, final int off, final int len) {
        InputStream is = null;

        try {
            is = new FileInputStream(file);
            return read(is, buf, off, len);
        } catch (IOException e) {
            throw new AbacusIOException(e);
        } finally {
            closeQuietly(is);
        }
    }

    /**
     *
     * @param is
     * @param buf
     * @param off the start offset in array <code>b</code> at which the data is written.
     * @param len
     * @return
     * @throws IOException
     */
    public static int read(final InputStream is, final byte[] buf, final int off, final int len) throws IOException {
        if ((off < 0) || (off > buf.length) || (len < 0) || ((off + len) > buf.length) || ((off + len) < 0)) {
            throw new IndexOutOfBoundsException();
        }

        if (len == 0) {
            return 0;
        }

        int n = is.read(buf, off, len);

        if (n < 0 || n == len) {
            return n;
        }

        while (n < len) {
            int n1 = is.read(buf, off + n, len - n);

            if (n1 < 0) {
                break;
            }

            n += n1;
        }

        return n;
    }

    /**
     *
     * @param file
     * @param buf
     * @param off the start offset in array <code>b</code> at which the data is written.
     * @param len
     * @return
     */
    public static int read(final File file, final char[] buf, final int off, final int len) {
        Reader reader = null;

        try {
            reader = new FileReader(file);
            return read(reader, buf, off, len);
        } catch (IOException e) {
            throw new AbacusIOException(e);
        } finally {
            closeQuietly(reader);
        }
    }

    /**
     *
     * @param reader
     * @param buf
     * @param off the start offset in array <code>b</code> at which the data is written.
     * @param len
     * @return
     * @throws IOException
     */
    public static int read(final Reader reader, final char[] buf, final int off, final int len) throws IOException {
        if ((off < 0) || (off > buf.length) || (len < 0) || ((off + len) > buf.length) || ((off + len) < 0)) {
            throw new IndexOutOfBoundsException();
        }

        if (len == 0) {
            return 0;
        }

        int n = reader.read(buf, off, len);

        if (n < 0 || n == len) {
            return n;
        }

        while (n < len) {
            int n1 = reader.read(buf, off + n, len - n);

            if (n1 < 0) {
                break;
            }

            n += n1;
        }

        return n;
    }

    public static void writeLine(final File file, final Object obj) {
        Writer writer = null;

        try {
            writer = new FileWriter(file);

            writeLine(writer, obj);

            writer.flush();
        } catch (IOException e) {
            throw new AbacusIOException(e);
        } finally {
            close(writer);
        }
    }

    public static void writeLine(final OutputStream os, final Object obj) {
        writeLine(os, obj, false);
    }

    public static void writeLine(final OutputStream os, final Object obj, final boolean flush) {
        writeLine(new OutputStreamWriter(os), obj, flush);
    }

    public static void writeLine(final Writer writer, final Object obj) {
        writeLine(writer, obj, false);
    }

    public static void writeLine(final Writer writer, final Object obj, final boolean flush) {
        try {
            if (obj == null) {
                writer.write(N.NULL_CHAR_ARRAY);
            } else {
                writer.write(obj.toString());
            }

            writer.write(N.LINE_SEPARATOR);

            if (flush) {
                writer.flush();
            }
        } catch (IOException e) {
            throw new AbacusIOException(e);
        }
    }

    public static void writeLines(final File file, final Object[] lines) {
        writeLines(file, lines, 0, lines.length);
    }

    public static void writeLines(final File file, final Object[] lines, final int offset, final int count) {
        Writer writer = null;

        try {
            writer = new FileWriter(file);

            writeLines(writer, lines, offset, count);

            writer.flush();
        } catch (IOException e) {
            throw new AbacusIOException(e);
        } finally {
            close(writer);
        }
    }

    public static void writeLines(final OutputStream os, final Object[] lines) {
        writeLines(os, lines, false);
    }

    public static void writeLines(final OutputStream os, final Object[] lines, final boolean flush) {
        writeLines(os, lines, 0, lines.length, flush);
    }

    public static void writeLines(final OutputStream os, final Object[] lines, final int offset, final int count) {
        writeLines(os, lines, offset, count, false);
    }

    public static void writeLines(final OutputStream os, final Object[] lines, final int offset, final int count, final boolean flush) {
        writeLines(new OutputStreamWriter(os), lines, offset, count, flush);
    }

    public static void writeLines(final Writer writer, final Object[] lines) {
        writeLines(writer, lines, false);
    }

    public static void writeLines(final Writer writer, final Object[] lines, final boolean flush) {
        writeLines(writer, lines, 0, lines.length, flush);
    }

    public static void writeLines(final Writer writer, final Object[] lines, final int offset, final int count) {
        writeLines(writer, lines, offset, count, false);
    }

    public static void writeLines(final Writer writer, final Object[] lines, final int offset, int count, final boolean flush) {
        boolean isBufferedWriter = writer instanceof BufferedWriter || writer instanceof java.io.BufferedWriter;
        final Writer bw = isBufferedWriter ? writer : ObjectFactory.createBufferedWriter(writer);

        try {
            int lineNum = 0;

            for (Object line : lines) {
                if (lineNum++ >= offset) {
                    if (line == null) {
                        writer.write(N.NULL_CHAR_ARRAY);
                    } else {
                        writer.write(line.toString());
                    }

                    writer.write(N.LINE_SEPARATOR);

                    count--;
                }

                if (count <= 0) {
                    break;
                }
            }

            if (flush || !isBufferedWriter) {
                bw.flush();
            }
        } catch (IOException e) {
            throw new AbacusIOException(e);
        } finally {
            if (!isBufferedWriter) {
                ObjectFactory.recycle((BufferedWriter) bw);
            }
        }
    }

    public static void writeLines(final File file, final Collection<?> lines) {
        writeLines(file, lines, 0, lines.size());
    }

    public static void writeLines(final File file, final Collection<?> lines, final int offset, final int count) {
        Writer writer = null;

        try {
            writer = new FileWriter(file);

            writeLines(writer, lines, offset, count);

            writer.flush();
        } catch (IOException e) {
            throw new AbacusIOException(e);
        } finally {
            close(writer);
        }
    }

    public static void writeLines(final OutputStream os, final Collection<?> lines) {
        writeLines(os, lines, false);
    }

    public static void writeLines(final OutputStream os, final Collection<?> lines, final boolean flush) {
        writeLines(os, lines, 0, lines.size(), flush);
    }

    public static void writeLines(final OutputStream os, final Collection<?> lines, final int offset, final int count) {
        writeLines(os, lines, offset, count, false);
    }

    public static void writeLines(final OutputStream os, final Collection<?> lines, final int offset, final int count, final boolean flush) {
        writeLines(new OutputStreamWriter(os), lines, offset, count, flush);
    }

    public static void writeLines(final Writer writer, final Collection<?> lines) {
        writeLines(writer, lines, false);
    }

    public static void writeLines(final Writer writer, final Collection<?> lines, final boolean flush) {
        writeLines(writer, lines, 0, lines.size(), flush);
    }

    public static void writeLines(final Writer writer, final Collection<?> lines, final int offset, final int count) {
        writeLines(writer, lines, offset, count, false);
    }

    public static void writeLines(final Writer writer, final Collection<?> lines, final int offset, int count, final boolean flush) {
        boolean isBufferedWriter = writer instanceof BufferedWriter || writer instanceof java.io.BufferedWriter;
        final Writer bw = isBufferedWriter ? writer : ObjectFactory.createBufferedWriter(writer);

        try {
            int lineNum = 0;

            for (Object line : lines) {
                if (lineNum++ >= offset) {
                    if (line == null) {
                        writer.write(N.NULL_CHAR_ARRAY);
                    } else {
                        writer.write(line.toString());
                    }

                    writer.write(N.LINE_SEPARATOR);

                    count--;
                }

                if (count <= 0) {
                    break;
                }
            }

            if (flush || !isBufferedWriter) {
                bw.flush();
            }
        } catch (IOException e) {
            throw new AbacusIOException(e);
        } finally {
            if (!isBufferedWriter) {
                ObjectFactory.recycle((BufferedWriter) bw);
            }
        }
    }

    public static void write(final Writer writer, final boolean b) throws IOException {
        write(writer, b, false);
    }

    public static void write(final Writer writer, final boolean b, final boolean flush) throws IOException {
        writer.write(N.stringOf(b));

        if (flush) {
            writer.flush();
        }
    }

    public static void write(final Writer writer, final char c) throws IOException {
        write(writer, c, false);
    }

    public static void write(final Writer writer, final char c, final boolean flush) throws IOException {
        writer.write(c);

        if (flush) {
            writer.flush();
        }
    }

    public static void write(final Writer writer, final byte b) throws IOException {
        write(writer, b, false);
    }

    public static void write(final Writer writer, final byte b, final boolean flush) throws IOException {
        writer.write(N.stringOf(b));

        if (flush) {
            writer.flush();
        }
    }

    public static void write(final Writer writer, final short s) throws IOException {
        write(writer, s, false);
    }

    public static void write(final Writer writer, final short s, final boolean flush) throws IOException {
        writer.write(N.stringOf(s));

        if (flush) {
            writer.flush();
        }
    }

    public static void write(final Writer writer, final int i) throws IOException {
        write(writer, i, false);
    }

    public static void write(final Writer writer, final int i, final boolean flush) throws IOException {
        writer.write(N.stringOf(i));

        if (flush) {
            writer.flush();
        }
    }

    public static void write(final Writer writer, final long lng) throws IOException {
        write(writer, lng, false);
    }

    public static void write(final Writer writer, final long lng, final boolean flush) throws IOException {
        writer.write(N.stringOf(lng));

        if (flush) {
            writer.flush();
        }
    }

    public static void write(final Writer writer, final float f) throws IOException {
        write(writer, f, false);
    }

    public static void write(final Writer writer, final float f, final boolean flush) throws IOException {
        writer.write(N.stringOf(f));

        if (flush) {
            writer.flush();
        }
    }

    public static void write(final Writer writer, final double d) throws IOException {
        write(writer, d, false);
    }

    public static void write(final Writer writer, final double d, final boolean flush) throws IOException {
        writer.write(N.stringOf(d));

        if (flush) {
            writer.flush();
        }
    }

    public static void write(final File out, final CharSequence str) {
        write(out, str, Charsets.DEFAULT);
    }

    public static void write(final File out, final CharSequence str, Charset charset) {
        charset = charset == null ? Charsets.DEFAULT : charset;

        write(out, chars2Bytes(toCharArray(str), charset));
    }

    public static void write(final OutputStream out, final CharSequence str) {
        write(out, str, false);
    }

    public static void write(final OutputStream out, final CharSequence str, final Charset charset) {
        write(out, str, charset, false);
    }

    public static void write(final OutputStream out, final CharSequence str, final boolean flush) {
        write(out, str, Charsets.DEFAULT, flush);
    }

    public static void write(final OutputStream out, final CharSequence str, Charset charset, final boolean flush) {
        charset = charset == null ? Charsets.DEFAULT : charset;

        try {
            out.write(chars2Bytes(toCharArray(str), charset));

            if (flush) {
                out.flush();
            }
        } catch (IOException e) {
            throw new AbacusIOException(e);
        }
    }

    public static void write(final Writer out, final CharSequence str) {
        write(out, str, false);
    }

    public static void write(final Writer out, final CharSequence str, final boolean flush) {
        write(out, toCharArray(str), flush);
    }

    public static void write(final File out, final char[] chars) {
        write(out, chars, 0, chars.length);
    }

    public static void write(final File out, final char[] chars, final int offset, final int len) {
        write(out, chars, offset, len, Charsets.DEFAULT);
    }

    public static void write(final File out, final char[] chars, final int offset, final int len, final Charset charset) {
        write(out, chars2Bytes(chars, offset, len, charset));
    }

    public static void write(final OutputStream out, final char[] chars) {
        write(out, chars, 0, chars.length);
    }

    public static void write(final OutputStream out, final char[] chars, final int offset, final int len) {
        write(out, chars, offset, len, Charsets.DEFAULT);
    }

    public static void write(final OutputStream out, final char[] chars, final int offset, final int len, final Charset charset) {
        write(out, chars, offset, len, charset, false);
    }

    public static void write(final OutputStream out, final char[] chars, final boolean flush) {
        write(out, chars, 0, chars.length, flush);
    }

    public static void write(final OutputStream out, final char[] chars, final int offset, final int len, final boolean flush) {
        write(out, chars, offset, len, Charsets.DEFAULT, flush);
    }

    public static void write(final OutputStream out, final char[] chars, final int offset, final int len, final Charset charset, final boolean flush) {
        write(out, chars2Bytes(chars, offset, len, charset), flush);
    }

    public static void write(final Writer out, final char[] chars) {
        write(out, chars, 0, chars.length);
    }

    public static void write(final Writer out, final char[] chars, final int offset, final int len) {
        write(out, chars, offset, len, false);
    }

    public static void write(final Writer out, final char[] chars, final boolean flush) {
        write(out, chars, 0, chars.length, flush);
    }

    public static void write(final Writer out, final char[] chars, final int offset, final int len, final boolean flush) {
        try {
            out.write(chars, offset, len);

            if (flush) {
                out.flush();
            }
        } catch (IOException e) {
            throw new AbacusIOException(e);
        }
    }

    public static void write(final File out, final byte[] bytes) {
        write(out, bytes, 0, bytes.length);
    }

    public static void write(final File out, final byte[] bytes, final int offset, final int len) {
        OutputStream os = null;

        try {
            if (!out.exists()) {
                out.createNewFile();
            }

            os = new FileOutputStream(out);

            write(os, bytes, offset, len);

            os.flush();
        } catch (IOException e) {
            throw new AbacusIOException(e);
        } finally {
            close(os);
        }
    }

    public static void write(final OutputStream out, final byte[] bytes) {
        write(out, bytes, 0, bytes.length);
    }

    public static void write(final OutputStream out, final byte[] bytes, final int offset, final int len) {
        write(out, bytes, offset, len, false);
    }

    public static void write(final OutputStream out, final byte[] bytes, final boolean flush) {
        write(out, bytes, 0, bytes.length, flush);
    }

    public static void write(final OutputStream out, final byte[] bytes, final int offset, final int len, final boolean flush) {
        try {
            out.write(bytes, offset, len);

            if (flush) {
                out.flush();
            }
        } catch (IOException e) {
            throw new AbacusIOException(e);
        }
    }

    public static long write(final File output, final InputStream input) {
        return write(output, input, 0, Long.MAX_VALUE);
    }

    /**
     * 
     * @param output
     * @param input
     * @param offset by byte
     * @param len by byte
     * @return
     */
    public static long write(final File output, final InputStream input, final long offset, final long len) {
        OutputStream os = null;

        try {
            if (!output.exists()) {
                output.createNewFile();
            }

            os = new FileOutputStream(output);

            long result = write(os, input, offset, len);

            os.flush();

            return result;
        } catch (IOException e) {
            throw new AbacusIOException(e);
        } finally {
            close(os);
        }
    }

    public static long write(final OutputStream output, final InputStream input) {
        return write(output, input, false);
    }

    public static long write(final OutputStream output, final InputStream input, final long offset, final long len) {
        return write(output, input, offset, len, false);
    }

    public static long write(final OutputStream output, final InputStream input, final boolean flush) {
        return write(output, input, 0, Long.MAX_VALUE, flush);
    }

    /**
     * 
     * @param output
     * @param input
     * @param offset by byte
     * @param len by byte
     * @param flush
     * @return
     */
    public static long write(final OutputStream output, final InputStream input, final long offset, final long len, final boolean flush) {
        final byte[] buf = ObjectFactory.createByteArrayBuffer();

        try {
            if (offset > 0) {
                skipFully(input, offset);
            }

            if (len == 0) {
                return 0;
            }

            final int bufLength = buf.length;
            long totalCount = 0;
            int count = 0;

            while ((totalCount < len) && (EOF != (count = read(input, buf, 0, (int) Math.min(len - totalCount, bufLength))))) {
                output.write(buf, 0, count);

                totalCount += count;
            }

            if (flush) {
                output.flush();
            }

            return totalCount;
        } catch (IOException e) {
            throw new AbacusIOException(e);
        } finally {
            ObjectFactory.recycle(buf);
        }
    }

    public static long write(final File output, final Reader input) {
        return write(output, input, Charsets.DEFAULT);
    }

    public static long write(final File output, final Reader input, final Charset charset) {
        return write(output, input, 0, Long.MAX_VALUE, charset);
    }

    public static long write(final File output, final Reader input, final long offset, final long len) {
        return write(output, input, offset, len, Charsets.DEFAULT);
    }

    /**
     * 
     * @param output
     * @param input
     * @param offset by char
     * @param len by char
     * @param charset
     * @return
     */
    public static long write(final File output, final Reader input, final long offset, final long len, final Charset charset) {
        Writer writer = null;

        try {
            writer = new OutputStreamWriter(new FileOutputStream(output), charset == null ? Charsets.DEFAULT : charset);

            long result = write(writer, input, offset, len);

            writer.flush();

            return result;
        } catch (IOException e) {
            throw new AbacusIOException(e);
        } finally {
            close(writer);
        }
    }

    public static long write(final Writer output, final Reader input) {
        return write(output, input, false);
    }

    public static long write(final Writer output, final Reader input, final long offset, final long len) {
        return write(output, input, offset, len, false);
    }

    public static long write(final Writer output, final Reader input, final boolean flush) {
        return write(output, input, 0, Long.MAX_VALUE, flush);
    }

    /**
     * 
     * @param output
     * @param input
     * @param offset by char
     * @param len by char
     * @param flush
     * @return
     */
    public static long write(final Writer output, final Reader input, final long offset, final long len, final boolean flush) {
        final char[] buf = ObjectFactory.createCharArrayBuffer();

        try {
            if (offset > 0) {
                skipFully(input, offset);
            }

            if (len == 0) {
                return 0;
            }

            final int bufLength = buf.length;
            long totalCount = 0;
            int count = 0;

            while ((totalCount < len) && (EOF != (count = read(input, buf, 0, (int) Math.min(len - totalCount, bufLength))))) {
                output.write(buf, 0, count);

                totalCount += count;
            }

            if (flush) {
                output.flush();
            }

            return totalCount;
        } catch (IOException e) {
            throw new AbacusIOException(e);
        } finally {
            ObjectFactory.recycle(buf);
        }
    }

    //    public static OutputStream base64Wrap(OutputStream os) {
    //        return Base64.getEncoder().wrap(os);
    //    }
    //
    //    public static InputStream base64Wrap(InputStream is) {
    //        return Base64.getDecoder().wrap(is);
    //    }

    // ============================= Java 8 only
    //    public static OutputStream base64Wrap(OutputStream os) {
    //        return java.util.Base64.getEncoder().wrap(os);
    //    }
    //
    //    public static InputStream base64Wrap(InputStream is) {
    //        return java.util.Base64.getDecoder().wrap(is);
    //    }

    public static long write(final File output, final File input) {
        return write(output, input, 0, Long.MAX_VALUE);
    }

    public static long write(final File output, final File input, final long offset, final long len) {
        OutputStream os = null;
        InputStream is = null;

        try {
            os = new FileOutputStream(output);
            is = new FileInputStream(input);

            return write(os, is, offset, len, true);
        } catch (IOException e) {
            throw new AbacusIOException(e);
        } finally {
            closeQuietly(os);
            closeQuietly(is);
        }
    }

    public static long write(final OutputStream output, final File input) {
        return write(output, input, false);
    }

    public static long write(final OutputStream output, final File input, final long offset, final long len) {
        return write(output, input, offset, len, false);
    }

    public static long write(final OutputStream output, final File input, final boolean flush) {
        return write(output, input, 0, Long.MAX_VALUE, flush);
    }

    /**
     * 
     * @param output
     * @param input
     * @param offset
     * @param offset by byte
     * @param len by byte
     * @return
     */
    public static long write(final OutputStream output, final File input, final long offset, final long len, final boolean flush) {
        InputStream is = null;
        try {
            is = new FileInputStream(input);

            return write(output, is, offset, len, flush);
        } catch (IOException e) {
            throw new AbacusIOException(e);
        } finally {
            closeQuietly(is);
        }
    }

    public static long write(final Writer output, final File input) {
        return write(output, input, false);
    }

    public static long write(final Writer output, final File input, final long offset, final long len) {
        return write(output, input, offset, len, false);
    }

    public static long write(final Writer output, final File input, final boolean flush) {
        return write(output, input, 0, Long.MAX_VALUE, flush);
    }

    /**
     * 
     * @param output
     * @param input
     * @param offset by char
     * @param len by char
     * @param flush
     * @return
     */
    public static long write(final Writer output, final File input, final long offset, final long len, final boolean flush) {
        Reader reader = null;
        try {
            reader = new FileReader(input);

            return write(output, reader, offset, len, flush);
        } catch (IOException e) {
            throw new AbacusIOException(e);
        } finally {
            closeQuietly(reader);
        }
    }

    public static void append(final File out, final byte[] bytes) {
        append(out, bytes, 0, bytes.length);
    }

    public static void append(final File out, final byte[] bytes, final int offset, final int len) {
        OutputStream os = null;

        try {
            if (!out.exists()) {
                out.createNewFile();
            }

            os = new FileOutputStream(out, true);

            write(os, bytes, offset, len);

            os.flush();
        } catch (IOException e) {
            throw new AbacusIOException(e);
        } finally {
            close(os);
        }
    }

    public static void append(final File out, final char[] chars) {
        append(out, chars, 0, chars.length);
    }

    public static void append(final File out, final char[] chars, final int offset, final int len) {
        append(out, chars, offset, len, Charsets.DEFAULT);
    }

    public static void append(final File out, final char[] chars, final int offset, final int len, final Charset charset) {
        append(out, chars2Bytes(chars, offset, len, charset));
    }

    public static void append(File output, CharSequence str) {
        append(output, str, Charsets.DEFAULT);
    }

    public static void append(File output, CharSequence str, Charset charset) {
        final char[] chs = toCharArray(str);

        append(output, chs, 0, chs.length, charset);
    }

    public static long append(final File output, final InputStream input) {
        return append(output, input, 0, Long.MAX_VALUE);
    }

    /**
     * 
     * @param output
     * @param input
     * @param offset by byte
     * @param len by byte
     * @return
     */
    public static long append(final File output, final InputStream input, final long offset, final long len) {
        OutputStream os = null;

        try {
            if (!output.exists()) {
                output.createNewFile();
            }

            os = new FileOutputStream(output, true);

            long result = write(os, input, offset, len);

            os.flush();

            return result;
        } catch (IOException e) {
            throw new AbacusIOException(e);
        } finally {
            close(os);
        }
    }

    public static long append(final File output, final Reader input) {
        return append(output, input, Charsets.DEFAULT);
    }

    public static long append(final File output, final Reader input, final Charset charset) {
        return append(output, input, 0, Long.MAX_VALUE, charset);
    }

    public static long append(final File output, final Reader input, final long offset, final long len) {
        return append(output, input, offset, len, Charsets.DEFAULT);
    }

    /**
     * 
     * @param output
     * @param input
     * @param offset by char
     * @param len by char
     * @param charset
     * @return
     */
    public static long append(final File output, final Reader input, final long offset, final long len, final Charset charset) {
        Writer writer = null;

        try {
            writer = new OutputStreamWriter(new FileOutputStream(output, true), charset == null ? Charsets.DEFAULT : charset);

            long result = write(writer, input, offset, len);

            writer.flush();

            return result;
        } catch (IOException e) {
            throw new AbacusIOException(e);
        } finally {
            close(writer);
        }
    }

    public static long append(final File output, final File input) {
        return append(output, input, 0, Long.MAX_VALUE);
    }

    public static long append(final File output, final File input, final long offset, final long len) {
        OutputStream os = null;
        InputStream is = null;

        try {
            os = new FileOutputStream(output, true);
            is = new FileInputStream(input);

            return write(os, is, offset, len, true);
        } catch (IOException e) {
            throw new AbacusIOException(e);
        } finally {
            closeQuietly(os);
            closeQuietly(is);
        }
    }

    /**
     * Return the count of skipped bytes.
     *
     * @param input
     * @param toSkip
     * @return
     */
    public static long skip(final InputStream input, final long toSkip) {
        if (toSkip < 0) {
            throw new IllegalArgumentException("Skip count must be non-negative, actual: " + toSkip);
        } else if (toSkip == 0) {
            return 0;
        }

        final byte[] buf = ObjectFactory.createByteArrayBuffer();
        long remain = toSkip;

        try {
            while (remain > 0) {
                long n = read(input, buf, 0, (int) Math.min(remain, buf.length));

                if (n < 0) { // EOF

                    break;
                }

                remain -= n;
            }

            return toSkip - remain;
        } catch (IOException e) {
            throw new AbacusIOException(e);
        } finally {
            ObjectFactory.recycle(buf);
        }
    }

    /**
     * Return the count of skipped chars.
     *
     * @param input
     * @param toSkip
     * @return
     */
    public static long skip(final Reader input, final long toSkip) {
        if (toSkip < 0) {
            throw new IllegalArgumentException("Skip count must be non-negative, actual: " + toSkip);
        } else if (toSkip == 0) {
            return 0;
        }

        final char[] buf = ObjectFactory.createCharArrayBuffer();
        long remain = toSkip;

        try {
            while (remain > 0) {
                long n = read(input, buf, 0, (int) Math.min(remain, buf.length));

                if (n < 0) { // EOF

                    break;
                }

                remain -= n;
            }

            return toSkip - remain;
        } catch (IOException e) {
            throw new AbacusIOException(e);
        } finally {
            ObjectFactory.recycle(buf);
        }
    }

    /**
     *
     * @param input
     * @param toSkip
     * @throws AbacusIOException if the remaining length of the specified <code>input</code> is less than the specified <code>toSkip</code>
     */
    public static void skipFully(final InputStream input, final long toSkip) throws AbacusIOException {
        if (toSkip < 0) {
            throw new IllegalArgumentException("Bytes to skip must not be negative: " + toSkip);
        }

        long skipped = skip(input, toSkip);

        if (skipped != toSkip) {
            throw new AbacusIOException("Bytes to skip: " + toSkip + " actual: " + skipped);
        }
    }

    /**
     *
     * @param input
     * @param toSkip
     * @throws AbacusIOException if the remaining length of the specified <code>input</code> is less than the specified <code>toSkip</code>
     */
    public static void skipFully(final Reader input, final long toSkip) throws AbacusIOException {
        long skipped = skip(input, toSkip);

        if (skipped != toSkip) {
            throw new AbacusIOException("Chars to skip: " + toSkip + " actual: " + skipped);
        }
    }

    /**
     * Note: copied from Google Guava under Apache License v2.
     * 
     * @param file
     * @return
     */
    public static MappedByteBuffer map(File file) {
        N.requireNonNull(file);

        return map(file, MapMode.READ_ONLY);
    }

    /**
     * Note: copied from Google Guava under Apache License v2.
     * 
     * Fully maps a file in to memory as per
     * {@link FileChannel#map(java.nio.channels.FileChannel.MapMode, long, long)}
     * using the requested {@link MapMode}.
     *
     * <p>Files are mapped from offset 0 to its length.
     *
     * <p>This only works for files <= {@link Integer#MAX_VALUE} bytes.
     *
     * @param file the file to map
     * @param mode the mode to use when mapping {@code file}
     * @return a buffer reflecting {@code file}
     * @throws FileNotFoundException if the {@code file} does not exist
     *
     * @see FileChannel#map(MapMode, long, long)
     * @since 2.0
     */
    public static MappedByteBuffer map(File file, MapMode mode) {
        N.requireNonNull(file);
        N.requireNonNull(mode);

        if (!file.exists()) {
            throw new AbacusIOException(file.toString() + " is not found");
        }

        return map(file, mode, 0, file.length());
    }

    /**
     * Note: copied from Google Guava under Apache License v2.
     * 
     * Maps a file in to memory as per
     * {@link FileChannel#map(java.nio.channels.FileChannel.MapMode, long, long)}
     * using the requested {@link MapMode}.
     *
     * <p>Files are mapped from offset 0 to {@code size}.
     *
     * <p>If the mode is {@link MapMode#READ_WRITE} and the file does not exist,
     * it will be created with the requested {@code size}. Thus this method is
     * useful for creating memory mapped files which do not yet exist.
     *
     * <p>This only works for files <= {@link Integer#MAX_VALUE} bytes.
     *
     * @param file the file to map
     * @param mode the mode to use when mapping {@code file}
     * @param offset
     * @param len
     * @return a buffer reflecting {@code file}
     *
     * @see FileChannel#map(MapMode, long, long)
     * @since 2.0
     */
    public static MappedByteBuffer map(File file, MapMode mode, long offset, long len) {
        N.requireNonNull(file);
        N.requireNonNull(mode);

        RandomAccessFile raf = null;

        try {
            raf = new RandomAccessFile(file, mode == MapMode.READ_ONLY ? "r" : "rw");
            return raf.getChannel().map(mode, offset, len);
        } catch (IOException e) {
            throw new AbacusIOException(e);
        } finally {
            IOUtil.closeQuietly(raf);
        }
    }

    /**
     * Note: copied from Google Guava under Apache License v2.
     * 
     * Returns the lexically cleaned form of the path name, <i>usually</i> (but
     * not always) equivalent to the original. The following heuristics are used:
     *
     * <ul>
     * <li>empty string becomes .
     * <li>. stays as .
     * <li>fold out ./
     * <li>fold out ../ when possible
     * <li>collapse multiple slashes
     * <li>delete trailing slashes (unless the path is just "/")
     * </ul>
     *
     * <p>These heuristics do not always match the behavior of the filesystem. In
     * particular, consider the path {@code a/../b}, which {@code simplifyPath}
     * will change to {@code b}. If {@code a} is a symlink to {@code x}, {@code
     * a/../b} may refer to a sibling of {@code x}, rather than the sibling of
     * {@code a} referred to by {@code b}.
     *
     * @since 11.0
     */
    public static String simplifyPath(String pathname) {
        if (N.isNullOrEmpty(pathname)) {
            return ".";
        }

        pathname = pathname.replace('\\', '/');

        // split the path apart
        String[] components = N.split(pathname, '/', true);
        List<String> path = new ArrayList<>();

        // resolve ., .., and //
        for (String component : components) {
            if (component.length() == 0 || component.equals(".")) {
                continue;
            } else if (component.equals("..")) {
                if (path.size() > 0 && !path.get(path.size() - 1).equals("..")) {
                    path.remove(path.size() - 1);
                } else {
                    path.add("..");
                }
            } else {
                path.add(component);
            }
        }

        // put it back together
        String result = N.join(path, '/');

        if (pathname.charAt(0) == '/') {
            result = "/" + result;
        }

        while (result.startsWith("/../")) {
            result = result.substring(3);
        }

        if (result.equals("/..")) {
            result = "/";
        } else if ("".equals(result)) {
            result = ".";
        }

        return result;
    }

    /**
     * Note: copied from Google Guava under Apache License v2.
     * 
     * Returns the <a href="http://en.wikipedia.org/wiki/Filename_extension">file
     * extension</a> for the given file name, or the empty string if the file has
     * no extension.  The result does not include the '{@code .}'.
     *
     * @since 11.0
     */
    public static String getFileExtension(String fullName) {
        N.requireNonNull(fullName);

        String fileName = new File(fullName).getName();
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex == -1) ? "" : fileName.substring(dotIndex + 1);
    }

    /**
     * Note: copied from Google Guava under Apache License v2.
     * 
     * Returns the file name without its
     * <a href="http://en.wikipedia.org/wiki/Filename_extension">file extension</a> or path. This is
     * similar to the {@code basename} unix command. The result does not include the '{@code .}'.
     *
     * @param file The name of the file to trim the extension from. This can be either a fully
     *     qualified file name (including a path) or just a file name.
     * @return The file name without its path or extension.
     * @since 14.0
     */
    public static String getNameWithoutExtension(String file) {
        N.requireNonNull(file);

        String fileName = new File(file).getName();
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);
    }

    static java.io.BufferedReader newBufferedReader(String filePath) {
        return newBufferedReader(new File(filePath));
    }

    public static java.io.BufferedReader newBufferedReader(File file) {
        try {
            return new java.io.BufferedReader(new FileReader(file));
        } catch (FileNotFoundException e) {
            throw new AbacusIOException(e);
        }
    }

    public static java.io.BufferedReader newBufferedReader(File file, Charset charset) {
        try {
            return new java.io.BufferedReader(new InputStreamReader(new FileInputStream(file), charset == null ? Charsets.DEFAULT : charset));
        } catch (FileNotFoundException e) {
            throw new AbacusIOException(e);
        }
    }

    public static java.io.BufferedReader newBufferedReader(Path path) {
        try {
            return Files.newBufferedReader(path, Charsets.DEFAULT);
        } catch (IOException e) {
            throw new AbacusIOException(e);
        }
    }

    public static java.io.BufferedReader newBufferedReader(Path path, Charset charset) {
        try {
            return Files.newBufferedReader(path, charset);
        } catch (IOException e) {
            throw new AbacusIOException(e);
        }
    }

    public static java.io.BufferedReader newBufferedReader(InputStream is) {
        return new java.io.BufferedReader(new InputStreamReader(is));
    }

    public static java.io.BufferedReader newBufferedReader(InputStream is, Charset charset) {
        return new java.io.BufferedReader(new InputStreamReader(is, charset == null ? Charsets.DEFAULT : charset));
    }

    static java.io.BufferedWriter newBufferedWriter(String filePath) {
        return newBufferedWriter(new File(filePath));
    }

    public static java.io.BufferedWriter newBufferedWriter(File file) {
        try {
            return new java.io.BufferedWriter(new FileWriter(file));
        } catch (IOException e) {
            throw new AbacusIOException(e);
        }
    }

    public static java.io.BufferedWriter newBufferedWriter(File file, Charset charset) {
        try {
            return new java.io.BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), charset == null ? Charsets.DEFAULT : charset));
        } catch (IOException e) {
            throw new AbacusIOException(e);
        }
    }

    public static java.io.BufferedWriter newBufferedWriter(OutputStream os) {
        return new java.io.BufferedWriter(new OutputStreamWriter(os));
    }

    public static java.io.BufferedWriter newBufferedWriter(OutputStream os, Charset charset) {
        return new java.io.BufferedWriter(new OutputStreamWriter(os, charset == null ? Charsets.DEFAULT : charset));
    }

    public static LZ4BlockInputStream newLZ4BlockInputStream(final InputStream is) {
        try {
            return new LZ4BlockInputStream(is);
        } catch (IOException e) {
            throw new AbacusIOException(e);
        }
    }

    public static LZ4BlockOutputStream newLZ4BlockOutputStream(final OutputStream os) {
        try {
            return new LZ4BlockOutputStream(os);
        } catch (IOException e) {
            throw new AbacusIOException(e);
        }
    }

    /**
     * Creates a new input stream with the specified buffer size.
     */
    public static LZ4BlockOutputStream newLZ4BlockOutputStream(final OutputStream os, final int blockSize) {
        try {
            return new LZ4BlockOutputStream(os, blockSize);
        } catch (IOException e) {
            throw new AbacusIOException(e);
        }
    }

    public static SnappyInputStream newSnappyInputStream(final InputStream is) {
        try {
            return new SnappyInputStream(is);
        } catch (IOException e) {
            throw new AbacusIOException(e);
        }
    }

    public static SnappyOutputStream newSnappyOutputStream(final OutputStream os) {
        try {
            return new SnappyOutputStream(os);
        } catch (IOException e) {
            throw new AbacusIOException(e);
        }
    }

    /**
     * Creates a new input stream with the specified buffer size.
     */
    public static SnappyOutputStream newSnappyOutputStream(final OutputStream os, final int bufferSize) {
        try {
            return new SnappyOutputStream(os, bufferSize);
        } catch (IOException e) {
            throw new AbacusIOException(e);
        }
    }

    public static GZIPInputStream newGZIPInputStream(final InputStream is) {
        try {
            return new GZIPInputStream(is);
        } catch (IOException e) {
            throw new AbacusIOException(e);
        }
    }

    /**
     * Creates a new input stream with the specified buffer size.
     */
    public static GZIPInputStream newGZIPInputStream(final InputStream is, final int bufferSize) {
        try {
            return new GZIPInputStream(is, bufferSize);
        } catch (IOException e) {
            throw new AbacusIOException(e);
        }
    }

    public static GZIPOutputStream newGZIPOutputStream(final OutputStream os) {
        try {
            return new GZIPOutputStream(os);
        } catch (IOException e) {
            throw new AbacusIOException(e);
        }
    }

    /**
     * Creates a new input stream with the specified buffer size.
     */
    public static GZIPOutputStream newGZIPOutputStream(final OutputStream os, final int bufferSize) {
        try {
            return new GZIPOutputStream(os, bufferSize);
        } catch (IOException e) {
            throw new AbacusIOException(e);
        }
    }

    public static void close(final ZipFile file) {
        if (file != null) {
            try {
                file.close();
            } catch (IOException e) {
                throw new AbacusIOException(e);
            }
        }
    }

    public static void close(final InputStream is) {
        close((Closeable) is);
    }

    public static void close(final OutputStream os) {
        close((Closeable) os);
    }

    public static void close(final Reader reader) {
        close((Closeable) reader);
    }

    public static void close(final Writer writer) {
        close((Closeable) writer);
    }

    public static void close(final AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception e) {
                throw new AbacusIOException(e);
            }
        }
    }

    @SafeVarargs
    public static void close(final AutoCloseable... a) {
        if (N.isNullOrEmpty(a)) {
            return;
        }

        close(Arrays.asList(a));
    }

    public static void close(final Collection<? extends AutoCloseable> c) {
        if (N.isNullOrEmpty(c)) {
            return;
        }

        RuntimeException ex = null;

        for (AutoCloseable closeable : c) {
            try {
                close(closeable);
            } catch (RuntimeException e) {
                if (ex == null) {
                    ex = e;
                } else {
                    ex.addSuppressed(e);
                }
            }
        }

        if (ex != null) {
            throw ex;
        }
    }

    public static void closeQuietly(final ZipFile file) {
        if (file != null) {
            try {
                file.close();
            } catch (Throwable e) {
                // ignore
                logger.error("Failed to close", e);
            }
        }
    }

    public static void closeQuietly(final InputStream is) {
        closeQuietly((Closeable) is);
    }

    public static void closeQuietly(final OutputStream os) {
        closeQuietly((Closeable) os);
    }

    public static void closeQuietly(final Reader reader) {
        closeQuietly((Closeable) reader);
    }

    public static void closeQuietly(final Writer writer) {
        closeQuietly((Closeable) writer);
    }

    public static void closeQuietly(final AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Throwable e) {
                // ignore
                logger.error("Failed to close", e);
            }
        }
    }

    @SafeVarargs
    public static void closeQuietly(final AutoCloseable... a) {
        if (N.isNullOrEmpty(a)) {
            return;
        }

        closeQuietly(Arrays.asList(a));
    }

    public static void closeQuietly(final Collection<? extends AutoCloseable> c) {
        if (N.isNullOrEmpty(c)) {
            return;
        }

        for (AutoCloseable closeable : c) {
            closeQuietly(closeable);
        }
    }

    public static void copy(final File srcFile, final File destDir) {
        copy(srcFile, destDir, true);
    }

    public static void copy(final File srcFile, final File destDir, final boolean preserveFileDate) {
        copy(srcFile, destDir, preserveFileDate, null);
    }

    /**
     * Copy the specified <code>scrFile</code> if it's a file or its sub files/directories if it's a directory to the target <code>destDir</code> with the specified <code>filter</code>
     * 
     * @param srcFile
     * @param destDir
     * @param preserveFileDate
     * @param filter
     */
    public static void copy(File srcFile, File destDir, final boolean preserveFileDate, final FileFilter filter) {
        if (!srcFile.exists()) {
            throw new AbacusIOException("The source file doesn't exist: " + srcFile.getAbsolutePath());
        }

        if (destDir.exists()) {
            if (destDir.isFile()) {
                throw new AbacusIOException("The destination file must be directory: " + destDir.getAbsolutePath());
            }
        } else {
            if (!destDir.mkdirs()) {
                throw new AbacusIOException("Failed to create destination directory: " + destDir.getAbsolutePath());
            }
        }

        if (destDir.canWrite() == false) {
            throw new AbacusIOException("Destination '" + destDir + "' cannot be written to");
        }

        String destCanonicalPath = null;
        String srcCanonicalPath = null;
        try {
            srcFile = srcFile.getCanonicalFile();
            destDir = destDir.getCanonicalFile();
            destCanonicalPath = destDir.getCanonicalPath();
            srcCanonicalPath = srcFile.getCanonicalPath();
        } catch (IOException e) {
            throw new AbacusIOException(e);
        }

        if (srcFile.isDirectory()) {
            if (destCanonicalPath.startsWith(srcCanonicalPath) && (destCanonicalPath.length() == srcCanonicalPath.length()
                    || destCanonicalPath.charAt(srcCanonicalPath.length()) == '/' || destCanonicalPath.charAt(srcCanonicalPath.length()) == '\\')) {
                throw new AbacusIOException(
                        "Failed to copy due to the target directory: " + destCanonicalPath + " is in or same as the source directory: " + srcCanonicalPath);
            }

            try {
                doCopyDirectory(srcFile, destDir, preserveFileDate, filter);
            } catch (IOException e) {
                throw new AbacusIOException(e);
            }
        } else {
            File destFile = null;

            try {
                if (destDir.getCanonicalPath().equals(srcFile.getParentFile().getCanonicalPath())) {
                    destFile = new File(destDir, "Copy of " + srcFile.getName());
                } else {
                    destFile = new File(destDir, srcFile.getName());
                }
            } catch (IOException e) {
                throw new AbacusIOException(e);
            }

            doCopyFile(srcFile, destFile, preserveFileDate);
        }
    }

    /**
     * Internal copy directory method.
     *
     * @param srcDir
     *            the validated source directory, must not be {@code null}
     * @param destDir
     *            the validated destination directory, must not be {@code null}
     * @param preserveFileDate
     *            whether to preserve the file date
     * @param filter
     *            the filter to apply, null means copy all directories and files
     * @param exclusionList
     *            List of files and directories to exclude from the copy, may be null
     * @throws IOException
     *             if an error occurs
     * @since 1.1
     */
    private static void doCopyDirectory(final File srcDir, final File destDir, final boolean preserveFileDate, final FileFilter filter) throws IOException {
        if (destDir.exists()) {
            if (destDir.isFile()) {
                throw new IOException("Destination '" + destDir + "' exists but is not a directory");
            }
        } else {
            if (!destDir.mkdirs()) {
                throw new IOException("Destination '" + destDir + "' directory cannot be created");
            }
        }

        final File[] subFiles = srcDir.listFiles();

        if (N.isNullOrEmpty(subFiles)) {
            return;
        }

        for (File subFile : subFiles) {
            if (subFile == null) {
                continue;
            }

            if (filter == null || filter.accept(srcDir, subFile)) {
                final File dest = new File(destDir, subFile.getName());

                if (subFile.isDirectory()) {
                    doCopyDirectory(subFile, dest, preserveFileDate, null);
                } else {
                    doCopyFile(subFile, dest, preserveFileDate);
                }
            } else if (subFile.isDirectory()) {
                final File dest = new File(destDir, subFile.getName());
                doCopyDirectory(subFile, dest, preserveFileDate, filter);
            }
        }

        // Do this last, as the above has probably affected directory metadata
        if (preserveFileDate) {
            destDir.setLastModified(srcDir.lastModified());
        }
    }

    /**
     * Internal copy file method.
     *
     * @param srcFile
     *            the validated source file, must not be {@code null}
     * @param destFile
     *            the validated destination file, must not be {@code null}
     * @param preserveFileDate
     *            whether to preserve the file date
     */
    private static void doCopyFile(final File srcFile, final File destFile, final boolean preserveFileDate) {
        if (destFile.exists()) {
            throw new AbacusIOException("The destination file already existed: " + destFile.getAbsolutePath());
        }

        FileInputStream fis = null;
        FileOutputStream fos = null;
        FileChannel input = null;
        FileChannel output = null;

        try {
            fis = new FileInputStream(srcFile);
            fos = new FileOutputStream(destFile);
            input = fis.getChannel();
            output = fos.getChannel();

            long size = input.size();
            long pos = 0;
            long count = 0;

            while (pos < size) {
                count = ((size - pos) > FILE_COPY_BUFFER_SIZE) ? FILE_COPY_BUFFER_SIZE : (size - pos);
                pos += output.transferFrom(input, pos, count);
            }
        } catch (IOException e) {
            throw new AbacusIOException(e);
        } finally {
            close(output);
            close(fos);
            close(input);
            close(fis);
        }

        if (srcFile.length() != destFile.length()) {
            deleteAllIfExists(destFile);
            throw new AbacusIOException("Failed to copy full contents from '" + srcFile + "' to '" + destFile + "'");
        }

        if (preserveFileDate) {
            destFile.setLastModified(srcFile.lastModified());
        }
    }

    /**
     * 
     * @param source
     * @param target
     * @param options
     * @return
     * @see Files#copy(Path, Path, CopyOption...)
     */
    @SafeVarargs
    public static Path copy(Path source, Path target, CopyOption... options) {
        try {
            return Files.copy(source, target, options);
        } catch (IOException e) {
            throw new AbacusIOException(e);
        }
    }

    /**
     * 
     * @param in
     * @param target
     * @param options
     * @return
     * @see Files#copy(InputStream, Path, CopyOption...)
     */
    @SafeVarargs
    public static long copy(InputStream in, Path target, CopyOption... options) {
        try {
            return Files.copy(in, target, options);
        } catch (IOException e) {
            throw new AbacusIOException(e);
        }
    }

    /**
     * 
     * @param source
     * @param out
     * @return
     * @see Files#copy(Path, OutputStream)
     */
    public static long copy(Path source, OutputStream out) {
        try {
            return Files.copy(source, out);
        } catch (IOException e) {
            throw new AbacusIOException(e);
        }
    }

    //-----------------------------------------------------------------------
    /**
     * Copies bytes from the URL <code>source</code> to a file
     * <code>destination</code>. The directories up to <code>destination</code>
     * will be created if they don't already exist. <code>destination</code>
     * will be overwritten if it already exists.
     * <p>
     * Warning: this method does not set a connection or read timeout and thus
     * might block forever. Use {@link #copyURLToFile(URL, File, int, int)}
     * with reasonable timeouts to prevent this.
     *
     * @param source  the <code>URL</code> to copy bytes from, must not be {@code null}
     * @param destination  the non-directory <code>File</code> to write bytes to
     *  (possibly overwriting), must not be {@code null}
     * @throws AbacusIOException if <code>source</code> URL cannot be opened
     * @throws AbacusIOException if <code>destination</code> is a directory
     * @throws AbacusIOException if <code>destination</code> cannot be written
     * @throws AbacusIOException if <code>destination</code> needs creating but can't be
     * @throws AbacusIOException if an IO error occurs during copying
     */
    public static void copyURLToFile(final URL source, final File destination) {
        InputStream is = null;
        try {
            is = source.openStream();

            write(destination, is);
        } catch (IOException e) {
            throw new AbacusIOException(e);
        } finally {
            close(is);
        }
    }

    /**
     * Copies bytes from the URL <code>source</code> to a file
     * <code>destination</code>. The directories up to <code>destination</code>
     * will be created if they don't already exist. <code>destination</code>
     * will be overwritten if it already exists.
     *
     * @param source  the <code>URL</code> to copy bytes from, must not be {@code null}
     * @param destination  the non-directory <code>File</code> to write bytes to
     *  (possibly overwriting), must not be {@code null}
     * @param connectionTimeout the number of milliseconds until this method
     *  will timeout if no connection could be established to the <code>source</code>
     * @param readTimeout the number of milliseconds until this method will
     *  timeout if no data could be read from the <code>source</code>
     * @throws AbacusIOException if <code>source</code> URL cannot be opened
     * @throws AbacusIOException if <code>destination</code> is a directory
     * @throws AbacusIOException if <code>destination</code> cannot be written
     * @throws AbacusIOException if <code>destination</code> needs creating but can't be
     * @throws AbacusIOException if an IO error occurs during copying
     */
    public static void copyURLToFile(final URL source, final File destination, final int connectionTimeout, final int readTimeout) {
        InputStream is = null;
        try {
            final URLConnection connection = source.openConnection();
            connection.setConnectTimeout(connectionTimeout);
            connection.setReadTimeout(readTimeout);
            is = connection.getInputStream();

            write(destination, is);
        } catch (IOException e) {
            throw new AbacusIOException(e);
        } finally {
            close(is);
        }
    }

    public static void move(final File srcFile, final File destDir) {
        if (!srcFile.exists()) {
            throw new AbacusIOException("The source file doesn't exist: " + srcFile.getAbsolutePath());
        }

        if (destDir.exists()) {
            if (destDir.isFile()) {
                throw new AbacusIOException("The destination file must be directory: " + destDir.getAbsolutePath());
            }
        } else {
            if (!destDir.mkdirs()) {
                throw new AbacusIOException("Failed to create destination directory: " + destDir.getAbsolutePath());
            }
        }

        File destFile = new File(destDir, srcFile.getName());

        if (!srcFile.renameTo(destFile)) {
            throw new AbacusIOException("Failed to move file from: " + srcFile.getAbsolutePath() + " to: " + destDir.getAbsolutePath());
        }
    }

    /**
     *
     * @param srcFile
     * @param rewName the new file name under same path.
     * @return <code>true</code> if and only if the renaming succeeded;
     *          <code>false</code> otherwise
     */
    public static boolean renameTo(final File srcFile, final String newFileName) {
        return srcFile.renameTo(new File(srcFile.getParent() + N.FILE_SEPARATOR + newFileName));
    }

    /**
     * Delete the specified file (or directory).
     * 
     * @param file
     * @return true if the file is deleted successfully, otherwise false if the file is null or doesn't exist, or can't be deleted.
     */
    public static boolean deleteIfExists(final File file) {
        if ((file == null) || !file.exists()) {
            return false;
        }

        return file.delete();
    }

    /**
     * Delete the specified file and all its sub files/directories if it's a directory.
     *
     * @param file
     * @return true if the file is deleted successfully, otherwise false if the file is null or doesn't exist, or can't be deleted.
     */
    public static boolean deleteAllIfExists(final File file) {
        if ((file == null) || !file.exists()) {
            return false;
        }

        if (file.isDirectory()) {
            final File[] files = file.listFiles();

            if (N.notNullOrEmpty(files)) {
                for (File subFile : files) {
                    if (subFile == null) {
                        continue;
                    }

                    if (subFile.isFile()) {
                        if (subFile.delete() == false) {
                            return false;
                        }
                    } else {
                        if (deleteAllIfExists(subFile) == false) {
                            return false;
                        }
                    }
                }
            }
        }

        return file.delete();
    }

    public static boolean deleteFiles(final File dir) {
        return deleteFiles(dir, null);
    }

    /**
     * Delete the specifield <code>dir</code> if it's a file or its sub files/directories if it's a directory with the specified filter.
     * 
     * @param dir
     * @param filter
     * @return
     */
    public static boolean deleteFiles(final File dir, FileFilter filter) {
        if ((dir == null) || !dir.exists()) {
            return false;
        }

        if (dir.isDirectory()) {
            final File[] files = dir.listFiles();

            if (N.isNullOrEmpty(files)) {
                return true;
            }

            for (File subFile : files) {
                if (subFile == null) {
                    continue;
                }

                if (filter == null || filter.accept(dir, subFile)) {
                    if (subFile.isFile()) {
                        if (subFile.delete() == false) {
                            return false;
                        }
                    } else {
                        if (deleteAllIfExists(subFile) == false) {
                            return false;
                        }
                    }
                } else {
                    if (subFile.isDirectory()) {
                        if (deleteFiles(subFile, filter) == false) {
                            return false;
                        }
                    }
                }
            }
        } else {
            if (filter == null || filter.accept(dir.getParentFile(), dir)) {
                return dir.delete();
            }
        }

        return true;
    }

    /**
     * 
     * @param file
     * @return <code>false</code> if file exists or failed to create new file.
     */
    public static boolean createIfNotExists(final File file) {
        try {
            return file.exists() ? false : file.createNewFile();
        } catch (IOException e) {
            throw new AbacusIOException(e);
        }
    }

    public static void zip(final File sourceFile, final File targetFile) {
        ZipOutputStream zos = null;

        try {
            zos = new ZipOutputStream(new FileOutputStream(targetFile));
            zipFile(sourceFile, zos, targetFile);
        } catch (IOException e) {
            throw new AbacusIOException(e);
        } finally {
            close(zos);
        }
    }

    public static void zip(final Collection<File> sourceFiles, final File targetFile) {
        ZipOutputStream zos = null;

        try {
            zos = new ZipOutputStream(new FileOutputStream(targetFile));

            for (File sourceFile : sourceFiles) {
                zipFile(sourceFile, zos, targetFile);
            }
        } catch (IOException e) {
            throw new AbacusIOException(e);
        } finally {
            close(zos);
        }
    }

    private static void zipFile(final File sourceFile, final ZipOutputStream zos, final File targetFile) throws IOException, FileNotFoundException {
        if (sourceFile.isFile()) {
            zipFile(sourceFile, null, zos, targetFile);
        } else {
            List<File> subFileList = listFiles(sourceFile, true, true);

            // subFileList.add(sourceFile);
            for (File subFile : subFileList) {
                zipFile(subFile, sourceFile, zos, targetFile);
            }
        }
    }

    private static void zipFile(final File file, final File sourceDir, final ZipOutputStream zos, final File targetFile)
            throws IOException, FileNotFoundException {
        if (file.equals(targetFile)) {
            return;
        }

        ZipEntry ze = null;
        String relativeFileName = null;

        if (sourceDir == null) {
            relativeFileName = file.getName();
        } else {
            relativeFileName = getRelativePath(sourceDir, file);
        }

        ze = new ZipEntry(relativeFileName);
        ze.setSize(file.length());
        ze.setTime(file.lastModified());
        zos.putNextEntry(ze);

        InputStream is = new FileInputStream(file);

        final byte[] buf = ObjectFactory.createByteArrayBuffer();

        try {
            int count = 0;

            while (EOF != (count = read(is, buf, 0, buf.length))) {
                zos.write(buf, 0, count);
            }
        } finally {
            ObjectFactory.recycle(buf);

            closeQuietly(is);
        }
    }

    public static void unzip(final File srcZipFile, final File targetDir) {
        ZipFile zip = null;
        ZipEntry ze = null;
        OutputStream os = null;
        InputStream is = null;

        final byte[] buf = ObjectFactory.createByteArrayBuffer();
        final int bufLength = buf.length;

        try {
            zip = new ZipFile(srcZipFile);

            Enumeration<? extends ZipEntry> entryEnum = zip.entries();

            while (entryEnum.hasMoreElements()) {
                ze = entryEnum.nextElement();

                if (ze.isDirectory()) {
                    continue;
                }

                os = new FileOutputStream(getAbsolutePath(targetDir, ze.getName()));

                is = zip.getInputStream(ze);

                int count = 0;

                while (EOF != (count = read(is, buf, 0, bufLength))) {
                    os.write(buf, 0, count);
                }

                os.flush();

                closeQuietly(is);
                is = null;
                close(os);
                os = null;
            }
        } catch (IOException e) {
            throw new AbacusIOException(e);
        } finally {
            ObjectFactory.recycle(buf);

            closeQuietly(zip);
            closeQuietly(is);
            close(os);
        }
    }

    public static void split(final File file, final int countOfParts) {
        split(file, countOfParts, file.getParentFile());
    }

    public static void split(final File file, final int countOfParts, final File destDir) {
        final long sizeOfPart = (file.length() % countOfParts) == 0 ? (file.length() / countOfParts) : (file.length() / countOfParts) + 1;

        splitBySize(file, sizeOfPart, destDir);
    }

    public static void splitBySize(final File file, final long sizeOfPart) {
        splitBySize(file, sizeOfPart, file.getParentFile());
    }

    /**
     * Mostly it's designed for (zipped/unzipped/log) text files.
     *
     * @param file
     * @param sizeOfPart
     * @param destDir
     */
    public static void splitBySize(final File file, final long sizeOfPart, final File destDir) {
        final int numOfParts = (int) ((file.length() % sizeOfPart) == 0 ? (file.length() / sizeOfPart) : (file.length() / sizeOfPart) + 1);

        final String fileName = file.getName();
        final long fileLength = file.length();
        int fileSerNum = 1;

        final byte[] buf = ObjectFactory.createByteArrayBuffer();
        InputStream input = null;
        OutputStream output = null;
        try {
            input = new FileInputStream(file);

            for (int i = 0; i < numOfParts; i++) {
                String subFileNmae = destDir.getAbsolutePath() + N.FILE_SEPARATOR + fileName + "_" + N.padStart(N.stringOf(fileSerNum++), 4, '0');
                output = new FileOutputStream(new File(subFileNmae));
                long partLength = sizeOfPart;

                if (i == numOfParts - 1) {
                    partLength += fileLength % numOfParts;
                }

                int count = 0;

                try {
                    while (partLength > 0 && EOF != (count = read(input, buf, 0, (int) Math.min(buf.length, partLength)))) {
                        output.write(buf, 0, count);

                        partLength = partLength - count;
                    }

                    output.flush();
                } finally {
                    close(output);
                }
            }
        } catch (IOException e) {
            throw new AbacusIOException(e);
        } finally {
            ObjectFactory.recycle(buf);

            closeQuietly(input);
        }
    }

    static void splitByLine(final File file, final int numOfParts) {
        splitByLine(file, numOfParts, file.getParentFile());
    }

    /**
     * Mostly it's designed for (zipped/unzipped/log) text files.
     *
     * @param file
     * @param numOfParts
     * @param destDir
     */
    static void splitByLine(final File file, final int numOfParts, final File destDir) {
        final long lineNumOfPart = estimateLineCount(file, 10000) / numOfParts;

        int index = file.getName().lastIndexOf('.');
        String prefix = file.getName().substring(0, index);
        String postfix = (index > 0) ? file.getName().substring(index) : "";

        final Output<ZipFile> outputZipFile = new Output<>();
        InputStream is = null;

        BufferedReader br = null;
        BufferedWriter bw = null;
        int fileSerNum = 1;

        try {
            is = openFile(outputZipFile, file);

            br = ObjectFactory.createBufferedReader(is);

            String subFileNmae = destDir.getAbsolutePath() + N.FILE_SEPARATOR + prefix + "_" + N.padStart(N.stringOf(fileSerNum++), 4, '0') + postfix;
            bw = ObjectFactory.createBufferedWriter(new FileWriter(new File(subFileNmae)));

            int lineCounter = 0;
            String line = null;
            while ((line = br.readLine()) != null) {
                bw.write(line);
                bw.write(N.LINE_SEPARATOR);
                lineCounter++;

                if ((lineCounter % lineNumOfPart) == 0) {
                    if (bw != null) {
                        close(bw);
                        ObjectFactory.recycle(bw);
                        bw = null;
                    }

                    subFileNmae = destDir.getAbsolutePath() + N.FILE_SEPARATOR + prefix + "_" + N.padStart(N.stringOf(fileSerNum++), 4, '0') + postfix;
                    bw = ObjectFactory.createBufferedWriter(new FileWriter(new File(subFileNmae)));
                }
            }

            if (bw != null) {
                close(bw);
                ObjectFactory.recycle(bw);
                bw = null;
            }
        } catch (IOException e) {
            throw new AbacusIOException(e);
        } finally {
            if (bw != null) {
                close(bw);
                ObjectFactory.recycle(bw);
            }

            closeQuietly(is);
            close(outputZipFile.value());

            ObjectFactory.recycle(br);
        }
    }

    /**
     * Estimate the total line count of the file by reading the specified line count ahead.
     *
     * @param file
     * @param byReadingLineNum
     * @return
     */
    private static long estimateLineCount(final File file, final int byReadingLineNum) {
        final Output<ZipFile> outputZipFile = new Output<>();
        InputStream is = null;
        BufferedReader br = null;

        try {
            is = openFile(outputZipFile, file);

            br = ObjectFactory.createBufferedReader(is);

            int cnt = 0;
            String line = null;
            long bytes = 0;
            while (cnt < byReadingLineNum && (line = br.readLine()) != null) {
                bytes += line.getBytes().length;

                cnt++;
            }

            return cnt == 0 ? 0 : (file.length() / (bytes / cnt == 0 ? 1 : bytes / cnt));
        } catch (IOException e) {
            throw new AbacusIOException(e);
        } finally {
            closeQuietly(is);
            closeQuietly(outputZipFile.value());

            ObjectFactory.recycle(br);
        }
    }

    public static long merge(final File[] sourceFiles, final File destFile) {
        return merge(N.asList(sourceFiles), destFile);
    }

    /**
     * Merge the specified source files into the destination file.
     *
     * @param sourceFiles
     * @param destFile
     * @return the total bytes have been merged into the destination file.
     */
    public static long merge(final Collection<File> sourceFiles, final File destFile) {
        final byte[] buf = ObjectFactory.createByteArrayBuffer();

        long totalCount = 0;
        OutputStream output = null;

        try {
            output = new FileOutputStream(destFile);

            InputStream input = null;
            for (File file : sourceFiles) {
                try {
                    input = new FileInputStream(file);

                    int count = 0;
                    while (EOF != (count = read(input, buf, 0, buf.length))) {
                        output.write(buf, 0, count);

                        totalCount += count;
                    }
                } finally {
                    close(input);
                }
            }

            output.flush();
        } catch (IOException e) {
            throw new AbacusIOException(e);
        } finally {
            ObjectFactory.recycle(buf);

            close(output);
        }

        return totalCount;
    }

    private static String getAbsolutePath(final File parentDir, String relativeFilePath) throws IOException {
        String newRelativePath = "";

        for (int i = 0; i < relativeFilePath.length(); i++) {
            char c = relativeFilePath.charAt(i);

            if ((c == '\\') || (c == '/')) {
                newRelativePath += File.separator;
            } else {
                newRelativePath += c;
            }
        }

        relativeFilePath = newRelativePath;

        String path = parentDir.getAbsolutePath() + File.separator + relativeFilePath;

        File dir = new File(path.substring(0, path.lastIndexOf(File.separator)));

        if (!dir.exists()) {
            dir.mkdirs();
        }

        return path;
    }

    private static String getRelativePath(final File parentDir, final File file) {
        if (file.equals(parentDir)) {
            return file.getName();
        } else {
            return file.getAbsolutePath().substring(parentDir.getAbsolutePath().length() + 1);
        }
    }

    public static List<String> list(final File path) {
        return list(path, false, false);
    }

    public static List<String> list(File path, final boolean recursively, final boolean excludeDirectory) {
        List<String> files = new ArrayList<>();

        if (!path.exists()) {
            return files;
        }

        path = new File(path.getAbsolutePath().replace(".\\", "\\").replace("./", "/"));

        File[] subFiles = path.listFiles();

        if (N.isNullOrEmpty(subFiles)) {
            return files;
        }

        for (File file : subFiles) {
            if (file.isFile()) {
                files.add(file.getAbsolutePath());
            } else {
                if (!excludeDirectory) {
                    files.add(file.getAbsolutePath());
                }

                if (recursively) {
                    files.addAll(list(file, recursively, excludeDirectory));
                }
            }
        }

        return files;
    }

    public static List<String> list(File path, final boolean recursively, final FileFilter filter) {
        List<String> files = new ArrayList<>();

        if (!path.exists()) {
            return files;
        }

        path = new File(path.getAbsolutePath().replace(".\\", "\\").replace("./", "/"));

        File[] subFiles = path.listFiles();

        if (N.isNullOrEmpty(subFiles)) {
            return files;
        }

        for (File file : subFiles) {
            if (filter.accept(path, file)) {
                files.add(file.getAbsolutePath());
            }

            if (recursively && file.isDirectory()) {
                files.addAll(list(file, recursively, filter));
            }
        }

        return files;
    }

    public static List<File> listFiles(final File path) {
        return listFiles(path, false, false);
    }

    public static List<File> listFiles(final File path, final boolean recursively, final boolean excludeDirectory) {
        List<File> files = new ArrayList<>();

        if (!path.exists()) {
            return files;
        }

        File[] subFiles = path.listFiles();

        if (N.isNullOrEmpty(subFiles)) {
            return files;
        }

        for (File file : subFiles) {
            if (file.isFile()) {
                files.add(file);
            } else {
                if (!excludeDirectory) {
                    files.add(file);
                }

                if (recursively) {
                    files.addAll(listFiles(file, recursively, excludeDirectory));
                }
            }
        }

        return files;
    }

    public static List<File> listFiles(final File path, final boolean recursively, final FileFilter filter) {
        List<File> files = new ArrayList<>();

        if (!path.exists()) {
            return files;
        }

        File[] subFiles = path.listFiles();

        if (N.isNullOrEmpty(subFiles)) {
            return files;
        }

        for (File file : subFiles) {
            if (filter.accept(path, file)) {
                files.add(file);
            }

            if (recursively && file.isDirectory()) {
                files.addAll(listFiles(file, recursively, filter));
            }
        }

        return files;
    }

    public static List<File> listDirectories(final File path) {
        return listDirectories(path, false);
    }

    public static List<File> listDirectories(final File path, final boolean recursively) {
        List<File> files = new ArrayList<>();

        if (!path.exists()) {
            return files;
        }

        File[] subFiles = path.listFiles();

        if (N.isNullOrEmpty(subFiles)) {
            return files;
        }

        for (File file : subFiles) {
            if (file.isDirectory()) {
                files.add(file);

                if (recursively) {
                    files.addAll(listDirectories(file, recursively));
                }
            }
        }

        return files;
    }

    public static List<File> listDirectories(final File path, final boolean recursively, final FileFilter filter) {
        List<File> files = new ArrayList<>();

        if (!path.exists()) {
            return files;
        }

        File[] subFiles = path.listFiles();

        if (N.isNullOrEmpty(subFiles)) {
            return files;
        }

        for (File file : subFiles) {
            if (file.isDirectory()) {
                if (filter.accept(path, file)) {
                    files.add(file);
                }

                if (recursively) {
                    files.addAll(listDirectories(file, recursively, filter));
                }
            }
        }

        return files;
    }

    //-----------------------------------------------------------------------
    /**
     * Convert from a <code>URL</code> to a <code>File</code>.
     * <p>
     * From version 1.1 this method will decode the URL.
     * Syntax such as <code>file:///my%20docs/file.txt</code> will be
     * correctly decoded to <code>/my docs/file.txt</code>. Starting with version
     * 1.5, this method uses UTF-8 to decode percent-encoded octets to characters.
     * Additionally, malformed percent-encoded octets are handled leniently by
     * passing them through literally.
     *
     * @param url  the file URL to convert, {@code null} returns {@code null}
     * @return the equivalent <code>File</code> object if the URL's protocol is not <code>file</code>
     * @throws NullPointerException if the parameter is null
     */
    public static File toFile(final URL url) {
        if (url.getProtocol().equals("file") == false) {
            throw new IllegalArgumentException("URL could not be converted to a File: " + url);
        }

        return new File(decodeUrl(url.getFile().replace('/', File.separatorChar)));
    }

    /**
     * Decodes the specified URL as per RFC 3986, i.e. transforms
     * percent-encoded octets to characters by decoding with the UTF-8 character
     * set. This function is primarily intended for usage with
     * {@link java.net.URL} which unfortunately does not enforce proper URLs. As
     * such, this method will leniently accept invalid characters or malformed
     * percent-encoded octets and simply pass them literally through to the
     * result string. Except for rare edge cases, this will make unencoded URLs
     * pass through unaltered.
     *
     * @param url  The URL to decode, may be {@code null}.
     * @return The decoded URL or {@code null} if the input was
     *         {@code null}.
     */
    // unavoidable until Java 7
    private static String decodeUrl(final String url) {
        String decoded = url;
        if (url != null && url.indexOf('%') >= 0) {
            final int n = url.length();
            final StringBuffer buffer = new StringBuffer();
            final ByteBuffer bytes = ByteBuffer.allocate(n);
            for (int i = 0; i < n;) {
                if (url.charAt(i) == '%') {
                    try {
                        do {
                            final byte octet = (byte) Integer.parseInt(url.substring(i + 1, i + 3), 16);
                            bytes.put(octet);
                            i += 3;
                        } while (i < n && url.charAt(i) == '%');
                        continue;
                    } catch (final RuntimeException e) {
                        // malformed percent-encoded octet, fall through and
                        // append characters literally
                    } finally {
                        if (bytes.position() > 0) {
                            bytes.flip();
                            buffer.append(Charsets.UTF_8.decode(bytes).toString());
                            bytes.clear();
                        }
                    }
                }
                buffer.append(url.charAt(i++));
            }
            decoded = buffer.toString();
        }
        return decoded;
    }

    /**
     * Converts each of an array of <code>URL</code> to a <code>File</code>.
     * <p>
     * Returns an array of the same size as the input.
     * If the input is {@code null}, an empty array is returned.
     * If the input contains {@code null}, the output array contains {@code null} at the same
     * index.
     * <p>
     * This method will decode the URL.
     * Syntax such as <code>file:///my%20docs/file.txt</code> will be
     * correctly decoded to <code>/my docs/file.txt</code>.
     *
     * @param urls  the file URLs to convert, {@code null} returns empty array
     * @return a non-{@code null} array of Files matching the input, with a {@code null} item
     *  if there was a {@code null} at that index in the input array
     * @throws IllegalArgumentException if any file is not a URL file
     * @throws IllegalArgumentException if any file is incorrectly encoded
     * @since 1.1
     */
    public static File[] toFiles(final URL[] urls) {
        if (N.isNullOrEmpty(urls)) {
            return new File[0];
        }

        final File[] files = new File[urls.length];

        for (int i = 0; i < urls.length; i++) {
            files[i] = toFile(urls[i]);
        }

        return files;
    }

    public static List<File> toFiles(final Collection<URL> urls) {
        if (N.isNullOrEmpty(urls)) {
            return new ArrayList<>();
        }

        final List<File> files = new ArrayList<>(urls.size());

        for (URL url : urls) {
            files.add(toFile(url));
        }

        return files;
    }

    public static URL toURL(final File file) {
        try {
            return file.toURI().toURL();
        } catch (IOException e) {
            throw new AbacusIOException(e);
        }
    }

    public static URL[] toURLs(final File[] files) {
        if (N.isNullOrEmpty(files)) {
            return new URL[0];
        }

        final URL[] urls = new URL[files.length];

        try {
            for (int i = 0; i < urls.length; i++) {
                urls[i] = files[i].toURI().toURL();
            }
        } catch (IOException e) {
            throw new AbacusIOException(e);
        }

        return urls;
    }

    public static List<URL> toURLs(final Collection<File> files) {
        if (N.isNullOrEmpty(files)) {
            return new ArrayList<>();
        }

        final List<URL> urls = new ArrayList<>(files.size());

        try {
            for (File file : files) {
                urls.add(file.toURI().toURL());
            }
        } catch (IOException e) {
            throw new AbacusIOException(e);
        }

        return urls;
    }

    /**
     * Update the last modified time of the file to system current time if the specified file exists.
     *
     * @param file the File to touch
     * @return true if the file exists and last modified time is updated successfully.
     */
    public static boolean touch(final File file) {
        return file.exists() && file.setLastModified(System.currentTimeMillis());
    }

    /**
     * Parse the specified file/directory line by line.
     * The last line will always be null to identity the ending of line reading even offset/count is specified.
     * 
     * @param file parse all the sub files recursively if it's a directory.
     * @param lineParser always remember to handle the ending element <code>null</code>
     */
    public static void parse(final File file, final Consumer<String> lineParser) {
        parse(file, 0, Long.MAX_VALUE, lineParser);
    }

    /**
     * Parse the specified file/directory line by line.
     * The last line will always be null to identity the ending of line reading even offset/count is specified.
     * 
     * @param file parse all the sub files recursively if it's a directory.
     * @param processThreadNumber thread number used to parse/process the lines/records
     * @param queueSize size of queue to save the processing records/lines loaded from source data. Default size is 1024.
     * @param lineParser always remember to handle the ending element <code>null</code>
     */
    @Deprecated
    static void parse(final File file, final int processThreadNumber, final int queueSize, final Consumer<String> lineParser) {
        parse(file, 0, Long.MAX_VALUE, processThreadNumber, queueSize, lineParser);
    }

    /**
     * Parse the specified file/directory line by line.
     * The last line will always be null to identity the ending of line reading even offset/count is specified.
     * 
     * @param file parse all the sub files recursively if it's a directory.
     * @param lineOffset
     * @param count
     * @param lineParser always remember to handle the ending element <code>null</code>
     */
    public static void parse(final File file, final long lineOffset, final long count, final Consumer<String> lineParser) {
        parse(file, lineOffset, count, 0, 0, lineParser);
    }

    /**
     * Parse the specified file/directory line by line.
     * The last line will always be null to identity the ending of line reading even offset/count is specified.
     * 
     * @param file parse all the sub files recursively if it's a directory.
     * @param lineOffset
     * @param count
     * @param processThreadNumber thread number used to parse/process the lines/records
     * @param queueSize size of queue to save the processing records/lines loaded from source data. Default size is 1024.
     * @param lineParser always remember to handle the ending element <code>null</code>
     */
    public static void parse(final File file, final long lineOffset, final long count, final int processThreadNumber, final int queueSize,
            final Consumer<String> lineParser) {
        parse(file.isDirectory() ? listFiles(file, true, true) : N.asList(file), lineOffset, count, processThreadNumber, queueSize, lineParser);
    }

    /**
     * Parse the specified files/directory line by line.
     * The last line will always be null to identity the ending of line reading even offset/count is specified.
     * 
     * @param files parse all the sub files recursively if the element is a directory.
     * @param lineParser always remember to handle the ending element <code>null</code>
     */
    public static void parse(final List<File> files, final Consumer<String> lineParser) {
        parse(files, 0, Long.MAX_VALUE, lineParser);
    }

    /**
     * Parse the specified files/directory line by line.
     * The last line will always be null to identity the ending of line reading even offset/count is specified.
     * 
     * @param files parse all the sub files recursively if the element is a directory.
     * @param processThreadNumber thread number used to parse/process the lines/records
     * @param queueSize size of queue to save the processing records/lines loaded from source data. Default size is 1024.
     * @param lineParser always remember to handle the ending element <code>null</code>
     */
    @Deprecated
    static void parse(final List<File> files, final int processThreadNumber, final int queueSize, final Consumer<String> lineParser) {
        parse(files, 0, Long.MAX_VALUE, processThreadNumber, queueSize, lineParser);
    }

    /**
     * Parse the specified files/directory line by line.
     * The last line will always be null to identity the ending of line reading even offset/count is specified.
     * 
     * @param files parse all the sub files recursively if the element is a directory.
     * @param lineOffset
     * @param count
     * @param lineParser always remember to handle the ending element <code>null</code>
     */
    public static void parse(final List<File> files, final long lineOffset, final long count, final Consumer<String> lineParser) {
        parse(files, lineOffset, count, 0, 0, lineParser);
    }

    /**
     * Parse the specified files/directory line by line.
     * The last line will always be null to identity the ending of line reading even offset/count is specified.
     * 
     * @param files parse all the sub files recursively if the element is a directory.
     * @param lineOffset
     * @param count
     * @param processThreadNumber thread number used to parse/process the lines/records
     * @param queueSize size of queue to save the processing records/lines loaded from source data. Default size is 1024.
     * @param lineParser always remember to handle the ending element <code>null</code>
     */
    public static void parse(final List<File> files, final long lineOffset, final long count, final int processThreadNumber, final int queueSize,
            final Consumer<String> lineParser) {
        if (N.isNullOrEmpty(files)) {
            return;
        }

        final List<Reader> readers = new ArrayList<>(files.size());

        try {
            for (final File subFile : files) {
                if (subFile.isFile()) {
                    readers.add(newBufferedReader(subFile));
                } else {
                    for (final File subSubFile : listFiles(subFile, true, true)) {
                        readers.add(newBufferedReader(subSubFile));
                    }
                }
            }

            final List<Iterator<String>> iterators = new ArrayList<>(readers.size());

            for (Reader reader : readers) {
                iterators.add(new LineIterator(reader));
            }

            N.parse(iterators, lineOffset, count, 1, processThreadNumber, queueSize, lineParser);
        } finally {
            for (Reader reader : readers) {
                closeQuietly(reader);
            }
        }
    }

    /**
     * Parse the specified file/directory line by line.
     * The last line will always be null to identity the ending of line reading even offset/count is specified.
     * 
     * @param file parse all the sub files recursively if it's a directory.
     * @param readThreadNumber
     * @param lineParser always remember to handle the ending element <code>null</code>
     */
    @Deprecated
    static void parse(final File file, final int readThreadNumber, final Consumer<String> lineParser) {
        parse(file, readThreadNumber, 0, 0, lineParser);
    }

    /**
     * Parse the specified file/directory line by line.
     * The last line will always be null to identity the ending of line reading even offset/count is specified.
     * 
     * @param file parse all the sub files recursively if it's a directory.
     * @param readThreadNumber
     * @param processThreadNumber thread number used to parse/process the lines/records
     * @param queueSize size of queue to save the processing records/lines loaded from source data. Default size is 1024.
     * @param lineParser always remember to handle the ending element <code>null</code>
     */
    public static void parse(final File file, final int readThreadNumber, final int processThreadNumber, final int queueSize,
            final Consumer<String> lineParser) {
        parse(file, 0, Long.MAX_VALUE, readThreadNumber, processThreadNumber, queueSize, lineParser);
    }

    public static void parse(final File file, final long lineOffset, final long count, final int readThreadNumber, final int processThreadNumber,
            final int queueSize, final Consumer<String> lineParser) {
        parse(file.isDirectory() ? listFiles(file, true, true) : N.asList(file), lineOffset, count, readThreadNumber, processThreadNumber, queueSize,
                lineParser);
    }

    /**
     * Parse the specified files/directory line by line.
     * The last line will always be null to identity the ending of line reading even offset/count is specified.
     * 
     * @param files parse all the sub files recursively if the element is a directory.
     * @param readThreadNumber
     * @param lineParser always remember to handle the ending element <code>null</code>
     */
    @Deprecated
    static void parse(final List<File> files, final int readThreadNumber, final Consumer<String> lineParser) {
        parse(files, readThreadNumber, 0, 0, lineParser);
    }

    /**
     * Parse the specified files/directory line by line.
     * The last line will always be null to identity the ending of line reading even offset/count is specified.
     * 
     * @param files parse all the sub files recursively if the element is a directory.
     * @param readThreadNumber
     * @param processThreadNumber thread number used to parse/process the lines/records
     * @param queueSize size of queue to save the processing records/lines loaded from source data. Default size is 1024.
     * @param lineParser always remember to handle the ending element <code>null</code>
     */

    public static void parse(final List<File> files, final int readThreadNumber, final int processThreadNumber, final int queueSize,
            final Consumer<String> lineParser) {
        parse(files, 0, Long.MAX_VALUE, readThreadNumber, processThreadNumber, queueSize, lineParser);
    }

    public static void parse(final List<File> files, final long lineOffset, final long count, final int readThreadNumber, final int processThreadNumber,
            final int queueSize, final Consumer<String> lineParser) {
        if (N.isNullOrEmpty(files)) {
            return;
        }

        final List<Reader> readers = new ArrayList<>(files.size());

        try {
            for (final File subFile : files) {
                if (subFile.isFile()) {
                    readers.add(newBufferedReader(subFile));
                } else {
                    for (final File subSubFile : listFiles(subFile, true, true)) {
                        readers.add(newBufferedReader(subSubFile));
                    }
                }
            }

            final List<Iterator<String>> iterators = new ArrayList<>(readers.size());

            for (Reader reader : readers) {
                iterators.add(new LineIterator(reader));
            }

            N.parse(iterators, lineOffset, count, readThreadNumber, processThreadNumber, queueSize, lineParser);
        } finally {
            for (Reader reader : readers) {
                closeQuietly(reader);
            }
        }
    }

    /**
     * Parse the specified InputStream line by line.
     * The last line will always be null to identity the ending of line reading even offset/count is specified.
     * 
     * @param is
     * @param lineParser always remember to handle the ending element <code>null</code>
     */
    public static void parse(final InputStream is, final Consumer<String> lineParser) {
        parse(is, 0, Long.MAX_VALUE, lineParser);
    }

    /**
     * Parse the specified InputStream line by line.
     * The last line will always be null to identity the ending of line reading even offset/count is specified.
     * 
     * @param is
     * @param processThreadNumber thread number used to parse/process the lines/records
     * @param queueSize size of queue to save the processing records/lines loaded from source data. Default size is 1024.
     * @param lineParser always remember to handle the ending element <code>null</code>
     */
    @Deprecated
    static void parse(final InputStream is, final int processThreadNumber, final int queueSize, final Consumer<String> lineParser) {
        parse(is, 0, Long.MAX_VALUE, processThreadNumber, queueSize, lineParser);
    }

    /**
     * Parse the specified InputStream line by line.
     * The last line will always be null to identity the ending of line reading even offset/count is specified.
     * 
     * @param is
     * @param lineOffset
     * @param count
     * @param lineParser always remember to handle the ending element <code>null</code>
     */
    public static void parse(final InputStream is, final long lineOffset, final long count, final Consumer<String> lineParser) {
        parse(is, lineOffset, count, 0, 0, lineParser);
    }

    /**
     * Parse the specified InputStream line by line.
     * The last line will always be null to identity the ending of line reading even offset/count is specified.
     * 
     * @param is
     * @param lineOffset
     * @param count
     * @param processThreadNumber thread number used to parse/process the lines/records
     * @param queueSize size of queue to save the processing records/lines loaded from source data. Default size is 1024.
     * @param lineParser always remember to handle the ending element <code>null</code>
     */
    public static void parse(final InputStream is, final long lineOffset, final long count, final int processThreadNumber, final int queueSize,
            final Consumer<String> lineParser) {
        final BufferedReader br = ObjectFactory.createBufferedReader(is);

        try {
            parse(br, lineOffset, count, processThreadNumber, queueSize, lineParser);
        } finally {
            ObjectFactory.recycle(br);
        }
    }

    /**
     * Parse the specified Reader line by line.
     * The last line will always be null to identity the ending of line reading even offset/count is specified.
     * 
     * @param reader
     * @param lineParser always remember to handle the ending element <code>null</code>
     */
    public static void parse(final Reader reader, final Consumer<String> lineParser) {
        parse(reader, 0, Long.MAX_VALUE, lineParser);
    }

    /**
     * Parse the specified Reader line by line.
     * The last line will always be null to identity the ending of line reading even offset/count is specified.
     * 
     * @param reader
     * @param processThreadNumber thread number used to parse/process the lines/records
     * @param queueSize size of queue to save the processing records/lines loaded from source data. Default size is 1024.
     * @param lineParser always remember to handle the ending element <code>null</code>
     */
    @Deprecated
    static void parse(final Reader reader, final int processThreadNumber, final int queueSize, final Consumer<String> lineParser) {
        parse(reader, 0, Long.MAX_VALUE, processThreadNumber, queueSize, lineParser);
    }

    /**
     * Parse the specified Reader line by line.
     * The last line will always be null to identity the ending of line reading even offset/count is specified.
     * 
     * @param reader
     * @param lineOffset
     * @param count
     * @param lineParser always remember to handle the ending element <code>null</code>
     */
    public static void parse(final Reader reader, final long lineOffset, final long count, final Consumer<String> lineParser) {
        parse(reader, lineOffset, count, 0, 0, lineParser);
    }

    /**
     * Parse the specified Reader line by line.
     * The last line will always be null to identity the ending of line reading even offset/count is specified.
     * 
     * @param reader
     * @param lineOffset
     * @param count
     * @param processThreadNumber thread number used to parse/process the lines/records
     * @param queueSize size of queue to save the processing records/lines loaded from source data. Default size is 1024.
     * @param lineParser always remember to handle the ending element <code>null</code>
     */
    public static void parse(final Reader reader, final long lineOffset, final long count, final int processThreadNumber, final int queueSize,
            final Consumer<String> lineParser) {
        N.parse(new LineIterator(reader), lineOffset, count, processThreadNumber, queueSize, lineParser);
    }

    //    private static void parse(final Reader reader, final File file, final AtomicLong offset, final AtomicLong count, final int processThreadNumber,
    //            final int queueSize, final Consumer<String> lineParser) {
    //        logger.info(file == null ? "### Start to parse" : "### Start to parse file: " + file);
    //
    //        final BufferedReader br = reader instanceof BufferedReader ? ((BufferedReader) reader) : ObjectFactory.createBufferedReader(reader);
    //        String line = null;
    //
    //        try {
    //            while (offset.longValue() > 0 && br.readLine() != null) {
    //                offset.decrementAndGet();
    //            }
    //
    //            if (processThreadNumber == 0) {
    //                while (count.longValue() > 0) {
    //                    line = br.readLine();
    //
    //                    if (line == null) {
    //                        break;
    //                    }
    //
    //                    lineParser.accept(line);
    //                    count.decrementAndGet();
    //                }
    //
    //                lineParser.accept(null);
    //            } else {
    //                final AtomicInteger activeThreadNum = new AtomicInteger();
    //                final ExecutorService executorService = Executors.newFixedThreadPool(processThreadNumber);
    //                final Queue<String> lineQueue = new ConcurrentLinkedQueue<String>();
    //                final MutableBoolean isReadDone = new MutableBoolean(false);
    //                final Holder<Throwable> exceptionHandle = new Holder<Throwable>();
    //                final Holder<String> errorMessageHandle = new Holder<String>();
    //
    //                for (int i = 0; i < processThreadNumber; i++) {
    //                    activeThreadNum.incrementAndGet();
    //
    //                    executorService.execute(new Runnable() {
    //                        @Override
    //                        public void run() {
    //                            String line = null;
    //                            try {
    //                                while (exceptionHandle.getValue() == null) {
    //                                    line = lineQueue.poll();
    //
    //                                    if (line == null) {
    //                                        if (isReadDone.booleanValue()) {
    //                                            line = lineQueue.poll();
    //                                            if (line == null) {
    //                                                break;
    //                                            } else {
    //                                                lineParser.accept(line);
    //                                            }
    //                                        } else {
    //                                            N.sleep(1);
    //                                        }
    //                                    } else {
    //                                        lineParser.accept(line);
    //                                    }
    //                                }
    //                            } catch (Throwable e) {
    //                                if (file == null) {
    //                                    errorMessageHandle.setValue("### Failed to parse at line: " + line + ". " + AbacusException.getErrorMsg(e));
    //                                } else {
    //                                    errorMessageHandle
    //                                            .setValue("### Failed to parse file: " + file + " at line: " + line + ". " + AbacusException.getErrorMsg(e));
    //                                }
    //
    //                                exceptionHandle.setValue(e);
    //                            } finally {
    //                                activeThreadNum.decrementAndGet();
    //                            }
    //                        }
    //                    });
    //                }
    //
    //                try {
    //                    while (exceptionHandle.getValue() == null && count.getAndDecrement() > 0) {
    //                        while (lineQueue.size() > queueSize) {
    //                            N.sleep(1);
    //                        }
    //
    //                        line = br.readLine();
    //
    //                        if (line == null) {
    //                            break;
    //                        }
    //
    //                        lineQueue.add(line);
    //                    }
    //                } finally {
    //                    isReadDone.setTrue();
    //
    //                    while (activeThreadNum.get() > 0) {
    //                        N.sleep(10);
    //                    }
    //                }
    //
    //                if (exceptionHandle.getValue() == null) {
    //                    try {
    //                        lineParser.accept(null);
    //                    } catch (Throwable e) {
    //                        errorMessageHandle.setValue("### Failed to parse null, the end of line. " + AbacusException.getErrorMsg(e));
    //                        exceptionHandle.setValue(e);
    //                    }
    //                }
    //
    //                if (exceptionHandle.getValue() != null) {
    //                    logger.error(errorMessageHandle.getValue());
    //                    throw new AbacusException(errorMessageHandle.getValue(), exceptionHandle.getValue());
    //                }
    //            }
    //        } catch (IOException e) {
    //            String msg = null;
    //            if (file == null) {
    //                msg = "### Failed to parse at line: " + line + ". " + AbacusException.getErrorMsg(e);
    //            } else {
    //                msg = "### Failed to parse file: " + file + " at line: " + line + ". " + AbacusException.getErrorMsg(e);
    //            }
    //            logger.error(msg);
    //            throw new AbacusIOException(msg, e);
    //        } finally {
    //            if (br != reader) {
    //                ObjectFactory.recycle(br);
    //            }
    //
    //            logger.info(file == null ? "### ### End to parse" : "### End to parse file: " + file);
    //        }
    //    }

    private static InputStream openFile(final Output<ZipFile> outputZipFile, final File file) throws IOException {
        InputStream is = null;

        if (file.getName().endsWith(GZ)) {
            is = new GZIPInputStream(new FileInputStream(file));
        } else if (file.getName().endsWith(ZIP)) {
            ZipFile zf = new ZipFile(file);

            ZipEntry ze = zf.entries().nextElement();
            is = zf.getInputStream(ze);
            outputZipFile.setValue(zf);
        } else {
            is = new FileInputStream(file);
        }

        return is;
    }

    @SuppressWarnings("deprecation")
    private static char[] toCharArray(CharSequence str) {
        return str == null ? N.NULL_CHAR_ARRAY : N.getCharsForReadOnly(str instanceof String ? (String) str : str.toString());
    }
}
