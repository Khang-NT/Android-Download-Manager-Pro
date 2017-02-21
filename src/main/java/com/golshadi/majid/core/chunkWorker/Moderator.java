package com.golshadi.majid.core.chunkWorker;


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

import java.io.File;
import java.util.List;

import okhttp3.OkHttpClient;
import rx.Subscription;
import rx.schedulers.Schedulers;
import timber.log.Timber;

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

    private final ChunksDataSource chunksDataSource;  // query on chunk table
    private final TasksDataSource tasksDataSource;    // query on task table
    protected final DownloadManagerListenerModerator downloadManagerListener;

    private final SparseArray<Subscription> workerList;          // chunk downloader list
    private final SparseArray<ReportStructure> processReports;  // to save download percent

    private QueueModerator finishedDownloadQueueObserver;
    private final OkHttpClient okHttpClient;

    public Moderator(TasksDataSource tasksDS, ChunksDataSource chunksDS,
                     DownloadManagerListenerModerator listenerModerator,
                     OkHttpClient okHttpClient) {
        this.tasksDataSource = tasksDS;
        this.chunksDataSource = chunksDS;
        this.workerList = new SparseArray<>(); // chunk downloader with they id key
        this.processReports = new SparseArray<>();
        this.downloadManagerListener = listenerModerator;
        this.okHttpClient = okHttpClient;
    }

    public void setQueueObserver(QueueModerator queueObserver) {
        finishedDownloadQueueObserver = queueObserver;
    }

    public void start(Task task) {
        // fetch task chunk info
        // set task state to Downloading
        // get any chunk file size calculate where it has to begin
        // start any of them as AsyncTask

        // fetch task chunk info
        synchronized (tasksDataSource) {
            task.state = TaskStates.DOWNLOADING;
            tasksDataSource.update(task);
        }
        List<Chunk> taskChunks = chunksDataSource.chunksRelatedTask(task.id);
        synchronized (processReports) {
            ReportStructure rps = processReports.get(task.id);
            if (rps == null) {
                rps = new ReportStructure();
                processReports.put(task.id, rps);
            }
            rps.setObjectValues(task, taskChunks);
        }

        long downloaded;
        long totalSize;

        // set task state to Downloading
        // to lock start download again!


        // get any chunk file size calculate
        synchronized (workerList) {
            for (Chunk chunk : taskChunks) {
                // chunk file must exist
                downloaded = FileUtils.size(task.save_address, ChunksDataSource.getChunkFileName(chunk.id));
                totalSize = chunk.end - chunk.begin + 1;
                chunk.begin = chunk.begin + downloaded;

                // chunk is downloaded completely
                if (!chunk.completed && downloaded == totalSize) chunk.completed = true;

                Subscription chunkDownloaderDisposable =
                        AsyncWorker.createAsyncWorker(task, chunk, chunksDataSource, this, okHttpClient)
                                .subscribeOn(Schedulers.io())
                                .subscribe();
                workerList.put(chunk.id, chunkDownloaderDisposable);
            }
        }
        // notify to developer------------------------------------------------------------
        downloadManagerListener.OnDownloadStarted(task.id);
    }

    /*
     * pause all chunk thread related to one Task
     */
    public boolean pause(int taskID) {
        Timber.d("[%d] Pause task", taskID);
        synchronized (tasksDataSource) {
            final Task task = tasksDataSource.getTaskInfo(taskID);
            if (task != null && task.state != TaskStates.PAUSED && task.state != TaskStates.ERROR
                    && task.state != TaskStates.END && task.state != TaskStates.DOWNLOAD_FINISHED) {
                // change task state
                // save in DB
                task.state = TaskStates.PAUSED;
                tasksDataSource.update(task);

                // pause task asyncWorker
                // change task state
                // save in DB
                // notify developer

                // pause task asyncWorker
                List<Chunk> taskChunks =
                        chunksDataSource.chunksRelatedTask(task.id);
                synchronized (workerList) {
                    for (Chunk chunk : taskChunks) {
                        final Subscription disposable = workerList.get(chunk.id);
                        if (disposable != null) {
                            disposable.unsubscribe();
                            workerList.remove(chunk.id);
                        }
                    }
                }

                final ReportStructure rs = getReport(taskID);
                if (rs != null) rs.setObjectValues(task, taskChunks);

                // notify to developer------------------------------------------------------------
                downloadManagerListener.OnDownloadPaused(task.id);
                return true;
            }
        }
        return false;
    }

    /*
    to calculate download percentage
    if download task is un resumable it return -1 as percent
     */
    private int downloadByteThreshold = 0;
    private static final int THRESHOLD = 1024 * 50;

    public void process(int taskId, long byteRead) {
        downloadManagerListener.countBytesDownloaded(byteRead);

        final ReportStructure report = getReport(taskId);
        double percent = -1;
        long downloadLength = report.increaseDownloadedLength(byteRead);

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
        List<Chunk> taskChunks;
        synchronized (workerList) {
            workerList.remove(chunk.id);
            taskChunks = chunksDataSource.chunksRelatedTask(chunk.task_id);
            for (Chunk ch : taskChunks) {
                if (workerList.get(ch.id) != null) {
                    return;
                }
            }
        }

        final Task task;
        synchronized (tasksDataSource) {
            task = tasksDataSource.getTaskInfo(chunk.task_id);
            // set state task state to finished
            task.state = TaskStates.DOWNLOAD_FINISHED;
            tasksDataSource.update(task);
        }

        final ReportStructure rs = getReport(task.id);
        rs.setObjectValues(task, taskChunks);

        downloadManagerListener.OnDownloadFinished(task.id);
        Thread t = new Rebuilder(task, taskChunks, this);
        t.start();
    }

    public void reBuildIsDone(Task task, List<Chunk> taskChunks) {
        synchronized (tasksDataSource) {
            // change task row state
            task.state = TaskStates.END;
            task.notify = false;
            tasksDataSource.update(task);
            final ReportStructure rs = getReport(task.id);
            rs.state = TaskStates.END;
            rs.onChanged();
        }

        // delete chunk row from chunk table
        for (Chunk chunk : taskChunks) {
            chunksDataSource.delete(chunk.id);
            FileUtils.delete(task.save_address, ChunksDataSource.getChunkFileName(chunk.id));
        }

        // notify to developer------------------------------------------------------------
        downloadManagerListener.OnDownloadRebuildFinished(task.id);

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

        final Task task;
        synchronized (tasksDataSource) {
            task = tasksDataSource.getTaskInfo(taskId);
            task.state = TaskStates.ERROR;
            task.errorMessage = errorMessage;
            task.notify = false;
            tasksDataSource.update(task);
        }

        final ReportStructure rs = getReport(taskId);
        rs.state = TaskStates.ERROR;
        rs.onChanged();

        // clean up
        List<Chunk> taskChunks = chunksDataSource.chunksRelatedTask(task.id);
        for (Chunk chunk : taskChunks) {
            try {
                FileUtils.delete(task.save_address, ChunksDataSource.getChunkFileName(chunk.id));
            } catch (Exception ex) {
                Timber.e(ex, "[%d] Clean up task file after error", taskId);
            }
            chunksDataSource.delete(chunk.id);
        }

        try {
            if (new File(task.save_address, task.name).exists())
                FileUtils.delete(task.save_address, task.name);
        } catch (Exception ex) {
            Timber.e(ex, "[%d] Clean up task file after error", taskId);
        }

        downloadManagerListener.onDownloadError(taskId, errorMessage);

        wakeUpObserver(taskId);
    }

    public void putReport(ReportStructure rs) {
        synchronized (processReports) {
            processReports.put(rs.id, rs);
        }
    }

    public ReportStructure getReport(int taskId) {
        synchronized (processReports) {
            return processReports.get(taskId);
        }
    }

    public void putAllReport(List<ReportStructure> reportStructures) {
        synchronized (processReports) {
            for (ReportStructure reportStructure : reportStructures) {
                processReports.put(reportStructure.id, reportStructure);
            }
        }
    }
}
