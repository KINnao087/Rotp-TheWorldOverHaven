package com.inza.twoh.client.render.timestop;

import net.minecraft.util.math.MathHelper;

import java.util.Random;

/**
 * EN: Both clients derive the same continuous palette from a seed owned by one stop.
 * 中文：每次时停独有一个种子，让两端得到同一条连续的色相轨迹。
 */
final class CastAppearance {
    final boolean colorCovered;
    private final float startHue;
    private final float middleHue;
    private final float endHue;

    CastAppearance(long seed) {
        Random random = new Random(seed);
        colorCovered = random.nextBoolean();
        startHue = random.nextFloat();
        middleHue = startHue + hueStep(random);
        endHue = middleHue + hueStep(random);
    }

    private static float hueStep(Random random) {
        float distance = .18F + .27F * random.nextFloat();
        return random.nextBoolean() ? distance : -distance;
    }

    int tint(float progress) {
        float t = MathHelper.clamp(progress, 0.0F, 1.0F) * 2.0F;
        boolean first = t < 1.0F;
        if (!first) t -= 1.0F;
        float eased = t * t * (3.0F - 2.0F * t);
        float hue = MathHelper.lerp(eased, first ? startHue : middleHue, first ? middleHue : endHue);
        return MathHelper.hsvToRgb(hue - MathHelper.floor(hue), .67F, 1.0F);
    }
}

