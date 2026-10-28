package dev.cobblecomfort.core;

import dev.cobblecomfort.core.config.ConfigManager;
import dev.isxander.yacl3.api.ConfigCategory;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

/**
 * A self-contained, individually toggleable module. Each feature owns one config file
 * and contributes one category to the config screen.
 */
public abstract class Feature<C extends FeatureConfig> {
	private final String id;
	private final Class<C> configClass;
	private final Supplier<C> defaults;
	protected C config;

	protected Feature(String id, Class<C> configClass, Supplier<C> defaults) {
		this.id = id;
		this.configClass = configClass;
		this.defaults = defaults;
	}

	public String id() {
		return id;
	}

	public Component name() {
		return Component.translatable("cobblecomfort.feature." + id);
	}

	public C config() {
		return config;
	}

	/** True when both the global master switch and this feature are on. */
	public boolean isEnabled() {
		return FeatureManager.general().enabled && config.enabled;
	}

	public void load() {
		config = ConfigManager.load(id, configClass, defaults);
		onConfigLoaded();
	}

	public void save() {
		ConfigManager.save(id, config);
	}

	/** Called once during client initialisation, after the config has been loaded. */
	public abstract void init();

	public abstract ConfigCategory buildCategory();

	protected void onConfigLoaded() {
	}
}
