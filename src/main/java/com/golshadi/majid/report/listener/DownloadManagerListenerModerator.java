package com.golshadi.majid.report.listener;

import android.content.Context;
import android.content.Intent;

import com.golshadi.majid.core.DownloadManagerPro;
import com.golshadi.majid.database.TasksDataSource;
import com.golshadi.majid.database.elements.Task;

import java.lang.ref.WeakReference;

/**
 * Created by Majid Golshadi on 4/21/2014.
 */
public class DownloadManagerListenerModerator {

    private final Context context;
    private final TasksDataSource tasksDataSource;
    private WeakReference<DownloadManagerListener> downloadManagerListenerWeakReference;


    public DownloadManagerListenerModerator(Context context, TasksDataSource tasksDataSource) {
        this.context = context;
        this.tasksDataSource = tasksDataSource;
    }

    public DownloadManagerListenerModerator setDownloadManagerListener(DownloadManagerListener downloadManagerListener) {
        this.downloadManagerListenerWeakReference = new WeakReference<>(downloadManagerListener);
        return this;
    }

    public void OnDownloadStarted(long taskId) {
        DownloadManagerListener downloadManagerListener = null;
        if (downloadManagerListenerWeakReference != null)
            downloadManagerListener = downloadManagerListenerWeakReference.get();
        if (downloadManagerListener != null) {
            downloadManagerListener.OnDownloadStarted(taskId);
        }
    }

    public void OnDownloadPaused(long taskId) {
        DownloadManagerListener downloadManagerListener = null;
        if (downloadManagerListenerWeakReference != null)
            downloadManagerListener = downloadManagerListenerWeakReference.get();
        if (downloadManagerListener != null) {
            downloadManagerListener.OnDownloadPaused(taskId);
        }
    }

    public void onDownloadProcess(long taskId, double percent, long downloadedLength) {
        DownloadManagerListener downloadManagerListener = null;
        if (downloadManagerListenerWeakReference != null)
            downloadManagerListener = downloadManagerListenerWeakReference.get();
        if (downloadManagerListener != null) {
            downloadManagerListener.onDownloadProcess(taskId, percent, downloadedLength);
        }
    }

    public void OnDownloadFinished(long taskId) {
        DownloadManagerListener downloadManagerListener = null;
        if (downloadManagerListenerWeakReference != null)
            downloadManagerListener = downloadManagerListenerWeakReference.get();
        if (downloadManagerListener != null) {
            downloadManagerListener.OnDownloadFinished(taskId);
        }
    }

    public void OnDownloadRebuildStart(long taskId) {
        DownloadManagerListener downloadManagerListener = null;
        if (downloadManagerListenerWeakReference != null)
            downloadManagerListener = downloadManagerListenerWeakReference.get();
        if (downloadManagerListener != null) {
            downloadManagerListener.OnDownloadRebuildStart(taskId);
        }
    }


    public void OnDownloadRebuildFinished(long taskId) {
        DownloadManagerListener downloadManagerListener = null;
        if (downloadManagerListenerWeakReference != null)
            downloadManagerListener = downloadManagerListenerWeakReference.get();
        if (downloadManagerListener != null) {
            downloadManagerListener.OnDownloadRebuildFinished(taskId);
        }
    }

    public void OnDownloadCompleted(long taskId) {
        DownloadManagerListener downloadManagerListener = null;
        if (downloadManagerListenerWeakReference != null)
            downloadManagerListener = downloadManagerListenerWeakReference.get();
        if (downloadManagerListener != null) {
            downloadManagerListener.OnDownloadCompleted(taskId);
        }

        Task task = tasksDataSource.getTaskInfo(Long.valueOf(taskId).intValue());
        Intent intent = new Intent(DownloadManagerPro.ACTION_DOWNLOAD_COMPLETED);
        intent.putExtra(DownloadManagerPro.EXTRA_JSON_KEY, task.jsonExtra);
        intent.putExtra(DownloadManagerPro.TASK_ID_KEY, task.id);

        context.sendBroadcast(intent);
    }
    
    public void ConnectionLost(long taskId){
        DownloadManagerListener downloadManagerListener = null;
        if (downloadManagerListenerWeakReference != null)
            downloadManagerListener = downloadManagerListenerWeakReference.get();
    	if (downloadManagerListener != null) {
			downloadManagerListener.connectionLost(taskId);
		}
    }
}
