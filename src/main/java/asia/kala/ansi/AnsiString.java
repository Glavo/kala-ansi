package asia.kala.ansi;

import java.io.IOException;
import java.io.Serializable;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AnsiString implements Serializable, Comparable<AnsiString> {
    private static final long serialVersionUID = -2640452895881997219L;

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

    public static int trimStatesInit(long[] states) {
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

    public static int trimStatesTail(long[] states, int limit) {
        if (states == null) {
            return 0;
        }
        return trimStatesTail(states, limit, states.length);
    }

    public static int trimStatesTail(long[] states, int limit, int arrayLength) {
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

    public static AnsiString valueOf() {
        return EMPTY;
    }

    public static AnsiString valueOf(AnsiString string) {
        return string == null ? NULL : string;
    }

    public static AnsiString valueOf(CharSequence raw) {
        if (raw == null) {
            return null;
        }
        return parse(raw);
    }

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

    public static AnsiString parse(CharSequence raw) {
        return parse(raw, ErrorMode.DEFAULT, true);
    }

    public static AnsiString parse(CharSequence raw, ErrorMode errorMode) {
        return parse(raw, errorMode, true);
    }

    public static AnsiString parse(CharSequence raw, boolean trimStates) {
        return parse(raw, ErrorMode.DEFAULT, trimStates);
    }

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

    public static AnsiString ofPlain(CharSequence plain) {
        if (plain == null) {
            return NULL;
        }

        if (plain.length() == 0) {
            return EMPTY;
        }
        return new AnsiString(plain.toString(), null, 0);
    }

    public static AnsiString concat(AnsiString string1, AnsiString string2) {
        return (string1 == null ? NULL : string1).concat(string2);
    }

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

    //region String like operators

    public final int length() {
        return plain.length();
    }

    public final boolean isEmpty() {
        return plain.isEmpty();
    }

    public final char charAt(int index) {
        return plain.charAt(index);
    }

    public final int codePointAt(int index) {
        return plain.codePointAt(index);
    }

    public final int codePointBefore(int index) {
        return plain.codePointBefore(index);
    }

    public final int codePointCount(int beginIndex, int endIndex) {
        return plain.codePointCount(beginIndex, endIndex);
    }

    public final int offsetByCodePoints(int index, int codePointOffset) {
        return plain.offsetByCodePoints(index, codePointOffset);
    }

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

    public final AnsiString concat(CharSequence string) {
        return concat(AnsiString.parse(string));
    }

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

        String newPlain = this.plain + other.plain;

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

    public final int indexOf(int ch) {
        return plain.indexOf(ch);
    }

    public final int indexOf(int ch, int fromIndex) {
        return plain.indexOf(ch, fromIndex);
    }

    public final int lastIndexOf(int ch) {
        return plain.lastIndexOf(ch);
    }

    public final int lastIndexOf(int ch, int fromIndex) {
        return plain.lastIndexOf(ch, fromIndex);
    }

    public final int indexOf(/* NotNull */ String str) {
        return plain.indexOf(str);
    }

    public final int indexOf(/* NotNull */ String str, int fromIndex) {
        return plain.indexOf(str, fromIndex);
    }

    public int lastIndexOf(/* NotNull */ String str) {
        return plain.lastIndexOf(str);
    }

    public int lastIndexOf(/* NotNull */ String str, int fromIndex) {
        return plain.lastIndexOf(str, fromIndex);
    }

    //endregion

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

    /**
     * {@inheritDoc}
     */
    @Override
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

        return hashCode = toString().hashCode();
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
     *
     */
    @Override
    public final String toString() {
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

    public enum ErrorMode {
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
        SANITIZE {
            @Override
            public int handle(int sourceIndex, CharSequence raw) {
                return sourceIndex + 1;
            }
        },
        STRIP {
            @Override
            public int handle(int sourceIndex, CharSequence raw) {
                Matcher matcher = ANSI_PATTERN.matcher(raw);
                //noinspection ResultOfMethodCallIgnored
                matcher.find(sourceIndex);
                return matcher.end();
            }
        };

        public static final ErrorMode DEFAULT = THROW;

        public abstract int handle(int sourceIndex, CharSequence raw);
    }

    public static abstract class Overlayable {
        Overlayable() {
        }

        public final AnsiString overlay(CharSequence string) {
            return AnsiString.parse(string).overlay(this);
        }

        public final AnsiString overlay(AnsiString string) {
            return string.overlay(this);
        }
    }

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
                @Override
                public final boolean hasNext() {
                    return it.hasPrevious();
                }

                @Override
                public final Attribute next() {
                    return it.previous();
                }

                @Override
                public final void remove() {
                    throw new UnsupportedOperationException("remove");
                }
            });
        }

        @SuppressWarnings("unchecked")
        public static Attribute of(/* NotNull */  Iterable<? extends Attribute> attributes) {
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

        @Override
        public int compareTo(AnsiString.Attribute o) {
            return Long.compare(this.applyMask, o.applyMask);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Attribute)) {
                return false;
            }

            return this.applyMask == ((Attribute) o).applyMask;
        }

        @Override
        public int hashCode() {
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

    public static final Attribute Reset = new Attr.Escape("Reset", "\u001b[0m", Integer.MAX_VALUE, 0);

    public static final class Bold {
        private Bold() {
        }

        static final Category category = new Category("Bold", 0, 1);

        public static final Attribute On = category.makeAttr("On", "\u001b[1m", 1);
        public static final Attribute Off = category.makeNoneAttr("Off", 0);
    }

    public static final class Reversed {
        private Reversed() {
        }

        static final Category category = new Category("Reversed", 1, 1);

        public static final Attribute On = category.makeAttr("On", "\u001b[7m", 1);
        public static final Attribute Off = category.makeAttr("Off", "\u001b[27m", 0);
    }

    public static final class Underlined {
        private Underlined() {
        }

        static final Category category = new Category("Underlined", 2, 1);

        public static final Attribute On = category.makeAttr("On", "\u001b[4m", 1);
        public static final Attribute Off = category.makeAttr("Off", "\u001b[24m", 0);
    }

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
