package com.inza.twoh.client.render.timestop;

final class CastTiming {
    private static final float RETURN_TO_EXPANSION = 0.75F;
    private static final float WAVE_DELAY_TICKS = 1.0F;
    private static final float WAVE_SWEEP_TICKS = 4.0F;
    private static final float LENS_PEAK = 26.0F / 53.0F;

    private CastTiming() {}

    // EN: Reserve the wave delay, then divide the remaining ticks in a 4:3 ratio.
    // 中文：先预留冲击波延迟的一刻，再将剩余时间按 4:3 分给展开和收回。
    private static float expansionTicks(float duration) {
        return Math.max(0.0F, duration - WAVE_DELAY_TICKS) / (1.0F + RETURN_TO_EXPANSION);
    }

    private static float expansion(float ticks, float span) {
        float t = Math.max(0.0F, Math.min(ticks / span, 1.0F));
        return t * (0.3F + 0.7F * t);
    }

    // EN: Retime the existing lens track; preserve its original peak and return keyframes.
    // 中文：重新映射原有透镜时间轴，保留峰值和收缩阶段的关键帧。
    static float lensProgress(float ticks, float duration) {
        float span = expansionTicks(duration);
        if (span == 0.0F) return 1.0F;
        if (ticks < span) {
            return LENS_PEAK * expansion(ticks, span);
        }
        float closing = Math.min((ticks - span) / (span * RETURN_TO_EXPANSION), 1.0F);
        return LENS_PEAK + (1.0F - LENS_PEAK) * closing;
    }

    // EN: Linear apparent expansion for observers; preserve the native activation window.
    // 中文：旁观者启用画面匀速，保留原有释放总时长和收缩时间。
    static float observerProgress(float ticks, float duration) {
        float span = expansionTicks(duration);
        if (span > 0 && ticks < span) {
            return LENS_PEAK * ticks / span;
        }
        return lensProgress(ticks, duration);
    }

    static float radiusFraction(float ticks, float duration) {
        float p = lensProgress(ticks, duration);
        return Math.min(p / LENS_PEAK, (1.0F - p) / (1.0F - LENS_PEAK));
    }

    static float waveTravel(float ticks, float duration) {
        float span = expansionTicks(duration);
        if (span == 0.0F) return 0.0F;
        // EN: Run the same burst curve backwards, so each front retraces its path
        // at the same speed. Short activations shorten both sweeps equally.
        // 中文：回收时倒放同一条爆发曲线，每道波在同一位置的往返速度相等。
        // 若释放动画过短，则等比例缩短往返两段。
        float sweep = Math.min(WAVE_SWEEP_TICKS, span * RETURN_TO_EXPANSION);
        float returnStart = span + WAVE_DELAY_TICKS;
        float localTicks = ticks < returnStart ? ticks : sweep - (ticks - returnStart);
        float burst = Math.max(0.0F, Math.min(localTicks / sweep, 1.0F));
        return burst * (1.5F - 0.5F * burst);
    }
}
