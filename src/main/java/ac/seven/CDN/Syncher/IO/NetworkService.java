package ac.seven.CDN.Syncher.IO;

import ac.seven.CDN.Lock.LockUtils;
import ac.seven.CDN.Server;
import ac.seven.CDN.Syncher.IO.Network.Client.NettyClient;

import java.util.HashMap;
import java.util.List;

public class NetworkService {

    private static HashMap<String, NettyClient> connections = new HashMap<>();

    private static LockUtils<String> serverlock = new LockUtils<>();

    public static LockUtils<String> NetworkServerLock() {
        return serverlock;
    }

    private static String thisHost = "0.0.0.0";
    private static int thisPort = 9999;

    private static NettyClient mainframe;

    private static String mainHost;
    private static int mainPort;

    public static void Load() throws Exception {
        mainHost = "db.itndev.com";
        mainPort = 27003;

        //first connect to the mainframe
        mainframe = new NettyClient(mainHost, mainPort);
        mainframe.run();
        mainframe.turnthisconnectionintoamainframeconnection();
        HashMap<String, Object> map = new HashMap<>();
        map.put("ServerName", String.valueOf(thisHost + ":" + thisPort));
        mainframe.send(map);
    }

    public static boolean isMainframe(String ServerName) {
        return ServerName.equals(mainHost + ":" + mainPort);
    }



    public static NettyClient getMainframe() {
        return mainframe;
    }

    public static boolean containsServer(String ServerName) {
        return connections.containsKey(ServerName);
    }

    public static void addConnectedServers(String Name, NettyClient client) {
        connections.put(Name, client);
    }

    public static void send(String ServerName, Object Data) {
        NetworkServerLock().tryOptainLock(ServerName);
        connections.get(ServerName).getConnection().write(Data);
        NetworkServerLock().releaseLock(ServerName);
    }

    public static void flush(String ServerName) {
        NetworkServerLock().tryOptainLock(ServerName);
        connections.get(ServerName).getConnection().flush();
        NetworkServerLock().releaseLock(ServerName);
    }

    public static void ListToConnect(List<String> servers) {
        servers.forEach(server -> {
            new Thread(() -> {
                boolean hasconnected = false;
                int c = 0;
                while(!hasconnected && c < 4) {
                    c++;
                    try {
                        String[] p = server.split(":");
                        NettyClient client = new NettyClient(p[0], Integer.parseInt(p[1]));
                        client.run();
                        hasconnected = true;
                        addConnectedServers(server, client);
                    } catch (Exception e) {
                        System.out.println("FAILED TO CONNECT TO SERVER : " + server);
                        System.out.println("MAX TRY TO RECONNECT IS 4 TIMES");
                        throw new RuntimeException(e);
                    }
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }).start();
        });
    }

}
