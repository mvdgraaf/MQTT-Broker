package org.mvdgraaf;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.mvdgraaf.exceptions.ExceptionTypes;
import org.mvdgraaf.exceptions.MqttBrokerException;

public class MqttBroker {

    private final MqttSessionManager sessionManager = new MqttSessionManager();
    private static final ChannelGroup allChannels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    public void start(int tcpPort, int wsPort) {
        EventLoopGroup bossGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        EventLoopGroup workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());

        try {
            ServerBootstrap tcpBootstrap = new ServerBootstrap();
            tcpBootstrap.group(bossGroup, workerGroup)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ch.pipeline().addLast(new MqttDecoder(), MqttEncoder.INSTANCE, new MqttHandler(sessionManager));
                            allChannels.add(ch);
                        }
                    });

            ServerBootstrap wsBootstrap = new ServerBootstrap();
            wsBootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ch.pipeline().addLast(new HttpServerCodec());
                            ch.pipeline().addLast(new HttpObjectAggregator(65536));
                            ch.pipeline().addLast(new WebSocketServerProtocolHandler("/mqtt", "mqtt", true));
                            ch.pipeline().addLast(new WebSocketMqtt());
                            ch.pipeline().addLast(new MqttDecoder(), MqttEncoder.INSTANCE, new MqttHandler(sessionManager));
                            allChannels.add(ch);
                        }
                    });

            ChannelFuture tcpFuture = tcpBootstrap.bind(tcpPort).sync();
            ChannelFuture wsFuture = wsBootstrap.bind(wsPort).sync();
            System.out.println("MQTT Broker listening on port: " + tcpPort);
            System.out.println("WebSocket listening on port: " + wsPort);
            tcpFuture.channel().closeFuture().sync();
            wsFuture.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MqttBrokerException("Broker unexpectedly interrupted", ExceptionTypes.SYS_INTERRUPTED, e);
        } catch (Exception e) {
            throw new MqttBrokerException("Could not start broker on port: " + tcpPort, ExceptionTypes.STARTUP_FAILED, e);
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
            System.out.println("Broker resources released");
        }
    }

    public static ChannelGroup getAllChannels() {
        return allChannels;
    }

    static void main(String[] args) {
        int tcpPort = args.length > 0 ? Integer.parseInt(args[0]) : 1883;
        int wsPort = args.length > 1 ? Integer.parseInt(args[1]) : 8083;
        new MqttBroker().start(tcpPort, wsPort);
    }
}
