package ac.seven.CDN;

import ac.seven.CDN.Lock.Settings;
import ac.seven.CDN.Syncher.ChunkSyncManager;
import ac.seven.CDN.Syncher.IO.Network.Client.NettyClient;
import ac.seven.CDN.Syncher.IO.Network.Server.NettyServer;

import javax.net.ssl.SSLException;
import java.security.cert.CertificateException;

public class Server {


    private static NettyServer server;
    private static NettyClient client;


    public static NettyServer getServer() {
        return server;
    }

    public static NettyClient getClient() {
        return client;
    }

    public void run() {
        new Thread(() -> {
            if(!Settings.TickChunks) {
                server = new NettyServer(Settings.Port);
                try {
                    server.run();
                } catch (InterruptedException | CertificateException | SSLException e) {
                    e.printStackTrace();
                }

            } else {
                client = new NettyClient(Settings.RemoteHost, Settings.Port);
                try {
                    client.run();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            ChunkSyncManager.ChunkSyncTask();
        }).start();

    }
}
