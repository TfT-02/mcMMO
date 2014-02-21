package com.gmail.nossr50;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import net.t00thpick1.mcmmo.com.wolvereness.overmapped.lib.MultiProcessor;

import com.gmail.nossr50.util.blockmeta.chunkmeta.ChunkStore;
import com.gmail.nossr50.util.blockmeta.chunkmeta.McMMOSimpleRegionFile;
import com.gmail.nossr50.util.blockmeta.chunkmeta.PrimitiveChunkStore;

@SuppressWarnings("javadoc")
public class Main {
    public static File directory;
    public static int threadCount = 5;
    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length == 0) {
            System.out.println("Need folder path");
            return;
        }
        directory = new File(args[0]);
        if (!directory.isDirectory()) {
            System.out.println("Folder path invalid");
            return;
        }
        if (args.length > 1) {
            threadCount = Integer.valueOf(args[1]);
        }
        new Main();
    }
    
    class Wrapper implements Runnable {
        private final AtomicInteger reads;
        private final int regionX;
        private final int regionZ;
        private final File file;
        private final Map<List<Integer>, Wrapper> wrappers;
        
        Wrapper(int x, int z, Map<List<Integer>, Wrapper> wrappers) {
            this.regionX = x;
            this.regionZ = z;
            this.wrappers = wrappers;
            
            this.file = new File(directory, "mcmmo_" + regionX + "_" + regionZ + "_.mcm");
            this.reads = new AtomicInteger(
                (regionX < 0) && (regionZ < 0)
                    ? 4
                    : 2
                );
        }
        
        void decrementUses() {
            if (reads.decrementAndGet() == 0 && file.exists()) {
                if (!file.delete()) {
                    System.err.println("Failed to delete `" + file + "'");
                }
            }
        }

        public void run() {
            try {
                writeNewRegionFile(regionX, regionZ, wrappers);
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }
    }

    public Main() throws IOException {
        File[] files = directory.listFiles(new ChunkStoreFilter());
        final Map<List<Integer>, Wrapper> toConvert = new LinkedHashMap<List<Integer>, Wrapper>();
        for (File file : files) {
            final int[] coords = parseFile(file);
            if ( coords == null ) {
                continue;
            }
            int x = coords[0];
            int z = coords[1];
            toConvert.put(Arrays.asList(x, z), null);
            if (x < 0) {
                toConvert.put(Arrays.asList(x - 1, z), null);
            }
            if (z < 0) {
                toConvert.put(Arrays.asList(x, z - 1), null);
            }
            if (x < 0 && z < 0) {
                toConvert.put(Arrays.asList(x - 1, z - 1), null);
            }
        }
        Object[] sorted = toConvert.keySet().toArray();
        Arrays.sort(sorted, new Comparator<Object>() {
            public int compare(List<Integer> o1, List<Integer> o2) {
                final int x1 = o1.get(0), z1 = o1.get(1);
                final int x2 = o2.get(0), z2 = o2.get(1);

                return (Math.abs(x1) + Math.abs(z1)) - (Math.abs(x2) + Math.abs(z2));
            }

            public int compare(Object o1, Object o2) {
                return compare((List<Integer>) o1, (List<Integer>) o2);
            }
        });

        toConvert.clear();

        for (Object object : sorted) {
            List<Integer> item = (List<Integer>) object;
            int x = item.get(0);
            int z = item.get(1);
            toConvert.put(item, new Wrapper(x, z, toConvert));
        }

        MultiProcessor processor = MultiProcessor.newMultiProcessor(threadCount, new ThreadFactory() {
            final AtomicInteger i = new AtomicInteger();
            public Thread newThread(Runnable r) {
                return new Thread(Main.class.getName() + "-Processor-" + i.incrementAndGet());
            }
        });

        List<Future<Wrapper>> tasks = new ArrayList<Future<Wrapper>>();
        for (final Wrapper wrapper : toConvert.values()) {
            final Callable<Wrapper> callable = new Callable<Wrapper>() {
                public Wrapper call() throws Exception {
                    wrapper.run();
                    return wrapper;
                }
            };
            tasks.add(processor.submit(callable));
        }
        
        for (int i = 0, l = tasks.size(); i < l; i++) {
            try {
                while (true) {
                    try {
                        Wrapper wrapper = tasks.get(i).get();
                        System.out.println((i + 1) + " / " + l + ", (" + wrapper.regionX + "," + wrapper.regionZ + ")");
                        break;
                    } catch (InterruptedException ex) { }
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

        processor.shutdown();
        File file = new File(directory, "mcMMO.format");
        if (!file.isFile()) {
            file.createNewFile();
        }
        ObjectOutputStream objectStream = new ObjectOutputStream(new FileOutputStream(file));
        objectStream.writeObject(1);
        objectStream.flush();
        objectStream.close();
    }

    private int[] parseFile(File file) {
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

        return new int[] { rx, rz };
    }

    public void writeNewRegionFile(int regionX, int regionZ, Map<List<Integer>, Wrapper> wrappers) throws IOException {
        McMMOSimpleRegionFile newFile = new McMMOSimpleRegionFile(new File(directory, "mcmmo_" + regionX + "_" + regionZ + "_.v1.mcm"), regionX, regionZ);
        File bulkRegion = new File(directory, "mcmmo_" + regionX + "_" + regionZ + "_.mcm");
        File leftRegion = new File(directory, "mcmmo_" + (regionX + 1) + "_" + regionZ + "_.mcm");
        File lowerRegion = new File(directory, "mcmmo_" + regionX + "_" + (regionZ + 1) + "_.mcm");
        File lowerLeftRegion = new File(directory, "mcmmo_" + (regionX + 1) + "_" + (regionZ + 1) + "_.mcm");
        if (bulkRegion.isFile()) {
            int oldRegionX = regionX;
            int oldRegionZ = regionZ;
            McMMOSimpleRegionFile original = new McMMOSimpleRegionFile(bulkRegion, oldRegionX, oldRegionZ, true);

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

            original.close();
            wrappers.get(Arrays.asList(oldRegionX, oldRegionZ)).decrementUses();
        }
        if (leftRegion.isFile() && regionX < 0) {
            int oldRegionX = regionX + 1;
            int oldRegionZ = regionZ;
            McMMOSimpleRegionFile original = new McMMOSimpleRegionFile(leftRegion, oldRegionX, oldRegionZ, true);

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

            original.close();
            wrappers.get(Arrays.asList(oldRegionX, oldRegionZ)).decrementUses();
        }
        if (lowerRegion.isFile() && regionZ < 0) {
            int oldRegionX = regionX;
            int oldRegionZ = regionZ + 1;
            McMMOSimpleRegionFile original = new McMMOSimpleRegionFile(lowerRegion, oldRegionX, oldRegionZ, true);

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

            original.close();
            wrappers.get(Arrays.asList(oldRegionX, oldRegionZ)).decrementUses();
        }
        if (lowerLeftRegion.isFile() && regionX < 0 && regionZ < 0) {
            int oldRegionX = regionX + 1;
            int oldRegionZ = regionZ + 1;
            McMMOSimpleRegionFile original = new McMMOSimpleRegionFile(lowerLeftRegion, oldRegionX, oldRegionZ, true);

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

            original.close();
            wrappers.get(Arrays.asList(oldRegionX, oldRegionZ)).decrementUses();
        }
        newFile.close();
    }

    private void writeChunkStore(McMMOSimpleRegionFile file, int x, int z, ChunkStore data) {
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
