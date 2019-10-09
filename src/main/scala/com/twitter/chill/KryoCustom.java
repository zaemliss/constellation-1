package com.twitter.chill;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.constellation.consensus.Snapshot;
import org.constellation.consensus.SnapshotInfo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public class KryoCustom {


    private static final ThreadLocal<Kryo> kryoThreadLocal
            = new ThreadLocal<Kryo>() {

        @Override
        protected Kryo initialValue() {
            Kryo kryo = new Kryo();
            kryo.register(ProductEntity.class);
            kryo.register(SnapshotInfo.class);
            kryo.register(Snapshot.class);
            return kryo;
        }
    };

    public byte[] write(Object productEntity)
            throws IOException {
        Kryo kryo = kryoThreadLocal.get();

        ByteArrayOutputStream byteArrayOutputStream =
                new ByteArrayOutputStream(16384);
        DeflaterOutputStream deflaterOutputStream =
                new DeflaterOutputStream(byteArrayOutputStream);
        Output output = new Output(deflaterOutputStream);
        kryo.writeObject(output, productEntity);
        output.close();

        return byteArrayOutputStream.toByteArray();
    }

    public <T> T read(byte[] inputBytes,Class<T> cls)
            throws IOException {
        InputStream in = new ByteArrayInputStream(inputBytes);
        in = new InflaterInputStream(in);
        Input input = new Input(in);
        Kryo kryo = kryoThreadLocal.get();
        return kryo.readObject(input, cls);

    }


}