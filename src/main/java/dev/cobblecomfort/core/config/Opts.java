package dev.cobblecomfort.core.config;

import dev.isxander.yacl3.api.NameableEnum;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.controller.ColorControllerBuilder;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.FloatSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import net.minecraft.network.chat.Component;

import java.awt.Color;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Small helpers to keep YACL option definitions short. Keys resolve to cobblecomfort.option.KEY and KEY.desc. */
public final class Opts {
	private Opts() {
	}

	public static Component name(String key) {
		return Component.translatable("cobblecomfort.option." + key);
	}

	public static OptionDescription desc(String key) {
		return OptionDescription.of(Component.translatable("cobblecomfort.option." + key + ".desc"));
	}

	public static Option<Boolean> bool(String key, boolean def, Supplier<Boolean> get, Consumer<Boolean> set) {
		return Option.<Boolean>createBuilder()
				.name(name(key))
				.description(desc(key))
				.binding(def, get, set)
				.controller(TickBoxControllerBuilder::create)
				.build();
	}

	public static Option<Float> slider(String key, float def, float min, float max, float step,
			Supplier<Float> get, Consumer<Float> set) {
		return Option.<Float>createBuilder()
				.name(name(key))
				.description(desc(key))
				.binding(def, get, set)
				.controller(opt -> FloatSliderControllerBuilder.create(opt).range(min, max).step(step))
				.build();
	}

	public static Option<Float> percent(String key, float def, Supplier<Float> get, Consumer<Float> set) {
		return Option.<Float>createBuilder()
				.name(name(key))
				.description(desc(key))
				.binding(def, get, set)
				.controller(opt -> FloatSliderControllerBuilder.create(opt).range(0f, 1f).step(0.05f)
						.formatValue(v -> Component.literal(Math.round(v * 100) + "%")))
				.build();
	}

	public static Option<Integer> intSlider(String key, int def, int min, int max, int step,
			Supplier<Integer> get, Consumer<Integer> set) {
		return Option.<Integer>createBuilder()
				.name(name(key))
				.description(desc(key))
				.binding(def, get, set)
				.controller(opt -> IntegerSliderControllerBuilder.create(opt).range(min, max).step(step))
				.build();
	}

	public static <E extends Enum<E> & NameableEnum> Option<E> enumOpt(String key, Class<E> type, E def,
			Supplier<E> get, Consumer<E> set) {
		return Option.<E>createBuilder()
				.name(name(key))
				.description(desc(key))
				.binding(def, get, set)
				.controller(opt -> EnumControllerBuilder.create(opt).enumClass(type))
				.build();
	}

	/** ARGB int colour stored in config, edited through YACL's colour picker. */
	public static Option<Color> color(String key, int def, boolean alpha, Supplier<Integer> get, Consumer<Integer> set) {
		return Option.<Color>createBuilder()
				.name(name(key))
				.description(desc(key))
				.binding(new Color(def, true), () -> new Color(get.get(), true), c -> set.accept(c.getRGB()))
				.controller(opt -> ColorControllerBuilder.create(opt).allowAlpha(alpha))
				.build();
	}
}
