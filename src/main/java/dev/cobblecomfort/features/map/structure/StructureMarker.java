package dev.cobblecomfort.features.map.structure;

/**
 * A structure on the map. {@code type} is the structure id path (e.g. "village_plains", "stronghold").
 * Source: EXACT (asked the integrated server), SEED (calculated from the seed, a good estimate) or DETECTED (seen).
 */
public record StructureMarker(String type, int x, int z, Source source) {
	public enum Source {
		EXACT, SEED, DETECTED
	}
}
