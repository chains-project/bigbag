package github.chains;

import com.hazelcast.core.EntryEvent;
import org.jivesoftware.openfire.cluster.NodeID;
import org.jivesoftware.openfire.plugin.util.cache.ClusteredCacheFactory;

public class Temp {
    public void entryExpired(EntryEvent event) {
        if (includeEventsFromLocalNode || !event.getMember().localMember()) {
            final NodeID eventNodeId = ClusteredCacheFactory.getNodeID(event.getMember());
            logger.trace("Processing entry expired event of node {} for key {}", eventNodeId, event.getKey());
            clusteredCacheEntryListener.entryEvicted((K) event.getKey(), (V) event.getOldValue(), eventNodeId);
        }
    }
}

