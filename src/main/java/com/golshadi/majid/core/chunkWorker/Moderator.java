package com.golshadi.majid.core.chunkWorker;


import android.util.Log;
import android.util.SparseArray;

import com.golshadi.majid.Utils.helper.FileUtils;
import com.golshadi.majid.core.enums.TaskStates;
import com.golshadi.majid.core.mainWorker.QueueModerator;
import com.golshadi.majid.database.ChunksDataSource;
import com.golshadi.majid.database.TasksDataSource;
import com.golshadi.majid.database.elements.Chunk;
import com.golshadi.majid.database.elements.Task;
import com.golshadi.majid.report.ReportStructure;
import com.golshadi.majid.report.listener.DownloadManagerListenerModerator;

import java.util.List;

/**
 * Created by Majid Golshadi on 4/14/2014.
 * <p>
 * start
 * stop
 * downloader thread hear i call them AsyncWorker because i use AsyncTask instead of thread
 * for more information you can see these ref:
 */

public class Moderator {

    public static final String TAG = "Moderator";
    private ChunksDataSource chunksDataSource;  // query on chunk table
    private TasksDataSource tasksDataSource;    // query on task table
    DownloadManagerListenerModerator downloadManagerListener;

    private SparseArray<Thread> workerList;          // chunk downloader list
    private SparseArray<ReportStructure> processReports;  // to save download percent

    private QueueModerator finishedDownloadQueueObserver;

    public Moderator(TasksDataSource tasksDS, ChunksDataSource chunksDS) {
        tasksDataSource = tasksDS;
        chunksDataSource = chunksDS;
        workerList = new SparseArray<>(); // chunk downloader with they id key
        processReports = new SparseArray<>();
    }

    public void setQueueObserver(QueueModerator queueObserver) {
        finishedDownloadQueueObserver = queueObserver;
    }

    public void start(Task task, DownloadManagerListenerModerator listener) {
        downloadManagerListener = listener;
        // fetch task chunk info
        // set task state to Downloading
        // get any chunk file size calculate where it has to begin
        // start any of them as AsyncTask

        // fetch task chunk info
        List<Chunk> taskChunks = chunksDataSource.chunksRelatedTask(task.id);
        ReportStructure rps = processReports.get(task.id);
        if (rps == null)
            rps = new ReportStructure();
        rps.setObjectValues(task, taskChunks);
        processReports.put(task.id, rps);

        long downloaded;
        long totalSize;
        if (taskChunks != null) {

            // set task state to Downloading
            // to lock start download again!
            task.state = TaskStates.DOWNLOADING;
            tasksDataSource.update(task);

            // get any chunk file size calculate
            for (Chunk chunk : taskChunks) {

                downloaded = FileUtils.size(task.save_address, String.valueOf(chunk.id));
                totalSize = chunk.end - chunk.begin + 1;

                if (!task.resumable) {
                    chunk.begin = 0;
                    chunk.end = 0;
                    // start one chunk as AsyncTask (duplicate code!! :( )                    
                    Thread chunkDownloaderThread = new AsyncWorker(task, chunk, this);
                    workerList.put(chunk.id, chunkDownloaderThread);
                    chunkDownloaderThread.start();

                    // sure: only one chunk for unresumable task
                    break;
                } else if (downloaded != totalSize) {
                    // where it has to begin
                    // modify start point but i have not save it in Database
                    chunk.begin = chunk.begin + downloaded;

                    // start any of them as AsyncTask
                    Thread chunkDownloaderThread = new AsyncWorker(task, chunk, this);
                    workerList.put(chunk.id, chunkDownloaderThread);
                    chunkDownloaderThread.start();
                }
            }

            // notify to developer------------------------------------------------------------
            downloadManagerListener.OnDownloadStarted(task.id);
        }
    }

    /*
     * pause all chunk thread related to one Task
     */
    public Task pause(int taskID) {
        Log.d(TAG, "pause() called with: taskID = [" + taskID + "]");
        Task task = tasksDataSource.getTaskInfo(taskID);

        if (task != null && task.state != TaskStates.PAUSED && task.state != TaskStates.ERROR) {
            // pause task asyncWorker
            // change task state
            // save in DB
            // notify developer

            // pause task asyncWorker
            List<Chunk> taskChunks =
                    chunksDataSource.chunksRelatedTask(task.id);
            for (Chunk chunk : taskChunks) {
                Thread worker = workerList.get(chunk.id);
                if (worker != null) {
                    worker.interrupt();
                    workerList.remove(chunk.id);
                }
            }

            // change task state
            // save in DB
            task.state = TaskStates.PAUSED;
            tasksDataSource.update(task);

            final ReportStructure rs = processReports.get(taskID);
            if (rs != null)
                rs.setObjectValues(task, taskChunks);

            // notify to developer------------------------------------------------------------
            downloadManagerListener.OnDownloadPaused(task.id);

        }
        return task;
    }

    /*
    to calculate download percentage
    if download task is un resumable it return -1 as percent
     */
    private int downloadByteThreshold = 0;
    private static final int THRESHOLD = 1024 * 50;

    public void process(int taskId, long byteRead) {
        downloadManagerListener.countBytesDownloaded(byteRead);

        ReportStructure report = processReports.get(taskId);
        double percent = -1;
        long downloadLength = report
                .increaseDownloadedLength(byteRead);

        downloadByteThreshold += byteRead;
        if (downloadByteThreshold > THRESHOLD) {
            downloadByteThreshold = 0;

            if (report.isResumable()) {
                percent = ((float) downloadLength / report.getTotalSize() * 100);
            }

            // notify to developer------------------------------------------------------------
            downloadManagerListener.onDownloadProcess(taskId, percent, downloadLength);
        }
    }

    public void rebuild(Chunk chunk) {
        workerList.remove(chunk.id);
        List<Chunk> taskChunks =
                chunksDataSource.chunksRelatedTask(chunk.task_id); // delete itself from worker list

        for (Chunk ch : taskChunks) {
            if (workerList.get(ch.id) != null)
                return;
        }

        Task task = tasksDataSource.getTaskInfo(chunk.task_id);

        // set state task state to finished
        task.state = TaskStates.DOWNLOAD_FINISHED;
        tasksDataSource.update(task);

        // notify to developer------------------------------------------------------------
        downloadManagerListener.OnDownloadFinished(task.id);

        // assign chunk files together
        Thread t = new Rebuilder(task, taskChunks, this);
        t.start();
    }

    public void reBuildIsDone(Task task, List<Chunk> taskChunks) {
        // delete chunk row from chunk table
        for (Chunk chunk : taskChunks) {
            chunksDataSource.delete(chunk.id);
            FileUtils.delete(task.save_address, String.valueOf(chunk.id));
        }

        // notify to developer------------------------------------------------------------
        downloadManagerListener.OnDownloadRebuildFinished(task.id);

        // change task row state
        task.state = TaskStates.END;
        task.notify = false;
        tasksDataSource.update(task);

        // notify to developer------------------------------------------------------------
        downloadManagerListener.OnDownloadCompleted(task.id);

        wakeUpObserver(task.id);
    }

    private void wakeUpObserver(int taskID) {
        if (finishedDownloadQueueObserver != null) {

            finishedDownloadQueueObserver.wakeUp(taskID);

        }
    }

    public void error(int taskId, String errorMessage) {
        pause(taskId);
        Task task = tasksDataSource.getTaskInfo(taskId);
        task.state = TaskStates.ERROR;
        task.errorMessage = errorMessage;
        tasksDataSource.update(task);

        // clean up
        List<Chunk> taskChunks =
                chunksDataSource.chunksRelatedTask(task.id);
        for (Chunk chunk : taskChunks) {
            FileUtils.delete(task.save_address, String.valueOf(chunk.id));
            chunksDataSource.delete(chunk.id);
        }

        long size = FileUtils.size(task.save_address, task.name);
        if (size > 0) {
            FileUtils.delete(task.save_address, task.name);
        }

        downloadManagerListener.onDownloadError(taskId, errorMessage);

        if (finishedDownloadQueueObserver != null) {
            finishedDownloadQueueObserver.wakeUp(taskId);
        }
    }

    public void putReport(ReportStructure rs) {
        processReports.put(rs.id, rs);
    }

    public ReportStructure getReport(int taskId) {
        return processReports.get(taskId);
    }

    public void putAllReport(List<ReportStructure> reportStructures) {
        for (ReportStructure reportStructure : reportStructures) {
            processReports.put(reportStructure.id, reportStructure);
        }
    }
}
