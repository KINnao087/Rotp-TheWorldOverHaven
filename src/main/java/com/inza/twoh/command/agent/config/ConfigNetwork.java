package com.inza.twoh.command.agent.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.inza.twoh.AddonMain;
import com.inza.twoh.client.AgentConfigScreen;
import com.inza.twoh.command.agent.AgentStrings.Wire;
import com.inza.twoh.command.agent.ChatApi;
import com.inza.twoh.command.agent.entitiy.ConfigDraft;
import com.inza.twoh.command.agent.entitiy.ModelConfig;
import com.inza.twoh.command.agent.entitiy.ProfileCatalog;
import com.inza.twoh.command.agent.entitiy.ProfileSummary;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.fml.network.simple.SimpleChannel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * EN: A separate admin channel. Validate authority again on the server for every write and test.
 * 中文：独立的管理通道；每次写入或测试都要在服务端重新核验权限。
 */
public final class ConfigNetwork {
    private static final String VERSION = "2";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(AddonMain.MOD_ID, "model_settings"), () -> VERSION,
            VERSION::equals, VERSION::equals);
    private static final AtomicBoolean TESTING = new AtomicBoolean();
    private static final ExecutorService TEST_WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "TWOH-model-test");
        thread.setDaemon(true);
        return thread;
    });

    private ConfigNetwork() {
    }

    public static void register() {
        CHANNEL.registerMessage(0, View.class, View::write, View::read, View::receive,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1, Edit.class, Edit::write, Edit::read, Edit::receive,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(2, Notice.class, Notice::write, Notice::read, Notice::receive,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static int open(ServerPlayerEntity player) {
        ModelRuntime runtime = ServerModels.get(player.getServer());
        if (!allowed(player) || runtime == null) {
            tell(player, ConfigText.STORAGE_ERROR);
            return 0;
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new View(runtime));
        return 1;
    }

    private static boolean allowed(ServerPlayerEntity player) {
        return player.createCommandSourceStack().hasPermission(ConfigText.ADMIN_LEVEL);
    }

    private static void tell(ServerPlayerEntity player, String text) {
        player.sendMessage(new StringTextComponent(text), player.getUUID());
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Notice(text));
    }

    private static final class Notice {
        final String text;

        Notice(String text) {
            this.text = text;
        }

        void write(PacketBuffer buffer) {
            buffer.writeUtf(text);
        }

        static Notice read(PacketBuffer buffer) {
            return new Notice(buffer.readUtf(256));
        }

        static void receive(Notice notice, Supplier<NetworkEvent.Context> context) {
            NetworkEvent.Context ctx = context.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> AgentConfigScreen.feedback(notice.text)));
            ctx.setPacketHandled(true);
        }
    }

    public static void send(ConfigDraft draft, long revision, boolean test) {
        CHANNEL.sendToServer(new Edit(draft, revision, test));
    }

    private static final class View {
        final String active;
        final long revision;
        final List<ProfileSummary> rows;

        View(ModelRuntime runtime) {
            ProfileCatalog catalog = runtime.catalog();
            active = catalog.active;
            revision = runtime.revision();
            rows = new ArrayList<>();
            for (ModelConfig profile : catalog.profiles.values()) {
                rows.add(new ProfileSummary(profile.id, profile.provider, profile.baseUrl, profile.model,
                        !profile.credentialRef.isEmpty(), profile.contextTokens));
            }
        }

        View(String active, long revision, List<ProfileSummary> rows) {
            this.active = active;
            this.revision = revision;
            this.rows = rows;
        }

        void write(PacketBuffer buffer) {
            buffer.writeUtf(active);
            buffer.writeLong(revision);
            buffer.writeVarInt(rows.size());
            for (ProfileSummary row : rows) {
                buffer.writeUtf(row.id);
                buffer.writeUtf(row.provider);
                buffer.writeUtf(row.baseUrl);
                buffer.writeUtf(row.model);
                buffer.writeBoolean(row.hasKey);
                buffer.writeVarInt(row.contextTokens);
            }
        }

        static View read(PacketBuffer buffer) {
            String active = buffer.readUtf(48);
            long revision = buffer.readLong();
            int count = buffer.readVarInt();
            if (count < 0 || count > ConfigText.MAX_PROFILES) {
                throw new IllegalArgumentException(ConfigText.INVALID);
            }
            List<ProfileSummary> rows = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                rows.add(new ProfileSummary(buffer.readUtf(48), buffer.readUtf(48), buffer.readUtf(512),
                        buffer.readUtf(128), buffer.readBoolean(), buffer.readVarInt()));
            }
            return new View(active, revision, rows);
        }

        static void receive(View view, Supplier<NetworkEvent.Context> context) {
            NetworkEvent.Context ctx = context.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> AgentConfigScreen.open(view.active, view.revision, view.rows)));
            ctx.setPacketHandled(true);
        }
    }

    private static final class Edit {
        final ConfigDraft draft;
        final long revision;
        final boolean test;

        Edit(ConfigDraft draft, long revision, boolean test) {
            this.draft = draft;
            this.revision = revision;
            this.test = test;
        }

        void write(PacketBuffer buffer) {
            buffer.writeLong(revision);
            buffer.writeBoolean(test);
            buffer.writeUtf(draft.id);
            buffer.writeUtf(draft.provider);
            buffer.writeUtf(draft.baseUrl);
            buffer.writeUtf(draft.model);
            buffer.writeUtf(draft.replacement);
            buffer.writeBoolean(draft.clearKey);
            buffer.writeBoolean(draft.allowFile);
            buffer.writeVarInt(draft.contextTokens);
        }

        static Edit read(PacketBuffer buffer) {
            long revision = buffer.readLong();
            boolean test = buffer.readBoolean();
            ConfigDraft draft = new ConfigDraft(buffer.readUtf(48), buffer.readUtf(48), buffer.readUtf(512),
                    buffer.readUtf(128), buffer.readUtf(2048), buffer.readBoolean(), buffer.readBoolean(), buffer.readVarInt());
            return new Edit(draft, revision, test);
        }

        static void receive(Edit edit, Supplier<NetworkEvent.Context> context) {
            NetworkEvent.Context ctx = context.get();
            ServerPlayerEntity sender = ctx.getSender();
            ctx.enqueueWork(() -> {
                if (sender == null || !allowed(sender)) {
                    return;
                }
                ModelRuntime runtime = ServerModels.get(sender.getServer());
                if (runtime == null) {
                    tell(sender, ConfigText.STORAGE_ERROR);
                    return;
                }
                try {
                    if (edit.test) {
                        testConnection(sender, runtime.forTest(edit.draft, edit.revision));
                    } else {
                        runtime.save(edit.draft, edit.revision);
                        open(sender);
                        tell(sender, ConfigText.SAVE_OK);
                    }
                } catch (IllegalArgumentException invalid) {
                    // EN: Never echo arbitrary URI/parser exceptions; an invalid URL may contain a secret.
                    // 中文：解析异常可能包含错误输入的凭据，不将其原文回显。
                    String reason = invalid.getMessage();
                    if (!ConfigText.STALE.equals(reason) && !ConfigText.REBIND.equals(reason)
                            && !ConfigText.SECRET_RULE.equals(reason)) {
                        reason = ConfigText.INVALID;
                    }
                    tell(sender, reason);
                } catch (IOException failure) {
                    tell(sender, ConfigText.STORAGE_ERROR);
                }
            });
            ctx.setPacketHandled(true);
        }
    }

    private static void testConnection(ServerPlayerEntity sender, ChatApi client) {
        if (!TESTING.compareAndSet(false, true)) {
            tell(sender, ConfigText.TEST_BUSY);
            return;
        }
        MinecraftServer server = sender.getServer();
        TEST_WORKER.execute(() -> {
            String result;
            try {
                JsonArray messages = new JsonArray();
                JsonObject input = new JsonObject();
                input.addProperty(Wire.ROLE, Wire.USER);
                input.addProperty(Wire.CONTENT, ConfigText.PROBE);
                messages.add(input);
                result = client.complete(messages).trim().isEmpty() ? ConfigText.TEST_FAIL : ConfigText.TEST_OK;
            } catch (IOException | RuntimeException failure) {
                result = ConfigText.TEST_FAIL;
            } finally {
                TESTING.set(false);
            }
            final String feedback = result;
            server.execute(() -> {
                if (server.getPlayerList().getPlayer(sender.getUUID()) == sender && allowed(sender)) {
                    tell(sender, feedback);
                }
            });
        });
    }
}
