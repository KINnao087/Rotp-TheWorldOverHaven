package com.inza.twoh.rift.portal;

import net.minecraft.util.math.vector.Vector3d;
import javax.annotation.Nullable;

/**
 * EN: Front-to-back segment intersection adapted from Immersive Portals Portal.rayTrace.
 * 中文：由 Immersive Portals 的 Portal.rayTrace 改编的正面到背面的线段求交。
 * SPDX-License-Identifier: Apache-2.0
 * See META-INF/twoh-third-party/immersive-portals.txt.
 */
public final class RiftCrossing {
    private RiftCrossing() {
    }

    @Nullable
    public static Vector3d planeHit(Vector3d from, Vector3d to, Vector3d origin, Vector3d normal) {
        double before = from.subtract(origin).dot(normal);
        double after = to.subtract(origin).dot(normal);
        if (before <= 0 || after >= 0) {
            return null;
        }
        return from.add(to.subtract(from).scale(before / (before - after)));
    }
}
