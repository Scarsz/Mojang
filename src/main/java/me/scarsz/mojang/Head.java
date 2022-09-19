package me.scarsz.mojang;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import org.apache.commons.codec.binary.Base64;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@SuppressWarnings("unused")
public class Head {

    static {
        ItemStack skull;
        try {
            skull = new ItemStack(Material.valueOf("PLAYER_HEAD"));
        } catch (IllegalArgumentException e) {
            //noinspection deprecation
            skull = new ItemStack(Material.valueOf("SKULL_ITEM"), 1, (short) SkullType.PLAYER.ordinal());
        }
        PLAYER_SKULL_ITEM = skull;
    }

    private static final ItemStack PLAYER_SKULL_ITEM;
    private static final Map<UUID, ItemStack> CACHE = new HashMap<>();

    public static ItemStack getPlayerSkullItem() {
        return getPlayerSkullItem(1);
    }
    public static ItemStack getPlayerSkullItem(int amount) {
        ItemStack itemStack = PLAYER_SKULL_ITEM.clone();
        itemStack.setAmount(amount);
        return itemStack;
    }

    public static ItemStack create(String playerName) {
        return create(playerName, 1);
    }

    @SuppressWarnings("deprecation")
    public static ItemStack create(String playerName, int amount) {
        return create(Bukkit.getOfflinePlayer(playerName), amount);
    }

    public static ItemStack create(OfflinePlayer player) {
        return create(player, 1);
    }

    public static ItemStack create(OfflinePlayer player, int amount) {
        return create(player.getUniqueId(), 1);
    }

    public static ItemStack create(Mojang.GameProfile profile) {
        return create(profile, 1);
    }

    public static ItemStack create(Mojang.GameProfile profile, int amount) {
        return create(profile.getUuid(), amount);
    }

    public static ItemStack create(UUID playerUuid) {
        return create(playerUuid, 1);
    }

    public static ItemStack create(UUID uuid, int amount) {
        ItemStack itemStack = CACHE.computeIfAbsent(uuid, t -> {
            try {
                Mojang.GameProfile profile = Mojang.fetch(uuid);
                ItemStack item = createFromTexture(profile.getSkin());
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName(ChatColor.YELLOW + profile.getName());
                item.setItemMeta(meta);
                return item;
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        });
        if (itemStack != null) {
            itemStack = itemStack.clone();
            itemStack.setAmount(amount);
        }
        return itemStack;
    }

    public static ItemStack createFromTexture(String textureId) {
        return createFromTexture(textureId, 1);
    }
    public static ItemStack createFromTexture(String textureId, int amount) {
        String skin = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + textureId + "\"}}}";
        String b64 = new String(Base64.encodeBase64(skin.getBytes()));

        try {
            ItemStack head = getPlayerSkullItem(amount);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            GameProfile profile = new GameProfile(UUID.randomUUID(), null);

            // construct new textures property
            Property property = new Property("textures", b64);

            // set profile's texture property to custom one
            profile.getProperties().put("textures", property);

            // set skull's profile field
            assert meta != null;
            Field profileField = meta.getClass().getDeclaredField("profile");
            profileField.setAccessible(true);
            profileField.set(meta, profile);

            head.setItemMeta(meta);
            return head;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

}
