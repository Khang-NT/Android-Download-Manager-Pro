package com.golshadi.majid.core.mainWorker;

import android.util.SparseArray;

import com.golshadi.majid.Utils.QueueObserver;
import com.golshadi.majid.core.chunkWorker.Moderator;
import com.golshadi.majid.database.ChunksDataSource;
import com.golshadi.majid.database.TasksDataSource;
import com.golshadi.majid.database.elements.Task;
import com.golshadi.majid.report.listener.DownloadManagerListenerModerator;

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
    private int downloadTaskPerTime;

    private final SparseArray<Task> uncompletedTasks;
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
        this.uncompletedTasks = new SparseArray<>();
        for (Task task : tasks) {
            uncompletedTasks.put(task.id, task);
        }
        
        downloaderList = new SparseArray<>();
    }

    public QueueModerator setDownloadTaskPerTime(int downloadTaskPerTime) {
        if (downloadTaskPerTime < 1)
            throw new IllegalArgumentException("Invalid download task per time: " + downloadTaskPerTime);
        this.downloadTaskPerTime = downloadTaskPerTime;
        return this;
    }

    public QueueModerator addTask(Task task) {
        this.uncompletedTasks.put(task.id, task);
        return this;
    }

    public void startQueue() {
        int location = 0;
        while (location < uncompletedTasks.size() &&
                downloadTaskPerTime > downloaderList.size()) {
            Task task = uncompletedTasks.get(uncompletedTasks.keyAt(location));
            if (downloaderList.get(task.id) == null) {
                Thread downloader =
                        new AsyncStartDownload(tasksDataSource, chunksDataSource, moderator, listener, task);

                downloaderList.put(task.id, downloader);
                downloader.start();
            }
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
        uncompletedTasks.remove(taskID);
        startQueue();
    }

    public void pause(){
        if (downloaderList.size() == 0) return;

        for (int i = 0; i < downloaderList.size(); i++) {
            int id = downloaderList.keyAt(i);
            moderator.pause(id);
        }
        downloaderList.clear();
    }
}
