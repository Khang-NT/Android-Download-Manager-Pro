package com.golshadi.majid.core.chunkWorker;

import com.golshadi.majid.database.ChunksDataSource;
import com.golshadi.majid.database.elements.Chunk;
import com.golshadi.majid.database.elements.Task;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;

import io.reactivex.Completable;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import timber.log.Timber;

/**
 * Created by Majid Golshadi on 4/14/2014.
 */
public class AsyncWorker {

    public static final int MAX_RETRY = 2;
    public static final String TAG = "AsyncWorker";
    private static final int BUFFER_SIZE = 32 * 1024; // 32KB

//    private final Task task;
//    private final Chunk chunk;
//    private final Moderator observer;
//    private byte[] buffer;
//    private ConnectionWatchDog watchDog;

//    public boolean stop = false;


    @SuppressWarnings("ThrowFromFinallyBlock")
    public synchronized static Completable createAsyncWorker(final Task task, final Chunk chunk,
                                                             final ChunksDataSource chunksDataSource,
                                                             final Moderator moderator,
                                                             final OkHttpClient okHttpClient) {
        return Completable.create(emitter -> {
            if (chunk.completed) {
                emitter.onComplete();
                return;
            }
            Request.Builder requestBuilder = new Request.Builder().get().url(task.url);
            if (task.resumable) // support unresumable links
                requestBuilder.header("Range", "bytes=" + chunk.begin + "-" + chunk.end);
            File chunkFile = new File(task.save_address, ChunksDataSource.getChunkFileName(chunk.id));
            Response response = null;
            FileOutputStream chunkOutStream = null;
            try {
                chunkOutStream = new FileOutputStream(chunkFile, true);
                response = okHttpClient.newCall(requestBuilder.build()).execute();
                if (!response.isSuccessful())
                    throw new IOException("Given URL response non-success status code: " + response.code() + ", " + response.message());
                InputStream inputStream = response.body().byteStream();
                int len;
                byte[] buffer = new byte[BUFFER_SIZE];
                while (true) {
                    // cancelled
                    if (emitter.isDisposed()) return;

                    // completed
                    if ((len = inputStream.read(buffer, 0, BUFFER_SIZE)) < 0) break;

                    chunkOutStream.write(buffer, 0, len);
                    moderator.process(task.id, len);
                }
                chunk.completed = true;
                emitter.onComplete();
            } catch (InterruptedIOException ex) {
                Timber.e("[%d, %d] Interrupted IO Exception", task.id, chunk.id);
            } catch (Exception ex) {
                emitter.onError(ex);
            } finally {
                if (response != null)
                    response.close();
                if (chunkOutStream != null)
                    chunkOutStream.close();
            }
        })
                .doOnSubscribe(disposable -> Timber.d("[%d, %d] Start download chunk", task.id, chunk.id))
                .doOnDispose(() -> Timber.d("[%d, %d] Disposed chunk download", task.id, chunk.id))
                .doOnError(error -> Timber.e("[%d, %d] Download chunk error", task.id, chunk.id))
                .doOnComplete(() -> {
                    Timber.d("[%d, %d] Download chunk completed", task.id, chunk.id);
                    chunksDataSource.markChunkAsCompleted(chunk.id);
                    moderator.rebuild(chunk);
                })
                .retry(MAX_RETRY)
                .onErrorResumeNext(error -> {
                    // Moderator didn't handle complete or error state by observable chain
                    // use callback method instead
                    moderator.error(task.id, "Download chunk failed: " + error.getMessage());
                    return Completable.complete();
                });
    }


//    public AsyncWorker(Task task, Chunk chunk, Moderator moderator) {
//        buffer = new byte[BUFFER_SIZE];
//
//        this.task = task;
//        this.chunk = chunk;
//        this.observer = moderator;
//    }
//
//
//    @Override
//    public void run() {
//        int errorCount = 0;
//        while (errorCount < MAX_RETRY) {
//            try {
//
//                URL url = new URL(task.url);
//                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
//
//                // Avoid timeout exception which usually occurs in low network
//                connection.setConnectTimeout(0);
//                connection.setReadTimeout(0);
//                if (chunk.end != 0) // support unresumable links
//                    connection.setRequestProperty("Range", "bytes=" + chunk.begin + "-" + chunk.end);
//
//                connection.connect();
//
//
//                File cf = new File(task.save_address, ChunksDataSource.getChunkFileName(chunk.id));
//                // Check response code first to avoid error stream
//                int status = connection.getResponseCode();
//                InputStream remoteFileIn;
//                if (status == 416)
//                    remoteFileIn = connection.getErrorStream();
//                else
//                    remoteFileIn = connection.getInputStream();
//
//                FileOutputStream chunkFile = new FileOutputStream(cf, true);
//
//                int len = 0;
//                // set watchDoger to stop thread after 1sec if no connection lost
//                watchDog = new ConnectionWatchDog(7000, this);
//                watchDog.start();
//                while (!this.isInterrupted() &&
//                        (len = remoteFileIn.read(buffer)) > 0) {
//
//                    watchDog.reset();
//                    chunkFile.write(buffer, 0, len);
//                    process(len);
//                }
//
//                chunkFile.flush();
//                chunkFile.close();
//                watchDog.interrupt();
//                connection.disconnect();
//
//                if (!this.isInterrupted()) {
//                    observer.rebuild(chunk);
//                }
//
//
//            } catch (SocketTimeoutException e) {
//                e.printStackTrace();
//                errorCount ++;
//                Log.d(TAG, "chunk id: " + chunk.id + " errorCount:" + errorCount);
//                if (errorCount == MAX_RETRY) {
//                    observer.error(task.id, "Connection timeout");
//                } else {
//                    continue;
//                }
//
//            } catch (IOException e) {
//                e.printStackTrace();
//                errorCount ++;
//                Log.d(TAG, "chunk id: " + chunk.id + " errorCount:" + errorCount);
//                if (errorCount == MAX_RETRY) {
//                    observer.error(task.id, e.getMessage());
//                } else {
//                    continue;
//                }
//            }
//
//            return;
//        }
//    }
//
//    private void process(int read) {
//        observer.process(chunk.task_id, read);
//    }
//
//    private void pauseRelatedTask() {
//        observer.pause(task.id);
//    }
//
//    private boolean flag = true;
//
//    public void connectionTimeOut() {
//        if (flag) {
//            watchDog.interrupt();
//            flag = false;
//            observer.error(task.id, "Connection timeout");
//            pauseRelatedTask();
//        }
//
//    }

}
