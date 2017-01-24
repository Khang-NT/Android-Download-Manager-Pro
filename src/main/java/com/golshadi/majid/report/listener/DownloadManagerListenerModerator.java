package com.golshadi.majid.report.listener;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.golshadi.majid.core.DownloadManagerPro;
import com.golshadi.majid.database.TasksDataSource;
import com.golshadi.majid.database.elements.Task;

import java.lang.ref.WeakReference;

/**
 * Created by Majid Golshadi on 4/21/2014.
 */
public class DownloadManagerListenerModerator {

    public static final String TAG = "DownloadListener";
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
        Log.d(TAG, "OnDownloadStarted() called with: taskId = [" + taskId + "]");
        DownloadManagerListener downloadManagerListener = null;
        if (downloadManagerListenerWeakReference != null)
            downloadManagerListener = downloadManagerListenerWeakReference.get();
        if (downloadManagerListener != null) {
            downloadManagerListener.OnDownloadStarted(taskId);
        }
    }

    public void OnDownloadPaused(long taskId) {
        Log.d(TAG, "OnDownloadPaused() called with: taskId = [" + taskId + "]");
        DownloadManagerListener downloadManagerListener = null;
        if (downloadManagerListenerWeakReference != null)
            downloadManagerListener = downloadManagerListenerWeakReference.get();
        if (downloadManagerListener != null) {
            downloadManagerListener.OnDownloadPaused(taskId);
        }
    }

    public void onDownloadProcess(long taskId, double percent, long downloadedLength) {
        Log.d(TAG, "onDownloadProcess() called with: taskId = [" + taskId + "], percent = [" + percent + "], downloadedLength = [" + downloadedLength + "]");
        DownloadManagerListener downloadManagerListener = null;
        if (downloadManagerListenerWeakReference != null)
            downloadManagerListener = downloadManagerListenerWeakReference.get();
        if (downloadManagerListener != null) {
            downloadManagerListener.onDownloadProcess(taskId, percent, downloadedLength);
        }
    }

    public void OnDownloadFinished(long taskId) {
        Log.d(TAG, "OnDownloadFinished() called with: taskId = [" + taskId + "]");
        DownloadManagerListener downloadManagerListener = null;
        if (downloadManagerListenerWeakReference != null)
            downloadManagerListener = downloadManagerListenerWeakReference.get();
        if (downloadManagerListener != null) {
            downloadManagerListener.OnDownloadFinished(taskId);
        }
    }

    public void OnDownloadRebuildStart(long taskId) {
        Log.d(TAG, "OnDownloadRebuildStart() called with: taskId = [" + taskId + "]");
        DownloadManagerListener downloadManagerListener = null;
        if (downloadManagerListenerWeakReference != null)
            downloadManagerListener = downloadManagerListenerWeakReference.get();
        if (downloadManagerListener != null) {
            downloadManagerListener.OnDownloadRebuildStart(taskId);
        }
    }


    public void OnDownloadRebuildFinished(long taskId) {
        Log.d(TAG, "OnDownloadRebuildFinished() called with: taskId = [" + taskId + "]");
        DownloadManagerListener downloadManagerListener = null;
        if (downloadManagerListenerWeakReference != null)
            downloadManagerListener = downloadManagerListenerWeakReference.get();
        if (downloadManagerListener != null) {
            downloadManagerListener.OnDownloadRebuildFinished(taskId);
        }
    }

    public void OnDownloadCompleted(long taskId) {
        Log.d(TAG, "OnDownloadCompleted() called with: taskId = [" + taskId + "]");
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

    public void onDownloadError(long taskId, String errorMessage) {
        Log.d(TAG, "onDownloadError() called with: taskId = [" + taskId + "], errorMessage = [" + errorMessage + "]");
        DownloadManagerListener downloadManagerListener = null;
        if (downloadManagerListenerWeakReference != null)
            downloadManagerListener = downloadManagerListenerWeakReference.get();
        if (downloadManagerListener != null) {
            downloadManagerListener.onDownloadError(taskId, errorMessage);
        }
    }
    
    public void ConnectionLost(long taskId){
        Log.d(TAG, "ConnectionLost() called with: taskId = [" + taskId + "]");
        DownloadManagerListener downloadManagerListener = null;
        if (downloadManagerListenerWeakReference != null)
            downloadManagerListener = downloadManagerListenerWeakReference.get();
    	if (downloadManagerListener != null) {
			downloadManagerListener.connectionLost(taskId);
		}
    }
}
