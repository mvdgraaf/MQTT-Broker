package org.mvdgraaf;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.*;
import org.mvdgraaf.exceptions.ExceptionTypes;
import org.mvdgraaf.exceptions.MqttBrokerException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MqttHandler extends io.netty.channel.SimpleChannelInboundHandler<MqttMessage>{

    private final MqttSessionManager sessionManager;

    public MqttHandler(MqttSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, MqttMessage msg) throws IOException {
        if (msg.decoderResult().isFailure()) {
            throw new MqttBrokerException("Received invalid MQTT packet", ExceptionTypes.PROTOCOL_ERROR, msg.decoderResult().cause());
        }

        MqttFixedHeader header = msg.fixedHeader();
        System.out.println("Bericht ontvangen van type: " + header.messageType());

        switch (header.messageType()) {
            case CONNECT:
                handleConnect(ctx, (MqttConnectMessage) msg);
                break;
            case PUBLISH:
                handlePublish(ctx, (MqttPublishMessage) msg);
                break;
            case PUBREC:
                handlePubRec(ctx, msg);
                break;
            case PUBCOMP:
                handlePubComp(ctx, msg);
                break;
            case SUBSCRIBE:
                handleSubscribe(ctx, (MqttSubscribeMessage) msg);
                break;
            case UNSUBSCRIBE:
                handleUnSubscribe(ctx, (MqttUnsubscribeMessage) msg);
                break;
            case PINGREQ:
                handlePing(ctx);
                break;
            case DISCONNECT:
                ctx.close();
                break;

            default:
                System.out.println("Unsupported packet: " + header.messageType());
        }
    }

    private void handleConnect(ChannelHandlerContext ctx, MqttConnectMessage msg) {
        String clientId = msg.payload().clientIdentifier();
        sessionManager.addClient(clientId, ctx.channel());
        MqttConnAckMessage connAckMessage = MqttMessageBuilders.connAck()
                .returnCode(MqttConnectReturnCode.CONNECTION_ACCEPTED)
                .build();
        ctx.writeAndFlush(connAckMessage);
        System.out.println("Client verbonden: " + clientId);
    }

    private void handlePublish(ChannelHandlerContext ctx, MqttPublishMessage msg) {
        String topic = msg.variableHeader().topicName();
        System.out.println("PUBLISH ontvangen op topic: " + topic);
        sessionManager.broadcast(topic, msg.retain());
    }

    private void handlePubRec(ChannelHandlerContext ctx, MqttMessage msg) {
        int messageId = ((MqttMessageIdVariableHeader) msg.variableHeader()).messageId();
        MqttFixedHeader fixedHeader = new MqttFixedHeader(
                MqttMessageType.PUBREL,
                false,
                MqttQoS.AT_MOST_ONCE,
                false,
                0
        );
        MqttMessageIdVariableHeader variableHeader = MqttMessageIdVariableHeader.from(messageId);
        MqttMessage pubRelMessage = new MqttMessage(fixedHeader, variableHeader);
        ctx.writeAndFlush(pubRelMessage);
    }

    private void handlePubComp(ChannelHandlerContext ctx, MqttMessage msg) {
        // Voor QoS 2, na PUBCOMP is de cyclus compleet. Hier kunnen we eventueel nog cleanup doen.
        int messageId = ((MqttMessageIdVariableHeader) msg.variableHeader()).messageId();
        System.out.println("PUBCOMP ontvangen voor messageId: " + messageId);
    }

    private void handleSubscribe(ChannelHandlerContext ctx, MqttSubscribeMessage msg) {
        int messageId = msg.variableHeader().messageId();

        List<MqttTopicSubscription> subscriptions = msg.payload().topicSubscriptions();
        List<Integer> grantedQosLevels = new ArrayList<>();

        for (MqttTopicSubscription sub : subscriptions) {
            String topic = sub.topicFilter();
            MqttQoS qos = sub.qualityOfService();

            sessionManager.subscribe(topic, ctx.channel());
            grantedQosLevels.add(qos.value());
            System.out.println("Sessie: Channel " + ctx.channel().id() + " geabonneerd op " + topic);
        }

        MqttFixedHeader fixedHeader = new MqttFixedHeader(
                MqttMessageType.SUBACK,
                false,
                MqttQoS.AT_MOST_ONCE,
                false,
                0
        );

        MqttMessageIdVariableHeader variableHeader = MqttMessageIdVariableHeader.from(messageId);

        MqttSubAckPayload payload = new MqttSubAckPayload(grantedQosLevels);
        MqttSubAckMessage subAckMessage = new MqttSubAckMessage(fixedHeader, variableHeader, payload);
        ctx.writeAndFlush(subAckMessage);
    }

    private void handleUnSubscribe(ChannelHandlerContext ctx, MqttUnsubscribeMessage msg) {
        int messageId = msg.variableHeader().messageId();
        List<String> topics = msg.payload().topics();

        for (String topic : topics) {
            sessionManager.unsubscribe(topic, ctx.channel());
        }

        MqttFixedHeader fixedHeader = new MqttFixedHeader(
                MqttMessageType.UNSUBACK,
                false,
                MqttQoS.AT_MOST_ONCE,
                false,
                0
        );

        MqttMessageIdVariableHeader variableHeader = MqttMessageIdVariableHeader.from(messageId);
        MqttUnsubAckMessage unsubAckMessage = new MqttUnsubAckMessage(fixedHeader, variableHeader);
        ctx.writeAndFlush(unsubAckMessage);
    }

    private void handlePing(ChannelHandlerContext ctx) {
        MqttMessage pingResp = new MqttMessage(
                new MqttFixedHeader(
                        MqttMessageType.PINGRESP,
                        false,
                        MqttQoS.AT_MOST_ONCE,
                        false,
                        0
                ));
        ctx.writeAndFlush(pingResp);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();
        System.out.println("Client disconnected: " + ctx.channel().id());
        sessionManager.removeChannel(channel);
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof MqttBrokerException) {
            System.err.println("Broker fout: " + ((MqttBrokerException) cause).getErrorCode());
        } else {
            System.err.println("Network error: " + cause.getMessage());
        }
        ctx.close();
    }
}
