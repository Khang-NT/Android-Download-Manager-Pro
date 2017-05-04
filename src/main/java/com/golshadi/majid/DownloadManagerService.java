package com.golshadi.majid;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.ContextCompat;

import com.golshadi.majid.core.DownloadManagerPro;
import com.golshadi.majid.core.mainWorker.QueueModerator;
import com.golshadi.majid.report.listener.DownloadSpeedListener;

import timber.log.Timber;

/**
 * Created by Khang NT on 1/24/17.
 * Email: khang.neon.1997@gmail.com
 */
public class DownloadManagerService extends Service {
    private static final String TAG = "DownloadManagerService";
    private static final int DOWNLOAD_MANAGER_NOTIFICATION_ID = 989900;

    public static final String DOWNLOAD_MANAGER_SERVICE_PREFS = "download_manager_service";
    public static final String DOWNLOAD_TASK_PER_TIME_KEY = "download_task_per_time";
    public static final String CHUNK_COUNT_KEY = "chunk_count";
    public static final String TASK_INFO_KEY = "task_info_key";
    public static final String TASK_ID_KEY = "task_id";
    public static final String TASK_DATA = "task_data";

    public static final String ACTION_START = "download.manager.action.start";
    public static final String ACTION_UPDATE_SETTINGS = "download.manager.action.update_settings";
    public static final String ACTION_START_QUEUE = "download.manager.start.queue";
    public static final String ACTION_PAUSE_QUEUE = "download.manager.pause.queue";
    public static final String ACTION_REMOVE_TASK = "download.manager.remove.task";
    public static final String ACTION_ADD_TASK = "download.manager.add.task";
    public static final String ACTION_START_DOWNLOAD_MANAGER_ACTIVITY = "download.manager.open.activity";
    public static final String CONCURRENCY_DOWNLOAD_KEY = "concurrency_download";
    public static final String MAX_CHUNKS_KEY = "max_chunks";
    public static final int DEFAULT_CONCURRENCY_DOWNLOAD = 2;
    public static final int DEFAULT_MAX_CHUNKS = 8;
    private HandlerThread handlerThread;

    public static DownloadManagerService getService(IBinder binder) {
        if (binder instanceof Binder) {
            return ((Binder) binder).getService();
        }
        throw new IllegalArgumentException("Invalid binder");
    }

    public static void addTask(Context context, TaskInfo taskInfo) {
        Intent downloadIntent = new Intent(context, DownloadManagerService.class);
        downloadIntent.setAction(DownloadManagerService.ACTION_ADD_TASK);
        downloadIntent.putExtra(DownloadManagerService.TASK_INFO_KEY, taskInfo);
        context.startService(downloadIntent);
    }

    public static void updateSetting(Context context, int concurrencyDownload, int maxChunks) {
        Intent downloadIntent = new Intent(context, DownloadManagerService.class);
        downloadIntent.setAction(ACTION_UPDATE_SETTINGS);
        downloadIntent.putExtra(CONCURRENCY_DOWNLOAD_KEY, concurrencyDownload);
        downloadIntent.putExtra(MAX_CHUNKS_KEY, maxChunks);
        context.startService(downloadIntent);
    }

    private class Binder extends android.os.Binder {
        public DownloadManagerService getService() {
            return DownloadManagerService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new Binder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return false;
    }

    private WifiManager.WifiLock wifiLock;
    private DownloadManagerPro downloadManagerPro;
    private SharedPreferences sharedPreferences;

    private int downloadTaskPerTime;
    private int chunkCount;
    private Handler handler;
    private Looper looper;

    @Override
    public void onCreate() {
        super.onCreate();
        sharedPreferences = getSharedPreferences(DOWNLOAD_MANAGER_SERVICE_PREFS, Context.MODE_PRIVATE);

        downloadTaskPerTime = sharedPreferences.getInt(DOWNLOAD_TASK_PER_TIME_KEY, 2);
        chunkCount = sharedPreferences.getInt(CHUNK_COUNT_KEY, 8);

        downloadManagerPro = new DownloadManagerPro(this, downloadTaskPerTime);

        WifiManager wifiManager = (WifiManager)getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wifiLock = wifiManager.createWifiLock(TAG);

        handlerThread = new HandlerThread("download_manager_handler");
        handlerThread.start();
        looper = handlerThread.getLooper();
        handler = new Handler(looper, new HandlerCallback());
    }

    public DownloadManagerPro getDownloadManager() {
        return downloadManagerPro;
    }

    public void setDownloadTaskPerTime(int n) {
        this.downloadTaskPerTime = n;
        this.downloadManagerPro.setDownloadTaskPerTime(n);
        sharedPreferences.edit().putInt(DOWNLOAD_TASK_PER_TIME_KEY, n).apply();
    }

    public void setChunkCount(int n) {
        this.chunkCount = n;
        sharedPreferences.edit().putInt(CHUNK_COUNT_KEY, n).apply();
    }

    public void addTask(String url, String fileName, String sdCardFolder, boolean overwrite, String jsonExtra,
                        long fileSize) {
        downloadManagerPro.addTask(url, fileName, sdCardFolder, chunkCount, overwrite, jsonExtra, fileSize);
    }

    public void startQueue() {
        downloadManagerPro.startQueueDownload();
    }

    public void pauseQueue() {
        downloadManagerPro.pauseQueueDownload();
    }

    public void toggleQueueState() {
        if (downloadManagerPro.isQueueStarted())
            pauseQueue();
        else startQueue();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            if (ACTION_ADD_TASK.equals(intent.getAction())) {
                TaskInfo taskInfo = intent.getParcelableExtra(TASK_INFO_KEY);
                addTask(taskInfo.url, taskInfo.fileName, taskInfo.sdCardFolder, taskInfo.overwrite, taskInfo.jsonExtra,
                        taskInfo.fileSize);
                downloadManagerPro.startQueueDownload();
                handler.sendEmptyMessage(0);
            } else if (ACTION_REMOVE_TASK.equals(intent.getAction())) {
                int taskId = intent.getIntExtra(TASK_ID_KEY, -1);
                if (taskId == -1)
                    throw new IllegalArgumentException("Task id not found: " + intent.getAction());
                downloadManagerPro.delete(taskId, true);
                handler.sendEmptyMessage(0);
            } else if (ACTION_PAUSE_QUEUE.equals(intent.getAction())) {
                pauseQueue();
            } else if (ACTION_START_QUEUE.equals(intent.getAction())) {
                startQueue();
            } else if (ACTION_UPDATE_SETTINGS.equals(intent.getAction())) {
                int concurrencyDownload = intent.getIntExtra(CONCURRENCY_DOWNLOAD_KEY, DEFAULT_CONCURRENCY_DOWNLOAD);
                int maxChunks = intent.getIntExtra(MAX_CHUNKS_KEY, DEFAULT_MAX_CHUNKS);
                setDownloadTaskPerTime(concurrencyDownload);
                setChunkCount(maxChunks);
                Timber.d("Change setting, concurrency download: %d, max chunks: %d", concurrencyDownload, maxChunks);
            }
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        looper.quit();
        handlerThread.interrupt();
        downloadManagerPro.dispose();
    }

    public static class TaskInfo implements Parcelable {
        final String url;
        final String fileName;
        final String sdCardFolder;
        final boolean overwrite;
        final String jsonExtra;
        final long fileSize;

        public TaskInfo(String url, String fileName, String sdCardFolder, boolean overwrite, String jsonExtra) {
            this(url, fileName, sdCardFolder, overwrite, jsonExtra, 0);
        }

        public TaskInfo(String url, String fileName, String sdCardFolder, boolean overwrite, String jsonExtra,
                        long fileSize) {
            this.url = url;
            this.fileName = fileName;
            this.sdCardFolder = sdCardFolder;
            this.overwrite = overwrite;
            this.jsonExtra = jsonExtra;
            this.fileSize = fileSize;
        }

        protected TaskInfo(Parcel in) {
            url = in.readString();
            fileName = in.readString();
            sdCardFolder = in.readString();
            overwrite = in.readInt() == 1;
            jsonExtra = in.readString();
            fileSize = in.readLong();
        }

        public static final Creator<TaskInfo> CREATOR = new Creator<TaskInfo>() {
            @Override
            public TaskInfo createFromParcel(Parcel in) {
                return new TaskInfo(in);
            }

            @Override
            public TaskInfo[] newArray(int size) {
                return new TaskInfo[size];
            }
        };

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(url);
            dest.writeString(fileName);
            dest.writeString(sdCardFolder);
            dest.writeInt(overwrite ? 1 : 0);
            dest.writeString(jsonExtra);
            dest.writeLong(fileSize);
        }

        @Override
        public String toString() {
            return "TaskInfo{" +
                    "url='" + url + '\'' +
                    ", fileName='" + fileName + '\'' +
                    ", sdCardFolder='" + sdCardFolder + '\'' +
                    ", overwrite=" + overwrite +
                    ", jsonExtra='" + jsonExtra + '\'' +
                    ", fileSize=" + fileSize +
                    '}';
        }
    }

    private class HandlerCallback implements Handler.Callback, DownloadSpeedListener, QueueModerator.OnQueueChanged {
        private final int PENDING_INTENT_REQUEST_CODE = 1;

        private final NotificationManagerCompat notificationManager;
        private final NotificationCompat.Action action;
        private final NotificationCompat.Builder builder;
        private long speed;

        public HandlerCallback() {
            notificationManager = NotificationManagerCompat.from(DownloadManagerService.this);

            action = new NotificationCompat.Action(R.drawable.ic_start_queue, "Resume", getStartQueuePendingIntent());

            builder = new NotificationCompat.Builder(DownloadManagerService.this)
                    .setSmallIcon(R.drawable.notification_downloader_icon)
                    .setColor(ContextCompat.getColor(getApplicationContext(), R.color.notification_icon))
                    .addAction(action)
                    .setAutoCancel(false)
                    .setTicker("Start download...");

            Intent intent = new Intent(ACTION_START_DOWNLOAD_MANAGER_ACTIVITY);
            intent.setPackage(getPackageName());
            if (getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).size() > 0) {
                intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                PendingIntent contentIntent = PendingIntent.getActivity(DownloadManagerService.this,
                        PENDING_INTENT_REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT);
                builder.setContentIntent(contentIntent);

                NotificationCompat.Action actionMore = new NotificationCompat.Action(
                        R.drawable.ic_details_black_24dp, "Details", contentIntent);
                builder.addAction(actionMore);

            }

            this.speed = 0;
            downloadManagerPro.setDownloadSpeedListener(this);
            downloadManagerPro.getQueueModerator().addOnQueueChangedListener(this);

            if (downloadManagerPro.isQueueStarted()) {
                onQueueChanged(downloadManagerPro.getQueueModerator().getDownloadingCount(),
                        downloadManagerPro.getQueueModerator().getPendingTaskCount());
            }
        }

        @Override
        public boolean handleMessage(Message msg) {
            return true;
        }

        public PendingIntent getStartQueuePendingIntent() {
            Intent startQueueIntent = new Intent(DownloadManagerService.this, DownloadManagerService.class);
            startQueueIntent.setAction(ACTION_START_QUEUE);
            return PendingIntent.getService(DownloadManagerService.this,
                    PENDING_INTENT_REQUEST_CODE, startQueueIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        }

        public PendingIntent getPauseQueuePendingIntent() {
            Intent pauseQueueIntent = new Intent(DownloadManagerService.this, DownloadManagerService.class);
            pauseQueueIntent.setAction(ACTION_PAUSE_QUEUE);
            return PendingIntent.getService(DownloadManagerService.this,
                    PENDING_INTENT_REQUEST_CODE, pauseQueueIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        }

        @Override
        public void onSpeedChanged(long bytesPerSec) {
            speed = bytesPerSec;
            String title = "Download manager " + getSpeedAsString(speed);
            builder.setContentTitle(title);
            notificationManager.notify(DOWNLOAD_MANAGER_NOTIFICATION_ID, builder.build());
        }

        @Override
        public synchronized void onQueueChanged(int downloadingCount, int pendingTaskCount) {
            String status = downloadingCount + " downloading. Pending: " + pendingTaskCount;
            String title = "Download manager " + getSpeedAsString(speed);

            if (downloadingCount != 0) {
                if (!wifiLock.isHeld()) wifiLock.acquire();

                builder.setContentTitle(title)
                        .setContentText(status);
                if (!builder.mActions.contains(action)) builder.mActions.add(0, action);
                action.icon = R.drawable.ic_pause_queue;
                action.actionIntent = getPauseQueuePendingIntent();
                action.title = "Pause";

                startForeground(DOWNLOAD_MANAGER_NOTIFICATION_ID, builder.build());
            } else {
                if (wifiLock.isHeld()) wifiLock.release();

                stopForeground(true);

                builder.setContentTitle(title).setContentText(status);
                action.title = "Resume";
                action.icon = R.drawable.ic_start_queue;
                action.actionIntent = getStartQueuePendingIntent();

                if (pendingTaskCount == 0) {
                    builder.mActions.remove(action);
                }

                builder.setOngoing(false);
                notificationManager.notify(DOWNLOAD_MANAGER_NOTIFICATION_ID, builder.build());
            }
        }

        private String getSpeedAsString(long speed) {
            if (speed == 0) return "";

            if (speed < 1024)
                return ". " + speed + "  B/s";
            speed /= 1024;
            if (speed < 1024)
                return ". " + speed + " KB/s";
            speed /= 1024;
            if (speed < 1024)
                return ". " + speed + " MB/s";
            speed /= 1024;
            return ". " + speed + " GB/s";
        }


    }
}
