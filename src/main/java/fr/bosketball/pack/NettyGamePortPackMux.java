package fr.bosketball.pack;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.util.ReferenceCountUtil;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Server;

/**
 * Zero-extra-port HTTP multiplexer on Paper's main Minecraft TCP port.
 *
 * <p>On hosted panels (Pterodactyl, Docker, NAT, firewalled VPS), extra ports like 8765 are blocked
 * by default while the Minecraft game port is always open. By peeking at the first 4 bytes of new
 * TCP connections on Paper's {@code ServerConnectionListener}:
 * <ul>
 *   <li>Normal Minecraft packets (VarInt Handshake / Status ping) immediately bypass and remove this
 *       inspector on byte 0 with zero allocations or latency.</li>
 *   <li>HTTP {@code GET /BosketBall-ResourcePack.zip} and {@code HEAD /BosketBall-ResourcePack.zip}
 *       requests strip Minecraft packet handlers, drain HTTP request headers cleanly until
 *       {@code \r\n\r\n} (preventing TCP RST on Windows), and stream the cached resource pack ZIP
 *       directly over the open game port.</li>
 * </ul>
 */
public final class NettyGamePortPackMux {

    public static final String ACCEPTOR_HANDLER_NAME = "bosketball_http_mux_acceptor";
    public static final String INSPECTOR_HANDLER_NAME = "bosketball_http_mux_inspector";
    private static final int MAX_HTTP_HEADER_BYTES = 8192;

    private final Supplier<byte[]> packBytesSupplier;
    private final Logger logger;
    private final Set<Channel> hookedServerChannels = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final ServerChannelAcceptor acceptor = new ServerChannelAcceptor();
    private volatile int detectedBoundPort = -1;

    public NettyGamePortPackMux(Supplier<byte[]> packBytesSupplier, Logger logger) {
        this.packBytesSupplier = packBytesSupplier;
        this.logger = logger;
    }

    /**
     * Hooks all active Netty server channels in Paper's {@code ServerConnectionListener}.
     * Safe and idempotent to call multiple times (e.g. during {@code onEnable}, tick 1, and reload).
     *
     * @return number of server channels currently hooked
     */
    public int ensureHooked() {
        Server bukkitServer = Bukkit.getServer();
        if (bukkitServer == null) {
            return 0;
        }
        try {
            Method getServerMethod = bukkitServer.getClass().getMethod("getServer");
            Object minecraftServer = getServerMethod.invoke(bukkitServer);
            if (minecraftServer == null) {
                return 0;
            }

            Object connectionListener = findServerConnectionListener(minecraftServer);
            if (connectionListener == null) {
                return 0;
            }

            int newlyHooked = 0;
            for (Field field : getAllFields(connectionListener.getClass())) {
                if (!List.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                field.setAccessible(true);
                Object rawList = field.get(connectionListener);
                if (!(rawList instanceof List<?> list)) {
                    continue;
                }
                synchronized (list) {
                    for (Object elem : list) {
                        if (elem instanceof ChannelFuture cf && cf.channel() != null) {
                            Channel serverCh = cf.channel();
                            updateBoundPort(serverCh.localAddress());
                            if (hookServerChannel(serverCh)) {
                                newlyHooked++;
                            }
                        }
                    }
                }
            }

            if (newlyHooked > 0 && logger != null) {
                int port = detectedBoundPort > 0 ? detectedBoundPort : Bukkit.getPort();
                logger.info("Hooked zero-port HTTP ResourcePack multiplexer onto Minecraft TCP port " + port
                    + " (" + hookedServerChannels.size() + " listener channel(s)).");
            }
            return hookedServerChannels.size();
        } catch (Throwable t) {
            if (logger != null) {
                logger.warning("Could not attach Netty game-port ResourcePack multiplexer: " + t.getMessage());
            }
            return hookedServerChannels.size();
        }
    }

    private void updateBoundPort(SocketAddress addr) {
        if (addr instanceof InetSocketAddress isa && isa.getPort() > 0) {
            this.detectedBoundPort = isa.getPort();
        }
    }

    public int detectedBoundPort() {
        return detectedBoundPort;
    }

    public boolean isHooked() {
        for (Channel ch : hookedServerChannels) {
            if (ch.isOpen()) {
                return true;
            }
        }
        return false;
    }

    private boolean hookServerChannel(Channel serverChannel) {
        if (!hookedServerChannels.add(serverChannel)) {
            return false;
        }
        Runnable install = () -> {
            ChannelPipeline pipe = serverChannel.pipeline();
            if (pipe.get(ACCEPTOR_HANDLER_NAME) == null) {
                pipe.addFirst(ACCEPTOR_HANDLER_NAME, acceptor);
            }
        };
        if (serverChannel.eventLoop() != null) {
            try {
                serverChannel.eventLoop().execute(install);
                return true;
            } catch (Throwable ignored) {
            }
        }
        install.run();
        return true;
    }

    public void unhookAll() {
        for (Channel serverChannel : hookedServerChannels) {
            Runnable remove = () -> {
                try {
                    ChannelPipeline pipe = serverChannel.pipeline();
                    if (pipe.get(ACCEPTOR_HANDLER_NAME) != null) {
                        pipe.remove(ACCEPTOR_HANDLER_NAME);
                    }
                } catch (Throwable ignored) {
                }
            };
            try {
                if (serverChannel.eventLoop() != null && serverChannel.isOpen()) {
                    serverChannel.eventLoop().execute(remove);
                } else {
                    remove.run();
                }
            } catch (Throwable ignored) {
            }
        }
        hookedServerChannels.clear();
    }

    private static Object findServerConnectionListener(Object minecraftServer) {
        // 1. Zero-arg method returning *ServerConnectionListener* / *ServerConnection*
        for (Method m : minecraftServer.getClass().getMethods()) {
            if (m.getParameterCount() == 0) {
                String rt = m.getReturnType().getSimpleName();
                if (rt.contains("ServerConnection") || rt.equals("ConnectionListener")) {
                    try {
                        Object val = m.invoke(minecraftServer);
                        if (val != null) {
                            return val;
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        // 2. Fallback: scan fields on MinecraftServer hierarchy
        for (Field f : getAllFields(minecraftServer.getClass())) {
            String ft = f.getType().getSimpleName();
            if (ft.contains("ServerConnection") || ft.equals("ConnectionListener")) {
                try {
                    f.setAccessible(true);
                    Object val = f.get(minecraftServer);
                    if (val != null) {
                        return val;
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private static List<Field> getAllFields(Class<?> start) {
        java.util.ArrayList<Field> out = new java.util.ArrayList<>();
        for (Class<?> c = start; c != null && c != Object.class; c = c.getSuperclass()) {
            Collections.addAll(out, c.getDeclaredFields());
        }
        return out;
    }

    /**
     * Installs a fresh {@link HttpPeekInspector} onto a child socket pipeline (also directly usable in JUnit tests).
     */
    public void installChildInspector(Channel childChannel) {
        ChannelPipeline pipe = childChannel.pipeline();
        if (pipe.get(INSPECTOR_HANDLER_NAME) == null) {
            pipe.addFirst(INSPECTOR_HANDLER_NAME, new HttpPeekInspector(packBytesSupplier));
        }
    }

    @ChannelHandler.Sharable
    private final class ServerChannelAcceptor extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof Channel child) {
                Runnable attach = () -> installChildInspector(child);
                if (child.eventLoop() != null) {
                    try {
                        child.eventLoop().execute(attach);
                    } catch (Throwable ignored) {
                        attach.run();
                    }
                } else {
                    attach.run();
                }
            }
            super.channelRead(ctx, msg);
        }
    }

    /**
     * Per-connection inspector that checks the first bytes for HTTP {@code GET } or {@code HEAD}.
     */
    public static final class HttpPeekInspector extends ChannelInboundHandlerAdapter {

        private final Supplier<byte[]> packBytesSupplier;
        private ByteArrayOutputStream headerBuffer;
        private boolean httpMode = false;

        public HttpPeekInspector(Supplier<byte[]> packBytesSupplier) {
            this.packBytesSupplier = packBytesSupplier;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (!(msg instanceof ByteBuf buf)) {
                passThroughAndRemove(ctx, msg);
                return;
            }
            int readable = buf.readableBytes();
            if (readable == 0) {
                ctx.fireChannelRead(msg);
                return;
            }

            if (!httpMode) {
                int ridx = buf.readerIndex();
                if (headerBuffer == null) {
                    byte first = buf.getByte(ridx);
                    // Fast path: 99.99% of Minecraft Handshake / Status ping packets do not start with 'G' or 'H'
                    if (first != (byte) 'G' && first != (byte) 'H') {
                        passThroughAndRemove(ctx, msg);
                        return;
                    }
                    if (readable >= 4) {
                        if (!isGetOrHead(buf.getByte(ridx), buf.getByte(ridx + 1), buf.getByte(ridx + 2), buf.getByte(ridx + 3))) {
                            passThroughAndRemove(ctx, msg);
                            return;
                        }
                        enterHttpMode(ctx);
                    } else {
                        headerBuffer = new ByteArrayOutputStream(256);
                        appendReadable(buf, headerBuffer);
                        ReferenceCountUtil.release(msg);
                        return;
                    }
                } else {
                    appendReadable(buf, headerBuffer);
                    ReferenceCountUtil.release(msg);
                    byte[] acc = headerBuffer.toByteArray();
                    if (acc.length < 4) {
                        return;
                    }
                    if (!isGetOrHead(acc[0], acc[1], acc[2], acc[3])) {
                        headerBuffer = null;
                        passThroughAndRemove(ctx, Unpooled.wrappedBuffer(acc));
                        return;
                    }
                    enterHttpMode(ctx);
                    if (tryRespondIfHeadersComplete(ctx, acc)) {
                        return;
                    }
                    return;
                }
            }

            // In confirmed HTTP mode: accumulate request headers until \r\n\r\n
            if (headerBuffer == null) {
                headerBuffer = new ByteArrayOutputStream(512);
            }
            appendReadable(buf, headerBuffer);
            ReferenceCountUtil.release(msg);

            if (headerBuffer.size() > MAX_HTTP_HEADER_BYTES) {
                sendSimpleErrorAndClose(ctx, "400 Bad Request");
                return;
            }
            tryRespondIfHeadersComplete(ctx, headerBuffer.toByteArray());
        }

        private static boolean isGetOrHead(byte b0, byte b1, byte b2, byte b3) {
            return (b0 == 'G' && b1 == 'E' && b2 == 'T' && b3 == ' ')
                || (b0 == 'H' && b1 == 'E' && b2 == 'A' && b3 == 'D');
        }

        private static void appendReadable(ByteBuf src, ByteArrayOutputStream dst) {
            int len = src.readableBytes();
            byte[] tmp = new byte[len];
            src.readBytes(tmp, 0, len);
            dst.write(tmp, 0, len);
        }

        private void passThroughAndRemove(ChannelHandlerContext ctx, Object msg) {
            try {
                ctx.pipeline().remove(this);
            } catch (Throwable ignored) {
            }
            ctx.fireChannelRead(msg);
        }

        private void enterHttpMode(ChannelHandlerContext ctx) {
            this.httpMode = true;
            // Remove all downstream Minecraft packet handlers so Paper never logs malformed VarInt packets
            ChannelPipeline pipe = ctx.pipeline();
            for (String name : List.copyOf(pipe.names())) {
                if (!INSPECTOR_HANDLER_NAME.equals(name) && pipe.get(name) != this && !name.startsWith("DefaultChannelPipeline")) {
                    try {
                        pipe.remove(name);
                    } catch (Throwable ignored) {
                    }
                }
            }
        }

        private boolean tryRespondIfHeadersComplete(ChannelHandlerContext ctx, byte[] data) {
            int headerEnd = findHeaderTerminator(data);
            if (headerEnd < 0) {
                return false;
            }
            String reqHead = new String(data, 0, headerEnd, StandardCharsets.US_ASCII);
            int lineEnd = reqHead.indexOf('\n');
            String requestLine = (lineEnd >= 0 ? reqHead.substring(0, lineEnd) : reqHead).trim();
            String[] parts = requestLine.split("\\s+");
            if (parts.length < 2) {
                sendSimpleErrorAndClose(ctx, "400 Bad Request");
                return true;
            }
            String method = parts[0];
            String rawTarget = parts[1];
            int qIdx = rawTarget.indexOf('?');
            String path = qIdx >= 0 ? rawTarget.substring(0, qIdx) : rawTarget;

            boolean validPackPath = "/BosketBall-ResourcePack.zip".equalsIgnoreCase(path)
                || "/pack.zip".equalsIgnoreCase(path);
            byte[] packBytes = packBytesSupplier != null ? packBytesSupplier.get() : null;
            if (!validPackPath || packBytes == null || packBytes.length == 0) {
                sendSimpleErrorAndClose(ctx, "404 Not Found");
                return true;
            }

            String headers = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: application/zip\r\n"
                + "Content-Length: " + packBytes.length + "\r\n"
                + "Accept-Ranges: none\r\n"
                + "Cache-Control: public, max-age=86400\r\n"
                + "Connection: close\r\n"
                + "\r\n";
            byte[] headerBytes = headers.getBytes(StandardCharsets.US_ASCII);

            if ("HEAD".equalsIgnoreCase(method)) {
                ctx.channel().writeAndFlush(Unpooled.wrappedBuffer(headerBytes))
                    .addListener(ChannelFutureListener.CLOSE);
            } else {
                ctx.channel().writeAndFlush(Unpooled.wrappedBuffer(headerBytes, packBytes))
                    .addListener(ChannelFutureListener.CLOSE);
            }
            return true;
        }

        private static int findHeaderTerminator(byte[] b) {
            for (int i = 0; i <= b.length - 4; i++) {
                if (b[i] == '\r' && b[i + 1] == '\n' && b[i + 2] == '\r' && b[i + 3] == '\n') {
                    return i;
                }
            }
            for (int i = 0; i <= b.length - 2; i++) {
                if (b[i] == '\n' && b[i + 1] == '\n') {
                    return i;
                }
            }
            return -1;
        }

        private static void sendSimpleErrorAndClose(ChannelHandlerContext ctx, String status) {
            byte[] body = status.getBytes(StandardCharsets.US_ASCII);
            String resp = "HTTP/1.1 " + status + "\r\n"
                + "Content-Type: text/plain; charset=us-ascii\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Connection: close\r\n"
                + "\r\n"
                + status;
            ctx.writeAndFlush(Unpooled.copiedBuffer(resp, StandardCharsets.US_ASCII))
                .addListener(ChannelFutureListener.CLOSE);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ctx.close();
        }
    }
}
