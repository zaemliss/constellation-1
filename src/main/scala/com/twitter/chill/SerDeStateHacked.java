package com.twitter.chill;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.constellation.consensus.SnapshotInfo;

import java.io.InputStream;

public class SerDeStateHacked extends SerDeState{

    protected SerDeStateHacked(Kryo k, Input in, Output out) {
        super(k, in, out);
    }

    public Object readClassAndObject(InputStream is) {
        return kryo.readClassAndObject(new Input(is));
    }

    public SnapshotInfo readObject(InputStream is ) {
        return kryo.readObject(new Input(is), SnapshotInfo.class);
    }
}
