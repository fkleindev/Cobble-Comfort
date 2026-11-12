package dev.cobblecomfort.features.wardrobe;

import dev.cobblecomfort.features.wardrobe.CompanionTexture.Material;

/**
 * What each worn piece is made of, textured like the companions: felt hats in cloth, beanies knitted, straw hats
 * plaited, wings feathered. Small parts (gems, buttons, frames, eyes) stay plain by themselves; boxes that are made of
 * something else say so ({@link HatLayer.Box#in}).
 */
final class WornMaterials {
	private WornMaterials() {
	}

	static Material of(Hat.Style style) {
		return switch (style) {
			case CROWN, TIARA -> Material.METAL;
			case WREATH, LEAVES -> Material.LEAF;
			case AVIATOR, COWBOY -> Material.LEATHER;
			case STRAW, SOMBRERO -> Material.STRAW;
			case BEANIE -> Material.KNIT;
			case USHANKA, BUNNY, FOX, BAT, REINDEER -> Material.FUR;
			case PIRATE, FISHER, CHEF, CAP, NIGHTCAP, PROPELLER, MORTARBOARD, TOPHAT, BERET, TYROLEAN, CAPTAIN, NINJA,
				 WITCH, SANTA, ELF -> Material.CLOTH;
			case ACORN -> Material.WOOD;
			case MUSHROOM -> Material.CAP;
			case PUMPKIN -> Material.PUMPKIN;
			case AXOLOTL, HORNS -> Material.SKIN;
			case MINER, HEADPHONES, PARTY -> Material.SOFT;
			case HALO, SPARKLE -> Material.PLAIN;
		};
	}

	static Material of(Face.Style style) {
		return switch (style) {
			case MASQUERADE, BANDANA -> Material.CLOTH;
			case EYEPATCH, GOGGLES -> Material.LEATHER;
			case MUSTACHE -> Material.FUR;
			case PIPE -> Material.WOOD;
			case HALLOWEEN_MASK, REINDEER_NOSE, CLOWN_NOSE -> Material.SOFT;
			// Glass and paint on the skin stay flat.
			case GLASSES, SUNGLASSES, BLUSH, FRECKLES, WAR_PAINT -> Material.PLAIN;
		};
	}

	static Material of(Neck.Style style) {
		return switch (style) {
			case SCARF, NORDIC -> Material.KNIT;
			case BOWTIE, TIE, BANDANA, VAMPIRE, AVIATOR -> Material.CLOTH;
			case NECKLACE, PUMPKIN_CHAIN -> Material.METAL;
			case BELL -> Material.LEATHER;
			case BOA -> Material.DOWN;
			case LEAF_SCARF, LEI -> Material.LEAF;
			case HEADPHONES, BONES -> Material.SOFT;
			case LIGHTS, TINSEL -> Material.PLAIN;
		};
	}

	static Material of(BackPiece.Style style) {
		return switch (style) {
			case HIKING, GIFT_SACK -> Material.CLOTH;
			case WINGS -> Material.FEATHER;
			case SWORD -> Material.LEATHER;
			case GUITAR, ROD, BROOM, SCYTHE -> Material.WOOD;
			case SHELL -> Material.SHELL;
			case PLUSH_WOLF, PLUSH_CAT -> Material.FUR;
			case BEEHIVE -> Material.STRAW;
			case BAT_WINGS -> Material.SKIN;
			case PUMPKIN_PACK -> Material.PUMPKIN;
			case TREE -> Material.LEAF;
			case ROCKETS -> Material.CARDBOARD;
			case ICE_WINGS -> Material.PLAIN;
		};
	}
}
