package com.gmail.nossr50.runnables;

import org.bukkit.scheduler.BukkitRunnable;

import com.gmail.nossr50.mcMMO;

public class CheckChunkStoreTask extends BukkitRunnable {

    @Override
    public void run() {
        mcMMO.getPlaceStore().checkAllWorlds();
    }
}
