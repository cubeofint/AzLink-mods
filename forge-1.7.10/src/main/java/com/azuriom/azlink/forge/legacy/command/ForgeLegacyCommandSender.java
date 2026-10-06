package com.azuriom.azlink.forge.legacy.command;

import com.azuriom.azlink.common.chat.TextComponent;
import com.azuriom.azlink.common.command.CommandSender;
import com.azuriom.azlink.common.data.PlayerData;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;

import java.util.UUID;

public final class ForgeLegacyCommandSender implements CommandSender {

    private final ICommandSender sender;

    public ForgeLegacyCommandSender(ICommandSender sender) {
        this.sender = sender;
    }

    @Override
    public String getName() {
        return this.sender.getCommandSenderName();
    }

    @Override
    public UUID getUuid() {
        if (this.sender instanceof EntityPlayerMP) {
            return ((EntityPlayerMP) this.sender).getUniqueID();
        }
        return new UUID(0L, 0L);
    }

    @Override
    public void sendMessage(String message) {
        this.sender.addChatMessage(new ChatComponentText(message));
    }

    @Override
    public void sendMessage(TextComponent message) {
        sendMessage(message.toMinecraftLegacy());
    }

    @Override
    public boolean hasPermission(String permission) {
        // No permission plugin bridge yet — require op permission level 2.
        return this.sender.canCommandSenderUseCommand(2, "azlink");
    }

    @Override
    public PlayerData toData() {
        return new PlayerData(getName(), getUuid(), null, this.sender instanceof EntityPlayerMP);
    }
}
