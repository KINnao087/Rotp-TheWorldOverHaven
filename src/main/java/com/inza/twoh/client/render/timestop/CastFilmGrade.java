package com.inza.twoh.client.render.timestop;

import net.minecraft.client.shader.ShaderInstance;
import net.minecraft.util.math.MathHelper;

final class CastFilmGrade {
    // EN: Keep direct-return's optical exposure, independently of the chosen palette.
    // 中文：保留旁观版曝光曲线，色相则取本次时停的调色板。
    private static final float[] EXPOSURE = {
        -0.0341440f, -0.0341440f, -0.0341440f, -0.0341440f, -0.0096342f, -0.0044306f,
        0.0121606f, 0.0050716f, 0.0169872f, 0.0472285f, -0.0720777f, 0.0208333f,
        -0.0076735f, -0.0054110f, 0.1083899f, 0.1700038f, 0.2195513f, 0.2400000f,
        0.2243778f, 0.1868213f, 0.1453431f, 0.1281486f, 0.0916478f, 0.0773190f,
        0.0342572f, 0.0025830f, -0.0627262f, -0.0881410f, 0.0017534f, -0.1048831f,
        -0.0945513f, -0.0593326f, -0.0141591f, 0.0108032f, 0.0764140f, 0.0856900f,
        0.1356900f, 0.1717383f, 0.1629148f, 0.2080128f, 0.2080128f,
    };

    private CastFilmGrade() {}

    static void apply(ShaderInstance effect, float progress, int tint) {
        float seconds = progress * (40.0F / 24.0F + 0.1F);
        float start = 17.0F / 24.0F;
        float span = 9.0F / 24.0F;
        float age = seconds - 0.1F * MathHelper.clamp((seconds - start) / (span + 0.1F), 0.0F, 1.0F);
        float frame = MathHelper.clamp(age * 24.0F, 0.0F, EXPOSURE.length - 1);
        int index = (int) frame;
        float exposure = MathHelper.lerp(frame - index, EXPOSURE[index],
                EXPOSURE[Math.min(index + 1, EXPOSURE.length - 1)]);
        effect.safeGetUniform("FilmTime").set(age);
        effect.safeGetUniform("FilmGrade").set(((tint >> 16) & 255) / 255.0F,
                ((tint >> 8) & 255) / 255.0F, (tint & 255) / 255.0F, exposure);
    }
}
