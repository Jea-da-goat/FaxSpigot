package ac.seven.CDN.Plugin;

import org.bukkit.plugin.PluginLoader;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public class SevenInjection extends JavaPlugin{

    public static void loadthis() {
        new SevenInjection().onEnable();
    }

    private static SevenInjection instance;

    public static SevenInjection getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
    }

    @Override
    public void onDisable() {
        instance = null;
    }
}
