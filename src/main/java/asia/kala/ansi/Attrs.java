package asia.kala.ansi;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Objects;

final class Attrs extends AnsiString.Attribute {
    final Attr[] attributes;

    Attrs(long resetMask, long applyMask, Attr @NotNull [] attributes) {
        super(resetMask, applyMask);
        this.attributes = attributes;
    }

    @Override
    public final AnsiString.Attribute concat(AnsiString.Attribute other) {
        Objects.requireNonNull(other);
        throw new UnsupportedOperationException();//TODO
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(attributes);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AnsiString.Attribute)) {
            return false;
        }
        if (o instanceof Attrs) {
            Attrs other = (Attrs) o;
            return this.attributes == other.attributes; // TODO
        } else {
            return this.attributes.length == 1 && this.attributes[0] == o;
        }
    }

    @Override
    public final String toString() {
        return "Attributes" + Arrays.toString(attributes);
    }
}
