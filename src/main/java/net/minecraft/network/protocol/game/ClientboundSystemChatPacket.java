// mc-dev import
package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;

// Spigot start
public record ClientboundSystemChatPacket(@javax.annotation.Nullable net.kyori.adventure.text.Component adventure$content, @javax.annotation.Nullable String content, boolean overlay) implements Packet<ClientGamePacketListener> { // Paper - Adventure

    public ClientboundSystemChatPacket(Component content, boolean overlay) {
        this(null, Component.Serializer.toJson(content), overlay); // Paper - Adventure
    }

    public ClientboundSystemChatPacket(net.md_5.bungee.api.chat.BaseComponent[] content, boolean overlay) {
        this(null, net.md_5.bungee.chat.ComponentSerializer.toString(content), overlay); // Paper - Adventure
    }
    // Spigot end
    // Paper start
    public ClientboundSystemChatPacket {
        com.google.common.base.Preconditions.checkArgument(!(adventure$content == null && content == null), "Component adventure$content and String (json) content cannot both be null");
    }

    public ClientboundSystemChatPacket(net.kyori.adventure.text.Component content, boolean overlay) {
        this(content, null, overlay);
    }
    // Paper end

    public ClientboundSystemChatPacket(FriendlyByteBuf buf) {
        this(buf.readComponent(), buf.readBoolean());
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        // Paper start
        if (this.adventure$content != null) {
            buf.writeComponent(this.adventure$content);
        } else if (this.content != null) {
        buf.writeUtf(this.content, 262144); // Spigot
        } else {
            throw new IllegalArgumentException("Must supply either adventure component or string json content");
        }
        // Paper end
        buf.writeBoolean(this.overlay);
    }

    public void handle(ClientGamePacketListener listener) {
        listener.handleSystemChat(this);
    }

    @Override
    public boolean isSkippable() {
        return true;
    }
}
