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
    public static File directory;
    public static void main(String[] args) throws IOException, InterruptedException {
        directory = new File(args[0]);
        new Main();
    }

    protected boolean done = false;

    public Main() throws IOException, InterruptedException {
        File[] files = directory.listFiles(new ChunkStoreFilter());
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

    public void writeNewRegionFile(int x, int z) throws IOException {
        McMMOSimpleRegionFile newFile = new McMMOSimpleRegionFile(new File(directory, "mcmmo_" + x + "_" + z + "_.v1.mcm"), x, z);
        File bulkRegion = new File(directory, "mcmmo_" + x + "_" + z + "_.mcm");
        File leftRegion = new File(directory, "mcmmo_" + (x + 1) + "_" + z + "_.mcm");
        File lowerRegion = new File(directory, "mcmmo_" + x + "_" + (z + 1) + "_.mcm");
        File lowerLeftRegion = new File(directory, "mcmmo_" + (x + 1) + "_" + (z + 1) + "_.mcm");
        if (bulkRegion.isFile()) {
            int rx = x;
            int rz = z;
            McMMOSimpleRegionFile original = new McMMOSimpleRegionFile(bulkRegion, rx, rz);

            int cx = rx << 5;
            if (cx < 0) {
                cx++;
            }
            for (; cx < (rx << 5) + 32; cx++) {
                int cz = rz << 5;
                if (cz < 0) {
                    cz++;
                }
                for (; cz < (rz << 5) + 32; cz++) {
                    ChunkStore chunk = getChunkStore(original, cx, cz);
                    if (chunk == null) {
                        continue;
                    }
                    int ncx = cx;
                    if (cx < 0) {
                        ncx--;
                    }
                    int ncz = cz;
                    if (cz < 0) {
                        ncz--;
                    }
                    writeChunkStore(newFile, ncx, ncz, chunk);
                }
            }
        }
        if (leftRegion.isFile() && x < 0) {
            int rx = x + 1;
            int rz = z;
            McMMOSimpleRegionFile original = new McMMOSimpleRegionFile(leftRegion, rx, rz);

            int cx = (rx << 5);
            for (int cz = rz << 5; cz < (rz << 5) + 32; cz++) {
                ChunkStore chunk = getChunkStore(original, cx, cz);
                if (chunk == null) {
                    continue;
                }
                int ncx = cx;
                if (cx < 0) {
                    ncx--;
                }
                int ncz = cz;
                if (cz < 0) {
                    ncz--;
                }
                writeChunkStore(newFile, ncx, ncz, chunk);
            }
        }
        if (lowerRegion.isFile() && z < 0) {
            int rx = x;
            int rz = z + 1;
            McMMOSimpleRegionFile original = new McMMOSimpleRegionFile(lowerRegion, rx, rz);

            int cz = (rz << 5);
            for (int cx = rx << 5; cx < (rx << 5) + 32; cx++) {
                ChunkStore chunk = getChunkStore(original, cx, cz);
                if (chunk == null) {
                    continue;
                }
                int ncx = cx;
                if (cx < 0) {
                    ncx--;
                }
                int ncz = cz;
                if (cz < 0) {
                    ncz--;
                }
                writeChunkStore(newFile, ncx, ncz, chunk);
            }
        }
        if (lowerLeftRegion.isFile() && x < 0 && z < 0) {
            int rx = x + 1;
            int rz = z + 1;
            McMMOSimpleRegionFile original = new McMMOSimpleRegionFile(lowerLeftRegion, rx, rz);

            int cz = (rz << 5);
            int cx = (rx << 5);
            ChunkStore chunk = getChunkStore(original, cx, cz);
            if (chunk != null) {
                int ncx = cx;
                if (cx < 0) {
                    ncx--;
                }
                int ncz = cz;
                if (cz < 0) {
                    ncz--;
                }
                writeChunkStore(newFile, ncx, ncz, chunk);
            }
        }
    }

    private void writeChunkStore(McMMOSimpleRegionFile file, int x, int z, ChunkStore data) {
        if (!data.isDirty()) {
            return;
        }
        try {
            ObjectOutputStream objectStream = new ObjectOutputStream(file.getOutputStream(x, z));
            objectStream.writeObject(data);
            objectStream.flush();
            objectStream.close();
            data.setDirty(false);
        } catch (IOException e) {
            throw new RuntimeException("Unable to write chunk meta data for " + x + ", " + z, e);
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
            file = new McMMOSimpleRegionFile(new File(directory, "mcmmo_" + rx + "_" + rz + "_.v1.mcm"), rx, rz);
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
            file.renameTo(new File(directory, "mcmmo_" + rx + "_" + rz + "_.v1.mcm"));
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
