package com.golshadi.majid.core.mainWorker;

import android.util.SparseArray;

import com.golshadi.majid.Utils.QueueObserver;
import com.golshadi.majid.core.chunkWorker.Moderator;
import com.golshadi.majid.database.ChunksDataSource;
import com.golshadi.majid.database.TasksDataSource;
import com.golshadi.majid.database.elements.Task;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import okhttp3.OkHttpClient;

/**
 * Created by Majid Golshadi on 4/21/2014.
 */
public class QueueModerator   
			implements QueueObserver {

    public interface OnQueueChanged {
        void onQueueChanged(int downloading, int pending);
    }

    private final TasksDataSource tasksDataSource;
    private final ChunksDataSource chunksDataSource;
    private final Moderator moderator;
    private int downloadTaskPerTime;

    private final SparseArray<Task> uncompletedTasks;
    private final SparseArray<Thread> downloaderList;
    private final List<WeakReference<OnQueueChanged>> listeners;
    private final OkHttpClient okHttpClient;

    public QueueModerator(TasksDataSource tasksDataSource, ChunksDataSource chunksDataSource,
                          Moderator localModerator, List<Task> tasks, int downloadPerTime,
                          OkHttpClient okHttpClient){

        this.tasksDataSource = tasksDataSource;
        this.chunksDataSource = chunksDataSource;
        this.moderator = localModerator;
        this.moderator.setQueueObserver(this);
        this.downloadTaskPerTime = downloadPerTime;
        this.uncompletedTasks = new SparseArray<>();
        for (Task task : tasks) {
            uncompletedTasks.put(task.id, task);
        }
        
        this.downloaderList = new SparseArray<>();
        this.listeners = new ArrayList<>();
        this.okHttpClient = okHttpClient;
    }

    public void addOnQueueChangedListener(OnQueueChanged listener) {
        for (WeakReference<OnQueueChanged> weakReference : listeners) {
            if (weakReference.get() == listener)
                return;
        }
        listeners.add(new WeakReference<>(listener));
    }

    public QueueModerator setDownloadTaskPerTime(int downloadTaskPerTime) {
        if (downloadTaskPerTime < 1)
            throw new IllegalArgumentException("Invalid download task per time: " + downloadTaskPerTime);
        this.downloadTaskPerTime = downloadTaskPerTime;
        return this;
    }

    public boolean checkExistTaskWithFileName(String fileName) {
        for (int i = 0; i < uncompletedTasks.size(); i++) {
            int key = uncompletedTasks.keyAt(i);
            if (fileName.equals(uncompletedTasks.get(key).name))
                return true;
        }
        return false;
    }

    public QueueModerator addTask(Task task) {
        this.uncompletedTasks.put(task.id, task);
        notifyListeners();
        return this;
    }

    public void removeTask(int token) {
        this.uncompletedTasks.remove(token);
        notifyListeners();
    }

    public void startQueue() {
        int location = 0;
        int startedTask = 0;
        while (location < uncompletedTasks.size() &&
                downloadTaskPerTime > downloaderList.size()) {
            Task task = uncompletedTasks.get(uncompletedTasks.keyAt(location));
            if (downloaderList.get(task.id) == null) {
                Thread downloader =
                        new AsyncStartDownload(tasksDataSource, chunksDataSource, moderator, task, okHttpClient);

                downloaderList.put(task.id, downloader);
                downloader.start();
                startedTask ++;
            }
            location++;
        }

        if (startedTask != 0)
            notifyListeners();
    }

    public int getDownloadingCount() {
        return downloaderList.size();
    }

    public int getPendingTaskCount() {
        return uncompletedTasks.size() - downloaderList.size();
    }

    public SparseArray<Task> getUncompletedTasks() {
        return uncompletedTasks;
    }

    public boolean isDownloading(int taskId) {
        return downloaderList.get(taskId) != null;
    }

    public void wakeUp(int taskID){
        downloaderList.remove(taskID);
        uncompletedTasks.remove(taskID);
        notifyListeners();
        startQueue();
    }

    public void pause(){
        if (downloaderList.size() == 0) return;

        for (int i = 0; i < downloaderList.size(); i++) {
            int id = downloaderList.keyAt(i);
            Task task = moderator.pause(id);
            // update task state
            uncompletedTasks.put(id, task);
        }
        downloaderList.clear();
        notifyListeners();
    }

    private synchronized void notifyListeners() {
        int downloading = getDownloadingCount(), pending = getPendingTaskCount();
        for (WeakReference<OnQueueChanged> weakReference : listeners) {
            final OnQueueChanged listener = weakReference.get();
            if (listener != null) {
                listener.onQueueChanged(downloading, pending);
            }
        }
        // clean up null reference
        if (listeners.size() > 5) {
            Iterator<WeakReference<OnQueueChanged>> iterator = listeners.iterator();
            while (iterator.hasNext()) {
                WeakReference<OnQueueChanged> weakReference = iterator.next();
                if (weakReference.get() == null)
                    iterator.remove();
            }
        }
    }
}
