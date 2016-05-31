package org.ethereum.sync;

import org.ethereum.config.SystemProperties;
import org.ethereum.core.BlockWrapper;
import org.ethereum.core.Blockchain;
import org.ethereum.listener.EthereumListener;
import org.ethereum.net.server.Channel;
import org.ethereum.net.server.ChannelManager;
import org.ethereum.sync.listener.CompositeSyncListener;
import org.ethereum.sync.listener.SyncListener;
import org.ethereum.sync.listener.SyncListenerAdapter;
import org.ethereum.sync.strategy.SyncStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.concurrent.*;

import static org.ethereum.sync.SyncState.*;

/**
 * @author Mikhail Kalinin
 * @since 14.07.2015
 */
@Component
public class SyncManager {

    private final static Logger logger = LoggerFactory.getLogger("sync");

    private static final long FORWARD_SWITCH_LIMIT = 100;
    private static final long BACKWARD_SWITCH_LIMIT = 200;

    @Autowired
    SystemProperties config;

    @Autowired
    Blockchain blockchain;

    @Autowired
    SyncQueue queue;

    @Autowired
    EthereumListener ethereumListener;

    @Autowired
    SyncPool pool;

    @Autowired
    ChannelManager channelManager;

    @Autowired
    SyncStrategy longSync;

    @Autowired
    CompositeSyncListener compositeSyncListener;

    @PostConstruct
    public void init() {

        // make it asynchronously
        new Thread(new Runnable() {
            @Override
            public void run() {

                if (!config.isSyncEnabled()) {
                    logger.info("Sync Manager: OFF");
                    return;
                }

                logger.info("Sync Manager: ON");

                // sync queue
                queue.init();

                // switch between Long and Short
                compositeSyncListener.add(onNewBlock);

                // recover a gap
                compositeSyncListener.add(onNoParent);

                // starting from long sync
                logger.info("Start Long sync");
                longSync.start();

                ethereumListener.onLongSyncStarted();

                if (logger.isInfoEnabled()) {
                    startLogWorker();
                }

            }
        }).start();
    }

    public boolean isSyncDone() {
        return !longSync.inProgress();
    }

    private void onSyncDone(boolean done) {

        channelManager.onSyncDone(done);

        if (done) {
            ethereumListener.onSyncDone();
            ethereumListener.onLongSyncDone();
            logger.info("Long sync is finished");
        } else {
            ethereumListener.onLongSyncStarted();
        }
    }

    // LISTENERS

    private final SyncListener onNewBlock = new SyncListenerAdapter() {

        @Override
        public void onNewBlockNumber(long newNumber) {
        }
    };

    private final SyncListener onNoParent = new SyncListenerAdapter() {

        @Override
        public void onNoParent(BlockWrapper block) {
        }
    };

    // LOGS

    private void startLogWorker() {
        Executors.newSingleThreadScheduledExecutor().scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                try {
                    pool.logActivePeers();
                    logger.info("\n");
                    logger.info("State {}\n", longSync.getState());
                } catch (Throwable t) {
                    t.printStackTrace();
                    logger.error("Exception in log worker", t);
                }
            }
        }, 0, 30, TimeUnit.SECONDS);
    }
}
