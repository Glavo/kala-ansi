package asia.kala.ansi;

class Category {
    private static Category[] categories;

    static Category[] categories() {
        if (categories != null) {
            return categories;
        }
        synchronized (Category.class) {
            if (categories != null) {
                return categories;
            }
            return categories = new Category[]{
                    AnsiString.Color.category,
                    AnsiString.Back.category,
                    AnsiString.Bold.category,
                    AnsiString.Underlined.category,
                    AnsiString.Reversed.category
            };
        }
    }


    final String name;
    final int offset;
    final int width;

    final Attr[] lookupAttrTable;

    Category(String name, int offset, int width) {
        this(name, offset, width, 1 << width);
    }

    Category(String name, int offset, int width, int lookupTableWidth) {
        this.name = name;
        this.offset = offset;
        this.width = width;

        this.lookupAttrTable = new Attr[lookupTableWidth];
    }


    final int mask() {
        return ((1 << width) - 1) << offset;
    }

    String lookupEscape(long applyState) {
        String escape = lookupAttr(applyState).escape;
        return escape == null ? "" : escape;
    }

    Attr lookupAttr(long applyState) {
        return lookupAttrTable[(int) (applyState >> offset)];
    }

    Attr.Escape makeAttr0(String attrName, String escape, long applyValue) {
        return new Attr.Escape(name + "." + attrName, escape, mask(), applyValue << offset);
    }

    Attr.Escape makeAttr(String attrName, String escape, long applyValue) {
        Attr.Escape attr = makeAttr0(attrName, escape, applyValue);
        lookupAttrTable[(int) applyValue] = attr;
        return attr;
    }

    @SuppressWarnings("SameParameterValue")
    Attr.Reset makeNoneAttr(String attrName, long applyValue) {
        Attr.Reset attr = new Attr.Reset(name + "." + attrName, mask(), applyValue << offset);
        lookupAttrTable[(int) applyValue] = attr;
        return attr;
    }

    @Override
    public final String toString() {
        return name;
    }
}
