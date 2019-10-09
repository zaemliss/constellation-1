/*
Copyright 2013 Twitter, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.twitter.chill;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.InputChunked;
import com.esotericsoftware.kryo.io.Output;
import org.constellation.consensus.SnapshotInfo;

import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.*;

/** Simple ResourcePool to save on Kryo instances, which
 * are expensive to allocate
 */
abstract public class KryoPoolHacked extends ResourcePool<SerDeStateHacked> {

  protected KryoPoolHacked(int poolSize) { super(poolSize); }

  @Override
  public void release(SerDeStateHacked st) {
    st.clear();
    super.release(st);
  }
  /** Output is created with new Output(outBufferMin, outBufferMax);
   */
  public static KryoPoolHacked withBuffer(int poolSize,
      final KryoInstantiator ki,
      final int outBufferMin,
      final int outBufferMax) {
    return new KryoPoolHacked(poolSize) {
      protected SerDeStateHacked newInstance() {
        return new SerDeStateHacked(ki.newKryo(), new Input(), new Output(new DeflaterOutputStream(new ByteArrayOutputStream(),new Deflater(4, true))));
      }
    };
  }

  /** Output is created with new Output(new ByteArrayOutputStream())
   * This will automatically resize internally
   */
  public static KryoPoolHacked withByteArrayOutputStream(int poolSize,
      final KryoInstantiator ki) {
    return new KryoPoolHacked(poolSize) {
      protected SerDeStateHacked newInstance() {
        return new SerDeStateHacked(ki.newKryo(), new Input(), new Output(new DeflaterOutputStream(new ByteArrayOutputStream(),new Deflater(4, true)))) {
          /*
           * We have to take extra care of the ByteArrayOutputStream
           */
          @Override
          public void clear() {
            super.clear();
            ByteArrayOutputStream byteStream = (ByteArrayOutputStream)output.getOutputStream();
            byteStream.reset();
          }
          @Override
          public byte[] outputToBytes() {
            output.flush();
            ByteArrayOutputStream byteStream = (ByteArrayOutputStream)output.getOutputStream();
            return byteStream.toByteArray();
          }
          @Override
          public void writeOutputTo(OutputStream os) throws IOException {
            output.flush();
            ByteArrayOutputStream byteStream = (ByteArrayOutputStream)output.getOutputStream();
            byteStream.writeTo(os);
          }
        };
      }
    };
  }

  public <T> T deepCopy(T obj) {
    return (T)fromBytes(toBytesWithoutClass(obj), obj.getClass());
  }

  public Object fromBytes(byte[] ary) {
    SerDeStateHacked serde = borrow();
    System.out.println("wkoszycki1");
    try {
      return serde.readClassAndObject(new InflaterInputStream(new ByteArrayInputStream(ary)));
    }
    finally {
      release(serde);
    }
  }
  public SnapshotInfo fromBytes2(byte[] ary) {
    SerDeStateHacked serde = borrow();
    System.out.println("wkoszycki3");
    try {
      return serde.readObject(new InflaterInputStream(new ByteArrayInputStream(ary)));
    }
    finally {
      release(serde);
    }
  }

  public <T> T fromBytes(byte[] ary, Class<T> cls) {
    SerDeStateHacked serde = borrow();
    System.out.println("wkoszycki2");
    try {
      serde.setInput(new InflaterInputStream(new ByteArrayInputStream(ary), new Inflater(true)));
      return serde.readObject(cls);
    }
    finally {
      release(serde);
    }
  }


  public byte[] toBytesWithClass(Object obj) {
    SerDeStateHacked serde = borrow();
    try {
      serde.writeClassAndObject(obj);
      return serde.outputToBytes();
    }
    finally {
      release(serde);
    }
  }

  public byte[] toBytesWithoutClass(Object obj) {
    SerDeStateHacked serde = borrow();
    try {
      serde.writeObject(obj);
      return serde.outputToBytes();
    }
    finally {
      release(serde);
    }
  }

  public boolean hasRegistration(Class obj) {
    SerDeStateHacked serde = borrow();
    try {
      return serde.hasRegistration(obj);
    }
    finally {
      release(serde);
    }
  }
}
