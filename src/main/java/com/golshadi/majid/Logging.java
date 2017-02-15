package com.golshadi.majid;

import timber.log.Timber;

public class Logging extends Timber.DebugTree {
    private static final String TAG = "Logging";
    private static Timber.DebugTree debugTree;

    public synchronized static void enable() {
        if (debugTree == null) {
            // validate debug tree doesn't exist
            for (Timber.Tree tree : Timber.forest()) {
                if (tree instanceof Timber.DebugTree) {
                    return;
                }
            }

            debugTree = new Timber.DebugTree();
            Timber.plant(debugTree);
        }
    }

    public synchronized static void disable() {
        if (debugTree != null) {
            Timber.uproot(debugTree);
            debugTree = null;
        }
    }


}