package com.inza.twoh.rift;

/** EN: Sample the demo impulse and return waves from the authoritative clocks, even on late tracking.
 *  中文：用同步时钟计算冲击和回收波，后来进入追踪范围的客户端也从正确阶段开始。 */
public final class RiftMotion {
    static final float FLIGHT = .94F;
    static final float WAVE_GAP = .51F;

    static final int BAND_COUNT = 3;
    private static final float BRANCH_FADE = .70F;
    private static final float SLIT_FADE = .93F;
    public static final float MAX_CLOSE_SECONDS = (BAND_COUNT - 1) * WAVE_GAP + FLIGHT + BRANCH_FADE + SLIT_FADE;
    private static final int CLEANUP_GRACE_TICKS = 2;
    public static final int CLOSE_LIFETIME = (int) Math.ceil(MAX_CLOSE_SECONDS * 20) + CLEANUP_GRACE_TICKS;

    public static float phaseSalt(long seed) {
        return ((seed >>> 16) & 511) / 47.0F;
    }

    public static final class Pose {
        public float x, y, z, roll, tilt, loose, cos, sin, tiltCos, tiltSin;
        public boolean lifted;

        void finish() {
            cos = (float) Math.cos(roll);
            sin = (float) Math.sin(roll);
            tiltCos = (float) Math.cos(tilt);
            tiltSin = (float) Math.sin(tilt);
        }
    }

    public final Pose[] poses;
    public float slit = 1, branches = 1, front;
    private final float[] delay = new float[BAND_COUNT];

    public RiftMotion(int count) {
        poses = new Pose[count];
        for (int i = 0; i < count; i++) {
            poses[i] = new Pose();
        }
    }

    public static float clamp(float v) {
        return Math.max(0, Math.min(1, v));
    }

    static float ease(float v) {
        float u = clamp(v);
        return clamp(u * u * u * (10 + u * (-15 + 6 * u)));
    }

    public void sample(RiftMesh mesh, float age, float closeAt, float salt) {
        boolean closing = closeAt >= 0;
        float elapsed = age - closeAt;
        float landed = 0;
        if (closing) {
            // EN: An interrupted opening recalls only airborne fragments. Empty bands add no delay.
            // 中文：中途收回只召回已飞出的碎片，空圈不占用时间。
            boolean[] occupied = new boolean[BAND_COUNT];
            for (int i = 1; i < mesh.plates.length; i++) {
                RiftMesh.Plate p = mesh.plates[i];
                if (closeAt > p.release) {
                    occupied[p.band] = true;
                }
            }
            float next = 0;
            for (int i = 0; i < BAND_COUNT; i++) {
                delay[i] = next;
                if (occupied[i]) {
                    landed = next + FLIGHT;
                    next += WAVE_GAP;
                }
            }
        }
        for (int i = 0; i < poses.length; i++) {
            Pose m = poses[i];
            RiftMesh.Plate p = mesh.plates[i];
            float dt = (closing ? Math.min(age, closeAt + delay[p.band]) : age) - p.release;
            boolean eligible = !closing || closeAt > p.release;
            float keep = closing ? 1 - ease((elapsed - delay[p.band]) / FLIGHT) : 1;
            m.lifted = i != 0 && eligible && dt > 0 && keep > 0;
            m.x = m.y = m.z = m.loose = m.roll = m.tilt = 0;
            if (m.lifted) {
                float key = p.key + salt;
                float hand = Math.sin(key * 2.39) < 0 ? -1 : 1;
                float swing =
                        1 - (float) (Math.exp(-(3.6 + .46 * Math.sin(key * 2.17)) * dt) * Math.cos(7.6 * dt));
                float load = 1 - (float) Math.exp(-8.5 * dt);
                float kick = (float) (8.3 * dt * Math.exp(-5.8 * dt));
                float drift = load * load;
                float sequence = key * .618034F + .17F;
                float rank = sequence - (float) Math.floor(sequence);
                m.x = keep
                        * ((p.landingX - p.x) * swing + hand * .18F * kick
                                + .075F * drift * (float) Math.sin(1.27 * dt + key));
                m.y = keep
                        * ((p.landingY - p.y) * swing + .22F * kick
                                + .085F * drift * (float) Math.cos(1.51 * dt + key));
                m.z = keep
                        * ((.21F + 1.89F * rank) * load + (.82F + .75F * rank) * kick
                                + .066F * drift * (float) Math.sin(1.17 * dt + key * 2.03));
                m.roll = keep
                        * (hand * (.36F * swing + .88F * kick) + .11F * drift * (float) Math.sin(1.26 * dt + key));
                m.tilt = keep
                        * ((float) Math.sin(key * 1.83) * (.36F * swing + .71F * kick)
                                + .07F * drift * (float) Math.sin(1.43 * dt + key));
                m.loose = load * keep;
            }
            m.finish();
        }
        branches = closing ? 1 - ease((elapsed - landed) / BRANCH_FADE) : 1;
        slit = closing ? 1 - ease((elapsed - landed - BRANCH_FADE) / SLIT_FADE) : 1;
        float t = closing ? closeAt : age;
        front = branches * (surge(t, .1F, 2, 24) + surge(t, .4F, 2.35F, 18) + surge(t, .73F, 3.25F, 17));
    }

    private static float surge(float age, float start, float amount, float damping) {
        return age <= start ? 0 : amount * (1 - (float) Math.exp(-damping * (age - start)));
    }
}
