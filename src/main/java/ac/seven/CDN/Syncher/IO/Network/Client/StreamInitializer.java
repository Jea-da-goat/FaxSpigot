package ac.seven.CDN.Syncher.IO.Network.Client;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;
import io.netty.handler.ssl.SslContext;

public class StreamInitializer extends ChannelInitializer<SocketChannel> {
    private final SslContext sslCtx;

    private final String HOST;// = System.getProperty("host", "127.0.0.1");
    private final int PORT;

    public StreamInitializer(SslContext sslCtx, String HOST, int PORT) {
        this.sslCtx = sslCtx;
        this.HOST = HOST;
        this.PORT = PORT;
    }

    @Override
    public void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast(sslCtx.newHandler(ch.alloc(), this.HOST, this.PORT));
        pipeline.addLast(new ObjectEncoder());
        pipeline.addLast(new ObjectDecoder(ClassResolvers.cacheDisabled(null)));

        // and then business logic.
        pipeline.addLast(new PacketHandler(this.HOST, this.PORT));
    }
}
