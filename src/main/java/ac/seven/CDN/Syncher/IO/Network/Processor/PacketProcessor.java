package ac.seven.CDN.Syncher.IO.Network.Processor;

import ac.seven.CDN.Syncher.IO.Network.Processor.Mainframe.Mainframe;
import ac.seven.CDN.Syncher.IO.Network.Processor.Node.Node;
import ac.seven.CDN.Syncher.IO.NetworkService;

import java.util.HashMap;

public class PacketProcessor {

    public static void initilize(String ServerName, HashMap<String, Object> data) {
        if(NetworkService.isMainframe(ServerName)) {
            Mainframe.read(data);
        } else {
            Node.read(data);
        }
    }
}
