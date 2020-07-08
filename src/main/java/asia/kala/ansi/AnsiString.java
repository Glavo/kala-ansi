package asia.kala.ansi;

import java.io.IOException;
import java.io.Serializable;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The {@code AnsiString} class represents a string decorated with ANSI colors.
 *
 * <p>AnsiString provides some basic string methods that work based on plain text,
 * you can invoke the {@link #getPlain()}  method to get the plain text and do more on it.
 * If you need the string containing ANSI escape characters, you can invoke the {@link #toString()} method to get it.
 */
public final class AnsiString implements Serializable, Comparable<AnsiString> {
    private static final long serialVersionUID = -2640452895881997219L;
    private static final int hashMagic = -1064710924;

    static final Pattern ANSI_PATTERN = Pattern.compile("(\u009b|\u001b\\[)[0-?]*[ -/]*[@-~]");
    static final String RESET = "\u001b[0m";
    static final AnsiString EMPTY = new AnsiString("", null, 0);
    static final AnsiString NULL = new AnsiString("null", null, 0);

    private final String plain;
    private final long[] states;
    private final int statesFrom;

    AnsiString(String plain, long[] states, int statesFrom) {
        this.plain = plain;
        this.states = states;
        this.statesFrom = statesFrom;

        if (states == null) {
            encodedCache = plain;
        }
    }

    static int trimStatesInit(long[] states) {
        if (states == null) {
            return 0;
        }

        final int statesLength = states.length;

        for (int i = 0; i < statesLength; i++) {
            if (states[i] != 0L) {
                return i;
            }
        }
        return statesLength;
    }

    static int trimStatesTail(long[] states, int limit) {
        if (states == null) {
            return 0;
        }
        return trimStatesTail(states, limit, states.length);
    }

    static int trimStatesTail(long[] states, int limit, int arrayLength) {
        if (states == null) {
            return 0;
        }
        for (int i = arrayLength - 1; i >= limit; i--) {
            if (states[i] != 0L) {
                return i + 1 - limit;
            }
        }
        return 0;
    }

    /**
     * Returns the ansi string representation of the {@code raw} argument.
     * <p>
     * This method is equivalent to {@code AnsiString.parse(raw)}.
     *
     * @param raw an {@code CharSequence}
     * @return if the argument is {@code null}, then a stateless ansi string equal to
     * {@code AnsiString.valueOf("null")}; otherwise, the value of
     * {@code AnsiString.parse(object.toString())} is returned
     */
    public static AnsiString valueOf(CharSequence raw) {
        if (raw == null) {
            return NULL;
        }
        return parse(raw);
    }

    /**
     * Returns the ansi string representation of the {@code Object} argument.
     * <p>
     * This method is equivalent to {@code AnsiString.parse(Objects.toString(object))}.
     *
     * @param object an {@code Object}
     * @return if the argument is {@code null}, then a stateless ansi string equal to
     * {@code AnsiString.valueOf("null")}; otherwise, the value of
     * {@code AnsiString.parse(object.toString())} is returned
     */
    public static AnsiString valueOf(Object object) {
        if (object == null) {
            return NULL;
        }
        if (object instanceof AnsiString) {
            return ((AnsiString) object);
        }
        if (object instanceof CharSequence) {
            return parse(((CharSequence) object));
        }
        return parse(object.toString());
    }

    /**
     * Parse a {@code CharSequence} containing ANSI escape sequence to {@code AnsiString}.
     *
     * <p>If you ensure that the {@code CharSequence} does not contain ANSI escape sequences,
     * use the {@link #ofPlain(CharSequence)} method to avoid the extra overhead of parsing the string.
     *
     * @param raw an not {@code null} {@code CharSequence}.
     * @return the parsed {@code AnsiString}
     * @see #parse(CharSequence, ErrorMode, boolean)
     */
    public static AnsiString parse(CharSequence raw) {
        return parse(raw, ErrorMode.DEFAULT, true);
    }

    /**
     * Parse a {@code CharSequence} containing ANSI escape sequence to {@code AnsiString}.
     *
     * <p>If you ensure that the {@code CharSequence} does not contain ANSI escape sequences,
     * use the {@link #ofPlain(CharSequence)} method to avoid the extra overhead of parsing the string.
     *
     * @param raw       an not {@code null} {@code CharSequence}.
     * @param errorMode handler of unrecognized ANSI escape sequences
     * @return the parsed {@code AnsiString}
     * @see #parse(CharSequence, ErrorMode, boolean)
     */
    public static AnsiString parse(CharSequence raw, ErrorMode errorMode) {
        return parse(raw, errorMode, true);
    }

    /**
     * Parse a {@code CharSequence} containing ANSI escape sequence to {@code AnsiString}.
     *
     * <p>If you ensure that the {@code CharSequence} does not contain ANSI escape sequences,
     * use the {@link #ofPlain(CharSequence)} method to avoid the extra overhead of parsing the string.
     *
     * @param raw        an not {@code null} {@code CharSequence}.
     * @param trimStates if {@code true}, compacting the states array
     * @return the parsed {@code AnsiString}
     * @see #parse(CharSequence, ErrorMode, boolean)
     */
    public static AnsiString parse(CharSequence raw, boolean trimStates) {
        return parse(raw, ErrorMode.DEFAULT, trimStates);
    }

    /**
     * Parse a {@code CharSequence} containing ANSI escape sequence to {@code AnsiString}.
     *
     * <p>If you ensure that the {@code CharSequence} does not contain ANSI escape sequences,
     * use the {@link #ofPlain(CharSequence)} method to avoid the extra overhead of parsing the string.
     *
     * @param raw        an not {@code null} {@code CharSequence}.
     * @param errorMode  handler of unrecognized ANSI escape sequences
     * @param trimStates if {@code true}, compacting the states array
     * @return the parsed {@code AnsiString}
     */
    public static AnsiString parse(CharSequence raw, ErrorMode errorMode, boolean trimStates) {
        if (raw == null) {
            throw new NullPointerException();
        }

        final int rawLength = raw.length();
        if (rawLength == 0) {
            return EMPTY;
        }

        StringBuilder builder = new StringBuilder(rawLength);
        long[] states = new long[rawLength];

        long currentColor = 0L;
        int sourceIndex = 0;
        int destIndex = 0;
        boolean hasStates = false;

        while (sourceIndex < rawLength) {
            final char ch = raw.charAt(sourceIndex);
            if (ch == '\u001b' || ch == '\u009b') {
                hasStates = true;
                final int escapeStartSourceIndex = sourceIndex;
                Trie.ValueWithLength tuple = Trie.parseMap().query(raw, escapeStartSourceIndex);
                if (tuple == null) {
                    sourceIndex = errorMode.handle(sourceIndex, raw);
                } else {
                    final int newIndex = tuple.length;
                    final Object v = tuple.value;
                    if (v instanceof Attribute) {
                        currentColor = ((Attribute) v).transform(currentColor);
                        sourceIndex += newIndex;
                    } else {
                        sourceIndex += newIndex;
                        ColorCategory category = ((ColorCategory) v);
                        if (sourceIndex >= raw.length() || raw.charAt(sourceIndex) < '0' || raw.charAt(sourceIndex) > '9') {
                            sourceIndex = errorMode.handle(escapeStartSourceIndex, raw);
                        } else {
                            int r = 0;
                            int count = 0;
                            while (sourceIndex < raw.length()
                                    && raw.charAt(sourceIndex) >= '0'
                                    && raw.charAt(sourceIndex) <= '9'
                                    && count < 3) {
                                r = r * 10 + (raw.charAt(sourceIndex) - '0');
                                sourceIndex += 1;
                                count += 1;
                            }

                            if (!(sourceIndex < raw.length() && raw.charAt(sourceIndex) == ';')
                                    || !(sourceIndex + 1 < raw.length()
                                    && raw.charAt(sourceIndex + 1) >= '0'
                                    && raw.charAt(sourceIndex + 1) <= '9')) {
                                sourceIndex = errorMode.handle(escapeStartSourceIndex, raw);
                            } else {
                                ++sourceIndex;
                                int g = 0;
                                count = 0;
                                while (sourceIndex < raw.length()
                                        && raw.charAt(sourceIndex) >= '0'
                                        && raw.charAt(sourceIndex) <= '9'
                                        && count < 3) {
                                    g = g * 10 + (raw.charAt(sourceIndex) - '0');
                                    ++sourceIndex;
                                    ++count;
                                }

                                if (!(sourceIndex < raw.length() && raw.charAt(sourceIndex) == ';')
                                        || !(sourceIndex + 1 < raw.length()
                                        && raw.charAt(sourceIndex + 1) >= '0'
                                        && raw.charAt(sourceIndex + 1) <= '9')) {
                                    sourceIndex = errorMode.handle(escapeStartSourceIndex, raw);
                                } else {
                                    ++sourceIndex;
                                    int b = 0;
                                    count = 0;
                                    while (sourceIndex < raw.length()
                                            && raw.charAt(sourceIndex) >= '0'
                                            && raw.charAt(sourceIndex) <= '9'
                                            && count < 3) {
                                        b = b * 10 + (raw.charAt(sourceIndex) - '0');
                                        ++sourceIndex;
                                        ++count;
                                    }
                                    if (!(sourceIndex < raw.length() && raw.charAt(sourceIndex) == 'm')) {
                                        sourceIndex = errorMode.handle(escapeStartSourceIndex, raw);
                                    } else {
                                        ++sourceIndex;
                                        if (!(0 <= r && r < 256 && 0 <= g && g < 256 && 0 <= b && b < 256)) {
                                            sourceIndex = errorMode.handle(escapeStartSourceIndex, raw);
                                        } else {
                                            currentColor =
                                                    (currentColor & ~category.mask()) |
                                                            ((273 + category.trueIndex(r, g, b)) << category.offset);

                                        }
                                    }
                                }
                            }
                        }

                    }
                }
            } else {
                states[destIndex] = currentColor;
                builder.append(ch);
                ++sourceIndex;
                ++destIndex;
            }
        }

        if (builder.length() == 0) {
            return EMPTY;
        }

        if (!hasStates) {
            return new AnsiString(raw instanceof String ? ((String) raw) : builder.toString(), null, 0);
        }

        String plain = builder.toString();
        if (plain.isEmpty()) {
            return EMPTY;
        }

        if (trimStates) {
            final int statesFrom = trimStatesInit(states);
            final int statesLength = trimStatesTail(states, statesFrom);


            if (statesLength == 0) {
                return new AnsiString(plain, null, 0);
            } else {
                long[] ss = Arrays.copyOfRange(states, statesFrom, statesFrom + statesLength);
                return new AnsiString(plain, ss, statesFrom);
            }
        } else {
            return new AnsiString(plain, Arrays.copyOf(states, plain.length()), plain.length());
        }
    }

    /**
     * Construct an {@code AnsiString} from plain text.
     */
    public static AnsiString ofPlain(CharSequence plain) {
        if (plain == null) {
            throw new NullPointerException();
        }

        if (plain.length() == 0) {
            return EMPTY;
        }
        return new AnsiString(plain.toString(), null, 0);
    }

    /**
     * Concatenates two {@code AnsiString}s.
     *
     * @param string1 the first {@code AnsiString}
     * @param string2 the second {@code AnsiString}
     * @return the concatenation of the two input strings
     */
    public static AnsiString concat(AnsiString string1, AnsiString string2) {
        return (string1 == null ? NULL : string1).concat(string2);
    }

    /**
     * Concatenates {@code AnsiString}s.
     *
     * @param strings input strings
     * @return the concatenation of all input strings
     */
    public static AnsiString concat(AnsiString... strings) {
        if (strings.length == 0) {
            return EMPTY;
        }
        if (strings.length == 1) {
            AnsiString res = strings[0];
            return res == null ? NULL : res;
        }

        return concat(Arrays.asList(strings));
    }

    /**
     * Concatenates {@code AnsiString}s.
     *
     * @param strings input strings
     * @return the concatenation of all input strings
     */
    public static AnsiString concat(Iterable<? extends AnsiString> strings) {
        int length = 0;
        int stateFrom = -1;
        int stateLimit = -1;

        for (AnsiString string : strings) {
            final long[] states = string.states;
            if (states != null && states.length > 0) {
                final int from = length + string.statesFrom;
                if (stateFrom < 0) {
                    stateFrom = from;
                }
                stateLimit = from + states.length;
            }
            length += string.length();
        }
        if (length == 0) {
            return EMPTY;
        }
        StringBuilder builder = new StringBuilder(length);

        if (stateFrom < 0 || stateFrom >= length) {
            for (AnsiString string : strings) {
                builder.append(string.plain);
            }
            return new AnsiString(builder.toString(), null, 0);
        }

        int offset = 0;
        final long[] states = new long[stateLimit - stateFrom];
        for (AnsiString string : strings) {
            builder.append(string.plain);
            if (offset + string.statesFrom >= stateFrom) {
                final long[] ss = string.states;
                if (ss != null) {
                    System.arraycopy(
                            ss, 0,
                            states, offset - stateFrom + string.statesFrom,
                            ss.length
                    );
                }
            }
            offset += string.length();
        }
        return new AnsiString(builder.toString(), states, stateFrom);
    }

    /**
     * Get the plain text of the {@code AnsiString}.
     *
     * @return the plain text of the {@code AnsiString}
     */
    public final String getPlain() {
        return plain;
    }

    public final long[] getStates() {
        final int length = this.length();
        long[] newStates = new long[length()];
        if (states == null) {
            return newStates;
        }
        System.arraycopy(states, 0, newStates, statesFrom, states.length);
        return newStates;
    }

    public final long stateAt(int index) {
        if (index < 0 || index >= length()) {
            throw new IndexOutOfBoundsException("Index out of range: " + index);
        }
        final long[] states = this.states;
        if (states == null) {
            return 0L;
        }
        int idx = index - statesFrom;
        return (idx < 0 || idx >= states.length) ? 0L : states[idx];
    }

    /**
     * Returns the length of the plain text.
     *
     * @return the length of the plain text.
     * @see String#length()
     */
    public final int length() {
        return plain.length();
    }

    /**
     * Returns {@code true} if, and only if, {@link #length()} is {@code 0}.
     *
     * @return {@code true} if {@link #length()} is {@code 0}, otherwise
     * {@code false}
     */
    public final boolean isEmpty() {
        return plain.isEmpty();
    }

    /**
     * Returns the {@code char} value at the specified index of the plain text
     *
     * @param index the index of the {@code char} value.
     * @return the {@code char} value at the specified index of the plain text.
     * The first {@code char} value is at index {@code 0}.
     * @throws IndexOutOfBoundsException if the {@code index} argument is negative or not less
     *                                   than the length of this string.
     * @see #getPlain()
     * @see String#charAt(int)
     */
    public final char charAt(int index) {
        return plain.charAt(index);
    }

    /**
     * Returns the character (Unicode code point) at the specified index of the plain text.
     * The index refers to {@code char} values (Unicode code units) and ranges from {@code 0} to
     * {@link #length()}{@code  - 1}.
     *
     * @param index the index to the {@code char} values
     * @return the code point value of the character at the {@code index} of the plain text
     * @throws IndexOutOfBoundsException if the {@code index} argument is negative or not less than
     *                                   the length of the plain text.
     * @see #getPlain()
     * @see String#codePointAt(int)
     */
    public final int codePointAt(int index) {
        return plain.codePointAt(index);
    }

    /**
     * Returns the character (Unicode code point) before the specified index of the plain text.
     * The index refers to {@code char} values (Unicode code units) and ranges
     * from {@code 1} to {@link AnsiString#length() length}.
     *
     * @param index the index following the code point of the plain text that should be returned
     * @return the Unicode code point value before the given index of the plain text.
     * @throws IndexOutOfBoundsException if the {@code index} argument is less than 1
     *                                   or greater than the length of the plain text.
     * @see #getPlain()
     * @see String#codePointBefore(int)
     */
    public final int codePointBefore(int index) {
        return plain.codePointBefore(index);
    }

    /**
     * Returns the number of Unicode code points in the specified text range of the plain text.
     * The text range begins at the specified {@code beginIndex} and extends to the {@code char}
     * at index {@code endIndex - 1}. Thus the length (in {@code char}s) of the text range is
     * {@code endIndex-beginIndex}. Unpaired surrogates within the text range count as one code point each.
     *
     * @param beginIndex the index to the first {@code char} of the text range.
     * @param endIndex   the index after the last {@code char} of the text range.
     * @return the number of Unicode code points in the specified text range of the plain text
     * @throws IndexOutOfBoundsException if the {@code beginIndex} is negative, or {@code endIndex}
     *                                   is larger than the length of this {@code AnsiString}, or
     *                                   {@code beginIndex} is larger than {@code endIndex}.
     * @see #getPlain()
     * @see String#codePointCount(int, int)
     */
    public final int codePointCount(int beginIndex, int endIndex) {
        return plain.codePointCount(beginIndex, endIndex);
    }

    /**
     * Returns the index within the plain text that is offset from the given {@code index} by
     * {@code codePointOffset} code points. Unpaired surrogates within the text range given
     * by {@code index} and {@code codePointOffset} count as one code point each.
     *
     * @param index           the index to be offset
     * @param codePointOffset the offset in code points
     * @return the index within this {@code String}
     * @throws IndexOutOfBoundsException if {@code index} is negative or larger then the length of this
     *                                   {@code AnsiString}, or if {@code codePointOffset} is positive
     *                                   and the substring starting with {@code index} has fewer
     *                                   than {@code codePointOffset} code points,
     *                                   or if {@code codePointOffset} is negative and the substring
     *                                   before {@code index} has fewer than the absolute value
     *                                   of {@code codePointOffset} code points.
     * @see #getPlain()
     * @see String#offsetByCodePoints(int, int)
     */
    public final int offsetByCodePoints(int index, int codePointOffset) {
        return plain.offsetByCodePoints(index, codePointOffset);
    }

    /**
     * Returns a string that is a substring of this ansi string. The substring
     * begins at the specified {@code beginIndex} and extends to the character
     * at index {@code endIndex - 1}. Thus the length of the substring is {@code endIndex-beginIndex}.
     *
     * @param beginIndex the beginning index, inclusive.
     * @param endIndex   the ending index, exclusive.
     * @return the specified substring.
     * @throws IndexOutOfBoundsException if the {@code beginIndex} is negative, or
     *                                   {@code endIndex} is larger than the length of
     *                                   this {@code AnsiString} object, or
     *                                   {@code beginIndex} is larger than
     *                                   {@code endIndex}.
     */
    public final AnsiString substring(final int beginIndex, final int endIndex) {
        final String plain = this.plain;
        final int size = plain.length();

        if (beginIndex < 0 || beginIndex >= size) {
            throw new IndexOutOfBoundsException("Index out of range: " + beginIndex);
        }
        if (endIndex < 0 || endIndex > size) {
            throw new IndexOutOfBoundsException("Index out of range: " + endIndex);
        }
        if (beginIndex > endIndex) {
            throw new IndexOutOfBoundsException();
        }

        final long[] states = this.states;

        if (states == null) {
            return new AnsiString(plain.substring(beginIndex, endIndex), null, 0);
        }

        final int newLen = endIndex - beginIndex;
        final int statesFrom = this.statesFrom;
        final int statesLen = states.length;

        if (newLen == 0 || statesFrom >= endIndex || statesFrom + statesLen <= beginIndex) {
            return new AnsiString(plain.substring(beginIndex, endIndex), null, 0);
        }

        final int newStateFrom = Math.max(statesFrom - beginIndex, 0);

        final int rf = Math.max(0, beginIndex - statesFrom);
        final int rt = Math.min(endIndex - statesFrom, statesLen);

        final long[] newStates = rf == rt ? null : Arrays.copyOfRange(states, rf, rt);

        return new AnsiString(plain.substring(beginIndex, endIndex), newStates, newStateFrom);
    }

    /**
     * Concatenates the specified {@code CharSequence} to the end of this ansi string.
     *
     * @param string the {@code CharSequence} that is concatenated to the end
     *               of this {@code AnsiString}.
     * @return an {@code AnsiString} that represents the concatenation of this object's
     * characters and states followed by the string argument's characters .
     */
    public final AnsiString concat(CharSequence string) {
        return concat(AnsiString.valueOf(string));
    }

    /**
     * Concatenates the specified {@code AnsiString} to the end of this ansi string.
     *
     * @param other the {@code AnsiString} that is concatenated to the end
     *              of this {@code AnsiString}
     * @return an {@code AnsiString} that represents the concatenation of this object's
     * characters and states followed by the string argument's characters and states
     */
    public final AnsiString concat(AnsiString other) {
        if (other == null) {
            return concat(NULL);
        }
        final int otherLength = other.length();
        if (otherLength == 0) {
            return this;
        }

        final int thisLength = this.length();
        if (thisLength == 0) {
            return other;
        }

        String newPlain = this.plain.concat(other.plain);

        final long[] states = this.states;
        final long[] otherStates = other.states;

        if (states == null) {
            if (otherStates == null) {
                return new AnsiString(newPlain, null, 0);
            } else {
                return new AnsiString(newPlain, otherStates, thisLength + other.statesFrom);
            }
        } else {
            if (otherStates == null) {
                return new AnsiString(newPlain, states, this.statesFrom);
            } else {
                final int statesFrom = this.statesFrom;
                long[] newStates = new long[otherStates.length + other.statesFrom + thisLength - this.statesFrom];
                System.arraycopy(states, 0, newStates, 0, states.length);
                System.arraycopy(otherStates, 0, newStates, other.statesFrom + thisLength - this.statesFrom, otherStates.length);
                return new AnsiString(newPlain, newStates, statesFrom);
            }
        }
    }

    public final AnsiString trim() {
        final String plain = this.plain;
        int len = plain.length();
        if (len == 0) {
            return this;
        }
        int st = 0;

        while (st < len && plain.charAt(st) <= ' ') {
            ++st;
        }
        while (st < len && plain.charAt(len - 1) <= ' ') {
            --len;
        }
        return substring(st, len);
    }

    /**
     * Returns {@code true} if the plain text is empty or contains only
     * {@linkplain Character#isWhitespace(int) white space} codepoints, otherwise {@code false}.
     *
     * @return {@code true} if the plain text is empty or contains only
     * {@linkplain Character#isWhitespace(int) white space} codepoints, otherwise {@code false}
     * @see #getPlain()
     * @see String#isBlank()
     */
    public final boolean isBlank() {
        final String plain = this.plain;
        final int length = plain.length();
        if (length == 0) {
            return true;
        }

        for (int i = 0; i < length; ) {
            final int cp = plain.codePointAt(i);
            if (!Character.isWhitespace(cp)) {
                return false;
            }
            i += Character.charCount(cp);
        }
        return true;
    }

    /**
     * Returns the index within the plain text of the first occurrence of the specified character.
     *
     * @param ch a character (Unicode code point)
     * @return the index of the first occurrence of the character in the
     * character sequence represented by {@link #plain}, or {@code -1} if the character does not occur
     * @see #getPlain()
     * @see String#indexOf(int)
     */
    public final int indexOf(int ch) {
        return plain.indexOf(ch);
    }

    /**
     * Returns the index within the plain text of the first occurrence of the specified character,
     * starting the search at the specified index.
     *
     * @param ch        a character (Unicode code point)
     * @param fromIndex the index to start the search from
     * @return the index of the first occurrence of the character in the character sequence
     * represented by {@link #plain} that is greater than or equal to {@code fromIndex}, or {@code -1}
     * if the character does not occur
     * @see #getPlain()
     * @see String#indexOf(int, int)
     */
    public final int indexOf(int ch, int fromIndex) {
        return plain.indexOf(ch, fromIndex);
    }

    /**
     * Returns the index within the plain text of the last occurrence of the specified character.
     *
     * @param ch a character (Unicode code point)
     * @return the index of the last occurrence of the character in the character sequence
     * represented by {@link #plain}, or {@code -1} if the character does not occur
     * @see #getPlain()
     * @see String#lastIndexOf(int)
     */
    public final int lastIndexOf(int ch) {
        return plain.lastIndexOf(ch);
    }

    /**
     * Returns the index within this string of the last occurrence of
     * the specified character, searching backward starting at the
     * specified index. For values of {@code ch} in the range
     * from 0 to 0xFFFF (inclusive), the index returned is the largest
     * value <i>k</i> such that:
     * <blockquote><pre>
     * (this.charAt(<i>k</i>) == ch) {@code &&} (<i>k</i> &lt;= fromIndex)
     * </pre></blockquote>
     * is true. For other values of {@code ch}, it is the
     * largest value <i>k</i> such that:
     * <blockquote><pre>
     * (this.codePointAt(<i>k</i>) == ch) {@code &&} (<i>k</i> &lt;= fromIndex)
     * </pre></blockquote>
     * is true. In either case, if no such character occurs in this
     * string at or before position {@code fromIndex}, then
     * {@code -1} is returned.
     *
     * <p>All indices are specified in {@code char} values
     * (Unicode code units).
     *
     * @param ch        a character (Unicode code point).
     * @param fromIndex the index to start the search from. There is no
     *                  restriction on the value of {@code fromIndex}. If it is
     *                  greater than or equal to the length of this string, it has
     *                  the same effect as if it were equal to one less than the
     *                  length of this string: this entire string may be searched.
     *                  If it is negative, it has the same effect as if it were -1:
     *                  -1 is returned.
     * @return the index of the last occurrence of the character in the
     * character sequence represented by this object that is less
     * than or equal to {@code fromIndex}, or {@code -1}
     * if the character does not occur before that point.
     */
    public final int lastIndexOf(int ch, int fromIndex) {
        return plain.lastIndexOf(ch, fromIndex);
    }

    /**
     * Returns the index within the plain text of the first occurrence of the specified substring.
     *
     * @param str the substring to search for
     * @return the index of the first occurrence of the specified substring,
     * or {@code -1} if there is no such occurrence
     * @see #getPlain()
     * @see String#indexOf(String)
     */
    public final int indexOf(String str) {
        return plain.indexOf(str);
    }

    /**
     * Returns the index within the plain text of the first occurrence of the
     * specified substring, starting at the specified index.
     *
     * @param str       the substring to search for
     * @param fromIndex the index from which to start the search
     * @return the index of the first occurrence of the specified substring,
     * starting at the specified index, or {@code -1} if there is no such occurrence
     * @see #getPlain()
     * @see String#indexOf(String, int)
     */
    public final int indexOf(String str, int fromIndex) {
        return plain.indexOf(str, fromIndex);
    }

    /**
     * Returns the index within the plain text of the last occurrence of the
     * specified substring.  The last occurrence of the empty string ""
     * is considered to occur at the index value {@code this.length()}.
     *
     * @param str the substring to search for
     * @return the index of the last occurrence of the specified substring,
     * or {@code -1} if there is no such occurrence
     * @see #getPlain()
     * @see String#lastIndexOf(String)
     */
    public final int lastIndexOf(String str) {
        return plain.lastIndexOf(str);
    }

    /**
     * Returns the index within the plain text of the last occurrence of the
     * specified substring, searching backward starting at the specified index.
     *
     * @param str       the substring to search for
     * @param fromIndex the index to start the search from
     * @return the index of the last occurrence of the specified substring,
     * searching backward from the specified index,
     * or {@code -1} if there is no such occurrence
     */
    public final int lastIndexOf(String str, int fromIndex) {
        return plain.lastIndexOf(str, fromIndex);
    }

    public final AnsiString overlay(Overlayable overlayable) {
        if (overlayable == null) {
            throw new NullPointerException();
        }

        if (overlayable instanceof Attribute) {
            return overlay(((Attribute) overlayable));
        }
        if (overlayable instanceof AttributeWithRange) {
            AttributeWithRange attr = (AttributeWithRange) overlayable;
            if (attr.end < 0) {
                return overlay(attr.attribute, attr.start);
            } else {
                return overlay(attr.attribute, attr.start, attr.end);
            }
        }
        throw new AssertionError();
    }

    public final AnsiString overlay(Attribute attribute) {
        return overlay(attribute, 0, length());
    }

    public final AnsiString overlay(Attribute attribute, int start) {
        return overlay(attribute, start, length());
    }

    public final AnsiString overlay(Attribute attribute, int start, int end) {
        if (attribute == null) {
            throw new NullPointerException();
        }

        final int length = this.length();
        if (start > end) {
            throw new IllegalArgumentException("startIndex(" + start + ") > endIndex(" + end + ")");
        }
        if (start < 0 || start >= length) {
            throw new IndexOutOfBoundsException("Index out of range: " + start);
        }
        if (end > length) {
            throw new IndexOutOfBoundsException("Index out of range: " + end);
        }

        if (start == end) {
            return this;
        }

        final int statesFrom = this.statesFrom;
        final long[] states = this.states;

        if (states == null) {
            long mask = attribute.applyMask;
            if (mask == 0) {
                return this;
            }
            long[] newStates = new long[end - start];
            Arrays.fill(newStates, mask);
            return new AnsiString(plain, newStates, start);
        }

        final int newStatesFrom = Math.min(start, statesFrom);
        final int newStatesLength = Math.max(statesFrom + states.length, end) - statesFrom;

        final long[] newStates = new long[newStatesLength];
        System.arraycopy(states, 0, newStates, statesFrom - newStatesFrom, states.length);

        for (int i = start; i < end; i++) {
            final int idx = i - newStatesFrom;
            newStates[idx] = attribute.transform(newStates[idx]);
        }

        return new AnsiString(plain, newStates, newStatesFrom);
    }

    public final AnsiString overlayAll(Overlayable... oas) {
        return overlayAll(false, Arrays.asList(oas));
    }

    public final AnsiString overlayAll(boolean trimStates, Overlayable... oas) {
        return overlayAll(trimStates, Arrays.asList(oas));
    }

    public final AnsiString overlayAll(Iterable<? extends Overlayable> oas) {
        return overlayAll(false, oas);
    }

    public final AnsiString overlayAll(boolean trimStates, Iterable<? extends Overlayable> oas) {
        Iterator<? extends Overlayable> iterator = oas.iterator();
        if (!iterator.hasNext()) {
            return this;
        }

        final int length = this.length();
        if (length == 0) {
            return this;
        }

        long[] newStates = new long[length];
        if (states != null) {
            System.arraycopy(states, 0, newStates, statesFrom, states.length);
        }

        while (iterator.hasNext()) {
            Overlayable oa = iterator.next();
            if (oa == null) {
                continue;
            }
            final Attribute attr;
            final int start;
            final int end;

            if (oa instanceof Attribute) {
                attr = ((Attribute) oa);
                start = 0;
                end = length;
            } else {
                AttributeWithRange ar = (AttributeWithRange) oa;
                attr = ar.attribute;
                start = ar.start;
                end = ar.end < 0 ? length : ar.end;
            }

            if (end > length) {
                throw new IndexOutOfBoundsException("Index out of range: " + end);
            }

            for (int i = start; i < end; i++) {
                newStates[i] = attr.transform(newStates[i]);
            }
        }

        if (trimStates) {
            final int newStatesFrom = trimStatesInit(newStates);
            final int newStatesLength = trimStatesTail(newStates, newStatesFrom);

            if (newStatesLength == 0) {
                return new AnsiString(plain, null, 0);
            } else if (newStatesLength == newStates.length) {
                return new AnsiString(plain, newStates, 0);
            } else {
                long[] ss = Arrays.copyOfRange(newStates, newStatesFrom, newStatesFrom + newStatesLength);
                return new AnsiString(plain, ss, statesFrom);
            }
        } else {
            return new AnsiString(plain, newStates, 0);
        }
    }

    //region Kotlin operators

    public final AnsiString plus(Object string) {
        return concat(AnsiString.valueOf(string));
    }

    /**
     * Alias of {@link #concat(CharSequence)}, used to overload the plus operator in kotlin.
     */
    public final AnsiString plus(CharSequence string) {
        return concat(AnsiString.valueOf(string));
    }

    /**
     * Alias of {@link #concat(AnsiString)}, used to overload the plus operator in kotlin.
     */
    public final AnsiString plus(AnsiString other) {
        return concat(other);
    }

    //endregion

    //region Scala operators

    public final AnsiString $plus$plus(Object string) {
        return concat(AnsiString.valueOf(string));
    }

    /**
     * Alias of {@link #concat(CharSequence)}, used to overload the `++` operator in kotlin.
     */
    public final AnsiString $plus$plus(CharSequence string) {
        return concat(AnsiString.valueOf(string));
    }

    /**
     * Alias of {@link #concat(AnsiString)}, used to overload the `++` operator in scala.
     */
    public final AnsiString $plus$plus(AnsiString other) {
        return concat(other);
    }

    //endregion

    /**
     * {@inheritDoc}
     */
    public final int compareTo(AnsiString o) {
        return toString().compareTo(o.toString());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof AnsiString)) {
            return false;
        }
        return toString().equals(o.toString());
    }

    private transient int hashCode;

    /**
     * {@inheritDoc}
     */
    @Override
    public final int hashCode() {
        if (hashCode != 0) {
            return hashCode;
        }

        return hashCode = toString().hashCode() + hashMagic;
    }

    private transient Object encodedCache = null;

    private String getCache() {
        final Object cache = this.encodedCache;
        if (cache == null) {
            return null;
        }
        if (cache instanceof String) {
            return ((String) cache);
        }
        if (cache instanceof Reference<?>) {
            return (String) ((Reference<?>) cache).get();
        }
        return null;
    }

    /**
     * Get the encoded string (including ANSI escape sequence represented by {@link #states }).
     *
     * <p>Results are calculated when it is needed. If states is {@code null}, then this method
     * will return plain text directly.
     *
     * <p>Currently, this method saves {@link WeakReference weak references} to the results.
     * This method will not repeatedly calculate the value until the string is recovered by gc.
     *
     * @return the encoded string
     */
    public final String getEncoded() {
        String res = getCache();
        if (res != null) {
            return res;
        }

        synchronized (this) {
            res = getCache();
            if (res != null) {
                return res;
            }

            final String plain = this.plain;
            final long[] states = this.states;
            if (states == null) {
                this.encodedCache = plain;
                return plain;
            }

            final int length = this.length();
            final int statesFrom = this.statesFrom;
            final int statesLimit = statesFrom + states.length;

            long currentState = 0;

            StringBuilder builder = new StringBuilder(length * 2);

            for (int i = 0; i < length; i++) {
                final long state = (i >= statesFrom && i < statesLimit) ? states[i - statesFrom] : 0;
                if (state != currentState) {
                    Attribute.emitAnsiCodes0(currentState, state, builder);
                    currentState = state;
                }
                builder.append(plain.charAt(i));
            }

            Attribute.emitAnsiCodes0(currentState, 0, builder);
            res = builder.toString();
            this.encodedCache = new WeakReference<>(res);
            return res;
        }
    }

    /**
     * {@inheritDoc}
     *
     * @see #getEncoded()
     */
    @Override
    public String toString() {
        return getEncoded();
    }

    /**
     * Used to handle unknown ANSI escape sequences when parsing a {@link CharSequence}.
     */
    public enum ErrorMode {
        /**
         * Throw an exception and abort the parse.
         */
        THROW {
            @Override
            public int handle(int sourceIndex, CharSequence raw) {
                Matcher matcher = ANSI_PATTERN.matcher(raw);
                String detail =
                        (!matcher.find(sourceIndex)) ?
                                ""
                                : " " + raw.subSequence(sourceIndex + 1, matcher.end());


                throw new IllegalArgumentException(
                        "Unknown ansi-escape$detail at index " + sourceIndex
                                + " inside string cannot be parsed into an AnsiString"
                );
            }
        },

        /**
         * Skip the {@code \u001b} that kicks off the unknown Ansi escape but leave
         * subsequent characters in place, so the end-user can see that an Ansi
         * escape was entered e.g. via the [A[B[A[C that appears in the result
         */
        SANITIZE {
            @Override
            public int handle(int sourceIndex, CharSequence raw) {
                return sourceIndex + 1;
            }
        },

        /**
         * Find the end of the unknown Ansi escape and skip over it's characters
         * entirely, so no trace of them appear in the parsed {@code AnsiString}.
         */
        STRIP {
            @Override
            public int handle(int sourceIndex, CharSequence raw) {
                Matcher matcher = ANSI_PATTERN.matcher(raw);
                //noinspection ResultOfMethodCallIgnored
                matcher.find(sourceIndex);
                return matcher.end();
            }
        };

        /**
         * The default {@code ErrorMode}.
         */
        public static final ErrorMode DEFAULT = THROW;

        public abstract int handle(int sourceIndex, CharSequence raw);
    }

    /**
     * Represents an {@link Attribute} or {@link AttributeWithRange}.
     *
     * @see Attribute
     * @see AttributeWithRange
     * @see AnsiString#overlay(Overlayable)
     * @see AnsiString#overlayAll(Overlayable...)
     * @see AnsiString#overlayAll(Iterable)
     */
    public static abstract class Overlayable {
        Overlayable() {
        }

        /**
         * Alias of {@link #overlay(CharSequence)}, used to overload the invoke operator in scala.
         */
        public final AnsiString apply(CharSequence string) {
            return overlay(string);
        }

        /**
         * Alias of {@link #overlay(AnsiString)}, used to overload the invoke operator in scala.
         */
        public final AnsiString apply(AnsiString string) {
            return overlay(string);
        }

        /**
         * Alias of {@link #overlay(CharSequence)}, used to overload the invoke operator in kotlin.
         */
        public final AnsiString invoke(CharSequence string) {
            return overlay(string);
        }

        /**
         * Alias of {@link #overlay(AnsiString)}, used to overload the invoke operator in kotlin.
         */
        public final AnsiString invoke(AnsiString string) {
            return overlay(string);
        }

        /**
         * Apply this to the given {@code CharSequence}, making it take effect
         * across the entire length of that string.
         */
        public final AnsiString overlay(CharSequence string) {
            return AnsiString.parse(string).overlay(this);
        }

        /**
         * Apply this to the given {@code AnsiString}, making it take effect
         * across the entire length of that string.
         */
        public final AnsiString overlay(AnsiString string) {
            return string.overlay(this);
        }
    }

    /**
     * Used for mapping between status integer and ANSI escape sequences.
     */
    public static abstract class Attribute extends Overlayable implements Comparable<Attribute> {
        final long resetMask;
        final long applyMask;

        Attribute(long resetMask, long applyMask) {
            this.resetMask = resetMask;
            this.applyMask = applyMask;
        }

        static void emitAnsiCodes0(long currentState, long nextState, Appendable output) {
            try {
                if (currentState != nextState) {

                    int hardOffMask = Bold.category.mask();

                    long currentState2;
                    if ((currentState & ~nextState & hardOffMask) != 0) {
                        output.append(RESET);
                        currentState2 = 0L;
                    } else {
                        currentState2 = currentState;
                    }

                    for (Category cat : Category.categories()) {
                        if ((cat.mask() & currentState2) != (cat.mask() & nextState)) {
                            output.append(cat.lookupEscape(nextState & cat.mask()));
                        }
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public static String emitAnsiCodes(long currentState, long nextState) {
            StringBuilder builder = new StringBuilder();
            emitAnsiCodes0(currentState, nextState, builder);
            return builder.toString();
        }

        public static Attribute empty() {
            return Attrs.EMPTY;
        }

        public static Attribute of() {
            return Attrs.EMPTY;
        }

        public static Attribute of(Attribute attribute) {
            if (attribute == null) {
                throw new NullPointerException();
            }
            return attribute;
        }

        public static Attribute of(Attribute attribute1, Attribute attribute2) {
            if (attribute1.equals(attribute2)) {
                return attribute1;
            }
            return of(new Attribute[]{attribute1, attribute2});
        }

        public static Attribute of(Attribute... attributes) {
            int attributesLength = attributes.length;
            if (attributesLength == 0) {
                return Attrs.EMPTY;
            }
            if (attributesLength == 1) {
                return attributes[0];
            }

            TreeSet<Attr> output = new TreeSet<>();
            long resetMask = 0L;
            long applyMask = 0L;

            for (int i = attributesLength - 1; i >= 0; i--) {
                final Attribute attr = attributes[i];
                if (attr instanceof Attrs) {
                    for (Attr attribute : ((Attrs) attr).attributes) {
                        if ((attribute.resetMask & ~resetMask) != 0) {
                            if ((attribute.applyMask & resetMask) == 0) {
                                applyMask = applyMask | attribute.applyMask;
                            }
                            resetMask = resetMask | attribute.resetMask;
                            output.add(attribute);
                        }
                    }
                } else {
                    if ((attr.resetMask & ~resetMask) != 0) {
                        if ((attr.applyMask & resetMask) == 0) {
                            applyMask = applyMask | attr.applyMask;
                        }
                        resetMask = resetMask | attr.resetMask;
                        output.add((Attr) attr);
                    }
                }
            }

            final int outputSize = output.size();
            if (outputSize == 0) {
                return Attrs.EMPTY;
            }
            if (outputSize == 1) {
                return output.first();
            }

            return new Attrs(resetMask, applyMask, output.toArray(new Attr[outputSize]));
        }

        public static Attribute of(List<? extends Attribute> list) {
            final ListIterator<? extends Attribute> it = list.listIterator(list.size());
            return of(new Iterator<Attribute>() {
                public final boolean hasNext() {
                    return it.hasPrevious();
                }

                public final Attribute next() {
                    return it.previous();
                }

                public final void remove() {
                    throw new UnsupportedOperationException("remove");
                }
            });
        }

        @SuppressWarnings("unchecked")
        public static Attribute of(Iterable<? extends Attribute> attributes) {
            if (attributes instanceof List<?>) {
                return of(((List<Attribute>) attributes));
            }
            LinkedList<Attribute> l = new LinkedList<>();
            for (Attribute attribute : attributes) {
                l.add(attribute);
            }
            return of(l);
        }

        private static Attribute of(Iterator<? extends Attribute> reverseIterator) {
            if (!reverseIterator.hasNext()) {
                return Attrs.EMPTY;
            }

            TreeSet<Attr> output = new TreeSet<>();
            long resetMask = 0L;
            long applyMask = 0L;

            while (reverseIterator.hasNext()) {
                final Attribute attr = reverseIterator.next();
                if (attr instanceof Attrs) {
                    for (Attr attribute : ((Attrs) attr).attributes) {
                        if ((attribute.resetMask & ~resetMask) != 0) {
                            if ((attribute.applyMask & resetMask) == 0) {
                                applyMask = applyMask | attribute.applyMask;
                            }
                            resetMask = resetMask | attribute.resetMask;
                            output.add(attribute);
                        }
                    }
                } else {
                    if ((attr.resetMask & ~resetMask) != 0) {
                        if ((attr.applyMask & resetMask) == 0) {
                            applyMask = applyMask | attr.applyMask;
                        }
                        resetMask = resetMask | attr.resetMask;
                        output.add((Attr) attr);
                    }
                }
            }

            final int outputSize = output.size();
            if (outputSize == 0) {
                return Attrs.EMPTY;
            }
            if (outputSize == 1) {
                return output.first();
            }

            return new Attrs(resetMask, applyMask, output.toArray(new Attr[outputSize]));
        }

        public abstract Attribute concat(Attribute other);

        public final long transform(long state) {
            return (state & ~resetMask) | applyMask;
        }

        public final AttributeWithRange withRange(int start) {
            if (start < 0) {
                throw new IllegalArgumentException("startIndex(" + start + ") < 0");
            }
            return new AttributeWithRange(this, start);
        }

        public final AttributeWithRange withRange(int start, int end) {
            if (start > end) {
                throw new IllegalArgumentException("startIndex(" + start + ") > endIndex(" + end + ")");
            }
            if (start < 0) {
                throw new IllegalArgumentException("startIndex(" + start + ") < 0");
            }
            return new AttributeWithRange(this, start, end);
        }

        public final int compareTo(AnsiString.Attribute o) {
            final long x = this.applyMask;
            final long y = o.applyMask;

            //noinspection UseCompareMethod
            return (x < y) ? -1 : ((x == y) ? 0 : 1); // Compatible with java 5
        }

        @Override
        public final boolean equals(Object o) {
            if (!(o instanceof Attribute)) {
                return false;
            }

            return this.applyMask == ((Attribute) o).applyMask;
        }

        @Override
        public final int hashCode() {
            return (int) (applyMask ^ (applyMask >>> 32));
        }
    }

    public static final class AttributeWithRange extends Overlayable {
        public final Attribute attribute;

        public final int start;
        public final int end;

        AttributeWithRange(Attribute attribute) {
            this.attribute = attribute;
            this.start = 0;
            this.end = -1;
        }

        AttributeWithRange(Attribute attribute, int start) {
            this.attribute = attribute;
            this.start = start;
            this.end = -1;
        }

        AttributeWithRange(Attribute attribute, int start, int end) {
            this.attribute = attribute;
            this.start = start;
            this.end = end;
        }

        @Override
        public final String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append(attribute)
                    .append(" with range(from ")
                    .append(start);
            if (end != -1) {
                builder.append(", to ")
                        .append(end);
            }
            builder.append(')');

            return builder.toString();
        }
    }

    /**
     * Represents the removal of all ansi text decoration. Doesn't fit into any
     * convenient category, since it applies to them all.
     */
    public static final Attribute Reset = new Attr.Escape("Reset", "\u001b[0m", Integer.MAX_VALUE, 0);

    /**
     * Attributes to turn text bold/bright or disable it.
     */
    public static final class Bold {
        private Bold() {
        }

        static final Category category = new Category("Bold", 0, 1);

        public static final Attribute On = category.makeAttr("On", "\u001b[1m", 1);
        public static final Attribute Off = category.makeNoneAttr("Off", 0);
    }

    /**
     * Attributes to reverse the background/foreground colors of your text, or un-reverse them.
     */
    public static final class Reversed {
        private Reversed() {
        }

        static final Category category = new Category("Reversed", 1, 1);

        public static final Attribute On = category.makeAttr("On", "\u001b[7m", 1);
        public static final Attribute Off = category.makeAttr("Off", "\u001b[27m", 0);
    }

    /**
     * Attributes to enable or disable underlined text.
     */
    public static final class Underlined {
        private Underlined() {
        }

        static final Category category = new Category("Underlined", 2, 1);

        public static final Attribute On = category.makeAttr("On", "\u001b[4m", 1);
        public static final Attribute Off = category.makeAttr("Off", "\u001b[24m", 0);
    }

    /**
     * Attributes to set or reset the color of your foreground text.
     */
    public static final class Color {
        private Color() {
        }

        static final ColorCategory category = new ColorCategory("Color", 3, 25, 38);

        public static final Attribute Reset =
                category.makeAttr("Reset", "\u001b[39m", 0);
        public static final Attribute Black =
                category.makeAttr("Black", "\u001b[30m", 1);
        public static final Attribute Red =
                category.makeAttr("Red", "\u001b[31m", 2);
        public static final Attribute Green =
                category.makeAttr("Green", "\u001b[32m", 3);
        public static final Attribute Yellow =
                category.makeAttr("Yellow", "\u001b[33m", 4);
        public static final Attribute Blue =
                category.makeAttr("Blue", "\u001b[34m", 5);
        public static final Attribute Magenta =
                category.makeAttr("Magenta", "\u001b[35m", 6);
        public static final Attribute Cyan =
                category.makeAttr("Cyan", "\u001b[36m", 7);
        public static final Attribute LightGray =
                category.makeAttr("LightGray", "\u001b[37m", 8);
        public static final Attribute DarkGray =
                category.makeAttr("DarkGray", "\u001b[90m", 9);
        public static final Attribute LightRed =
                category.makeAttr("LightRed", "\u001b[91m", 10);
        public static final Attribute LightGreen =
                category.makeAttr("LightGreen", "\u001b[92m", 11);
        public static final Attribute LightYellow =
                category.makeAttr("LightYellow", "\u001b[93m", 12);
        public static final Attribute LightBlue =
                category.makeAttr("LightBlue", "\u001b[94m", 13);
        public static final Attribute LightMagenta =
                category.makeAttr("LightMagenta", "\u001b[95m", 14);
        public static final Attribute LightCyan =
                category.makeAttr("LightCyan", "\u001b[96m", 15);
        public static final Attribute White =
                category.makeAttr("White", "\u001b[97m", 16);


        public static Attribute True(int r, int g, int b) {
            return category.True(r, g, b);
        }
    }

    /**
     * Attributes to set or reset the color of your background.
     */
    public static final class Back {
        private Back() {
        }

        static final ColorCategory category = new ColorCategory("Back", 28, 25, 48);

        public static final Attribute Reset =
                category.makeAttr("Reset", "\u001b[49m", 0);
        public static final Attribute Black =
                category.makeAttr("Black", "\u001b[40m", 1);
        public static final Attribute Red =
                category.makeAttr("Red", "\u001b[41m", 2);
        public static final Attribute Green =
                category.makeAttr("Green", "\u001b[42m", 3);
        public static final Attribute Yellow =
                category.makeAttr("Yellow", "\u001b[43m", 4);
        public static final Attribute Blue =
                category.makeAttr("Blue", "\u001b[44m", 5);
        public static final Attribute Magenta =
                category.makeAttr("Magenta", "\u001b[45m", 6);
        public static final Attribute Cyan =
                category.makeAttr("Cyan", "\u001b[46m", 7);
        public static final Attribute LightGray =
                category.makeAttr("LightGray", "\u001b[47m", 8);
        public static final Attribute DarkGray =
                category.makeAttr("DarkGray", "\u001b[100m", 9);
        public static final Attribute LightRed =
                category.makeAttr("LightRed", "\u001b[101m", 10);
        public static final Attribute LightGreen =
                category.makeAttr("LightGreen", "\u001b[102m", 11);
        public static final Attribute LightYellow =
                category.makeAttr("LightYellow", "\u001b[103m", 12);
        public static final Attribute LightBlue =
                category.makeAttr("LightBlue", "\u001b[104m", 13);
        public static final Attribute LightMagenta =
                category.makeAttr("LightMagenta", "\u001b[105m", 14);
        public static final Attribute LightCyan =
                category.makeAttr("LightCyan", "\u001b[106m", 15);
        public static final Attribute White =
                category.makeAttr("White", "\u001b[107m", 16);

        public static Attribute True(int r, int g, int b) {
            return category.True(r, g, b);
        }
    }
}
