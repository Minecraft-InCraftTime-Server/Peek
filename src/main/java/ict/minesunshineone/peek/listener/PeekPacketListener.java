package ict.minesunshineone.peek.listener;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;

import ict.minesunshineone.peek.PeekPlugin;

public class PeekPacketListener {

    private final PeekPlugin plugin;
    private final ProtocolManager protocolManager;

    public PeekPacketListener(PeekPlugin plugin) {
        this.plugin = plugin;
        this.protocolManager = ProtocolLibrary.getProtocolManager();
        registerPacketListeners();
    }

    private void registerPacketListeners() {
        // 监听观察者模式传送数据包
        protocolManager.addPacketListener(new PacketAdapter(plugin, PacketType.Play.Client.SPECTATE) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                Player player = event.getPlayer();

                // 检查玩家是否在peek状态
                if (((PeekPlugin) plugin).getStateHandler().getActivePeeks().containsKey(player.getUniqueId())
                        && player.getGameMode() == GameMode.SPECTATOR) {
                    // 取消数据包，阻止传送
                    event.setCancelled(true);
                }
            }
        });
    }

    public void unregisterPacketListeners() {
        protocolManager.removePacketListeners(plugin);
    }
}
