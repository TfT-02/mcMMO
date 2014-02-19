package com.gmail.nossr50;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import com.gmail.nossr50.util.blockmeta.chunkmeta.ChunkStore;
import com.gmail.nossr50.util.blockmeta.chunkmeta.McMMOSimpleRegionFile;
import com.gmail.nossr50.util.blockmeta.chunkmeta.PrimitiveChunkStore;

public class Main {
    public static final HashMap<Long, McMMOSimpleRegionFile> worldRegions = new HashMap<Long, McMMOSimpleRegionFile>();
    public static final HashMap<Long, McMMOSimpleRegionFile> newWorldRegions = new HashMap<Long, McMMOSimpleRegionFile>();
    public static final List<ChunkStore> chunkRegions = new ArrayList<ChunkStore>();
    public static String filePath;
    public static void main(String[] args) throws IOException, InterruptedException {
        filePath = args[0];
        new Main();
    }

    protected boolean done = false;

    public Main() throws IOException, InterruptedException {
        File[] files = new File(filePath).listFiles(new ChunkStoreFilter());
        for (File file : files) {
            convertSimpleRegionFile(file); // Load files for conversion
        }
    }
    private void convertSimpleRegionFile(File file) {

        try {
            McMMOSimpleRegionFile original = loadSimpleRegionFile(file);
            if (original == null) {
                return;
            }
            int rx = original.getX();
            int rz = original.getZ();
            // Grab all chunks from region
            for (int x = rx << 5; x < (rx << 5) + 32; x++) {
                for (int z = rz << 5; z < (rz << 5) + 32; z++) {
                    PrimitiveChunkStore chunk = getChunkStore(original, x, z);
                    if (chunk == null) {
                        continue;
                    }
                    chunk.update(); // Correct chunk coordinates
                    chunkRegions.add(chunk); // Add to save queue
                }
            }
            original.close();
            file.delete();
        } catch (Exception e) {
            e.printStackTrace();
        }
        while (!chunkRegions.isEmpty()) { // Save all chunks in queue
            try {
                // Get correct region file for storage
                ChunkStore chunk = chunkRegions.remove(0);
                McMMOSimpleRegionFile rf = getSimpleRegionFile(chunk.getChunkX(), chunk.getChunkZ());
                ObjectOutputStream objectStream = new ObjectOutputStream(rf.getOutputStream(chunk.getChunkX(), chunk.getChunkZ()));

                objectStream.writeObject(chunk); // Write chunk to file
                objectStream.flush();
                objectStream.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    private PrimitiveChunkStore getChunkStore(McMMOSimpleRegionFile rf, int x, int z) throws IOException {
        InputStream in = rf.getInputStream(x, z);
        if (in == null) {
            return null;
        }
        ObjectInputStream objectStream = new ObjectInputStream(in);
        try {
            Object o = objectStream.readObject();
            if (o instanceof PrimitiveChunkStore) {
                return (PrimitiveChunkStore) o;
            }

            throw new RuntimeException("Wrong class type read for chunk meta data for " + x + ", " + z);
        }
        catch (IOException e) {
            return null;
        }
        catch (ClassNotFoundException e) {
            return null;
        }
        finally {
            objectStream.close();
        }
    }

    private McMMOSimpleRegionFile getSimpleRegionFile(int x, int z) {
        int rx = x >> 5;
        int rz = z >> 5;

        long key2 = (((long) rx) << 32) | ((rz) & 0xFFFFFFFFL);
        McMMOSimpleRegionFile file = newWorldRegions.get(key2);
        if (file == null) {
            file = new McMMOSimpleRegionFile(new File(new File(filePath), "mcmmo_" + rx + "_" + rz + "_.v1.mcm"), rx, rz);
            newWorldRegions.put(key2, file);
        }
        return file;
    }

    private McMMOSimpleRegionFile loadSimpleRegionFile(File file) {
        // Parse coordinates
        String coordString = file.getName().substring(6);
        coordString = coordString.substring(0, coordString.length() - 5);
        String[] coords = coordString.split("_");
        int rx = Integer.valueOf(coords[0]);
        int rz = Integer.valueOf(coords[1]);
        if (rx >= 0 && rz >= 0) { // Only chunks with negative coords are messed up, so we can just rename positive ones
            file.renameTo(new File(new File(filePath), "mcmmo_" + rx + "_" + rz + "_.v1.mcm"));
            file.delete();
            return null;
        }

        return new McMMOSimpleRegionFile(file, rx, rz); // Store loaded file for conversion
    }

    class ChunkStoreFilter implements FilenameFilter {
        @Override
        public boolean accept(File dir, String name) {
            return name.endsWith(".mcm") && !name.endsWith(".v1.mcm"); // Grab unconverted files only
        }
    }
}
