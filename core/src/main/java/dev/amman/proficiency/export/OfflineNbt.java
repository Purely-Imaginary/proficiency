package dev.amman.proficiency.export;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

/**
 * A minimal reader of a player's {@code <uuid>.dat} (gzip NBT) that pulls out the one tag Proficiency saves its
 * skills under, with no Minecraft class and without building the rest of the file (inventory, advancements). The
 * tag is returned as JSON so {@code PlayerSkills.CODEC} can parse it with {@code JsonOps}: NBT numbers become JSON
 * numbers, except a byte of 0 or 1, which is a boolean (how a codec saves one).
 *
 * <p>Where the tag sits depends on the loader: NeoForge {@code neoforge:attachments}, Fabric
 * {@code fabric:attachments}, Forge 1.20.1 {@code ForgeCaps}; each holds {@code proficiency:skills}.
 */
public final class OfflineNbt {

    /** Top-level compounds that can hold the mod's data, one per loader. */
    static final String[] HOLDERS = {"neoforge:attachments", "fabric:attachments", "ForgeCaps"};
    public static final String KEY = "proficiency:skills";

    private static final int MAX_DEPTH = 64;
    /** A player file is some tens of KB; a stream that expands past this is not one. */
    private static final long MAX_BYTES = 32L * 1024 * 1024;

    private OfflineNbt() {
    }

    /** The skills tag of a gzip NBT stream, or null when the player has none (or the file is not a player file). */
    public static JsonElement skillsTag(InputStream raw) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new CappedStream(new GZIPInputStream(raw), MAX_BYTES)))) {
            if (in.readByte() != 10) {
                return null;
            }
            in.readUTF();
            JsonElement found = null;
            for (int type = in.readByte(); type != 0; type = in.readByte()) {
                String name = in.readUTF();
                if (type == 10 && isHolder(name)) {
                    JsonElement holder = read(in, 10, 0);
                    JsonElement skills = holder.getAsJsonObject().get(KEY);
                    if (skills != null && found == null) {
                        found = skills;
                    }
                } else {
                    skip(in, type, 0);
                }
            }
            return found;
        }
    }

    private static boolean isHolder(String name) {
        for (String h : HOLDERS) {
            if (h.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static JsonElement read(DataInputStream in, int type, int depth) throws IOException {
        if (depth > MAX_DEPTH) {
            throw new IOException("nbt too deep");
        }
        switch (type) {
            case 1:
            {
                // A codec writes a boolean as a byte, and JsonOps refuses a number for a boolean, so 0 and 1 become
                // booleans (JsonOps still reads a boolean as the number 0 or 1 for a numeric field).
                byte b = in.readByte();
                return b == 0 || b == 1 ? new JsonPrimitive(b == 1) : new JsonPrimitive(b);
            }
            case 2:
                return new JsonPrimitive(in.readShort());
            case 3:
                return new JsonPrimitive(in.readInt());
            case 4:
                return new JsonPrimitive(in.readLong());
            case 5:
                return new JsonPrimitive(in.readFloat());
            case 6:
                return new JsonPrimitive(in.readDouble());
            case 8:
                return new JsonPrimitive(in.readUTF());
            case 7: {
                int n = length(in);
                JsonArray a = new JsonArray();
                for (int i = 0; i < n; i++) {
                    a.add(in.readByte());
                }
                return a;
            }
            case 11: {
                int n = length(in);
                JsonArray a = new JsonArray();
                for (int i = 0; i < n; i++) {
                    a.add(in.readInt());
                }
                return a;
            }
            case 12: {
                int n = length(in);
                JsonArray a = new JsonArray();
                for (int i = 0; i < n; i++) {
                    a.add(in.readLong());
                }
                return a;
            }
            case 9: {
                int element = in.readByte();
                int n = length(in);
                JsonArray a = new JsonArray();
                for (int i = 0; i < n; i++) {
                    a.add(element == 0 ? JsonNull.INSTANCE : read(in, element, depth + 1));
                }
                return a;
            }
            case 10: {
                JsonObject o = new JsonObject();
                for (int t = in.readByte(); t != 0; t = in.readByte()) {
                    String name = in.readUTF();
                    o.add(name, read(in, t, depth + 1));
                }
                return o;
            }
            default:
                throw new IOException("unknown nbt tag " + type);
        }
    }

    private static void skip(DataInputStream in, int type, int depth) throws IOException {
        if (depth > MAX_DEPTH) {
            throw new IOException("nbt too deep");
        }
        switch (type) {
            case 1:
                skipFully(in, 1);
                break;
            case 2:
                skipFully(in, 2);
                break;
            case 3:
            case 5:
                skipFully(in, 4);
                break;
            case 4:
            case 6:
                skipFully(in, 8);
                break;
            case 8:
                skipFully(in, in.readUnsignedShort());
                break;
            case 7:
                skipFully(in, length(in));
                break;
            case 11:
                skipFully(in, 4L * length(in));
                break;
            case 12:
                skipFully(in, 8L * length(in));
                break;
            case 9: {
                int element = in.readByte();
                int n = length(in);
                for (int i = 0; i < n && element != 0; i++) {
                    skip(in, element, depth + 1);
                }
                break;
            }
            case 10:
                for (int t = in.readByte(); t != 0; t = in.readByte()) {
                    skipFully(in, in.readUnsignedShort());
                    skip(in, t, depth + 1);
                }
                break;
            default:
                throw new IOException("unknown nbt tag " + type);
        }
    }

    private static int length(DataInputStream in) throws IOException {
        int n = in.readInt();
        if (n < 0) {
            throw new IOException("negative nbt length");
        }
        return n;
    }

    private static void skipFully(DataInputStream in, long n) throws IOException {
        while (n > 0) {
            long s = in.skip(n);
            if (s <= 0) {
                if (in.read() < 0) {
                    throw new IOException("unexpected end of nbt");
                }
                s = 1;
            }
            n -= s;
        }
    }

    /** Ends the stream with an error once more than {@code max} bytes were read (a gzip bomb). */
    private static final class CappedStream extends InputStream {
        private final InputStream in;
        private long left;

        CappedStream(InputStream in, long max) {
            this.in = in;
            this.left = max;
        }

        @Override
        public int read() throws IOException {
            if (left-- <= 0) {
                throw new IOException("nbt too large");
            }
            return in.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (left <= 0) {
                throw new IOException("nbt too large");
            }
            int n = in.read(b, off, (int) Math.min(len, left));
            if (n > 0) {
                left -= n;
            }
            return n;
        }

        @Override
        public void close() throws IOException {
            in.close();
        }
    }
}
