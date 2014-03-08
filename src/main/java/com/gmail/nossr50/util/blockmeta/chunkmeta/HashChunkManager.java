package com.gmail.nossr50.util.blockmeta.chunkmeta;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;

import com.gmail.nossr50.mcMMO;

public class HashChunkManager implements ChunkManager {
    private static final int FORMAT = 1;
    private HashMap<UUID, HashMap<Long, McMMOSimpleRegionFile>> regionFiles = new HashMap<UUID, HashMap<Long, McMMOSimpleRegionFile>>();
    public HashMap<String, ChunkStore> store = new HashMap<String, ChunkStore>();
    private HashMap<UUID, Boolean> oldData = new HashMap<UUID, Boolean>();

    @Override
    public synchronized void closeAll() {
        for (UUID uid : regionFiles.keySet()) {
            HashMap<Long, McMMOSimpleRegionFile> worldRegions = regionFiles.get(uid);
            for (Iterator<McMMOSimpleRegionFile> worldRegionIterator = worldRegions.values().iterator(); worldRegionIterator.hasNext();) {
                McMMOSimpleRegionFile rf = worldRegionIterator.next();
                if (rf != null) {
                    rf.close();
                    worldRegionIterator.remove();
                }
            }
        }
        regionFiles.clear();
    }

    @Override
    public synchronized ChunkStore readChunkStore(World world, int x, int z) throws IOException {
        McMMOSimpleRegionFile rf = getSimpleRegionFile(world, x, z);
        InputStream in = rf.getInputStream(x, z);
        if (in == null) {
            return null;
        }
        ObjectInputStream objectStream = new ObjectInputStream(in);
        try {
            Object o = objectStream.readObject();
            if (o instanceof ChunkStore) {
                return (ChunkStore) o;
            }

            throw new RuntimeException("Wrong class type read for chunk meta data for " + x + ", " + z);
        }
        catch (IOException e) {
            // Assume the format changed
            return null;
            //throw new RuntimeException("Unable to process chunk meta data for " + x + ", " + z, e);
        }
        catch (ClassNotFoundException e) {
            // Assume the format changed
            //System.out.println("[SpoutPlugin] is Unable to find serialized class for " + x + ", " + z + ", " + e.getMessage());
            return null;
            //throw new RuntimeException("Unable to find serialized class for " + x + ", " + z, e);
        }
        finally {
            objectStream.close();
        }
    }

    @Override
    public synchronized void writeChunkStore(World world, int x, int z, ChunkStore data) {
        if (!data.isDirty()) {
            return;
        }
        try {
            McMMOSimpleRegionFile rf = getSimpleRegionFile(world, x, z);
            ObjectOutputStream objectStream = new ObjectOutputStream(rf.getOutputStream(x, z));
            objectStream.writeObject(data);
            objectStream.flush();
            objectStream.close();
            data.setDirty(false);
        }
        catch (IOException e) {
            throw new RuntimeException("Unable to write chunk meta data for " + x + ", " + z, e);
        }
    }

    @Override
    public synchronized void closeChunkStore(World world, int x, int z) {
        McMMOSimpleRegionFile rf = getSimpleRegionFile(world, x, z);
        if (rf != null) {
            rf.close();
        }
    }

    private synchronized McMMOSimpleRegionFile getSimpleRegionFile(World world, int x, int z) {
        File directory = new File(world.getWorldFolder(), "mcmmo_regions");

        directory.mkdirs();

        UUID key = world.getUID();

        HashMap<Long, McMMOSimpleRegionFile> worldRegions = regionFiles.get(key);

        if (worldRegions == null) {
            worldRegions = new HashMap<Long, McMMOSimpleRegionFile>();
            regionFiles.put(key, worldRegions);
        }

        int rx = x >> 5;
        int rz = z >> 5;

        long key2 = (((long) rx) << 32) | ((rz) & 0xFFFFFFFFL);

        McMMOSimpleRegionFile regionFile = worldRegions.get(key2);

        if (regionFile == null) {
            File file;
            if (oldData.get(world.getUID())) {
                file = new File(directory, "mcmmo_" + rx + "_" + rz + "_.mcm");
            } else {
                file = new File(directory, "mcmmo_" + rx + "_" + rz + "_.v" + FORMAT + ".mcm");
            }
            regionFile = new McMMOSimpleRegionFile(file, rx, rz);
            worldRegions.put(key2, regionFile);
        }

        return regionFile;
    }

    @Override
    public synchronized void loadChunklet(int cx, int cy, int cz, World world) {
        loadChunk(cx, cz, world, null);
    }

    @Override
    public synchronized void unloadChunklet(int cx, int cy, int cz, World world) {
        unloadChunk(cx, cz, world);
    }

    @Override
    public void checkAllWorlds() {
        mcMMO.p.debug("Checking all worlds if the old mcMMO ChunkStore format is present.");

        List<String> worldList = new ArrayList();
        for (World worlds : mcMMO.p.getServer().getWorlds()) {
            File file = new File(new File(worlds.getWorldFolder(), "mcmmo_regions"), "mcMMO.format");
            UUID key = worlds.getUID();

            if (!file.isFile()) {
                worldList.add(worlds.getName());
                oldData.put(key, true);
            }
            else {
                oldData.put(key, false);
            }
        }

        mcMMO.p.getLogger().severe("The following worlds are using a mcMMO ChunkStore format with known exploits and need conversion:");
        mcMMO.p.getLogger().severe(worldList.toString());
        mcMMO.p.getLogger().severe("It is recommended that you convert these worlds to the new file format for them immediately.");
        mcMMO.p.getLogger().severe("Instructions to do so can be found at www.placeholderURL.com"); // TODO: Real link
        saveWorldsToConvert(worldList);
    }

    public static void saveWorldsToConvert(List<String> worlds) {
        File toConvertFile = new File(mcMMO.p.getDataFolder().getParentFile().getParent(), "worldsToConvert.yml");

        if (toConvertFile.exists()) {
            if (!toConvertFile.delete()) {
                mcMMO.p.getLogger().warning("Could not delete worldsToConvert.yml");
                return;
            }
        }

        try {
            FileWriter fileWriter = new FileWriter(toConvertFile);
            String newLine = System.getProperty("line.separator");

            for (String worldName : worlds) {
                fileWriter.write(worldName + newLine);
            }
            fileWriter.close();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public synchronized void loadChunk(int cx, int cz, World world, Entity[] entities) {
        if (world == null || store.containsKey(world.getName() + "," + cx + "," + cz)) {
            return;
        }

        UUID key = world.getUID();

        if (!oldData.containsKey(key)) {
            // TODO: Eventually use FORMAT version instead of boolean, but right now that doesn't matter
            File file = new File(new File(world.getWorldFolder(), "mcmmo_regions"), "mcMMO.format");
            if (!file.isFile()) {
                mcMMO.p.getLogger().severe("World " + world.getName() + " is using a mcMMO ChunkStore format with known exploits.");
            }
        }
        else if (oldData.get(key)) {
            // Old files are off by one
            if (cx < 0) {
                cx++;
            }
            if (cz < 0) {
                cz++;
            }
        }

        ChunkStore chunkStore = null;

        try {
            chunkStore = readChunkStore(world, cx, cz);
        }
        catch (Exception e) {}

        if (chunkStore == null) {
            return;
        }

        store.put(world.getName() + "," + cx + "," + cz, chunkStore);
    }

    @Override
    public synchronized void unloadChunk(int cx, int cz, World world) {
        saveChunk(cx, cz, world);

        if (store.containsKey(world.getName() + "," + cx + "," + cz)) {
            store.remove(world.getName() + "," + cx + "," + cz);

            //closeChunkStore(world, cx, cz);
        }
    }

    @Override
    public synchronized void saveChunk(int cx, int cz, World world) {
        if (world == null) {
            return;
        }

        String key = world.getName() + "," + cx + "," + cz;

        if (store.containsKey(key)) {
            ChunkStore out = store.get(world.getName() + "," + cx + "," + cz);

            if (!out.isDirty()) {
                return;
            }

            writeChunkStore(world, cx, cz, out);
        }
    }

    @Override
    public synchronized boolean isChunkLoaded(int cx, int cz, World world) {
        if (world == null) {
            return false;
        }

        return store.containsKey(world.getName() + "," + cx + "," + cz);
    }

    @Override
    public synchronized void chunkLoaded(int cx, int cz, World world) {}

    @Override
    public synchronized void chunkUnloaded(int cx, int cz, World world) {
        if (world == null) {
            return;
        }

        unloadChunk(cx, cz, world);
    }

    @Override
    public synchronized void saveWorld(World world) {
        if (world == null) {
            return;
        }

        closeAll();
        String worldName = world.getName();

        List<String> keys = new ArrayList<String>(store.keySet());
        for (String key : keys) {
            String[] info = key.split(",");
            if (worldName.equals(info[0])) {
                try {
                    saveChunk(Integer.parseInt(info[1]), Integer.parseInt(info[2]), world);
                }
                catch (Exception e) {
                    // Ignore
                }
            }
        }
    }

    @Override
    public synchronized void unloadWorld(World world) {
        if (world == null) {
            return;
        }

        closeAll();
        String worldName = world.getName();

        List<String> keys = new ArrayList<String>(store.keySet());
        for (String key : keys) {
            String[] info = key.split(",");
            if (worldName.equals(info[0])) {
                try {
                    unloadChunk(Integer.parseInt(info[1]), Integer.parseInt(info[2]), world);
                }
                catch (Exception e) {
                    // Ignore
                }
            }
        }
    }

    @Override
    public synchronized void loadWorld(World world) {}

    @Override
    public synchronized void saveAll() {
        closeAll();

        for (World world : mcMMO.p.getServer().getWorlds()) {
            saveWorld(world);
        }
    }

    @Override
    public synchronized void unloadAll() {
        closeAll();

        for (World world : mcMMO.p.getServer().getWorlds()) {
            unloadWorld(world);
        }
    }

    @Override
    public synchronized boolean isTrue(int x, int y, int z, World world) {
        if (world == null) {
            return false;
        }

        int cx = x >> 4;
        int cz = z >> 4;
        String key = world.getName() + "," + cx + "," + cz;

        if (!store.containsKey(key)) {
            loadChunk(cx, cz, world, null);
        }

        if (!store.containsKey(key)) {
            return false;
        }

        ChunkStore check = store.get(key);
        int ix = Math.abs(x) % 16;
        int iz = Math.abs(z) % 16;

        return check.isTrue(ix, y, iz);
    }

    @Override
    public synchronized boolean isTrue(Block block) {
        if (block == null) {
            return false;
        }

        return isTrue(block.getX(), block.getY(), block.getZ(), block.getWorld());
    }

    @Override
    public synchronized boolean isTrue(BlockState blockState) {
        if (blockState == null) {
            return false;
        }

        return isTrue(blockState.getX(), blockState.getY(), blockState.getZ(), blockState.getWorld());
    }

    @Override
    public synchronized void setTrue(int x, int y, int z, World world) {
        if (world == null) {
            return;
        }

        int cx = x >> 4;
        int cz = z >> 4;

        int ix = Math.abs(x) % 16;
        int iz = Math.abs(z) % 16;

        String key = world.getName() + "," + cx + "," + cz;

        if (!store.containsKey(key)) {
            loadChunk(cx, cz, world, null);
        }

        ChunkStore cStore = store.get(key);

        if (cStore == null) {
            cStore = ChunkStoreFactory.getChunkStore(world, cx, cz);
            store.put(key, cStore);
        }

        cStore.setTrue(ix, y, iz);
    }

    @Override
    public synchronized void setTrue(Block block) {
        if (block == null) {
            return;
        }

        setTrue(block.getX(), block.getY(), block.getZ(), block.getWorld());
    }

    @Override
    public void setTrue(BlockState blockState) {
        if (blockState == null) {
            return;
        }

        setTrue(blockState.getX(), blockState.getY(), blockState.getZ(), blockState.getWorld());
    }

    @Override
    public synchronized void setFalse(int x, int y, int z, World world) {
        if (world == null) {
            return;
        }

        int cx = x >> 4;
        int cz = z >> 4;

        int ix = Math.abs(x) % 16;
        int iz = Math.abs(z) % 16;

        String key = world.getName() + "," + cx + "," + cz;

        if (!store.containsKey(key)) {
            loadChunk(cx, cz, world, null);
        }

        ChunkStore cStore = store.get(key);

        if (cStore == null) {
            return; // No need to make a store for something we will be setting to false
        }

        cStore.setFalse(ix, y, iz);
    }

    @Override
    public synchronized void setFalse(Block block) {
        if (block == null) {
            return;
        }

        setFalse(block.getX(), block.getY(), block.getZ(), block.getWorld());
    }

    @Override
    public synchronized void setFalse(BlockState blockState) {
        if (blockState == null) {
            return;
        }

        setFalse(blockState.getX(), blockState.getY(), blockState.getZ(), blockState.getWorld());
    }

    @Override
    public synchronized void cleanUp() {}
}
