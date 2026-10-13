package codes.castled.arboretum.core.nbt;

/** Thrown when an NBT stream is malformed, truncated, or uses an encoding Arboretum cannot read. */
public class NbtException extends RuntimeException {
    public NbtException(String message) {
        super(message);
    }

    public NbtException(String message, Throwable cause) {
        super(message, cause);
    }
}