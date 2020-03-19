package asia.kala.ansi;

import java.util.Arrays;

final class Attrs extends AnsiString.Attribute {
    static final Attr[] EMPTY_ATTR_ARRAY = new Attr[0];

    static final Attrs EMPTY = new Attrs(0L, 0L, EMPTY_ATTR_ARRAY);

    final Attr[] attributes;

    Attrs(long resetMask, long applyMask, Attr[] attributes) {
        super(resetMask, applyMask);
        this.attributes = attributes;
    }

    @Override
    public final AnsiString.Attribute concat(AnsiString.Attribute other) {
        if (other == null) {
            throw new NullPointerException();
        }

        final Attr[] attributes = this.attributes;
        final int attributesLength = attributes.length;
        if (attributesLength == 0) {
            return other;
        }

        if (other instanceof Attrs) {
            final Attrs os = (Attrs) other;
            final int osAttributesLength = os.attributes.length;

            if (osAttributesLength == 0) {
                return this;
            }
            if (Arrays.equals(this.attributes, os.attributes)) {
                return this;
            }

            Attr[] newAttrs = new Attr[attributesLength + osAttributesLength];
            System.arraycopy(attributes, 0, newAttrs, 0, attributesLength);
            System.arraycopy(os.attributes, 0, newAttrs, attributesLength, osAttributesLength);
            return AnsiString.Attribute.of(newAttrs);
        } else {
            int idx = Arrays.binarySearch(attributes, other);
            if (idx < 0) {
                Attr[] s = Arrays.copyOfRange(attributes, 0, attributesLength + 1);
                s[attributesLength] = ((Attr) other);
                return AnsiString.Attribute.of(s);
            } else {
                return this;
            }
        }
    }

    @Override
    public final String toString() {
        return "Attributes" + Arrays.toString(attributes);
    }
}
