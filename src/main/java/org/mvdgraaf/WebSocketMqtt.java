package org.mvdgraaf;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;

import java.util.List;

public class WebSocketMqtt extends MessageToMessageCodec<BinaryWebSocketFrame, ByteBuf> {
    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuffer, List<Object> list) {
        list.add(new BinaryWebSocketFrame(byteBuffer.retain()));
    }

    @Override
    protected void decode(ChannelHandlerContext channelHandlerContext, BinaryWebSocketFrame binaryWebSocketFrame, List<Object> list) {
        list.add(binaryWebSocketFrame.content().retain());
    }
}
