package com.golshadi.majid.core.chunkWorker;

import com.golshadi.majid.database.ChunksDataSource;
import com.golshadi.majid.database.elements.Chunk;
import com.golshadi.majid.database.elements.Task;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import rx.Observable;
import timber.log.Timber;

/**
 * Created by Majid Golshadi on 4/14/2014.
 */
public class AsyncWorker {

    public static final int MAX_RETRY = 2;
    public static final String TAG = "AsyncWorker";
    private static final int BUFFER_SIZE = 32 * 1024; // 32KB


    public synchronized static Observable createAsyncWorker(final Task task, final Chunk chunk,
                                                             final ChunksDataSource chunksDataSource,
                                                             final Moderator moderator,
                                                             final OkHttpClient okHttpClient) {
        return Observable.create(subscriber -> {
            if (chunk.completed) {
                subscriber.onCompleted();
                return;
            }

            Request.Builder requestBuilder = new Request.Builder().get().url(task.url);
            if (task.resumable) // support unresumable links
                requestBuilder.header("Range", "bytes=" + chunk.begin + "-" + chunk.end);
            File chunkFile = new File(task.save_address, ChunksDataSource.getChunkFileName(chunk.id));
            okHttpClient.newCall(requestBuilder.build()).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    subscriber.onError(e);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (subscriber.isUnsubscribed()) return;
                    FileOutputStream chunkOutStream = null;
                    try {
                        if (!response.isSuccessful()) {
                            subscriber.onError(new IOException("Given URL response non-success status code: "
                                    + response.code() + ", " + response.message()));
                            return;
                        }
                        chunkOutStream = new FileOutputStream(chunkFile, true);
                        InputStream inputStream = response.body().byteStream();
                        int len;
                        byte[] buffer = new byte[BUFFER_SIZE];
                        while (true) {
                            // cancelled
                            if (subscriber.isUnsubscribed()) return;

                            // completed
                            if ((len = inputStream.read(buffer, 0, BUFFER_SIZE)) < 0) break;

                            chunkOutStream.write(buffer, 0, len);
                            moderator.process(task.id, len);
                        }
                        chunk.completed = true;
                        subscriber.onCompleted();
                    } finally {
                        try {
                            response.close();
                            if (chunkOutStream != null) chunkOutStream.close();
                        } catch (Exception ignore){
                        }
                    }
                }
            });
        })
                .doOnSubscribe(() -> Timber.d("[%d, %d] Start download chunk", task.id, chunk.id))
                .doOnUnsubscribe(() -> Timber.d("[%d, %d] Disposed chunk download", task.id, chunk.id))
                .doOnError(error -> Timber.e("[%d, %d] Download chunk error", task.id, chunk.id))
                .doOnCompleted(() -> {
                    if (chunk.completed) {
                        Timber.d("[%d, %d] Download chunk completed", task.id, chunk.id);
                        chunksDataSource.markChunkAsCompleted(chunk);
                        moderator.rebuild(chunk);
                    }
                })
                .retry(MAX_RETRY)
                .onErrorResumeNext(error -> {
                    // Moderator didn't handle complete or error state by observable chain
                    // use callback method instead
                    try {
                        moderator.error(task.id, "Download chunk failed: " + error.getMessage());
                    } catch (Throwable ex) {
                        Timber.d(ex);
                    }
                    return Observable.empty();
                });
    }

}
