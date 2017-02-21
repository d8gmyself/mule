/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.streaming.bytes;

import static org.mule.runtime.api.util.Preconditions.checkState;
import org.mule.runtime.api.streaming.CursorStream;
import org.mule.runtime.core.api.Event;

import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AbstractCursorStreamProviderAdapter implements CursorStreamProviderAdapter {

  protected final InputStream wrappedStream;

  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final Event creatorEvent;
  private final ByteBufferManager bufferManager;

  /**
   * Creates a new instance
   *
   * @param wrappedStream the original stream to be decorated
   * @param bufferManager the {@link ByteBufferManager} that will be used to allocate all buffers
   * @param event         the {@link Event} in which streaming is taking place
   */
  public AbstractCursorStreamProviderAdapter(InputStream wrappedStream, ByteBufferManager bufferManager, Event event) {
    this.wrappedStream = wrappedStream;
    this.bufferManager = bufferManager;
    this.creatorEvent = event;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public final CursorStream openCursor() {
    checkState(!closed.get(), "Cannot open a new cursor on a closed stream");
    return doOpenCursor();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void close() {
    closed.set(true);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isClosed() {
    return closed.get();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Event getCreatorEvent() {
    return creatorEvent;
  }

  /**
   * @return the {@link ByteBufferManager} that <b>MUST</b> to be used to allocate byte buffers
   */
  protected ByteBufferManager getBufferManager() {
    return bufferManager;
  }

  protected abstract CursorStream doOpenCursor();
}
