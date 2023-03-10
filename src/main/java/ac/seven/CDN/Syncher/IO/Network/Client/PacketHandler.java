package ac.seven.CDN.Syncher.IO.Network.Client;

import ac.seven.CDN.Syncher.IO.Network.Processor.PacketProcessor;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.GlobalEventExecutor;

import java.util.HashMap;

public class PacketHandler extends SimpleChannelInboundHandler<Object> {

    private final ChannelGroup channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    public ChannelGroup getChannels() {
        return channels;
    }

    private final String HOST;
    private final int PORT;
    public PacketHandler(String HOST, int PORT) {
        this.HOST = HOST;
        this.PORT = PORT;
    }

    public String getHOST() {
        return this.HOST;
    }

    public int getPORT() {
        return this.PORT;
    }

    @Override
    public void channelActive(final ChannelHandlerContext channelHandlerContext) {
        channelHandlerContext.pipeline().get(SslHandler.class).handshakeFuture().addListener(
                (GenericFutureListener<Future<Channel>>) future -> {
                    channels.add(channelHandlerContext.channel());
                });
    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws InterruptedException {
        cause.printStackTrace();
        ctx.close().sync();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, Object o) throws Exception {
        if(o instanceof HashMap) {
            HashMap<String, Object> map = (HashMap<String, Object>) o;
            PacketProcessor.initilize(getHOST() + ":" + getPORT(), map);
        }
    }
}
