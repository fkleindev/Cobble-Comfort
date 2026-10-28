package dev.cobblecomfort.features.enchantdesc;

import dev.cobblecomfort.core.Feature;
import dev.isxander.yacl3.api.ConfigCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Adds a description, max level, applicable items and conflicts below each enchantment in item tooltips.
 * Descriptions come from the lang keys {@code enchantment.<namespace>.<path>.desc}, the same keys the
 * Enchantment Descriptions mod uses, so other mods' enchantments are covered when they ship those keys.
 */
public final class EnchantDescFeature extends Feature<EnchantDescConfig> {
	public static final EnchantDescFeature INSTANCE = new EnchantDescFeature();
	private static final String CURSE_ICON = "⚠ ";

	/** State of one ItemEnchantments.addToTooltip call; thread local because tooltips are also built off-thread. */
	private static final class Call {
		boolean details;
		boolean any;
		Holder<Enchantment> pending;
	}

	private final ThreadLocal<Call> call = new ThreadLocal<>();

	private EnchantDescFeature() {
		super("enchant_desc", EnchantDescConfig.class, EnchantDescConfig::new);
	}

	@Override
	public void init() {
	}

	@Override
	public ConfigCategory buildCategory() {
		return EnchantDescConfigUi.buildCategory(this);
	}

	// ---------------------------------------------------------------- tooltip hooks

	/** Start of a tooltip section; decides whether details are shown for it. */
	public void begin(ItemEnchantments enchantments, DataComponentGetter components) {
		if (!isEnabled()) {
			call.remove();
			return;
		}
		boolean book = components != null && components.get(DataComponents.STORED_ENCHANTMENTS) == enchantments;
		Call c = new Call();
		c.details = switch (config.displayMode) {
			case ALWAYS -> true;
			case BOOKS_ALWAYS -> book || shiftDown();
			case SHIFT -> shiftDown();
		};
		call.set(c);
	}

	/** Restyles an enchantment name line and remembers the enchantment for the detail lines that follow it. */
	public Component decorateName(Holder<Enchantment> holder, int level, Component name) {
		Call c = call.get();
		if (c == null) {
			return name;
		}
		c.any = true;
		c.pending = holder;
		int max = holder.value().getMaxLevel();
		MutableComponent out = name.copy();
		if (isCurse(holder)) {
			if (config.markCurses) {
				out = Component.literal(CURSE_ICON).withColor(config.curseColor).append(out.withColor(config.curseColor));
			}
		} else if (config.highlightMaxLevel && max > 1 && level >= max) {
			out = out.withColor(config.maxLevelColor);
		}
		if (config.showMaxLevel && max > 1 && (config.maxLevelAlways || c.details)) {
			out.append(Component.literal("/").append(Component.translatable("enchantment.level." + max))
					.withColor(config.labelColor));
		}
		return out;
	}

	/** Called right after a name line was added; adds the detail lines for that enchantment. */
	public void appendDetails(Consumer<Component> out) {
		Call c = call.get();
		if (c == null || c.pending == null) {
			return;
		}
		Holder<Enchantment> holder = c.pending;
		c.pending = null;
		if (c.details) {
			addDetails(holder, out);
		}
	}

	/** Description, applicable items and conflicts of one enchantment, as configured. */
	public void addDetails(Holder<Enchantment> holder, Consumer<Component> out) {
		Enchantment enchantment = holder.value();
		if (config.showDescription) {
			String text = description(holder);
			if (text != null) {
				int color = isCurse(holder) && config.markCurses ? dim(config.curseColor) : config.descriptionColor;
				for (String line : wrap(text, 0)) {
					out.accept(Component.literal(indent() + line).withColor(color));
				}
			}
		}
		if (config.showApplicable) {
			labelled(out, "cobblecomfort.enchant_desc.applies_to", applicableText(enchantment.getSupportedItems()));
		}
		if (config.showConflicts) {
			List<String> names = new ArrayList<>();
			for (Holder<Enchantment> other : enchantment.exclusiveSet()) {
				if (!other.is(holder)) {
					names.add(other.value().description().getString());
				}
			}
			if (!names.isEmpty()) {
				labelled(out, "cobblecomfort.enchant_desc.conflicts", joinLimited(names));
			}
		}
	}

	/** End of the section: adds the "hold Shift" hint when details are hidden. */
	public void end(Consumer<Component> out) {
		Call c = call.get();
		call.remove();
		if (c != null && c.any && !c.details && config.shiftHint && config.displayMode != DisplayMode.ALWAYS) {
			out.accept(hint());
		}
	}

	/** Whether details are shown outside item tooltips (enchanting table): always mode or Shift held. */
	public boolean detailsShown() {
		return config.displayMode == DisplayMode.ALWAYS || shiftDown();
	}

	/** The "hold Shift" hint line, or null when it is switched off. */
	public Component hint() {
		return config.shiftHint ? Component.translatable("cobblecomfort.enchant_desc.hint").withColor(config.hintColor) : null;
	}

	// ---------------------------------------------------------------- content

	/** The description text, or null if neither the description's own key nor the registry id has one. */
	private static String description(Holder<Enchantment> holder) {
		Language lang = Language.getInstance();
		if (holder.value().description().getContents() instanceof TranslatableContents translatable) {
			String key = translatable.getKey() + ".desc";
			if (lang.has(key)) {
				return lang.getOrDefault(key);
			}
		}
		Optional<String> key = holder.unwrapKey().map(k -> descKey("enchantment", k.identifier()) + ".desc");
		return key.filter(lang::has).map(lang::getOrDefault).orElse(null);
	}

	/** A friendly name for known item tags, otherwise the item names. */
	private String applicableText(HolderSet<Item> items) {
		Optional<TagKey<Item>> tag = items.unwrapKey();
		if (tag.isPresent()) {
			String key = descKey("cobblecomfort.enchant_desc.tag", tag.get().location());
			if (Language.getInstance().has(key)) {
				return Language.getInstance().getOrDefault(key);
			}
		}
		List<String> names = new ArrayList<>();
		for (Holder<Item> item : items) {
			String name = Component.translatable(item.value().getDescriptionId()).getString();
			if (!names.contains(name)) {
				names.add(name);
			}
		}
		return joinLimited(names);
	}

	private static String descKey(String prefix, Identifier id) {
		return prefix + "." + id.getNamespace() + "." + id.getPath().replace('/', '.');
	}

	private String joinLimited(List<String> names) {
		int shown = Math.min(names.size(), config.maxListed);
		String joined = String.join(", ", names.subList(0, shown));
		return shown < names.size() ? joined + " +" + (names.size() - shown) : joined;
	}

	/** Adds "Label: text" wrapped, with the label in its own colour. */
	private void labelled(Consumer<Component> out, String labelKey, String text) {
		if (text.isEmpty()) {
			return;
		}
		String label = Component.translatable(labelKey).getString() + " ";
		List<String> lines = wrap(label + text, 0);
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);
			MutableComponent component = Component.literal(indent());
			if (i == 0 && line.startsWith(label)) {
				component.append(Component.literal(label).withColor(config.labelColor))
						.append(Component.literal(line.substring(label.length())).withColor(config.extraColor));
			} else {
				component.append(Component.literal(line).withColor(config.extraColor));
			}
			out.accept(component);
		}
	}

	// ---------------------------------------------------------------- helpers

	/** Word-wraps plain text to the configured width (minus the indent). */
	private List<String> wrap(String text, int extraIndent) {
		Font font = Minecraft.getInstance().font;
		int width = Math.max(40, config.wrapWidth - font.width(indent()) - extraIndent);
		List<String> lines = new ArrayList<>();
		for (String paragraph : text.split("\n")) {
			StringBuilder line = new StringBuilder();
			for (String word : paragraph.split(" ")) {
				if (word.isEmpty()) {
					continue;
				}
				String candidate = line.isEmpty() ? word : line + " " + word;
				if (!line.isEmpty() && font.width(candidate) > width) {
					lines.add(line.toString());
					line = new StringBuilder(word);
				} else {
					line = new StringBuilder(candidate);
				}
			}
			if (!line.isEmpty()) {
				lines.add(line.toString());
			}
		}
		return lines;
	}

	private String indent() {
		return " ".repeat(Math.max(0, config.indent));
	}

	private static boolean isCurse(Holder<Enchantment> holder) {
		return holder.is(EnchantmentTags.CURSE);
	}

	/** The colour at two thirds brightness, for description lines of curses. */
	private static int dim(int color) {
		int r = (color >> 16 & 0xFF) * 2 / 3;
		int g = (color >> 8 & 0xFF) * 2 / 3;
		int b = (color & 0xFF) * 2 / 3;
		return 0xFF000000 | r << 16 | g << 8 | b;
	}

	private static boolean shiftDown() {
		return Minecraft.getInstance().hasShiftDown();
	}
}
