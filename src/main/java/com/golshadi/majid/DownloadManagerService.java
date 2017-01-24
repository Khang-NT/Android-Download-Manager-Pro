package com.golshadi.majid;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
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

import com.golshadi.majid.core.DownloadManagerPro;
import com.golshadi.majid.core.mainWorker.QueueModerator;

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

    public static final String ACTION_START_QUEUE = "download.manager.start.queue";
    public static final String ACTION_PAUSE_QUEUE = "download.manager.pause.queue";
    public static final String ACTION_REMOVE_TASK = "download.manager.remove.task";
    public static final String ACTION_ADD_TASK = "download.manager.add.task";

    public static DownloadManagerService getService(IBinder binder) {
        if (binder instanceof Binder) {
            return ((Binder) binder).getService();
        }
        return null;
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

        downloadTaskPerTime = sharedPreferences.getInt(DOWNLOAD_TASK_PER_TIME_KEY, 1);
        chunkCount = sharedPreferences.getInt(CHUNK_COUNT_KEY, 8);

        downloadManagerPro = new DownloadManagerPro(this, downloadTaskPerTime);

        WifiManager wifiManager = (WifiManager)getSystemService(Context.WIFI_SERVICE);
        wifiLock = wifiManager.createWifiLock(TAG);

        HandlerThread handlerThread = new HandlerThread("download_manager_handler");
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

    public void addTask(String url, String fileName, String sdCardFolder, boolean overwrite, String jsonExtra) {
        downloadManagerPro.addTask(url, fileName, sdCardFolder, chunkCount, overwrite, jsonExtra);
    }

    public void startQueue() {
        if (!wifiLock.isHeld()) {
            wifiLock.acquire();
        }
        downloadManagerPro.startQueueDownload();
    }

    public void pauseQueue() {
        if (wifiLock.isHeld()) {
            wifiLock.release();
        }
        downloadManagerPro.pauseQueueDownload();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (ACTION_ADD_TASK.equals(intent.getAction())) {
            TaskInfo taskInfo = intent.getParcelableExtra(TASK_INFO_KEY);
            addTask(taskInfo.url, taskInfo.fileName, taskInfo.sdCardFolder, taskInfo.overwrite, taskInfo.jsonExtra);
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
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        looper.quit();
    }

    public static class TaskInfo implements Parcelable {
        final String url;
        final String fileName;
        final String sdCardFolder;
        final boolean overwrite;
        final String jsonExtra;

        public TaskInfo(String url, String fileName, String sdCardFolder, boolean overwrite, String jsonExtra) {
            this.url = url;
            this.fileName = fileName;
            this.sdCardFolder = sdCardFolder;
            this.overwrite = overwrite;
            this.jsonExtra = jsonExtra;
        }

        protected TaskInfo(Parcel in) {
            url = in.readString();
            fileName = in.readString();
            sdCardFolder = in.readString();
            overwrite = in.readInt() == 1;
            jsonExtra = in.readString();
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
            dest.writeSerializable(sdCardFolder);
            dest.writeInt(overwrite ? 1 : 0);
            dest.writeString(jsonExtra);
        }
    }

    private class HandlerCallback implements Handler.Callback {
        private final int PENDING_INTENT_REQUEST_CODE = 1;

        private final NotificationManagerCompat notificationManager;
        private final String appName;
        private final PendingIntent pauseQueuePendingIntent;
        private final PendingIntent startQueuePendingIntent;
        private final NotificationCompat.Builder builder;
        private int lastDownloadingCount;
        private int lastPendingTaskCount;

        public HandlerCallback() {
            notificationManager = NotificationManagerCompat.from(DownloadManagerService.this);
            lastDownloadingCount = -1;
            lastPendingTaskCount = -1;

            PackageManager packageManager = getPackageManager();
            ApplicationInfo applicationInfo;
            try {
                applicationInfo = packageManager.getApplicationInfo(getApplicationInfo().packageName, 0);
            } catch (final PackageManager.NameNotFoundException e) {
                throw new RuntimeException(e);
            }
            this.appName = String.valueOf(packageManager.getApplicationLabel(applicationInfo));


            Intent startQueueIntent = new Intent(DownloadManagerService.this, DownloadManagerService.class);
            startQueueIntent.setAction(ACTION_START_QUEUE);
            startQueuePendingIntent = PendingIntent.getService(DownloadManagerService.this,
                    PENDING_INTENT_REQUEST_CODE, startQueueIntent, PendingIntent.FLAG_UPDATE_CURRENT);

            Intent pauseQueueIntent = new Intent(DownloadManagerService.this, DownloadManagerService.class);
            pauseQueueIntent.setAction(ACTION_PAUSE_QUEUE);
            pauseQueuePendingIntent = PendingIntent.getService(DownloadManagerService.this,
                    PENDING_INTENT_REQUEST_CODE, pauseQueueIntent, PendingIntent.FLAG_UPDATE_CURRENT);


            builder = new NotificationCompat.Builder(DownloadManagerService.this)
                    .setContentTitle("Download manager [" + appName + "]")
                    .setSmallIcon(R.drawable.notification_downloader_icon)
                    .setAutoCancel(false);
        }

        @Override
        public boolean handleMessage(Message msg) {
            QueueModerator queue = downloadManagerPro.getQueueModerator();
            int downloadingCount = queue.getDownloadingCount();
            int pendingTaskCount = queue.getPendingTaskCount();
            builder.setContentInfo("Downloading: " + downloadingCount + ". Pending: " + pendingTaskCount);

            if (downloadManagerPro.isQueueStarted()) {

                if (downloadingCount != lastDownloadingCount || pendingTaskCount != lastPendingTaskCount) {
//                    builder.setOngoing(true);
                    builder.mActions.clear();
                    builder.addAction(R.drawable.ic_pause_queue, "Pause all", pauseQueuePendingIntent);
                    startForeground(DOWNLOAD_MANAGER_NOTIFICATION_ID, builder.build());
                }

                lastDownloadingCount = downloadingCount;
                lastPendingTaskCount = pendingTaskCount;
            } else {
                /**
                 * If previous state is Downloading, call pause queue to release wifi lock then
                 *      If is foreground: stop foreground
                 *
                 * If notification is active, update pending count
                 */
                if (lastDownloadingCount != -1) {
                    pauseQueue();
                    stopForeground(false);

                    lastDownloadingCount = -1;
                    // force update notification
                    lastPendingTaskCount = -1;
                }

                if (lastPendingTaskCount != pendingTaskCount && isNotificationActive()) {
                    builder.mActions.clear();
                    builder.addAction(R.drawable.ic_start_queue, "Start queue", startQueuePendingIntent);
                    notificationManager.notify(DOWNLOAD_MANAGER_NOTIFICATION_ID, builder.build());

                    notificationManager.notify();

                    lastPendingTaskCount = pendingTaskCount;
                }
            }

            handler.sendEmptyMessageDelayed(0, 1000);
            return true;
        }

        public boolean isNotificationActive() {
            Intent intent = new Intent(DownloadManagerService.this, DownloadManagerService.class);
            intent.setAction(ACTION_PAUSE_QUEUE);
            PendingIntent test = PendingIntent.getService(DownloadManagerService.this,
                    PENDING_INTENT_REQUEST_CODE, intent, PendingIntent.FLAG_NO_CREATE);

            intent.setAction(ACTION_START_QUEUE);
            PendingIntent test2 = PendingIntent.getService(DownloadManagerService.this,
                    PENDING_INTENT_REQUEST_CODE, intent, PendingIntent.FLAG_NO_CREATE);
            return test != null && test2 != null;
        }
    }
}
