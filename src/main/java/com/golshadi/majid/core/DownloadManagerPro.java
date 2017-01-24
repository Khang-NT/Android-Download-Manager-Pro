package com.golshadi.majid.core;

import android.content.Context;
import android.util.Log;

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
import com.golshadi.majid.report.exceptions.QueueDownloadInProgressException;
import com.golshadi.majid.report.exceptions.QueueDownloadNotStartedException;
import com.golshadi.majid.report.listener.DownloadManagerListenerModerator;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Majid Golshadi on 4/10/2014.
 */
public class DownloadManagerPro {

    private static final int MAX_CHUNKS = 16;

    private Moderator moderator;
    private DatabaseHelper dbHelper;

    private TasksDataSource tasksDataSource;
    private ChunksDataSource chunksDataSource;

    private DownloadManagerListenerModerator downloadManagerListener;

    private QueueModerator qt;

    private int downloadTaskPerTime;

    /**
     * <p>
     * Download manager pro Object constructor
     * </p>
     *
     * @param context
     */
    public DownloadManagerPro(Context context, int downloadTaskPerTime) {
        this.downloadTaskPerTime = downloadTaskPerTime;

        dbHelper = new DatabaseHelper(context);

        // ready database data source to access tables
        tasksDataSource = new TasksDataSource();
        tasksDataSource.openDatabase(dbHelper);

        chunksDataSource = new ChunksDataSource();
        chunksDataSource.openDatabase(dbHelper);

        // moderate chunks to download one task
        moderator = new Moderator(tasksDataSource, chunksDataSource);
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
    public int addTask(String url, String saveName, String sdCardFolderAddress, int chunk, boolean overwrite) {
        if (!overwrite)
            saveName = getUniqueName(saveName);
        else
            deleteSameDownloadNameTask(saveName);

        chunk = setMaxChunk(chunk);
        return insertNewTask(saveName, url, chunk, sdCardFolderAddress, true);
    }

    public void setDownloadTaskPerTime(int downloadTaskPerTime) {
        this.downloadTaskPerTime = downloadTaskPerTime;
        if (qt != null) {
            qt.setDownloadTaskPerTime(downloadTaskPerTime);
        }
    }

    public void startQueueDownload() {

        List<Task> unCompletedTasks = tasksDataSource.getUnCompletedTasks(QueueSort.OLDEST_FIRST);

        if (qt == null) {
            qt = new QueueModerator(tasksDataSource, chunksDataSource,
                    moderator, downloadManagerListener, unCompletedTasks, downloadTaskPerTime);
            qt.startQueue();

        } else {
            throw new QueueDownloadInProgressException();
        }
    }

    public boolean isQueueStarted() {
        return qt != null;
    }

    public void pauseQueueDownload() {

        if (qt != null) {
            qt.pause();
            qt = null;
        } else {
            throw new QueueDownloadNotStartedException();
        }
    }


    //-----------Reports

    /**
     * report task download status in "ReportStructure" style
     *
     * @param token when you add a new download task it's return to you
     * @return
     */
    public ReportStructure singleDownloadStatus(int token) {
        ReportStructure report = new ReportStructure();
        Task task = tasksDataSource.getTaskInfo(token);
        if (task != null) {
            List<Chunk> taskChunks = chunksDataSource.chunksRelatedTask(task.id);
            report.setObjectValues(task, taskChunks);

            return report;
        }

        return null;
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
     * @return
     */
    public List<ReportStructure> downloadTasksInSameState(int state) {
        List<ReportStructure> reportList;
        List<Task> inStateTasks = tasksDataSource.getTasksInState(state);

        reportList = readyTaskList(inStateTasks);

        return reportList;
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
        Task task = tasksDataSource.getTaskInfo(token);
        if (task.url != null) {
            List<Chunk> taskChunks =
                    chunksDataSource.chunksRelatedTask(task.id);
            for (Chunk chunk : taskChunks) {
                FileUtils.delete(task.save_address, String.valueOf(chunk.id));
                chunksDataSource.delete(chunk.id);
            }

            if (deleteTaskFile) {
                long size = FileUtils.size(task.save_address, task.name + "." + task.extension);
                if (size > 0) {
                    FileUtils.delete(task.save_address, task.name + "." + task.extension);
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
        dbHelper.close();
    }


    private List<Task> uncompleted() {
        return tasksDataSource.getUnCompletedTasks(QueueSort.OLDEST_FIRST);
    }

    private int insertNewTask(String taskName, String url, int chunk, String save_address, boolean priority) {
        Task task = new Task(0, taskName, url, TaskStates.INIT, chunk, save_address, priority);
        task.id = (int) tasksDataSource.insertTask(task);
        Log.d("--------", "task id " + String.valueOf(task.id));
        return task.id;
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
            FileUtils.delete(task.save_address, task.name + "." + task.extension);
        }
    }
}
