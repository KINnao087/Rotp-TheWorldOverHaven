package com.inza.twoh.command.agent.chat;

import com.inza.twoh.AddonMain;
import com.inza.twoh.client.AgentChatScreen;
import com.inza.twoh.client.AgentReplyNotice;
import com.inza.twoh.command.MiniAgentCommand;
import com.inza.twoh.command.agent.AgentStrings.Text;
import com.inza.twoh.command.agent.CommandExecutor;
import com.inza.twoh.command.agent.entitiy.ChatTurn;
import com.inza.twoh.command.agent.entitiy.ChatView;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.fml.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * EN: Clients submit only their new text or preferences; the server owns identity, history and credentials.
 * 中文：客户端只提交新消息或设置；玩家身份、历史和凭据由服务端掌管。
 */
public final class ChatNetwork {
    private static final String VERSION = "2";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(AddonMain.MOD_ID, ChatText.CHANNEL), () -> VERSION,
            VERSION::equals, VERSION::equals);

    private ChatNetwork() {
    }

    public static void register() {
        CHANNEL.registerMessage(0, Action.class, Action::write, Action::read, Action::receive,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, Snapshot.class, Snapshot::write, Snapshot::read, Snapshot::receive,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(2, Completion.class, Completion::write, Completion::read, Completion::receive,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void send(String request) {
        CHANNEL.sendToServer(new Action(Operation.SEND, request, false, 0));
    }

    public static void configure(boolean summarize, int window) {
        CHANNEL.sendToServer(new Action(Operation.SETTINGS, "", summarize, window));
    }

    public static void close() {
        CHANNEL.sendToServer(new Action(Operation.CLOSE, "", false, 0));
    }

    public static void show(ServerPlayerEntity player, ChatView view, boolean open) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Snapshot(view, open));
    }

    public static void completed(ServerPlayerEntity player, String message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Completion(message));
    }

    private static final class Completion {
        private final String message;

        Completion(String message) {
            this.message = message;
        }

        void write(PacketBuffer buffer) {
            buffer.writeUtf(message);
        }

        static Completion read(PacketBuffer buffer) {
            return new Completion(buffer.readUtf(ChatText.MAX_REPLY));
        }

        static void receive(Completion packet, Supplier<NetworkEvent.Context> context) {
            NetworkEvent.Context ctx = context.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> AgentReplyNotice.receive(ctx.getNetworkManager(), packet.message)));
            ctx.setPacketHandled(true);
        }
    }

    private enum Operation {
        SEND, SETTINGS, CLOSE
    }

    private static final class Action {
        private final Operation operation;
        private final String request;
        private final boolean summarize;
        private final int window;

        Action(Operation operation, String request, boolean summarize, int window) {
            this.operation = operation;
            this.request = request;
            this.summarize = summarize;
            this.window = window;
        }

        void write(PacketBuffer buffer) {
            buffer.writeEnum(operation);
            if (operation == Operation.SEND) {
                buffer.writeUtf(request);
            } else if (operation == Operation.SETTINGS) {
                buffer.writeBoolean(summarize);
                buffer.writeVarInt(window);
            }
        }

        static Action read(PacketBuffer buffer) {
            Operation operation = buffer.readEnum(Operation.class);
            if (operation == Operation.SEND) {
                return new Action(operation, buffer.readUtf(ChatText.MAX_REQUEST), false, 0);
            }
            if (operation == Operation.SETTINGS) {
                return new Action(operation, "", buffer.readBoolean(), buffer.readVarInt());
            }
            return new Action(operation, "", false, 0);
        }

        static void receive(Action action, Supplier<NetworkEvent.Context> context) {
            NetworkEvent.Context ctx = context.get();
            ServerPlayerEntity player = ctx.getSender();
            ctx.enqueueWork(() -> {
                if (player == null || player.getServer().getPlayerList().getPlayer(player.getUUID()) != player) {
                    return;
                }
                if (action.operation == Operation.CLOSE) {
                    MiniAgentCommand.closeChat(player);
                    return;
                }
                if (!player.createCommandSourceStack().hasPermission(CommandExecutor.PERMISSION_LEVEL)) {
                    show(player, new ChatView(false, ChatText.DEFAULT_WINDOW, "", Collections.emptyList(),
                            "", Text.PLAYER_CHANGED), false);
                    return;
                }
                if (action.operation == Operation.SEND) {
                    MiniAgentCommand.request(player, action.request, true);
                } else {
                    MiniAgentCommand.configureChat(player, action.summarize, action.window);
                }
            });
            ctx.setPacketHandled(true);
        }
    }

    private static final class Snapshot {
        private final ChatView view;
        private final boolean open;

        Snapshot(ChatView view, boolean open) {
            this.view = view;
            this.open = open;
        }

        void write(PacketBuffer buffer) {
            buffer.writeBoolean(open);
            buffer.writeBoolean(view.summarize);
            buffer.writeVarInt(view.window);
            buffer.writeUtf(view.summary);
            buffer.writeVarInt(view.turns.size());
            for (ChatTurn turn : view.turns) {
                buffer.writeUtf(turn.request);
                buffer.writeUtf(turn.answer);
            }
            buffer.writeUtf(view.pending);
            buffer.writeUtf(view.notice);
        }

        static Snapshot read(PacketBuffer buffer) {
            boolean open = buffer.readBoolean();
            boolean summarize = buffer.readBoolean();
            int window = buffer.readVarInt();
            String summary = buffer.readUtf(ChatText.MAX_SUMMARY);
            int count = buffer.readVarInt();
            if (window < ChatText.MIN_WINDOW || window > ChatText.MAX_WINDOW || count < 0 || count > window) {
                throw new IllegalArgumentException(ChatText.LIMIT);
            }
            List<ChatTurn> turns = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                turns.add(new ChatTurn(buffer.readUtf(ChatText.MAX_REQUEST), buffer.readUtf(ChatText.MAX_REPLY)));
            }
            String pending = buffer.readUtf(ChatText.MAX_REQUEST);
            String notice = buffer.readUtf(ChatText.MAX_REPLY);
            return new Snapshot(new ChatView(summarize, window, summary, turns, pending, notice), open);
        }

        static void receive(Snapshot packet, Supplier<NetworkEvent.Context> context) {
            NetworkEvent.Context ctx = context.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> AgentChatScreen.receive(packet.view, packet.open)));
            ctx.setPacketHandled(true);
        }
    }
}
