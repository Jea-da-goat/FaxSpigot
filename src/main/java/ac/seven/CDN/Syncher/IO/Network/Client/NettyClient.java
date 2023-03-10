package ac.seven.CDN.Syncher.IO.Network.Client;


import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import java.util.HashMap;

import static java.lang.Thread.sleep;

public class NettyClient {
    private String HOST;// = System.getProperty("host", "127.0.0.1");
    private int PORT;// = Integer.parseInt(System.getProperty("port", "8992"));

    private Channel channel;

    public Channel getConnection() {
        return channel;
    }

    public void send(HashMap<String, Object> stream) {
        channel.writeAndFlush(stream);
    }

    private boolean isMainframe = false;

    public boolean isConnectionToMainframe() {
        return isMainframe;
    }

    public void turnthisconnectionintoamainframeconnection() {
        isMainframe = true;
    }

    public NettyClient(String host, int port) {
        HOST = host;
        PORT = port;
    }

    public void run() throws Exception {

        final SslContext sslCtx = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE).build();

        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new StreamInitializer(sslCtx, HOST, PORT));

            // Start the connection attempt.
            channel = b.connect(HOST, PORT).sync().channel();
        } finally {
            // The connection is closed automatically on shutdown.
            //group.shutdownGracefully();
        }
    }
}
