package io.hgraphdb;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.KryoSerializable;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.apache.hadoop.hbase.util.Order;
import org.apache.hadoop.hbase.util.OrderedBytes;
import org.apache.hadoop.hbase.util.PositionedByteRange;
import org.apache.hadoop.hbase.util.SimplePositionedByteRange;
import org.apache.hadoop.hbase.util.SimplePositionedMutableByteRange;

import java.io.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;

public final class Serializer {

    public static final int DEFAULT_NUM_BUCKETS = 256;

    @SuppressWarnings("unchecked")
    public static <T> T deserialize(byte[] target) {
        if (target == null) return null;
        PositionedByteRange buffer = new SimplePositionedByteRange(target);
        return deserialize(buffer);
    }

    @SuppressWarnings("unchecked")
    public static <T> T deserialize(PositionedByteRange buffer) {
        ValueType type = ValueType.valueOf(OrderedBytes.decodeInt8(buffer));

        switch (type) {
            case NULL:
                return null;
            case BOOLEAN:
                return (T) Boolean.valueOf(OrderedBytes.decodeInt8(buffer) == 1);
            case STRING:
                return (T) OrderedBytes.decodeString(buffer);
            case BYTE:
                return (T) Byte.valueOf(OrderedBytes.decodeInt8(buffer));
            case SHORT:
                return (T) Short.valueOf(OrderedBytes.decodeInt16(buffer));
            case INT:
                return (T) Integer.valueOf(OrderedBytes.decodeInt32(buffer));
            case LONG:
                return (T) Long.valueOf(OrderedBytes.decodeInt64(buffer));
            case FLOAT:
                return (T) Float.valueOf(OrderedBytes.decodeFloat32(buffer));
            case DOUBLE:
                return (T) Double.valueOf(OrderedBytes.decodeFloat64(buffer));
            case DECIMAL:
                return (T) OrderedBytes.decodeNumericAsBigDecimal(buffer);
            case DATE:
                return (T) LocalDate.ofEpochDay(OrderedBytes.decodeInt64(buffer));
            case TIME:
                return (T) LocalTime.ofNanoOfDay(OrderedBytes.decodeInt64(buffer));
            case TIMESTAMP:
                return (T) LocalDateTime.ofEpochSecond(OrderedBytes.decodeInt64(buffer), 0, ZoneOffset.UTC);
            case INTERVAL:
                return (T) Duration.ofNanos(OrderedBytes.decodeInt64(buffer));
            case BINARY:
                return (T) OrderedBytes.decodeBlobVar(buffer);
            case ENUM:
                try {
                    String clsName = OrderedBytes.decodeString(buffer);
                    @SuppressWarnings("rawtypes")
                    Class<? extends Enum> cls = (Class<? extends Enum>) Class.forName(clsName);
                    String val = OrderedBytes.decodeString(buffer);
                    return (T) Enum.valueOf(cls, val);
                } catch (ClassNotFoundException cnfe) {
                    throw new RuntimeException("Unexpected error deserializing enum.", cnfe);
                }
            case KRYO_SERIALIZABLE:
                try {
                    byte[] blob = OrderedBytes.decodeBlobVar(buffer);
                    ByteArrayInputStream bin = new ByteArrayInputStream(blob);
                    Input input = new Input(bin);
                    return (T) new Kryo().readClassAndObject(input);
                } catch (KryoException e) {
                    throw new RuntimeException("Unexpected error deserializing object.", e);
                }
            case SERIALIZABLE:
                try {
                    byte[] blob = OrderedBytes.decodeBlobVar(buffer);
                    ByteArrayInputStream bin = new ByteArrayInputStream(blob);
                    ObjectInputStream ois = new ObjectInputStream(bin);
                    return (T) ois.readObject();
                } catch (IOException | ClassNotFoundException e) {
                    throw new RuntimeException("Unexpected error deserializing object.", e);
                }
            default:
                throw new IllegalArgumentException("Unexpected data type: " + type);
        }
    }

    public static <T> T deserializeWithSalt(byte[] target) {
        PositionedByteRange buffer = new SimplePositionedByteRange(target);
        return deserializeWithSalt(buffer);
    }

    @SuppressWarnings("unchecked")
    public static <T> T deserializeWithSalt(PositionedByteRange buffer) {
        buffer.get();  // discard salt
        return deserialize(buffer);
    }

    public static byte[] serialize(Object o) {
        PositionedByteRange buffer = new SimplePositionedMutableByteRange(4096);
        serialize(buffer, o);
        buffer.setLength(buffer.getPosition());
        buffer.setPosition(0);
        byte[] bytes = new byte[buffer.getRemaining()];
        buffer.get(bytes);
        return bytes;
    }

    public static void serialize(PositionedByteRange buffer, Object o) {
        final Order order = Order.ASCENDING;
        if (o == null) {
            OrderedBytes.encodeInt8(buffer, ValueType.NULL.getCode(), order);
        } else if (o instanceof Boolean) {
            OrderedBytes.encodeInt8(buffer, ValueType.BOOLEAN.getCode(), order);
            OrderedBytes.encodeInt8(buffer, (Boolean) o ? (byte) 1 : (byte) 0, order);
        } else if (o instanceof String) {
            OrderedBytes.encodeInt8(buffer, ValueType.STRING.getCode(), order);
            OrderedBytes.encodeString(buffer, (String) o, order);
        } else if (o instanceof Byte) {
            OrderedBytes.encodeInt8(buffer, ValueType.BYTE.getCode(), order);
            OrderedBytes.encodeInt8(buffer, (Byte) o, order);
        } else if (o instanceof Short) {
            OrderedBytes.encodeInt8(buffer, ValueType.SHORT.getCode(), order);
            OrderedBytes.encodeInt16(buffer, (Short) o, order);
        } else if (o instanceof Integer) {
            OrderedBytes.encodeInt8(buffer, ValueType.INT.getCode(), order);
            OrderedBytes.encodeInt32(buffer, (Integer) o, order);
        } else if (o instanceof Long) {
            OrderedBytes.encodeInt8(buffer, ValueType.LONG.getCode(), order);
            OrderedBytes.encodeInt64(buffer, (Long) o, order);
        } else if (o instanceof Float) {
            OrderedBytes.encodeInt8(buffer, ValueType.FLOAT.getCode(), order);
            OrderedBytes.encodeFloat32(buffer, (Float) o, order);
        } else if (o instanceof Double) {
            OrderedBytes.encodeInt8(buffer, ValueType.DOUBLE.getCode(), order);
            OrderedBytes.encodeFloat64(buffer, (Double) o, order);
        } else if (o instanceof BigDecimal) {
            OrderedBytes.encodeInt8(buffer, ValueType.DECIMAL.getCode(), order);
            OrderedBytes.encodeNumeric(buffer, (BigDecimal) o, order);
        } else if (o instanceof LocalDate) {
            OrderedBytes.encodeInt8(buffer, ValueType.DATE.getCode(), order);
            OrderedBytes.encodeInt64(buffer, ((LocalDate) o).toEpochDay(), order);
        } else if (o instanceof LocalTime) {
            OrderedBytes.encodeInt8(buffer, ValueType.TIME.getCode(), order);
            OrderedBytes.encodeInt64(buffer, ((LocalTime) o).toNanoOfDay(), order);
        } else if (o instanceof LocalDateTime) {
            OrderedBytes.encodeInt8(buffer, ValueType.TIMESTAMP.getCode(), order);
            OrderedBytes.encodeInt64(buffer, ((LocalDateTime) o).toEpochSecond(ZoneOffset.UTC), order);
        } else if (o instanceof Duration) {
            OrderedBytes.encodeInt8(buffer, ValueType.INTERVAL.getCode(), order);
            OrderedBytes.encodeInt64(buffer, ((Duration) o).toNanos(), order);
        } else if (o instanceof byte[]) {
            OrderedBytes.encodeInt8(buffer, ValueType.BINARY.getCode(), order);
            OrderedBytes.encodeBlobVar(buffer, (byte[]) o, order);
        } else if (o instanceof Enum) {
            OrderedBytes.encodeInt8(buffer, ValueType.ENUM.getCode(), order);
            OrderedBytes.encodeString(buffer, o.getClass().getName(), order);
            OrderedBytes.encodeString(buffer, o.toString(), order);
        } else if (o instanceof KryoSerializable) {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream(32);
                Output output = new Output(baos);
                new Kryo().writeClassAndObject(output, o);
                output.close();
                OrderedBytes.encodeInt8(buffer, ValueType.KRYO_SERIALIZABLE.getCode(), order);
                OrderedBytes.encodeBlobVar(buffer, baos.toByteArray(), order);
            } catch (KryoException io) {
                throw new RuntimeException("Unexpected error serializing object.", io);
            }
        } else if (o instanceof Serializable) {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream(32);
                ObjectOutputStream oos = new ObjectOutputStream(baos);
                oos.writeObject(o);
                oos.close();
                OrderedBytes.encodeInt8(buffer, ValueType.SERIALIZABLE.getCode(), order);
                OrderedBytes.encodeBlobVar(buffer, baos.toByteArray(), order);
            } catch (IOException io) {
                throw new RuntimeException("Unexpected error serializing object.", io);
            }
        } else {
            throw new IllegalArgumentException("Unexpected data of type : " + o.getClass().getName());
        }
    }

    public static byte[] serializeWithSalt(Object o) {
        PositionedByteRange buffer = new SimplePositionedMutableByteRange(4096);
        serializeWithSalt(buffer, o);
        buffer.setLength(buffer.getPosition());
        buffer.setPosition(0);
        byte[] bytes = new byte[buffer.getRemaining()];
        buffer.get(bytes);
        return bytes;
    }

    public static void serializeWithSalt(PositionedByteRange buffer, Object o) {
        byte[] bytes = serialize(o);
        byte saltingByte = getSaltingByte(bytes);
        buffer.put(saltingByte);
        buffer.put(bytes);
    }

    /**
     * Returns the salt for a given value.
     *
     * @param value    the value
     * @return the salt to prepend to {@code value}
     */
    public static byte getSaltingByte(byte[] value) {
        int hash = calculateHashCode(value);
        return (byte) (Math.abs(hash) % DEFAULT_NUM_BUCKETS);
    }

    private static int calculateHashCode(byte a[]) {
        if (a == null) return 0;
        int result = 1;
        for (int i = 0; i < a.length; i++) {
            result = 31 * result + a[i];
        }
        return result;
    }
}
