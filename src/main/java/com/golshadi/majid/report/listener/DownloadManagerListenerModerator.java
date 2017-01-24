package com.golshadi.majid.report.listener;

import java.lang.ref.WeakReference;

/**
 * Created by Majid Golshadi on 4/21/2014.
 */
public class DownloadManagerListenerModerator {

    private WeakReference<DownloadManagerListener> downloadManagerListenerWeakReference;

    public DownloadManagerListenerModerator() {
    }

    public DownloadManagerListenerModerator(DownloadManagerListener listener){
        downloadManagerListenerWeakReference = new WeakReference<>(listener);
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
