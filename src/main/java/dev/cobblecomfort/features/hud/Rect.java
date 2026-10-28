package dev.cobblecomfort.features.hud;

public record Rect(float x, float y, float w, float h) {
	public float right() {
		return x + w;
	}

	public float bottom() {
		return y + h;
	}

	public float pointX(Anchor anchor) {
		return x + w * anchor.fx;
	}

	public float pointY(Anchor anchor) {
		return y + h * anchor.fy;
	}

	public boolean contains(double px, double py) {
		return px >= x && px < x + w && py >= y && py < y + h;
	}

	public Rect offset(float dx, float dy) {
		return new Rect(x + dx, y + dy, w, h);
	}
}
