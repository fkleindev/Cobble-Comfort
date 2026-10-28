package dev.cobblecomfort.features.map.seed;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.biome.TheEndBiomeSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * Vanilla world generation for one seed and dimension, built from the built-in registries (the client does not get
 * the server's worldgen data). Only correct for vanilla world generation.
 */
public final class SeedGenerator {
	private static HolderLookup.Provider vanilla;

	public final long seed;
	public final ResourceKey<Level> dimension;
	public final BiomeSource biomeSource;
	public final NoiseBasedChunkGenerator generator;
	public final RandomState randomState;
	public final LevelHeightAccessor heightAccessor;
	public final int seaLevel;

	private SeedGenerator(long seed, ResourceKey<Level> dimension, BiomeSource biomeSource, Holder<NoiseGeneratorSettings> settings) {
		this.seed = seed;
		this.dimension = dimension;
		this.biomeSource = biomeSource;
		this.generator = new NoiseBasedChunkGenerator(biomeSource, settings);
		this.randomState = RandomState.create(lookup().lookupOrThrow(Registries.NOISE), seed, settings.value());
		this.heightAccessor = LevelHeightAccessor.create(settings.value().noiseSettings().minY(), settings.value().noiseSettings().height());
		this.seaLevel = settings.value().seaLevel();
	}

	/** The built-in vanilla registries; built once (takes a moment). */
	public static synchronized HolderLookup.Provider lookup() {
		if (vanilla == null) {
			vanilla = VanillaRegistries.createWorldLookup();
		}
		return vanilla;
	}

	/** A generator for the dimension, or null for dimensions without vanilla generation (modded ones). */
	public static SeedGenerator create(long seed, ResourceKey<Level> dimension) {
		HolderLookup.Provider lookup = lookup();
		var settings = lookup.lookupOrThrow(Registries.NOISE_SETTINGS);
		if (dimension == Level.OVERWORLD) {
			var params = lookup.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
					.getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
			return new SeedGenerator(seed, dimension, MultiNoiseBiomeSource.createFromPreset(params), settings.getOrThrow(NoiseGeneratorSettings.OVERWORLD));
		}
		if (dimension == Level.NETHER) {
			var params = lookup.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
					.getOrThrow(MultiNoiseBiomeSourceParameterLists.NETHER);
			return new SeedGenerator(seed, dimension, MultiNoiseBiomeSource.createFromPreset(params), settings.getOrThrow(NoiseGeneratorSettings.NETHER));
		}
		if (dimension == Level.END) {
			return new SeedGenerator(seed, dimension, TheEndBiomeSource.create(lookup.lookupOrThrow(Registries.BIOME)),
					settings.getOrThrow(NoiseGeneratorSettings.END));
		}
		return null;
	}

	/** A biome lookup for one thread (resolvers cache and are not shared between threads). */
	public BiomeResolver resolver() {
		return biomeSource.createUncachedResolver(randomState);
	}

	/** Top of the solid ground (ignoring water) at a column. */
	public int floorHeight(int x, int z) {
		return generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, heightAccessor, randomState);
	}

	public Holder<Biome> biome(BiomeResolver resolver, int x, int y, int z) {
		return resolver.getNoiseBiome(x >> 2, y >> 2, z >> 2);
	}
}
