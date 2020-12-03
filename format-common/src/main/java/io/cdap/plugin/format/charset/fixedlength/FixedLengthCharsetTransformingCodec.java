/*
 * Copyright © 2020 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package io.cdap.plugin.format.charset.fixedlength;

import org.apache.hadoop.conf.Configurable;
import org.apache.hadoop.fs.Seekable;
import org.apache.hadoop.io.compress.CompressionInputStream;
import org.apache.hadoop.io.compress.CompressionOutputStream;
import org.apache.hadoop.io.compress.Compressor;
import org.apache.hadoop.io.compress.Decompressor;
import org.apache.hadoop.io.compress.DefaultCodec;
import org.apache.hadoop.io.compress.DirectDecompressor;
import org.apache.hadoop.io.compress.SplitCompressionInputStream;
import org.apache.hadoop.io.compress.SplittableCompressionCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Codec implementation that returns a decompressor for Fixed Length character encodings.
 */
public class FixedLengthCharsetTransformingCodec extends DefaultCodec
  implements Configurable, SplittableCompressionCodec {
  private static final Logger LOG = LoggerFactory.getLogger(FixedLengthCharsetTransformingCodec.class);

  FixedLengthCharset fixedLengthCharset;

  public FixedLengthCharsetTransformingCodec(FixedLengthCharset fixedLengthCharset) {
    this.fixedLengthCharset = fixedLengthCharset;
  }

  @Override
  public CompressionOutputStream createOutputStream(OutputStream out) throws IOException {
    throw new RuntimeException("Not supported");
  }

  @Override
  public CompressionOutputStream createOutputStream(OutputStream out, Compressor compressor) throws IOException {
    throw new RuntimeException("Not supported");
  }

  @Override
  public Class<? extends Compressor> getCompressorType() {
    throw new RuntimeException("Not supported");
  }

  @Override
  public Compressor createCompressor() {
    throw new RuntimeException("Not supported");
  }

  @Override
  public CompressionInputStream createInputStream(InputStream in) throws IOException {
    return super.createInputStream(in, new FixedLengthCharsetTransformingDecompressor(this.fixedLengthCharset));
  }

  @Override
  public CompressionInputStream createInputStream(InputStream in, Decompressor decompressor) throws IOException {
    return super.createInputStream(in, decompressor);
  }

  @Override
  public Class<? extends Decompressor> getDecompressorType() {
    return FixedLengthCharsetTransformingDecompressor.class;
  }

  @Override
  public DirectDecompressor createDirectDecompressor() {
    throw new RuntimeException("Not supported");
  }

  @Override
  public String getDefaultExtension() {
    return ".*";
  }

  @Override
  public Decompressor createDecompressor() {
    return new FixedLengthCharsetTransformingDecompressor(fixedLengthCharset);
  }

  @Override
  public SplitCompressionInputStream createInputStream(InputStream seekableIn,
                                                       Decompressor decompressor,
                                                       long start,
                                                       long end,
                                                       READ_MODE readMode) throws IOException {
    if (!(seekableIn instanceof Seekable)) {
      throw new IOException("seekableIn must be an instance of " +
                              Seekable.class.getName());
    }

    //Adjust start to align to the next character boundary.
    if (start % fixedLengthCharset.getCharLength() != 0) {
      LOG.info("Adjusted Start from {} to {} by {} bytes",
               start,
               start + fixedLengthCharset.getCharLength() - (start % fixedLengthCharset.getCharLength()),
               fixedLengthCharset.getCharLength() - (start % fixedLengthCharset.getCharLength()));
      start += fixedLengthCharset.getCharLength() - (start % fixedLengthCharset.getCharLength());
    }

    //Adjust end to align to the next character boundary.
    if (end % fixedLengthCharset.getCharLength() != 0) {
      LOG.info("Adjusted End from {} to {} by {} bytes",
               end,
               end + fixedLengthCharset.getCharLength() - (end % fixedLengthCharset.getCharLength()),
               fixedLengthCharset.getCharLength() - (end % fixedLengthCharset.getCharLength()));
      end += fixedLengthCharset.getCharLength() - (end % fixedLengthCharset.getCharLength());
    }

    FixedLengthCharsetTransformingDecompressorStream decompressorStream =
      new FixedLengthCharsetTransformingDecompressorStream(seekableIn, this.fixedLengthCharset, start, end);

    return new TransformingCompressionInputStream(decompressorStream, start, end);
  }

  /**
   * Wrapper for the decompressor stream.
   */
  public static class TransformingCompressionInputStream extends SplitCompressionInputStream {

    FixedLengthCharsetTransformingDecompressorStream decompressorStream;

    public TransformingCompressionInputStream(FixedLengthCharsetTransformingDecompressorStream in, long start, long end)
      throws IOException {
      super(in, start, end);
      this.decompressorStream = in;
    }

    @Override
    public int read() throws IOException {
      return this.decompressorStream.read();
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      return this.decompressorStream.read(b, off, len);
    }

    @Override
    public void resetState() throws IOException {
      this.decompressorStream.reset();
    }

    @Override
    public long getPos() throws IOException {
      return this.decompressorStream.getPos();
    }
  }
}