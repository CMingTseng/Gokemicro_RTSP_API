package com.gokemicro.rtspplayer;

import android.net.Uri;

import java.util.Date;

/**
 * Created by Neo on 2017/11/22.
 */

public class MediaInfo {
    private String mTitle;

    private String mName;

    private String mPath;

    private Uri mCachePath;

    private String mPreviewPath;

    private int mStatus;

    private int mDuration;

    private int mSize;

    private Date mTimestamp;

    public String getTitle() {
        return mTitle;
    }

    public void setTitle(String videotitle) {
        mTitle = videotitle;
    }

    public String getName() {
        return mName;
    }

    public void setPath(String path) {
        mPath = path;
    }

    public String getPath() {
        return mPath;
    }

    public String getPreviewPath() {
        return mPreviewPath;
    }

    public void setPreviewPath(String previewPath) {
        mPreviewPath = previewPath;
    }

    public void setName(String videoname) {
        mName = videoname;
    }

    public int getStatus() {
        return mStatus;
    }

    public void setStatus(int videostatus) {
        mStatus = videostatus;
    }

    public int getDuration() {
        return mDuration;
    }

    public void setDuration(int videoduration) {
        mDuration = videoduration;
    }

    public int getSize() {
        return mSize;
    }

    public void setSize(int filesize) {
        mSize = filesize;
    }

    public Date getTimestamp() {
        return mTimestamp;
    }

    public void setTimestamp(Date timestamp) {
        mTimestamp = timestamp;
    }

    public Uri getCachePath() {
        return mCachePath;
    }

    public void setCachePath(Uri cachePath) {
        mCachePath = cachePath;
    }
}
