package com.golshadi.majid.core.mainWorker;

import android.util.Log;

import com.golshadi.majid.Utils.helper.FileUtils;
import com.golshadi.majid.core.chunkWorker.Moderator;
import com.golshadi.majid.core.chunkWorker.Rebuilder;
import com.golshadi.majid.core.enums.TaskStates;
import com.golshadi.majid.database.ChunksDataSource;
import com.golshadi.majid.database.TasksDataSource;
import com.golshadi.majid.database.elements.Chunk;
import com.golshadi.majid.database.elements.Task;

import java.io.IOException;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import timber.log.Timber;

/**
 * Created by Majid Golshadi on 4/20/2014.
 */
public class AsyncStartDownload extends Thread {

    private static final long MEGA_BYTE = 1024 * 1024L;
    private final TasksDataSource tasksDataSource;
    private final ChunksDataSource chunksDataSource;
    private final Moderator moderator;
    private final Task task;
    private final OkHttpClient okHttpClient;

    public AsyncStartDownload(TasksDataSource taskDs, ChunksDataSource chunkDs,
                              Moderator moderator, Task task, OkHttpClient okHttpClient) {
        this.tasksDataSource = taskDs;
        this.chunksDataSource = chunkDs;
        this.moderator = moderator;
        this.task = task;
        this.okHttpClient = okHttpClient;
    }

    @Override
    public void run() {
        // switch on task state
        switch (task.state) {

            case TaskStates.INIT:
                // -->get file info
                // -->save in table
                // -->slice file to some chunks ( in some case maybe user set 16 but we need only 4 chunk)
                //      and make file in directory
                // -->save chunks in tables

                try {
                    getTaskFileInfo(task);
                    convertTaskToChunks(task);
                    task.state = TaskStates.READY;
                    tasksDataSource.update(task);
                } catch (IOException e) {
                    e.printStackTrace();
                    Timber.e(e, "[%d] Init task failed.", task.id);
                    moderator.error(task.id, "Init task failed: " + e.getMessage());
                    return;
                }

            case TaskStates.READY:
            case TaskStates.PAUSED:
                // -->-->if it's not resumable
                //          * fetch chunks
                //          * delete it's chunk
                //          * delete old file
                //          * insert new chunk
                //          * make new file
                // -->start to download any chunk
                if (!task.resumable) {
                    deleteChunk(task);
                    try {
                        generateNewChunk(task);
                    } catch (IOException e) {
                        e.printStackTrace();
                        Timber.e(e, "[%d] Resume/start download task failed.", task.id);
                        moderator.error(task.id, "Can't create chunk file: " + e.getMessage());
                        break;
                    }
                }
                Timber.d("[%d] Start download", task.id);
                moderator.start(task);
                break;

            case TaskStates.DOWNLOAD_FINISHED:
                // -->rebuild general file
                // -->save in database
                // -->report to user
                Thread rb = new Rebuilder(task,
                        chunksDataSource.chunksRelatedTask(task.id), moderator);
                rb.run();

            case TaskStates.END:

            case TaskStates.DOWNLOADING:
                // -->do nothing
                break;
        }

        return;
    }

    private void getTaskFileInfo(Task task) throws IOException {
        if (task.size <= 0) {
            Request request = new Request.Builder().head().url(task.url)
                    .header("Range", "bytes=0-")
                    .build();
            Response response = okHttpClient.newCall(request).execute();
            if (!response.isSuccessful()) throw new IOException("Given URL response non-success status code: " + response.code() + ", " + response.message());
            if (response.isRedirect()) throw new IllegalStateException("OkHttpClient not following redirect");
            task.size = Long.parseLong(response.header("Content-Length", "0"));
        }
    }


    private void convertTaskToChunks(Task task) throws IOException {
        if (task.size == 0) {
            // it's NOT resumable!!
            // one chunk
            task.resumable = false;
            task.chunks = 1;
        } else {
            // resumable
            // depend on file size assign number of chunks; up to Maximum user
            task.resumable = true;
            int MaximumUserCHUNKS = task.chunks / 2;
            task.chunks = 1;

            for (int f = 1; f <= MaximumUserCHUNKS; f++)
                if (task.size > MEGA_BYTE * f)
                    task.chunks = f * 2;
        }


        // Change Task State
        int firstChunkID =
                chunksDataSource.insertChunks(task);
        makeFileForChunks(firstChunkID, task);
    }

    private void makeFileForChunks(int firstId, Task task) throws IOException {
        for (int endId = firstId + task.chunks; firstId < endId; firstId++)
            FileUtils.create(task.save_address, ChunksDataSource.getChunkFileName(firstId));
        // task chunk file name: ._1 ._2 ...
    }


    private void deleteChunk(Task task) {
        List<Chunk> TaskChunks = chunksDataSource.chunksRelatedTask(task.id);

        for (Chunk chunk : TaskChunks) {
            FileUtils.delete(task.save_address, ChunksDataSource.getChunkFileName(chunk.id));
            chunksDataSource.delete(chunk.id);
        }
    }

    private void generateNewChunk(Task task) throws IOException {
        int firstChunkID =
                chunksDataSource.insertChunks(task);
        makeFileForChunks(firstChunkID, task);
    }

}
