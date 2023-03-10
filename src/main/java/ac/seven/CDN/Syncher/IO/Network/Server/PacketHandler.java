package ac.seven.CDN.Syncher.IO.Network.Server;

import ac.seven.CDN.Lock.LockUtils;
import ac.seven.CDN.Syncher.IO.Network.Processor.Node.Node;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.GlobalEventExecutor;

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.HashMap;

public class PacketHandler extends SimpleChannelInboundHandler<Object> {

    private static LockUtils<String> channelLock = new LockUtils<>();

    public static LockUtils<String> getChannelLock() {
        return channelLock;
    }
    private static final ChannelGroup channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    public static ChannelGroup getChannels() {
        return channels;
    }

    public static HashMap<Channel, String> Channel2Name = new HashMap<>();
    public static HashMap<String, Channel> Name2Channel = new HashMap<>();

    private static String toIP(String address) {
        return address.split("/")[1].split(":")[0];
    }

    private static ArrayList<String> Whitelist = new ArrayList<>();

    private static Boolean isWhitelisted(SocketAddress ip) {
        String onlyIP = toIP(ip.toString());
        return Whitelist.contains(onlyIP);
    }

    @Override
    public void channelActive(final ChannelHandlerContext channelHandlerContext) {
        try {
            channelHandlerContext.pipeline().get(SslHandler.class).handshakeFuture().addListener(
                    (GenericFutureListener<Future<Channel>>) future -> {
                        if(channelHandlerContext.channel().remoteAddress().toString() == null) {
                            channelHandlerContext.close();
                        } else {
                            channels.add(channelHandlerContext.channel());
                        }

                    /*ctx.writeAndFlush(
                            "Welcome to " + InetAddress.getLocalHost().getHostName() + " secure chat service!\n");
                    ctx.writeAndFlush(
                            "Your session is protected by " +
                                    ctx.pipeline().get(SslHandler.class).engine().getSession().getCipherSuite() +
                                    " cipher suite.\n");*/

                    });
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, Object o) throws Exception {
        if(o instanceof HashMap) {
            HashMap<String, Object> map = (HashMap<String, Object>) o;
            Node.read(map);
            if(false) {
                Channel channel = channelHandlerContext.channel();
                //map = (HashMap<String, Object>) o;
                if (Channel2Name.containsKey(channel)) {
                    // read packet

                } else {
                    String name = (String) map.get("ServerName");
                    getChannelLock().tryOptainLock(name);
                    Channel2Name.put(channel, name);
                    Name2Channel.put(name, channel);
                    getChannelLock().releaseLock(name);
                }
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws InterruptedException {
        Channel channel = ctx.channel();
        if(Channel2Name.containsKey(channel)) {
            String Name = Channel2Name.get(channel);
            getChannelLock().tryOptainLock(Name);
            Channel2Name.remove(channel);
            Name2Channel.remove(Name);
            getChannelLock().releaseLock(Name);
        }
        cause.printStackTrace();
        ctx.close().sync();
    }
}
