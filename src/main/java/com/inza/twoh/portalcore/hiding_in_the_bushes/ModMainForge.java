package com.inza.twoh.portalcore.hiding_in_the_bushes;

import com.inza.twoh.portalcore.immersive_portals.Global;

// TWOH replacement for upstream's standalone mod bootstrap. No portal items, dimensions or commands.
public final class ModMainForge {
    public static boolean enableModelDataFix = true;
    private ModMainForge() {}
    public static void applyServerConfigs() {
        Global.enableAlternateDimensions = false;
        Global.netherPortalMode = Global.NetherPortalMode.vanilla;
        Global.endPortalMode = Global.EndPortalMode.vanilla;
    }
}
