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

    public Main() throws IOException {
        File[] files = directory.listFiles(new ChunkStoreFilter());
        for (File file : files) {
            convertFile(file);
        }
        // TODO: Delete file
    }

    private void convertFile(File file) {
        // Parse coordinates
        String coordString = file.getName().substring(6);
        coordString = coordString.substring(0, coordString.length() - 5);
        String[] coords = coordString.split("_");
        int rx = Integer.valueOf(coords[0]);
        int rz = Integer.valueOf(coords[1]);
        if (rx >= 0 && rz >= 0) { // Only chunks with negative coords are messed up, so we can just rename positive ones
            file.renameTo(new File(directory, "mcmmo_" + rx + "_" + rz + "_.v1.mcm"));
            file.delete();
            return;
        }

        // TODO:  Convert non-positive files
    }

    public void writeNewRegionFile(int regionX, int regionZ) throws IOException {
        McMMOSimpleRegionFile newFile = new McMMOSimpleRegionFile(new File(directory, "mcmmo_" + regionX + "_" + regionZ + "_.v1.mcm"), regionX, regionZ);
        File bulkRegion = new File(directory, "mcmmo_" + regionX + "_" + regionZ + "_.mcm");
        File leftRegion = new File(directory, "mcmmo_" + (regionX + 1) + "_" + regionZ + "_.mcm");
        File lowerRegion = new File(directory, "mcmmo_" + regionX + "_" + (regionZ + 1) + "_.mcm");
        File lowerLeftRegion = new File(directory, "mcmmo_" + (regionX + 1) + "_" + (regionZ + 1) + "_.mcm");
        if (bulkRegion.isFile()) {
            int oldRegionX = regionX;
            int oldRegionZ = regionZ;
            McMMOSimpleRegionFile original = new McMMOSimpleRegionFile(bulkRegion, oldRegionX, oldRegionZ);

            int chunkX = oldRegionX << 5;
            if (chunkX < 0) {
                chunkX++;
            }
            for (; chunkX < (oldRegionX << 5) + 32; chunkX++) {
                int chunkZ = oldRegionZ << 5;
                if (chunkZ < 0) {
                    chunkZ++;
                }
                for (; chunkZ < (oldRegionZ << 5) + 32; chunkZ++) {
                    PrimitiveChunkStore chunk = getChunkStore(original, chunkX, chunkZ);
                    if (chunk == null) {
                        continue;
                    }
                    int newChunkX = chunkX;
                    if (chunkX < 0) {
                        newChunkX--;
                    }
                    int newChunkZ = chunkZ;
                    if (chunkZ < 0) {
                        newChunkZ--;
                    }
                    chunk.convertCoordinatesToVersionOne();
                    writeChunkStore(newFile, newChunkX, newChunkZ, chunk);
                }
            }
        }
        if (leftRegion.isFile() && regionX < 0) {
            int oldRegionX = regionX + 1;
            int oldRegionZ = regionZ;
            McMMOSimpleRegionFile original = new McMMOSimpleRegionFile(leftRegion, oldRegionX, oldRegionZ);

            int chunkX = (oldRegionX << 5);
            for (int chunkZ = oldRegionZ << 5; chunkZ < (oldRegionZ << 5) + 32; chunkZ++) {
                PrimitiveChunkStore chunk = getChunkStore(original, chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                int newChunkX = chunkX;
                if (chunkX < 0) {
                    newChunkX--;
                }
                int newChunkZ = chunkZ;
                if (chunkZ < 0) {
                    newChunkZ--;
                }
                chunk.convertCoordinatesToVersionOne();
                writeChunkStore(newFile, newChunkX, newChunkZ, chunk);
            }
        }
        if (lowerRegion.isFile() && regionZ < 0) {
            int oldRegionX = regionX;
            int oldRegionZ = regionZ + 1;
            McMMOSimpleRegionFile original = new McMMOSimpleRegionFile(lowerRegion, oldRegionX, oldRegionZ);

            int chunkZ = (oldRegionZ << 5);
            for (int chunkX = oldRegionX << 5; chunkX < (oldRegionX << 5) + 32; chunkX++) {
                PrimitiveChunkStore chunk = getChunkStore(original, chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                int newChunkX = chunkX;
                if (chunkX < 0) {
                    newChunkX--;
                }
                int newChunkZ = chunkZ;
                if (chunkZ < 0) {
                    newChunkZ--;
                }
                chunk.convertCoordinatesToVersionOne();
                writeChunkStore(newFile, newChunkX, newChunkZ, chunk);
            }
        }
        if (lowerLeftRegion.isFile() && regionX < 0 && regionZ < 0) {
            int oldRegionX = regionX + 1;
            int oldRegionZ = regionZ + 1;
            McMMOSimpleRegionFile original = new McMMOSimpleRegionFile(lowerLeftRegion, oldRegionX, oldRegionZ);

            int chunkZ = (oldRegionZ << 5);
            int chunkX = (oldRegionX << 5);
            PrimitiveChunkStore chunk = getChunkStore(original, chunkX, chunkZ);
            if (chunk != null) {
                int newChunkX = chunkX;
                if (chunkX < 0) {
                    newChunkX--;
                }
                int newChunkZ = chunkZ;
                if (chunkZ < 0) {
                    newChunkZ--;
                }
                chunk.convertCoordinatesToVersionOne();
                writeChunkStore(newFile, newChunkX, newChunkZ, chunk);
            }
        }
        newFile.close();
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

    class ChunkStoreFilter implements FilenameFilter {
        @Override
        public boolean accept(File dir, String name) {
            return name.endsWith(".mcm") && !name.endsWith(".v1.mcm"); // Grab unconverted files only
        }
    }
}
