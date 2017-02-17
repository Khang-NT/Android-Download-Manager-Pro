package com.golshadi.majid.report;

import android.support.annotation.Nullable;
import android.support.annotation.RestrictTo;

import com.golshadi.majid.Utils.helper.FileUtils;
import com.golshadi.majid.core.enums.TaskStates;
import com.golshadi.majid.database.ChunksDataSource;
import com.golshadi.majid.database.elements.Chunk;
import com.golshadi.majid.database.elements.Task;

import java.lang.ref.WeakReference;
import java.util.List;

import timber.log.Timber;

/**
 * Created by Majid Golshadi on 4/10/2014.
 */
public class ReportStructure {

    public interface OnChanged {
        void onChanged(ReportStructure rs);
    }

    public int id;
    public String fileName;
    public int state;
    public String url;
    public long fileSize;
    public boolean resumable;
    public int chunks;
    public double percent;
    public long downloadedLength;
    public String folder;
    public boolean priority;
    public @Nullable String jsonExtra;
    public @Nullable String errorMessage;
    private WeakReference<OnChanged> onChangedWeakReference;
    private int threshold = 0;
    private long lastDownloadedLength = 0;

    public ReportStructure() {
    }

    public long increaseDownloadedLength(long n) {
        this.downloadedLength += n;
        this.percent = this.fileSize > 0 ? downloadedLength * 100 / fileSize : 0;

        if (downloadedLength - lastDownloadedLength > threshold) {
            lastDownloadedLength = downloadedLength;
            onChanged();
        }
        return downloadedLength;
    }

    public synchronized void setOnChangedListener(OnChanged onChanged, int threshold) {
        Timber.d("ReportStructure.setOnChangedListener " + onChanged);
        this.onChangedWeakReference = new WeakReference<>(onChanged);
        this.threshold = threshold;
    }

    public long getTotalSize() {
        return fileSize;
    }

    public boolean isResumable() {
        return resumable;
    }

    public ReportStructure setObjectValues(Task task, List<Chunk> taskChunks) {
        this.id = task.id;
        this.fileName = task.name;
        this.state = task.state;
        this.resumable = task.resumable;
        this.url = task.url;
        this.fileSize = task.size;
        this.chunks = task.chunks;
        this.priority = task.priority;
        this.folder = task.save_address;
        this.jsonExtra = task.jsonExtra;
        this.errorMessage = task.errorMessage;

        calculatePercent(task, taskChunks);

        lastDownloadedLength = downloadedLength;
        onChanged();

        return this;
    }

    public boolean isError() {
        return state == TaskStates.ERROR;
    }

    /**
     * calculate download percent from compare chunks size with real file size
     **/
    private void calculatePercent(Task task, List<Chunk> chunks) {
        // initialize report
        this.percent = 0;
        this.downloadedLength = 0;

        // if download not completed we have chunks
        if (task.state != TaskStates.DOWNLOAD_FINISHED) {
            for (Chunk chunk : chunks) {
                this.downloadedLength += FileUtils.size(task.save_address, ChunksDataSource.getChunkFileName(chunk.id));
            }

            if (task.size > 0) {
                this.percent = ((float) downloadedLength / task.size * 100);
            }
        } else {
            this.downloadedLength = task.size;
            this.percent = 100;
        }
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public void onChanged() {
        if (onChangedWeakReference != null) {
            final OnChanged onChanged = onChangedWeakReference.get();
            if (onChanged != null) onChanged.onChanged(this);
        }
    }
}
