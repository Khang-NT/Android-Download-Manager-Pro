package com.golshadi.majid.core.mainWorker;

import android.util.SparseArray;

import com.golshadi.majid.Utils.QueueObserver;
import com.golshadi.majid.core.chunkWorker.Moderator;
import com.golshadi.majid.database.ChunksDataSource;
import com.golshadi.majid.database.TasksDataSource;
import com.golshadi.majid.database.elements.Task;
import com.golshadi.majid.report.listener.DownloadManagerListenerModerator;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Majid Golshadi on 4/21/2014.
 */
public class QueueModerator   
			implements QueueObserver {

    private final TasksDataSource tasksDataSource;
    private final ChunksDataSource chunksDataSource;
    private final Moderator moderator;
    private final DownloadManagerListenerModerator listener;
    private final List<Task> uncompletedTasks;
    private int downloadTaskPerTime;

    private final SparseArray<Thread> downloaderList;

    public QueueModerator(TasksDataSource tasksDataSource, ChunksDataSource chunksDataSource,
                       Moderator localModerator, DownloadManagerListenerModerator downloadManagerListener,
                       List<Task> tasks, int downloadPerTime){

        this.tasksDataSource = tasksDataSource;
        this.chunksDataSource = chunksDataSource;
        this.moderator = localModerator;
        this.moderator.setQueueObserver(this);
        this.listener = downloadManagerListener;
        this.downloadTaskPerTime = downloadPerTime;
        this.uncompletedTasks = new ArrayList<>(tasks);
        
        downloaderList = new SparseArray<>();
    }

    public QueueModerator setDownloadTaskPerTime(int downloadTaskPerTime) {
        if (downloadTaskPerTime < 1)
            throw new IllegalArgumentException("Invalid download task per time: " + downloadTaskPerTime);
        this.downloadTaskPerTime = downloadTaskPerTime;
        return this;
    }

    public QueueModerator addTask(Task task) {
        this.uncompletedTasks.add(task);
        startQueue();
        return this;
    }

    public void startQueue() {
        int location = 0;
        while (uncompletedTasks.size() > 0 &&
                downloadTaskPerTime >= downloaderList.size()) {
            Task task = uncompletedTasks.get(location);
            Thread downloader =
                    new AsyncStartDownload(tasksDataSource, chunksDataSource, moderator, listener, task);

            downloaderList.put(task.id, downloader);
            uncompletedTasks.remove(location);

            downloader.start();

            location++;
        }
    }

    public int getDownloadingCount() {
        return downloaderList.size();
    }

    public int getPendingTaskCount() {
        return uncompletedTasks.size();
    }

    public void wakeUp(int taskID){
        downloaderList.remove(taskID);
        startQueue();
    }

    public void pause(){
        for (int i = 0; i < downloaderList.size(); i++) {
            int id = downloaderList.keyAt(i);
            moderator.pause(id);
        }
        downloaderList.clear();
    }
}
