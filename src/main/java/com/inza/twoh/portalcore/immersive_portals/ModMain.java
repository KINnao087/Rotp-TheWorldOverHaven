// Modified for TWOH from Immersive Portals Forge, copyright 2020 qouteall, Apache-2.0.
package com.inza.twoh.portalcore.immersive_portals;

import com.inza.twoh.portalcore.hiding_in_the_bushes.MyNetwork;
import com.inza.twoh.portalcore.immersive_portals.api.IPDimensionAPI;
import com.inza.twoh.portalcore.immersive_portals.chunk_loading.ChunkDataSyncManager;
import com.inza.twoh.portalcore.immersive_portals.chunk_loading.EntitySync;
import com.inza.twoh.portalcore.immersive_portals.chunk_loading.NewChunkTrackingGraph;
import com.inza.twoh.portalcore.immersive_portals.chunk_loading.WorldInfoSender;
import com.inza.twoh.portalcore.immersive_portals.miscellaneous.GcMonitor;
import com.inza.twoh.portalcore.immersive_portals.my_util.MyTaskList;
import com.inza.twoh.portalcore.immersive_portals.my_util.Signal;
import com.inza.twoh.portalcore.immersive_portals.portal.PortalExtension;
import com.inza.twoh.portalcore.immersive_portals.portal.global_portals.GlobalPortalStorage;
import com.inza.twoh.portalcore.immersive_portals.teleportation.CollisionHelper;
import com.inza.twoh.portalcore.immersive_portals.teleportation.ServerTeleportationManager;

public class ModMain {
    public static final Signal postClientTickSignal = new Signal();
    public static final Signal postServerTickSignal = new Signal();
    public static final Signal preGameRenderSignal = new Signal();
    public static final MyTaskList clientTaskList = new MyTaskList();
    public static final MyTaskList serverTaskList = new MyTaskList();
    public static final MyTaskList preGameRenderTaskList = new MyTaskList();
    
    public static final MyTaskList preTotalRenderTaskList = new MyTaskList();
    
    public static final Signal clientCleanupSignal = new Signal();
    public static final Signal serverCleanupSignal = new Signal();
    
    public static void init() {
        Helper.log("TWOH rift engine initializing");
        
        MyNetwork.init();
        
        postClientTickSignal.connect(clientTaskList::processTasks);
        postServerTickSignal.connect(serverTaskList::processTasks);
        preGameRenderSignal.connect(preGameRenderTaskList::processTasks);
        
        clientCleanupSignal.connect(() -> {
            if (ClientWorldLoader.getIsInitialized()) {
                clientTaskList.forceClearTasks();
            }
        });
        serverCleanupSignal.connect(serverTaskList::forceClearTasks);
        
        Global.serverTeleportationManager = new ServerTeleportationManager();
        Global.chunkDataSyncManager = new ChunkDataSyncManager();
        
        NewChunkTrackingGraph.init();
        
        WorldInfoSender.init();
        
        GlobalPortalStorage.init();
        
        EntitySync.init();
        
        CollisionHelper.init();
        
        PortalExtension.init();
        
        GcMonitor.initCommon();
    
        IPDimensionAPI.init();
        
    }
    
}
