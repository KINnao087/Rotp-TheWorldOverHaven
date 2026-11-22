package com.inza.twoh.network;

import com.inza.twoh.AddonMain;
import com.inza.twoh.client.RiftDestinationScreen;
import com.inza.twoh.init.InitDimension;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.fml.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/** EN: The server lists actual worlds and owns each player's selected destination.
 * 中文：服务器列出当前存档的真实维度，并保管每名玩家选择的目的地。 */
public final class RiftDestinations {
    private static final String TAG = "twohaddon.rift_destination";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(AddonMain.MOD_ID, "rift_destination"), () -> "1", "1"::equals, "1"::equals);
    private static final Map<ServerPlayerEntity, UUID> menus = new WeakHashMap<>();

    private RiftDestinations() {}

    public static void register() {
        CHANNEL.registerMessage(0, Menu.class, Menu::write, Menu::read, Menu::receive,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1, Choice.class, Choice::write, Choice::read, Choice::receive,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public static RegistryKey<World> selected(LivingEntity player) {
        String value = player.getPersistentData().getString(TAG);
        ResourceLocation id = ResourceLocation.tryParse(value);
        return value.isEmpty() || id == null ? InitDimension.OVER_HEAVEN : RegistryKey.create(Registry.DIMENSION_REGISTRY, id);
    }

    public static void open(ServerPlayerEntity player) {
        List<ResourceLocation> ids = new ArrayList<>();
        for (ServerWorld world : player.getLevel().getServer().getAllLevels()) {
            ids.add(world.dimension().location());
        }
        ids.sort(Comparator.comparing((ResourceLocation id) -> !World.OVERWORLD.location().equals(id))
                .thenComparing(ResourceLocation::toString));
        UUID token = UUID.randomUUID();
        menus.put(player, token);
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Menu(token, ids, selected(player).location()));
    }

    public static void choose(UUID token, ResourceLocation id) {
        CHANNEL.sendToServer(new Choice(token, id));
    }

    private static final class Menu {
        final UUID token;
        final List<ResourceLocation> ids;
        final ResourceLocation current;

        Menu(UUID token, List<ResourceLocation> ids, ResourceLocation current) {
            this.token = token;
            this.ids = ids;
            this.current = current;
        }

        void write(PacketBuffer buf) {
            buf.writeUUID(token);
            buf.writeResourceLocation(current);
            buf.writeVarInt(ids.size());
            ids.forEach(buf::writeResourceLocation);
        }

        static Menu read(PacketBuffer buf) {
            UUID token = buf.readUUID();
            ResourceLocation current = buf.readResourceLocation();
            int count = buf.readVarInt();
            List<ResourceLocation> ids = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                ids.add(buf.readResourceLocation());
            }
            return new Menu(token, ids, current);
        }

        void receive(Supplier<NetworkEvent.Context> context) {
            NetworkEvent.Context ctx = context.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> RiftDestinationScreen.open(token, ids, current)));
            ctx.setPacketHandled(true);
        }
    }

    private static final class Choice {
        final UUID token;
        final ResourceLocation id;

        Choice(UUID token, ResourceLocation id) {
            this.token = token;
            this.id = id;
        }

        void write(PacketBuffer buf) {
            buf.writeUUID(token);
            buf.writeResourceLocation(id);
        }

        static Choice read(PacketBuffer buf) {
            return new Choice(buf.readUUID(), buf.readResourceLocation());
        }

        void receive(Supplier<NetworkEvent.Context> context) {
            NetworkEvent.Context ctx = context.get();
            ServerPlayerEntity player = ctx.getSender();
            ctx.enqueueWork(() -> {
                if (player == null || !token.equals(menus.get(player))) {
                    return;
                }
                menus.remove(player);
                RegistryKey<World> key = RegistryKey.create(Registry.DIMENSION_REGISTRY, id);
                if (player.getLevel().getServer().getLevel(key) == null) {
                    player.displayClientMessage(new TranslationTextComponent("rift.twohaddon.no_destination"), false);
                    return;
                }
                player.getPersistentData().putString(TAG, id.toString());
                player.displayClientMessage(new TranslationTextComponent("rift.twohaddon.selected", id.toString()), false);
            });
            ctx.setPacketHandled(true);
        }
    }
}
