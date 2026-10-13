package codes.castled.arboretum.core.nbt;

/** The NBT tag types Arboretum's structure templates use. */
public enum TagType {
    END(0),
    BYTE(1),
    SHORT(2),
    INT(3),
    LONG(4),
    FLOAT(5),
    DOUBLE(6),
    BYTE_ARRAY(7),
    STRING(8),
    LIST(9),
    COMPOUND(10),
    INT_ARRAY(11),
    LONG_ARRAY(12);

    private final int id;

    TagType(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public static TagType byId(int id) {
        for (TagType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        throw new NbtException("Unknown NBT tag id " + id + ".");
    }
}