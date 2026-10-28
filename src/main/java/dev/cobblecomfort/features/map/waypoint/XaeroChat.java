package dev.cobblecomfort.features.map.waypoint;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.cobblecomfort.features.map.MapFeature;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Waypoints in chat in Xaero's format ("xaero-waypoint:name:initials:x:y:z:color:..."), so they work both ways with
 * Xaero users: received ones become a readable message with a click to add them, shared ones go out in that format.
 */
public final class XaeroChat {
	private static final String PREFIX = "xaero-waypoint:";
	/** Xaero writes colons inside names as this. */
	private static final String COLON_ESCAPE = "§§";
	private static final int MIN_PARTS = 6;
	private static final Map<Integer, Waypoint> PENDING = new HashMap<>();
	private static int nextId;

	private XaeroChat() {
	}

	public static void register() {
		ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signed, sender, params, time) -> {
			if (!enabled()) {
				return true;
			}
			Optional<Waypoint> waypoint = parse(message.getString());
			if (waypoint.isEmpty()) {
				return true;
			}
			String from = sender != null ? sender.name() : "?";
			Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(pretty(from, waypoint.get()));
			return false;
		});
		ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> {
			if (overlay || !enabled()) {
				return message;
			}
			return parse(message.getString()).<Component>map(w -> pretty(null, w)).orElse(message);
		});
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(
				ClientCommands.literal("cobblecomfort").then(ClientCommands.literal("addwaypoint")
						.then(ClientCommands.argument("id", IntegerArgumentType.integer()).executes(ctx -> {
							Waypoint waypoint = PENDING.remove(IntegerArgumentType.getInteger(ctx, "id"));
							if (waypoint != null && MapFeature.INSTANCE.waypoints().loaded()) {
								MapFeature.INSTANCE.waypoints().add(waypoint);
								ctx.getSource().sendFeedback(Component.translatable("cobblecomfort.waypoint.added", waypoint.name)
										.withStyle(ChatFormatting.GREEN));
							}
							return 1;
						})))));
	}

	private static boolean enabled() {
		return MapFeature.INSTANCE.isEnabled() && MapFeature.INSTANCE.config().chatWaypoints;
	}

	/** The waypoint in a chat message in Xaero's format, if there is one. */
	public static Optional<Waypoint> parse(String text) {
		int start = text.indexOf(PREFIX);
		if (start < 0) {
			return Optional.empty();
		}
		String[] parts = text.substring(start + PREFIX.length()).trim().split(":");
		if (parts.length < MIN_PARTS) {
			return Optional.empty();
		}
		try {
			Waypoint waypoint = new Waypoint();
			waypoint.name = parts[0].replace(COLON_ESCAPE, ":");
			waypoint.initials = parts[1].isEmpty() ? Waypoint.initialsOf(waypoint.name) : parts[1];
			waypoint.x = Integer.parseInt(parts[2].trim());
			waypoint.y = parts[3].trim().equals("~") ? null : Integer.parseInt(parts[3].trim());
			waypoint.z = Integer.parseInt(parts[4].trim());
			waypoint.color = Math.clamp(Integer.parseInt(parts[5].trim()), 0, 15);
			String dimension = parts.length > 8 ? parts[8].toLowerCase(Locale.ROOT) : "";
			waypoint.dimension = dimension.contains("nether") || dimension.contains("dim%-1") ? "minecraft:the_nether"
					: dimension.contains("end") || dimension.contains("dim%1") ? "minecraft:the_end" : "minecraft:overworld";
			waypoint.set = "shared";
			return Optional.of(waypoint);
		} catch (NumberFormatException e) {
			return Optional.empty();
		}
	}

	public static String format(Waypoint waypoint) {
		String dimension = switch (waypoint.dimension) {
			case "minecraft:the_nether" -> "Internal-the-nether-waypoints";
			case "minecraft:the_end" -> "Internal-the-end-waypoints";
			default -> "Internal-overworld-waypoints";
		};
		return PREFIX + waypoint.name.replace(":", COLON_ESCAPE) + ":" + waypoint.initials + ":" + waypoint.x + ":"
				+ (waypoint.y == null ? "~" : waypoint.y) + ":" + waypoint.z + ":" + waypoint.color + ":false:0:" + dimension;
	}

	/** Sends a waypoint to the chat in Xaero's format. */
	public static void share(Waypoint waypoint) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.connection.sendChat(format(waypoint));
		}
	}

	private static Component pretty(String from, Waypoint waypoint) {
		int id = nextId++;
		PENDING.put(id, waypoint);
		MutableComponent text = Component.literal("📍 ").withStyle(ChatFormatting.AQUA);
		if (from != null) {
			text.append(Component.translatable("cobblecomfort.waypoint.shared_by", from).withStyle(ChatFormatting.GRAY));
		}
		text.append(Component.literal(waypoint.name).withColor(waypoint.rgb()))
				.append(Component.literal(" (" + waypoint.x + ", " + (waypoint.y == null ? "~" : waypoint.y) + ", " + waypoint.z + ") ")
						.withStyle(ChatFormatting.GRAY))
				.append(Component.translatable("cobblecomfort.waypoint.add_button").withStyle(style -> style
						.withColor(ChatFormatting.GREEN).withUnderlined(true)
						.withClickEvent(new ClickEvent.RunCommand("cobblecomfort addwaypoint " + id))
						.withHoverEvent(new HoverEvent.ShowText(Component.translatable("cobblecomfort.waypoint.add_hover")))));
		return text;
	}
}
