package dev.cobblecomfort.features.plush;

import dev.cobblecomfort.CobbleComfortClient;
import dev.cobblecomfort.core.Feature;
import dev.isxander.yacl3.api.ConfigCategory;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Totems of undying look like a plush of the player holding them, whatever the resource pack says. The item model is
 * swapped in {@code ItemModelResolverMixin} for {@code cobblecomfort:totem_plush}, a special model drawn by
 * {@link PlushRenderer}; it stays an ordinary item layer, so mods that adjust held items (like Punchy) keep working.
 */
public final class PlushFeature extends Feature<PlushConfig> {
	public static final PlushFeature INSTANCE = new PlushFeature();
	public static final Identifier MODEL_ID = CobbleComfortClient.id("totem_plush");

	/** A hand counts as newly filled when it has not shown a plush for this long. */
	private static final long EQUIP_GAP_NANOS = 250_000_000L;
	private static final float EQUIP_SQUISH_SECONDS = 0.55f;
	private static final float ACTIVATION_SQUISH_SECONDS = 1.1f;
	private static final float PUNCHY_UPRIGHT = 115f;

	/** Squish and jiggle of the plush in one hand of one entity. */
	private static final class HandState {
		long lastSeen;
		long equipped;
		final PlushJiggle jiggle = new PlushJiggle();
	}

	private final Long2ObjectOpenHashMap<HandState> hands = new Long2ObjectOpenHashMap<>();
	private PlushRenderer.Args pending;
	private boolean inPunchyArm;
	private long activationStart;
	private int lastActivationTicks;

	private PlushFeature() {
		super("plush", PlushConfig.class, PlushConfig::new);
	}

	@Override
	public void init() {
		SpecialModelRenderers.ID_MAPPER.put(CobbleComfortClient.id("plush"), PlushRenderer.Unbaked.MAP_CODEC);
	}

	@Override
	public ConfigCategory buildCategory() {
		return PlushConfigUi.buildCategory(this);
	}

	/**
	 * Whether this stack is drawn as a plush here; if so, prepares the arguments {@link PlushRenderer} picks up while
	 * the model updates.
	 */
	public boolean begin(ItemStack stack, ItemDisplayContext context, ItemOwner owner) {
		if (!isEnabled() || !stack.is(Items.TOTEM_OF_UNDYING)) {
			return false;
		}
		LivingEntity holder = owner == null ? null : owner.asLivingEntity();
		AbstractClientPlayer player = holder instanceof AbstractClientPlayer p ? p : null;
		boolean hand = context.firstPerson() || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
				|| context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
		boolean activation = context == ItemDisplayContext.FIXED && owner == null && activationShowing();
		boolean shown;
		if (context == ItemDisplayContext.GUI) {
			shown = config.inGui;
		} else if (activation) {
			shown = config.activation;
		} else if (hand && player != null) {
			shown = config.inHand;
		} else {
			shown = config.inWorld;
		}
		if (!shown) {
			return false;
		}

		PlayerSkin skin = skinOf(player);
		float squish = 1f;
		float tiltZ = 0f;
		float tiltX = 0f;
		PlushPose limbs = PlushPose.REST;
		long now = System.nanoTime();
		if (activation) {
			squish = activationSquish(now);
		} else if (hand && holder != null) {
			HandState state = hand(holder, context, now);
			if (config.equipSquish) {
				squish = equipSquish((now - state.equipped) / 1e9f);
			}
			if (config.wobble) {
				// A plush seen in first person faces its holder, so its sway is mirrored.
				boolean mirrored = context.firstPerson() || inPunchyArm;
				tiltZ = state.jiggle.bodyRoll(mirrored);
				tiltX = state.jiggle.bodyPitch(mirrored);
				limbs = state.jiggle.pose(mirrored);
			}
		}
		// Punchy draws first-person items in its own outstretched arm with the third-person transform, which would lay
		// the plush on its back; turn it upright there.
		float upright = inPunchyArm ? PUNCHY_UPRIGHT : 0f;
		pending = new PlushRenderer.Args(skin.body().texturePath(), skin.model() == PlayerModelType.SLIM, squish, tiltZ, tiltX,
				upright, limbs);
		return true;
	}

	/** Whether the plush is moving right now, so its render state must not be cached between frames. */
	public boolean animating() {
		return pending != null && (pending.squish() != 1f || pending.tiltZ() != 0f || pending.tiltX() != 0f
				|| !pending.limbs().equals(PlushPose.REST));
	}

	public PlushRenderer.Args pendingArgs() {
		return pending;
	}

	/** Set while Punchy draws an item in its first-person arm ({@code PunchyArmRendererMixin}). */
	public void setInPunchyArm(boolean inArm) {
		inPunchyArm = inArm;
	}

	public void end() {
		pending = null;
	}

	private static PlayerSkin skinOf(AbstractClientPlayer holder) {
		if (holder != null) {
			return holder.getSkin();
		}
		LocalPlayer self = Minecraft.getInstance().player;
		return self != null ? self.getSkin() : DefaultPlayerSkin.getDefaultSkin();
	}

	// ---------------------------------------------------------------- animation

	/** Tracks one hand: notices when it starts holding a plush and lets the plush jiggle as its holder moves. */
	private HandState hand(LivingEntity holder, ItemDisplayContext context, long now) {
		boolean left = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
		long key = (long) holder.getId() << 1 | (left ? 1 : 0);
		HandState state = hands.get(key);
		if (state == null || now - state.lastSeen > EQUIP_GAP_NANOS) {
			if (state == null) {
				state = new HandState();
				hands.put(key, state);
			}
			state.equipped = now;
			state.jiggle.reset();
			if (hands.size() > 64) {
				hands.long2ObjectEntrySet().removeIf(e -> now - e.getValue().lastSeen > EQUIP_GAP_NANOS);
			}
		}
		// The same hand can be resolved several times a frame (first person, Punchy's arm); only the first moves it.
		float dt = (now - state.lastSeen) / 1e9f;
		state.lastSeen = now;
		if (dt > 0.001f) {
			float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
			state.jiggle.update(holder, partialTick, dt);
		}
		return state;
	}

	/** Pressed flat when picked up, then springing back with a little overshoot. */
	private static float equipSquish(float t) {
		if (t >= EQUIP_SQUISH_SECONDS) {
			return 1f;
		}
		float decay = (float) Math.exp(-7f * t);
		return 1f - 0.32f * decay * Mth.cos(t * 26f);
	}

	private boolean activationShowing() {
		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && player.itemActivation().isActive();
	}

	/** Squeezed hard as the totem pops, then wobbling back while it flies. */
	private float activationSquish(long now) {
		LocalPlayer player = Minecraft.getInstance().player;
		int ticks = player.itemActivation().ticks();
		// A new activation starts over at 40 ticks; the counter only runs down while one plays.
		if (ticks > lastActivationTicks || activationStart == 0) {
			activationStart = now - (40 - ticks) * 50_000_000L;
		}
		lastActivationTicks = ticks;
		if (!config.activationSquish) {
			return 1f;
		}
		float t = (now - activationStart) / 1e9f;
		if (t >= ACTIVATION_SQUISH_SECONDS) {
			return 1f;
		}
		float decay = (float) Math.exp(-4.5f * t);
		return 1f - 0.45f * decay * Mth.cos(t * 18f);
	}
}
