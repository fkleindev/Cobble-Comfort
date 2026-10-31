package dev.cobblecomfort.sync;

import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shares player positions between clients with the Cobble Comfort mod. Sharing is opt-in: a client tells the server
 * whether its player shares (a switch in the mod), and only sharing players receive the positions of the other
 * sharing players. Players without the mod never get or show up in anything.
 */
public final class CobbleComfortSync extends JavaPlugin implements Listener {
	private static final String PERMISSION = "cobblecomfort.share";

	/** Players whose client said they share. Messages arrive on the main thread, but quits may race the task. */
	private final Set<UUID> sharing = ConcurrentHashMap.newKeySet();
	/** The plush look (passed on unread) of every player whose client sent one, and all clients that speak the plush channel. */
	private final Map<UUID, byte[]> plushes = new ConcurrentHashMap<>();
	private final Set<UUID> plushClients = ConcurrentHashMap.newKeySet();
	private boolean sharePlushes;
	private BukkitTask task;
	private int intervalTicks;
	private double maxDistance;
	private boolean hideSpectators;
	private boolean hideInvisible;
	private boolean shareHealth;
	private Attribute armorAttribute;
	private Attribute maxHealthAttribute;

	@Override
	public void onEnable() {
		saveDefaultConfig();
		armorAttribute = attribute("armor");
		maxHealthAttribute = attribute("max_health");
		getServer().getMessenger().registerIncomingPluginChannel(this, Protocol.SHARE, (channel, player, data) -> onShare(player, data));
		getServer().getMessenger().registerOutgoingPluginChannel(this, Protocol.PLAYERS);
		getServer().getMessenger().registerIncomingPluginChannel(this, Protocol.PLUSH, (channel, player, data) -> onPlush(player, data));
		getServer().getMessenger().registerOutgoingPluginChannel(this, Protocol.PLUSHES);
		getServer().getPluginManager().registerEvents(this, this);
		loadSettings();
	}

	@Override
	public void onDisable() {
		if (task != null) {
			task.cancel();
		}
		sharing.clear();
		plushes.clear();
		plushClients.clear();
	}

	private void loadSettings() {
		reloadConfig();
		FileConfiguration config = getConfig();
		intervalTicks = Math.max(1, config.getInt("interval-ticks", 10));
		maxDistance = Math.max(0, config.getDouble("max-distance", 0));
		hideSpectators = config.getBoolean("hide-spectators", true);
		hideInvisible = config.getBoolean("hide-invisible", true);
		shareHealth = config.getBoolean("share-health", true);
		sharePlushes = config.getBoolean("share-plushes", true);
		if (task != null) {
			task.cancel();
		}
		task = getServer().getScheduler().runTaskTimer(this, this::broadcast, intervalTicks, intervalTicks);
	}

	/** Attributes were renamed (generic.armor -> armor), so both keys are tried; null if neither exists. */
	private static Attribute attribute(String name) {
		Attribute attribute = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(name));
		return attribute != null ? attribute : Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic." + name));
	}

	private void onShare(Player player, byte[] data) {
		Boolean share = Protocol.readShare(data);
		if (share == null) {
			return;
		}
		if (share && player.hasPermission(PERMISSION)) {
			sharing.add(player.getUniqueId());
			send(player, entries());
		} else {
			sharing.remove(player.getUniqueId());
			// An empty list so the client clears the others at once.
			player.sendPluginMessage(this, Protocol.PLAYERS, Protocol.writePlayers(intervalTicks, List.of()));
		}
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		sharing.remove(event.getPlayer().getUniqueId());
		plushClients.remove(event.getPlayer().getUniqueId());
		if (plushes.remove(event.getPlayer().getUniqueId()) != null) {
			sendPlushes();
		}
	}

	/** A client tells us its plush look; everyone with the mod gets the new list. */
	private void onPlush(Player player, byte[] data) {
		byte[] look = Protocol.readPlush(data);
		if (look == null) {
			return;
		}
		UUID uuid = player.getUniqueId();
		plushClients.add(uuid);
		if (sharePlushes) {
			plushes.put(uuid, look);
		} else {
			plushes.remove(uuid);
		}
		sendPlushes();
	}

	private void sendPlushes() {
		byte[] data = Protocol.writePlushes(Map.copyOf(plushes));
		for (UUID uuid : plushClients) {
			Player player = getServer().getPlayer(uuid);
			if (player == null) {
				plushClients.remove(uuid);
			} else {
				player.sendPluginMessage(this, Protocol.PLUSHES, data);
			}
		}
	}

	/** Every interval: each sharing player gets the other sharing players. */
	private void broadcast() {
		if (sharing.isEmpty()) {
			return;
		}
		List<Shared> entries = entries();
		for (UUID uuid : sharing) {
			Player player = getServer().getPlayer(uuid);
			if (player == null) {
				sharing.remove(uuid);
			} else if (player.hasPermission(PERMISSION)) {
				send(player, entries);
			}
		}
	}

	/** A shared player and the world it is in (for the distance limit). */
	private record Shared(Player player, Protocol.Entry entry) {
	}

	/** The sharing players that may be shown right now. */
	private List<Shared> entries() {
		List<Shared> list = new ArrayList<>();
		for (UUID uuid : sharing) {
			Player player = getServer().getPlayer(uuid);
			if (player == null || !player.hasPermission(PERMISSION) || hidden(player)) {
				continue;
			}
			var location = player.getLocation();
			float health = shareHealth ? (float) player.getHealth() : -1f;
			float maxHealth = (float) value(player, maxHealthAttribute, 20);
			int armor = shareHealth ? (int) Math.round(value(player, armorAttribute, 0)) : 0;
			list.add(new Shared(player, new Protocol.Entry(uuid, player.getName(), player.getWorld().getKey().toString(),
					location.getX(), location.getY(), location.getZ(), location.getYaw(), health, maxHealth, armor)));
		}
		return list;
	}

	private boolean hidden(Player player) {
		return (hideSpectators && player.getGameMode() == GameMode.SPECTATOR)
				|| (hideInvisible && player.hasPotionEffect(PotionEffectType.INVISIBILITY));
	}

	private static double value(Player player, Attribute attribute, double fallback) {
		AttributeInstance instance = attribute == null ? null : player.getAttribute(attribute);
		return instance == null ? fallback : instance.getValue();
	}

	private void send(Player receiver, List<Shared> all) {
		List<Protocol.Entry> visible = new ArrayList<>();
		for (Shared shared : all) {
			Player other = shared.player();
			if (other.equals(receiver) || !receiver.canSee(other)) {
				continue;
			}
			if (maxDistance > 0 && (!other.getWorld().equals(receiver.getWorld())
					|| other.getLocation().distanceSquared(receiver.getLocation()) > maxDistance * maxDistance)) {
				continue;
			}
			visible.add(shared.entry());
		}
		receiver.sendPluginMessage(this, Protocol.PLAYERS, Protocol.writePlayers(intervalTicks, visible));
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
			loadSettings();
			sender.sendMessage("§aCobble Comfort Sync reloaded (every " + intervalTicks + " ticks).");
			return true;
		}
		List<String> names = new ArrayList<>();
		for (UUID uuid : sharing) {
			Player player = getServer().getPlayer(uuid);
			if (player != null) {
				names.add(player.getName());
			}
		}
		sender.sendMessage("§7Sharing their position (" + names.size() + "): §f" + (names.isEmpty() ? "-" : String.join(", ", names)));
		return true;
	}
}
