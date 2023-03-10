package ac.seven.CDN.Syncher.IO;


public class NetworkUtils {

    public static boolean isExistingServer(String ServerName) {
        return NetworkService.containsServer(ServerName);
    }
}
