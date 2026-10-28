package dev.cobblecomfort.features.toasts;

/** A shown toast: how many slots it takes and where it is drawn while it glides to its place. */
public interface ToastSlot {
	int cobblecomfort$slots();

	float cobblecomfort$y();

	void cobblecomfort$setY(float y);
}
