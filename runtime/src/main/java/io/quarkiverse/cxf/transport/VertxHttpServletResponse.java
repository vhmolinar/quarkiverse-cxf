package io.quarkiverse.cxf.transport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Locale;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.undertow.httpcore.HttpHeaderNames;
import io.undertow.vertx.VertxBufferImpl;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;

public class VertxHttpServletResponse implements HttpServletResponse {
    protected final RoutingContext context;
    private final HttpServerRequest request;
    protected final HttpServerResponse response;
    private VertxServletOutputStream os;

    public VertxHttpServletResponse(RoutingContext context) {
        this.request = context.request();
        this.response = context.response();
        this.context = context;
        this.os = new VertxServletOutputStream();
    }

    @Override
    public void addCookie(Cookie cookie) {

    }

    @Override
    public boolean containsHeader(String name) {
        return response.headers().contains(name);
    }

    @Override
    public String encodeURL(String url) {
        return null;
    }

    @Override
    public String encodeRedirectURL(String url) {
        return null;
    }

    @Override
    public String encodeUrl(String url) {
        return null;
    }

    @Override
    public String encodeRedirectUrl(String url) {
        return null;
    }

    @Override
    public void sendError(int sc, String msg) throws IOException {

    }

    @Override
    public void sendError(int sc) throws IOException {

    }

    @Override
    public void sendRedirect(String location) throws IOException {

    }

    @Override
    public void setDateHeader(String name, long date) {

    }

    @Override
    public void addDateHeader(String name, long date) {

    }

    @Override
    public void setHeader(String name, String value) {
        response.headers().set(name, value);
    }

    @Override
    public void addHeader(String name, String value) {
        response.headers().add(name, value);
    }

    @Override
    public void setIntHeader(String name, int value) {
        response.headers().set(name, Integer.toBinaryString(value));
    }

    @Override
    public void addIntHeader(String name, int value) {
        response.headers().add(name, Integer.toBinaryString(value));
    }

    @Override
    public void setStatus(int sc) {
        response.setStatusCode(sc);
    }

    @Override
    public void setStatus(int sc, String sm) {
        response.setStatusCode(sc);
        response.setStatusMessage(sm);
    }

    @Override
    public int getStatus() {
        return response.getStatusCode();
    }

    @Override
    public String getHeader(String name) {
        return response.headers().get(name);
    }

    @Override
    public Collection<String> getHeaders(String name) {
        return response.headers().getAll(name);
    }

    @Override
    public Collection<String> getHeaderNames() {
        return response.headers().names();
    }

    @Override
    public String getCharacterEncoding() {
        return null;
    }

    @Override
    public String getContentType() {
        return response.headers().get("Content-Type");
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        return os;
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        return null;
    }

    @Override
    public void setCharacterEncoding(String charset) {

    }

    @Override
    public void setContentLength(int len) {
        response.headers().set("Content-Length", Integer.toString(len));
    }

    @Override
    public void setContentLengthLong(long len) {
        response.headers().set("Content-Length", Long.toString(len));
    }

    @Override
    public void setContentType(String type) {
        response.headers().set("Content-Type", type);
    }

    @Override
    public void setBufferSize(int size) {
        os.setBufferSize(size);
    }

    @Override
    public int getBufferSize() {
        return os.getBufferSize();
    }

    @Override
    public void flushBuffer() throws IOException {
        os.flush();
    }

    @Override
    public void resetBuffer() {
        os = new VertxServletOutputStream();
    }

    @Override
    public boolean isCommitted() {
        return false;
    }

    @Override
    public void reset() {

    }

    @Override
    public void setLocale(Locale loc) {

    }

    @Override
    public Locale getLocale() {
        return null;
    }

    private class VertxServletOutputStream extends ServletOutputStream {
        private ByteBuf pooledBuffer;
        private long written;
        private boolean committed;

        private boolean closed;
        private boolean finished;
        protected boolean waitingForDrain;
        protected boolean drainHandlerRegistered;
        protected boolean first = true;
        protected Throwable throwable;
        private ByteArrayOutputStream overflow;
        private int bufferSize = 256;

        public boolean isCommitted() {
            return committed;
        }

        public void setBufferSize(int size) {
            bufferSize = size;
        }

        public int getBufferSize() {
            return bufferSize;
        }

        @Override
        public void write(int b) throws IOException {
            byte[] data = new byte[1];
            data[0] = (byte) b;
            write(data, 0, 1);
        }

        @Override
        public void write(byte[] data) throws IOException {
            write(data, 0, data.length);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            if (len < 1) {
                return;
            }
            if (closed) {
                throw new IOException("Stream is closed");
            }

            int rem = len;
            int idx = off;
            ByteBuf buffer = pooledBuffer;
            try {
                if (buffer == null) {
                    pooledBuffer = buffer = PooledByteBufAllocator.DEFAULT.directBuffer();
                }
                while (rem > 0) {
                    int toWrite = Math.min(rem, buffer.writableBytes());
                    buffer.writeBytes(b, idx, toWrite);
                    rem -= toWrite;
                    idx += toWrite;
                    if (!buffer.isWritable()) {
                        ByteBuf tmpBuf = buffer;
                        this.pooledBuffer = buffer = PooledByteBufAllocator.DEFAULT.directBuffer(bufferSize);
                        writeBlocking(tmpBuf, false);
                    }
                }
            } catch (Exception e) {
                if (buffer != null && buffer.refCnt() > 0) {
                    buffer.release();
                }
                throw new IOException(e);
            }
            written += len;
        }

        public void writeBlocking(ByteBuf buffer, boolean finished) throws IOException {
            prepareWrite(buffer, finished);
            write(buffer, finished);
        }

        private void prepareWrite(ByteBuf buffer, boolean finished) throws IOException {
            if (!committed) {
                committed = true;
                if (finished) {
                    if (buffer == null) {
                        response.putHeader(HttpHeaderNames.CONTENT_LENGTH, "0");
                    } else {
                        response.putHeader(HttpHeaderNames.CONTENT_LENGTH, "" + buffer.readableBytes());
                    }
                } else if (!response.headers().contains(HttpHeaderNames.CONTENT_LENGTH)) {
                    request.response().setChunked(true);
                }
            }
            if (finished) {
                this.finished = true;
            }
        }

        public void write(ByteBuf data, boolean last) throws IOException {
            if (last && data == null) {
                request.response().end();
                return;
            }
            //do all this in the same lock
            synchronized (request.connection()) {
                try {
                    boolean bufferRequired = awaitWriteable() || (overflow != null && overflow.size() > 0);
                    if (bufferRequired) {
                        //just buffer everything
                        registerDrainHandler();
                        if (overflow == null) {
                            overflow = new ByteArrayOutputStream();
                        }
                        overflow.write(data.array(), data.arrayOffset() + data.readerIndex(),
                                data.arrayOffset() + data.writerIndex());
                        if (last) {
                            closed = true;
                        }
                    } else {
                        if (last) {
                            request.response().end(createBuffer(data));
                        } else {
                            request.response().write(createBuffer(data));
                        }
                    }
                } catch (Exception e) {
                    if (data != null && data.refCnt() > 0) {
                        data.release();
                    }
                    throw new IOException("Failed to write", e);
                }
            }
        }

        public void close() throws IOException {

            super.close();
        }

        Buffer createBuffer(ByteBuf data) {
            return new VertxBufferImpl(data);
        }

        private boolean awaitWriteable() throws IOException {
            if (Context.isOnEventLoopThread()) {
                return request.response().writeQueueFull();
            }
            if (first) {
                first = false;
                return false;
            }
            assert Thread.holdsLock(request.connection());
            while (request.response().writeQueueFull()) {
                if (throwable != null) {
                    throw new IOException(throwable);
                }
                if (request.response().closed()) {
                    throw new IOException("Connection has been closed");
                }
                registerDrainHandler();
                try {
                    waitingForDrain = true;
                    request.connection().wait();
                } catch (InterruptedException e) {
                    throw new InterruptedIOException(e.getMessage());
                } finally {
                    waitingForDrain = false;
                }
            }
            return false;
        }

        private void registerDrainHandler() {
            if (!drainHandlerRegistered) {
                drainHandlerRegistered = true;
                Handler<Void> handler = new Handler<Void>() {
                    @Override
                    public void handle(Void event) {
                        HttpConnection connection = request.connection();
                        synchronized (connection) {
                            if (waitingForDrain) {
                                connection.notifyAll();
                            }
                            if (overflow != null) {
                                if (overflow.size() > 0) {
                                    if (closed) {
                                        request.response().end(Buffer.buffer(overflow.toByteArray()));
                                    } else {
                                        request.response().write(Buffer.buffer(overflow.toByteArray()));
                                    }
                                    overflow.reset();
                                }
                            }
                        }
                    }
                };
                request.response().drainHandler(handler);
                request.response().closeHandler(handler);
            }
        }

        @Override
        public boolean isReady() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
            throw new UnsupportedOperationException();
        }
    }
}
