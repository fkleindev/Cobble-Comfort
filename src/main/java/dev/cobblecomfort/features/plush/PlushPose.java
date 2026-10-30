package dev.cobblecomfort.features.plush;

/** How far each limb of a plush swings away from its rest pose right now, in radians. */
public record PlushPose(float headX, float headZ, float rightArmX, float rightArmZ, float leftArmX, float leftArmZ,
		float rightLegX, float leftLegX) {
	public static final PlushPose REST = new PlushPose(0, 0, 0, 0, 0, 0, 0, 0);
}
