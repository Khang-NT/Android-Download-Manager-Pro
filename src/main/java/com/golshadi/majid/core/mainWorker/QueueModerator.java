package com.golshadi.majid.core.mainWorker;

import android.util.SparseArray;

import com.golshadi.majid.Utils.QueueObserver;
import com.golshadi.majid.core.chunkWorker.Moderator;
import com.golshadi.majid.database.ChunksDataSource;
import com.golshadi.majid.database.TasksDataSource;
import com.golshadi.majid.database.elements.Task;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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

    private final Set<Integer> uncompletedTasks;
    private final SparseArray<Thread> downloaderList;
    private final List<WeakReference<OnQueueChanged>> listeners;
    private final OkHttpClient okHttpClient;

    public QueueModerator(TasksDataSource tasksDataSource, ChunksDataSource chunksDataSource,
                          Moderator localModerator, List<Task> tasks, int downloadPerTime,
                          OkHttpClient okHttpClient) {

        this.tasksDataSource = tasksDataSource;
        this.chunksDataSource = chunksDataSource;
        this.moderator = localModerator;
        this.moderator.setQueueObserver(this);
        this.downloadTaskPerTime = downloadPerTime;

        this.uncompletedTasks = new LinkedHashSet<>();
        for (Task task : tasks) uncompletedTasks.add(task.id);

        this.downloaderList = new SparseArray<>();
        this.listeners = new ArrayList<>();
        this.okHttpClient = okHttpClient;
    }

    public void addOnQueueChangedListener(OnQueueChanged listener) {
        synchronized (listeners) {
            for (WeakReference<OnQueueChanged> weakReference : listeners) {
                if (weakReference.get() == listener)
                    return;
            }
            listeners.add(new WeakReference<>(listener));
        }
    }

    public QueueModerator setDownloadTaskPerTime(int downloadTaskPerTime) {
        if (downloadTaskPerTime < 1)
            throw new IllegalArgumentException("Invalid download task per time: " + downloadTaskPerTime);
        this.downloadTaskPerTime = downloadTaskPerTime;
        return this;
    }

//    public boolean checkExistTaskWithFileName(String fileName) {
//        for (int i = 0; i < uncompletedTasks.size(); i++) {
//            int key = uncompletedTasks.keyAt(i);
//            if (fileName.equals(uncompletedTasks.get(key).name))
//                return true;
//        }
//        return false;
//    }

    public QueueModerator addTask(Task task) {
        synchronized (uncompletedTasks) {
            this.uncompletedTasks.add(task.id);
        }
        notifyListeners();
        return this;
    }

    public void removeTask(int token) {
        synchronized (uncompletedTasks) {
            synchronized (downloaderList) {
                this.downloaderList.remove(token);
                this.uncompletedTasks.remove(token);
            }
        }
        notifyListeners();
    }

    public void startQueue() {
        int startedTask = 0;
        synchronized (uncompletedTasks) {
            synchronized (downloaderList) {
                for (Integer taskId : uncompletedTasks) {
                    if (downloaderList.size() >= downloadTaskPerTime) break;
                    synchronized (tasksDataSource) {
                        if (downloaderList.get(taskId) == null) {
                            final Task task = tasksDataSource.getTaskInfo(taskId);
                            final Thread downloader =
                                    new AsyncStartDownload(tasksDataSource, chunksDataSource, moderator, task, okHttpClient);
                            synchronized (downloaderList) {
                                downloaderList.put(task.id, downloader);
                            }
                            downloader.start();
                            startedTask++;
                        }
                    }
                }
            }
        }

        if (startedTask != 0)
            notifyListeners();
    }

    public int getDownloadingCount() {
        synchronized (downloaderList) {
            return downloaderList.size();
        }
    }

    public int getPendingTaskCount() {
        synchronized (uncompletedTasks) {
            synchronized (downloaderList) {
                return uncompletedTasks.size() - downloaderList.size();
            }
        }
    }

    public Set<Integer> getUncompletedTasks() {
        return Collections.unmodifiableSet(uncompletedTasks);
    }

    public boolean isDownloading(int taskId) {
        synchronized (downloaderList) {
            return downloaderList.get(taskId) != null;
        }
    }

    public void wakeUp(int taskID) {
        synchronized (uncompletedTasks) {
            uncompletedTasks.remove(taskID);
        }
        synchronized (downloaderList) {
            downloaderList.remove(taskID);
        }
        startQueue();
        notifyListeners();
    }

    public void pause() {
        synchronized (downloaderList) {
            for (int i = 0; i < downloaderList.size(); i++) {
                int id = downloaderList.keyAt(i);
                final Thread thread = downloaderList.get(id);
                thread.interrupt();
                moderator.pause(id);
            }
            downloaderList.clear();
        }
        notifyListeners();
    }

    private synchronized void notifyListeners() {
        int downloading = getDownloadingCount(), pending = getPendingTaskCount();
        synchronized (listeners) {
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
}
