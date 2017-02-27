package com.golshadi.majid.core.chunkWorker;

import com.golshadi.majid.Utils.helper.FileUtils;
import com.golshadi.majid.database.ChunksDataSource;
import com.golshadi.majid.database.elements.Chunk;
import com.golshadi.majid.database.elements.Task;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Created by Majid Golshadi on 4/15/2014.
 */
public class Rebuilder extends Thread{

    final List<Chunk> taskChunks;
    final Task task;
    final Moderator observer;

    public Rebuilder(Task task, List<Chunk> taskChunks, Moderator moderator){
        this.taskChunks = taskChunks;
        this.task = task;
        this.observer = moderator;
    }

    @Override
    public void run() {
        // notify to developer------------------------------------------------------------
        observer.downloadManagerListener.OnDownloadRebuildStart(task.id);

        final File file = new File(task.save_address, task.name);

        FileOutputStream finalFile = null;
        try {
            // append = false : overwrite existing file
            finalFile = new FileOutputStream(file, false);

            byte[] readBuffer = new byte[1024];
            int read;
            for (Chunk chunk : taskChunks) {
                FileInputStream chFileIn;
                try {
                    chFileIn = FileUtils.getInputStream(task.save_address, ChunksDataSource.getChunkFileName(chunk.id));
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                    observer.error(task.id, "Chunk file not found");
                    return;
                }

                try {
                    while ((read = chFileIn.read(readBuffer)) > 0) {
                        finalFile.write(readBuffer, 0, read);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    observer.error(task.id, "Merge chunk files error: " + e);
                    return;
                }


                try {
                    finalFile.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                    observer.error(task.id, "Merge chunk files error: " + e);
                    return;
                }

            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            observer.error(task.id, "Can't create output file: " + file);
            return;
        } finally {
            try {
                if (finalFile != null) finalFile.close();
            } catch (Throwable ignore) {
                ignore.printStackTrace();
            }
        }

        observer.reBuildIsDone(task, taskChunks);
    }
}
