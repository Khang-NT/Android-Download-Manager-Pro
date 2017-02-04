package com.golshadi.majid.database.elements;

import android.content.ContentValues;
import android.database.Cursor;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.Nullable;

import com.golshadi.majid.database.constants.TASKS;

/**
 * Created by Majid Golshadi on 4/10/2014.
 * <p>
 * "CREATE TABLE "+ TABLES.TASKS + " ("
 * + TASKS.COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
 * + TASKS.COLUMN_NAME + " CHAR( 128 ) NOT NULL, "
 * + TASKS.COLUMN_SIZE + " INTEGER, "
 * + TASKS.COLUMN_STATE + " INT( 3 ), "
 * + TASKS.COLUMN_URL + " CHAR( 256 ), "
 * + TASKS.COLUMN_PERCENT + " INT( 3 ), "
 * + TASKS.COLUMN_CHUNKS + " BOOLEAN, "
 * + TASKS.COLUMN_NOTIFY + " BOOLEAN, "
 * + TASKS.COLUMN_SAVE_ADDRESS + " CHAR( 256 ),"
 * + TASKS.COLUMN_EXTENSION + " CHAR( 32 )"
 * + " ); "
 */
public class Task implements Parcelable {

    public int id;
    public String name;
    public long size;
    public int state;
    public String url;
    public int percent;
    public int chunks;
    public boolean notify;
    public boolean resumable;
    public String save_address;
    public boolean priority;
    public @Nullable String jsonExtra;
    public @Nullable String errorMessage;

    public Task() {
        this.id = 0;
        this.name = null;
        this.size = 0;
        this.state = 0;
        this.url = null;
        this.percent = 0;
        this.chunks = 0;
        this.notify = true;
        this.resumable = true;
        this.save_address = null;
        this.priority = false;  // low priority
        this.jsonExtra = null;
    }

    public Task(long size, String name, String url,
                int state, int chunks, String sdCardFolderAddress,
                boolean priority, String jsonExtra) {
        this.id = 0;
        this.name = name;
        this.size = size;
        this.state = state;
        this.url = url;
        this.percent = 0;
        this.chunks = chunks;
        this.notify = true;
        this.resumable = true;
        this.save_address = sdCardFolderAddress;
        this.priority = priority;
        this.jsonExtra = jsonExtra;
    }

    protected Task(Parcel in) {
        id = in.readInt();
        name = in.readString();
        size = in.readLong();
        state = in.readInt();
        url = in.readString();
        percent = in.readInt();
        chunks = in.readInt();
        notify = in.readByte() != 0x00;
        resumable = in.readByte() != 0x00;
        save_address = in.readString();
        priority = in.readByte() != 0x00;
        jsonExtra = (String) in.readValue(String.class.getClassLoader());
        errorMessage = (String) in.readValue(String.class.getClassLoader());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(id);
        dest.writeString(name);
        dest.writeLong(size);
        dest.writeInt(state);
        dest.writeString(url);
        dest.writeInt(percent);
        dest.writeInt(chunks);
        dest.writeByte((byte) (notify ? 0x01 : 0x00));
        dest.writeByte((byte) (resumable ? 0x01 : 0x00));
        dest.writeString(save_address);
        dest.writeByte((byte) (priority ? 0x01 : 0x00));
        dest.writeValue(jsonExtra);
        dest.writeValue(errorMessage);
    }

    @SuppressWarnings("unused")
    public static final Parcelable.Creator<Task> CREATOR = new Parcelable.Creator<Task>() {
        @Override
        public Task createFromParcel(Parcel in) {
            return new Task(in);
        }

        @Override
        public Task[] newArray(int size) {
            return new Task[size];
        }
    };

    public Task setErrorMessage(@Nullable String errorMessage) {
        this.errorMessage = errorMessage;
        return this;
    }

    public ContentValues convertToContentValues() {
        ContentValues contentValues = new ContentValues();

        if (id != 0)
            contentValues.put(TASKS.COLUMN_ID, id);

        contentValues.put(TASKS.COLUMN_NAME, name);
        contentValues.put(TASKS.COLUMN_SIZE, size);
        contentValues.put(TASKS.COLUMN_STATE, state);
        contentValues.put(TASKS.COLUMN_URL, url);
        contentValues.put(TASKS.COLUMN_PERCENT, percent);
        contentValues.put(TASKS.COLUMN_CHUNKS, chunks);
        contentValues.put(TASKS.COLUMN_NOTIFY, notify);
        contentValues.put(TASKS.COLUMN_RESUMABLE, resumable);
        contentValues.put(TASKS.COLUMN_SAVE_ADDRESS, save_address);
        contentValues.put(TASKS.COLUMN_PRIORITY, priority);
        contentValues.put(TASKS.COLUMN_EXTRA_JSON, jsonExtra);

        return contentValues;
    }

    public void cursorToTask(Cursor cr) {
        id = cr.getInt(
                cr.getColumnIndex(TASKS.COLUMN_ID));
        name = cr.getString(
                cr.getColumnIndex(TASKS.COLUMN_NAME));
        size = cr.getLong(
                cr.getColumnIndex(TASKS.COLUMN_SIZE));
        state = cr.getInt(
                cr.getColumnIndex(TASKS.COLUMN_STATE));
        url = cr.getString(
                cr.getColumnIndex(TASKS.COLUMN_URL));
        percent = cr.getInt(
                cr.getColumnIndex(TASKS.COLUMN_PERCENT));
        chunks = cr.getInt(
                cr.getColumnIndex(TASKS.COLUMN_CHUNKS));
        notify = cr.getInt(
                cr.getColumnIndex(TASKS.COLUMN_NOTIFY)) > 0;
        resumable = cr.getInt(
                cr.getColumnIndex(TASKS.COLUMN_RESUMABLE)) > 0;
        save_address = cr.getString(
                cr.getColumnIndex(TASKS.COLUMN_SAVE_ADDRESS));
        priority = cr.getInt(
                cr.getColumnIndex(TASKS.COLUMN_PRIORITY)) > 0;
        jsonExtra = cr.getString(
                cr.getColumnIndex(TASKS.COLUMN_EXTRA_JSON));
        errorMessage = cr.getString(
                cr.getColumnIndex(TASKS.COLUMN_ERROR_MESSAGE));
    }
}
