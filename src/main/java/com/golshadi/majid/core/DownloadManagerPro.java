package com.golshadi.majid.core;

import android.content.Context;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.util.Log;
import android.util.SparseArray;

import com.golshadi.majid.Utils.helper.FileUtils;
import com.golshadi.majid.core.chunkWorker.Moderator;
import com.golshadi.majid.core.enums.QueueSort;
import com.golshadi.majid.core.enums.TaskStates;
import com.golshadi.majid.core.mainWorker.QueueModerator;
import com.golshadi.majid.database.ChunksDataSource;
import com.golshadi.majid.database.DatabaseHelper;
import com.golshadi.majid.database.TasksDataSource;
import com.golshadi.majid.database.elements.Chunk;
import com.golshadi.majid.database.elements.Task;
import com.golshadi.majid.report.ReportStructure;
import com.golshadi.majid.report.listener.DownloadManagerListener;
import com.golshadi.majid.report.listener.DownloadManagerListenerModerator;
import com.golshadi.majid.report.listener.DownloadSpeedListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by Majid Golshadi on 4/10/2014.
 */
public class DownloadManagerPro {

    public static final String TASK_ID_KEY = "task_id";
    public static final String EXTRA_JSON_KEY = "json_extra";
    public static final String ACTION_DOWNLOAD_COMPLETED = "download.manager.download.completed";

    private static final int MAX_CHUNKS = 16;

    private Moderator moderator;
    private DatabaseHelper dbHelper;

    private TasksDataSource tasksDataSource;
    private ChunksDataSource chunksDataSource;

    private final DownloadManagerListenerModerator downloadManagerListener;

    private final QueueModerator queue;

    /**
     * <p>
     * Download manager pro Object constructor
     * </p>
     *
     * @param context
     */
    public DownloadManagerPro(Context context, int downloadTaskPerTime) {
        dbHelper = new DatabaseHelper(context);

        // ready database data source to access tables
        tasksDataSource = new TasksDataSource();
        tasksDataSource.openDatabase(dbHelper);

        chunksDataSource = new ChunksDataSource();
        chunksDataSource.openDatabase(dbHelper);

        // moderate chunks to download one task
        moderator = new Moderator(tasksDataSource, chunksDataSource);

        downloadManagerListener = new DownloadManagerListenerModerator(context, tasksDataSource);

        List<Task> unCompletedTasks = tasksDataSource.getUnCompletedTasks(QueueSort.OLDEST_FIRST);

        queue = new QueueModerator(tasksDataSource, chunksDataSource,
                moderator, downloadManagerListener, unCompletedTasks, downloadTaskPerTime);
        List<ReportStructure> reportStructures = readyTaskList(unCompletedTasks);
        moderator.putAllReport(reportStructures);
    }

    /**
     * <p>
     * add a new download Task
     * </p>
     *
     * @param saveName            file name
     * @param url                 url file address
     * @param chunk               number of chunks
     * @param sdCardFolderAddress downloaded file save address
     * @param overwrite           if exist an other file with same name
     *                            "true" over write that file
     *                            "false" find new name and save it with new name
     * @return id
     * inserted task id
     */
    public int addTask(String url, String saveName, String sdCardFolderAddress, int chunk, boolean overwrite, @Nullable String jsonExtra,
                       long fileSize) {
        if (queue.checkExistTaskWithFileName(saveName))
            return -1;
        if (!overwrite)
            saveName = getUniqueName(saveName);
        else
            deleteSameDownloadNameTask(saveName);

        chunk = setMaxChunk(chunk);
        String saveAddress = Environment.getExternalStorageDirectory() + "/" + sdCardFolderAddress;
        Task task = insertNewTask(saveName, url, chunk, saveAddress, true, jsonExtra, fileSize);
        ReportStructure rs = new ReportStructure();
        rs.setObjectValues(task, Collections.emptyList());
        moderator.putReport(rs);
        queue.addTask(task);
        return task.id;
    }

    public void setDownloadTaskPerTime(int downloadTaskPerTime) {
        queue.setDownloadTaskPerTime(downloadTaskPerTime);
    }

    public void startQueueDownload() {
        queue.startQueue();
    }

    public boolean isQueueStarted() {
        return queue.getDownloadingCount() > 0;
    }

    public void pauseQueueDownload() {
        queue.pause();
    }

    public QueueModerator getQueueModerator() {
        return queue;
    }

    public void setDownloadManagerListener(DownloadManagerListener listener) {
        downloadManagerListener.setDownloadManagerListener(listener);
    }

    public void setDownloadSpeedListener(DownloadSpeedListener listener) {
        downloadManagerListener.setDownloadSpeedListener(listener);
    }

    //-----------Reports


    public List<ReportStructure> queueStatusReport(boolean downloading) {
        List<ReportStructure> result = new ArrayList<>();
        SparseArray<Task> uncompletedTasks = queue.getUncompletedTasks();
        for (int i = 0; i < uncompletedTasks.size(); i++) {
            int taskId = uncompletedTasks.keyAt(i);
            if (queue.isDownloading(taskId) == downloading) {
                ReportStructure rs = singleDownloadStatus(taskId);
                result.add(rs);
            }
        }
        return result;
    }

    /**
     * report task download status in "ReportStructure" style
     *
     * @param token when you add a new download task it's return to you
     * @return
     */
    @Nullable
    public ReportStructure singleDownloadStatus(int token) {
        ReportStructure report = moderator.getReport(token);
        if (report == null) {
            Task task = tasksDataSource.getTaskInfo(token);
            if (task != null) {
                report = new ReportStructure();
                List<Chunk> taskChunks = chunksDataSource.chunksRelatedTask(task.id);
                report.setObjectValues(task, taskChunks);
            }
        }
        return report;
    }


    /**
     * <p>
     * it's an report method for
     * return list of download task in same state that developer want as ReportStructure List object
     * </p>
     *
     * @param state 0. get all downloads Status
     *              1. init
     *              2. ready
     *              3. downloading
     *              4. paused
     *              5. download finished
     *              6. end
     *              7. error
     * @return
     */
    public List<ReportStructure> downloadTasksInSameState(int state) {
        List<Task> inStateTasks = tasksDataSource.getTasksInState(state);
        return readyTaskList(inStateTasks);
    }


    /**
     * return list of last completed Download tasks in "ReportStructure" style
     * you can use it as notifier
     *
     * @return
     */
    public List<ReportStructure> lastCompletedDownloads() {
        List<ReportStructure> reportList;
        List<Task> lastCompleted = tasksDataSource.getUnnotifiedCompleted();

        reportList = readyTaskList(lastCompleted);

        return reportList;
    }


    private List<ReportStructure> readyTaskList(List<Task> tasks) {
        List<ReportStructure> reportList = new ArrayList<>();

        for (Task task : tasks) {
            List<Chunk> taskChunks = chunksDataSource.chunksRelatedTask(task.id);
            ReportStructure singleReport = new ReportStructure();
            singleReport.setObjectValues(task, taskChunks);
            reportList.add(singleReport);
        }

        return reportList;
    }


    /**
     * <p>
     * check all notified tasks
     * so in another "lastCompletedDownloads" call ,completed task does not show again
     * <p>
     * persian:
     * "lastCompletedDownloads" list akharin task haii ke takmil shodeand ra namayesh midahad
     * ba seda zadan in method tamami task haii ke dar gozaresh e ghabli elam shode boodand ra
     * az liste "lastCompeletedDownloads" hazf mikonad
     * <p>
     * !!!SHIT!!!
     * </p>
     *
     * @return true or false
     */
    public boolean notifiedTaskChecked() {
        return tasksDataSource.checkUnNotifiedTasks();
    }


    /**
     * delete download task from db and if you set deleteTaskFile as true
     * it's go to saved folder and delete that file
     *
     * @param token          when you add a new download task it's return to you
     * @param deleteTaskFile delete completed download file from sd card if you set it true
     * @return "true" if anything goes right
     * "false" if something goes wrong
     */
    public boolean delete(int token, boolean deleteTaskFile) {
        moderator.pause(token);
        queue.removeTask(token);
        Task task = tasksDataSource.getTaskInfo(token);
        if (task.url != null) {
            List<Chunk> taskChunks =
                    chunksDataSource.chunksRelatedTask(task.id);
            for (Chunk chunk : taskChunks) {
                FileUtils.delete(task.save_address, ChunksDataSource.getChunkFileName(chunk.id));
                chunksDataSource.delete(chunk.id);
            }

            if (deleteTaskFile) {
                long size = FileUtils.size(task.save_address, task.name);
                if (size > 0) {
                    FileUtils.delete(task.save_address, task.name);
                }
            }

            return tasksDataSource.delete(task.id);
        }

        return false;
    }


    /**
     * close db connection
     * if your activity goes to paused or stop state
     * you have to call this method to disconnect from db
     */
    public void dispose() {
        pauseQueueDownload();
        dbHelper.close();
        downloadManagerListener.setDownloadManagerListener(null);
        downloadManagerListener.setDownloadSpeedListener(null);
    }


    private List<Task> uncompleted() {
        return tasksDataSource.getUnCompletedTasks(QueueSort.OLDEST_FIRST);
    }

    private Task insertNewTask(String fileName, String url, int chunk, String save_address, boolean priority, String jsonExtra,
                               long fileSize) {
        Task task = new Task(0, fileName, url, TaskStates.INIT, chunk, save_address, priority, jsonExtra);
        task.size = fileSize;
        task.id = (int) tasksDataSource.insertTask(task);
        Log.d("--------", "task id " + String.valueOf(task.id));
        return task;
    }


    private int setMaxChunk(int chunk) {

        if (chunk < MAX_CHUNKS)
            return chunk;

        return MAX_CHUNKS;
    }

    private String getUniqueName(String name) {
        String uniqueName = name;
        int count = 0;

        while (isDuplicatedName(uniqueName)) {
            uniqueName = name + "_" + count;
            count++;
        }

        return uniqueName;
    }

    private boolean isDuplicatedName(String name) {
        return tasksDataSource.containsTask(name);
    }


    /*
        valid values are
            INIT          = 0;
            READY         = 1;
            DOWNLOADING   = 2;
            PAUSED        = 3;
            DOWNLOAD_FINISHED      = 4;
            END           = 5;
        so if his token was wrong return -1
     */
    private void deleteSameDownloadNameTask(String saveName) {
        if (isDuplicatedName(saveName)) {
            Task task = tasksDataSource.getTaskInfoWithName(saveName);
            tasksDataSource.delete(task.id);
            FileUtils.delete(task.save_address, task.name);
        }
    }
}
