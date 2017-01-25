package com.golshadi.majid.report.listener;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.Nullable;
import android.util.Log;

import com.golshadi.majid.core.DownloadManagerPro;
import com.golshadi.majid.database.TasksDataSource;
import com.golshadi.majid.database.elements.Task;

import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

/**
 * Created by Majid Golshadi on 4/21/2014.
 */
public class DownloadManagerListenerModerator implements Consumer<Long> {

    public static final String TAG = "DownloadListener";
    public static final int TIMER_INTERVAL = 1000;
    private final Context context;
    private final TasksDataSource tasksDataSource;
    private WeakReference<DownloadManagerListener> downloadManagerListenerWeakReference;
    private WeakReference<DownloadSpeedListener> downloadSpeedListener;
    private Disposable disposable;
    private long accumulateByteDownloaded;
    private long speed;

    public DownloadManagerListenerModerator(Context context, TasksDataSource tasksDataSource) {
        this.context = context;
        this.tasksDataSource = tasksDataSource;
        this.accumulateByteDownloaded = 0;
        this.speed = 0;
    }

    public DownloadManagerListenerModerator setDownloadManagerListener(DownloadManagerListener downloadManagerListener) {
        this.downloadManagerListenerWeakReference = new WeakReference<>(downloadManagerListener);
        return this;
    }

    public void setDownloadSpeedListener(@Nullable DownloadSpeedListener listener) {
        if (listener != null) {
            this.downloadSpeedListener = new WeakReference<>(listener);
            this.accumulateByteDownloaded = 0;
            synchronized (context) {
                if (disposable != null && !disposable.isDisposed()) {
                    disposable.dispose();
                    disposable = null;
                }
                this.disposable = Observable.interval(TIMER_INTERVAL, TimeUnit.MILLISECONDS, Schedulers.computation())
                        .subscribe(this);
            }
        } else {
            this.downloadSpeedListener = null;
        }
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

    public void countBytesDownloaded(long bytes) {
        this.accumulateByteDownloaded += bytes;
    }

    @Override
    public void accept(Long aLong) throws Exception {
        long tmp = accumulateByteDownloaded * 1000 / TIMER_INTERVAL;
        Log.d(TAG, "speed: " + tmp);
        accumulateByteDownloaded = 0;
        synchronized (context) {
            final DownloadSpeedListener listener = downloadSpeedListener == null ? null : downloadSpeedListener.get();
            if (listener == null) {
                if (disposable != null && !disposable.isDisposed()) {
                    disposable.dispose();
                    disposable = null;
                }
                return;
            }
            if (tmp != speed) {
                this.speed = tmp;
                listener.onSpeedChanged(speed);
            }
        }
    }
}
