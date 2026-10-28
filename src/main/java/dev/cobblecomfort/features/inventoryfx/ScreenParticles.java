package dev.cobblecomfort.features.inventoryfx;

import dev.cobblecomfort.features.hud.render.RenderUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/** A tiny 2D particle system in screen space (GUI pixels). */
public final class ScreenParticles {
	private static final float CRUMB_GRAVITY = 320f;
	private static final float SPARK_GRAVITY = 40f;
	private static final float AIR_DRAG = 1.5f;

	private final List<Particle> particles = new ArrayList<>();
	private final Random random = new Random();

	private static final class Particle {
		float x, y, vx, vy, age, life;
		int color;
		int size;
		boolean spark;
	}

	public boolean isEmpty() {
		return particles.isEmpty();
	}

	public void clear() {
		particles.clear();
	}

	/** Spawns one particle; (vx, vy) is the base velocity and a random velocity up to {@code spread} is added. */
	public void spawn(float x, float y, float vx, float vy, float spread, int color, boolean spark,
			float lifetime, int max) {
		if (particles.size() >= max) {
			particles.removeFirst();
		}
		Particle p = new Particle();
		p.x = x;
		p.y = y;
		double angle = random.nextDouble() * Math.PI * 2;
		float speed = spread * (0.3f + random.nextFloat() * 0.7f);
		p.vx = vx + (float) Math.cos(angle) * speed;
		p.vy = vy + (float) Math.sin(angle) * speed;
		p.life = lifetime * (0.6f + random.nextFloat() * 0.8f);
		p.color = color;
		p.spark = spark;
		p.size = spark ? 1 : (random.nextFloat() < 0.3f ? 2 : 1);
		particles.add(p);
	}

	public void update(float dt, float gravityScale) {
		Iterator<Particle> it = particles.iterator();
		while (it.hasNext()) {
			Particle p = it.next();
			p.age += dt;
			if (p.age >= p.life) {
				it.remove();
				continue;
			}
			float drag = (float) Math.exp(-AIR_DRAG * dt);
			p.vx *= drag;
			p.vy = p.vy * drag + (p.spark ? SPARK_GRAVITY : CRUMB_GRAVITY) * gravityScale * dt;
			p.x += p.vx * dt;
			p.y += p.vy * dt;
		}
	}

	public void draw(GuiGraphicsExtractor g) {
		for (Particle p : particles) {
			float remaining = 1f - p.age / p.life;
			int x = Math.round(p.x);
			int y = Math.round(p.y);
			if (p.spark) {
				// Twinkle: alpha flickers, plus-shaped while young.
				float twinkle = 0.6f + 0.4f * (float) Math.sin(p.age * 40 + p.x);
				int c = RenderUtil.withAlpha(p.color, remaining * twinkle);
				g.fill(x, y, x + 1, y + 1, c);
				if (remaining > 0.5f) {
					int arm = RenderUtil.withAlpha(p.color, remaining * twinkle * 0.5f);
					g.fill(x - 1, y, x, y + 1, arm);
					g.fill(x + 1, y, x + 2, y + 1, arm);
					g.fill(x, y - 1, x + 1, y, arm);
					g.fill(x, y + 1, x + 1, y + 2, arm);
				}
			} else {
				int c = RenderUtil.withAlpha(p.color, Math.min(1f, remaining * 2f));
				g.fill(x, y, x + p.size, y + p.size, c);
			}
		}
	}
}
