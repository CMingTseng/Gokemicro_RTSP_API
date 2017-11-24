package com.gokemicro.rtspplayer;

import android.databinding.DataBindingUtil;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.TextureView;
import android.view.ViewGroup;

import com.bumptech.glide.Glide;
import com.gokemicro.api.CGIService;
import com.gokemicro.api.define.APIInfo;
import com.gokemicro.api.json.ResultData;
import com.gokemicro.api.json.VideoInfo;
import com.gokemicro.api.json.VideolistInfo;
import com.gokemicro.rtspplayer.databinding.ActivityMainBinding;
import com.gokemicro.rtspplayer.databinding.ItemImageBinding;

import org.videolan.libvlc.MediaPlayer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import idv.neo.restful.engine.RestfulFactory;
import idv.neo.restful.engine.RestfulRetryHelper;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


public class MainActivity extends AppCompatActivity implements TextureView.SurfaceTextureListener, MediaPlayer.onVideoSizeChangedListener, MediaPlayer.onHardwareAccelerationErrorListener, MediaPlayer.EventListener {
    private final static String TAG = "MainActivity";
    private ActivityMainBinding mBinding;
    private VideoAdapter mAdapter;
    private String mIP;
    private CGIService mService;
    private MediaPlayer mMediaPlayer;
    private Surface mSurface;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = DataBindingUtil.setContentView(this, R.layout.activity_main);
        mAdapter = new VideoAdapter();
        mBinding.video.setSurfaceTextureListener(this);
        mBinding.videolist.setAdapter(mAdapter);
        mBinding.fab.setOnClickListener(view -> {
            if (!TextUtils.isEmpty(mBinding.address.getText().toString())) {
                mIP = mBinding.address.getText().toString();
                mService = RestfulFactory.getService("http://" + mIP + "/", null, CGIService.class);
                tryStartMediaPlayer();
                preGetVideoInfo(new ArrayList<>());
            }
        });
    }

    private String getImagePath(String name) {
        return String.format(Locale.getDefault(), "http://" + mIP + ":%d/sdcard/DVR/PIC/" + name, 80);
    }

    private String getVideoPath(String name) {
        return String.format(Locale.getDefault(), "http://" + mIP + ":%d/sdcard/DVR/VIDEO/" + name, 80);
    }

    private void preGetVideoInfo(final ArrayList<MediaInfo> medias) {
        final Call<VideolistInfo> getvideolistcall = mService.getVideolist(APIInfo.TYPEITEM_FILE, APIInfo.FILE_SEARCHFILE, medias.size());
        RestfulRetryHelper.enqueueWithRetry(getvideolistcall, 3, new Callback<VideolistInfo>() {
            @Override
            public void onResponse(@NonNull Call<VideolistInfo> call, @NonNull Response<VideolistInfo> response) {
                final String resultcode = response.body().getResultCode();
                if (response.isSuccessful()) {
                    switch (resultcode) {
                        case ResultData.RESULT_OK:
                            final List<VideoInfo> infos = response.body().getPrevideo();
                            if (infos != null && infos.size() > 0) {
                                for (VideoInfo info : infos) {
                                    final MediaInfo preinfo = new MediaInfo();
                                    preinfo.setName(info.getVideoname());
                                    preinfo.setTitle(info.getVideotitle());
                                    preinfo.setPath(getVideoPath(info.getVideoname()));
                                    preinfo.setDuration(info.getVideoduration());
                                    preinfo.setSize(info.getFilesize());
                                    preinfo.setStatus(info.getVideostatus());
                                    final String name = info.getVideoname();
                                    preinfo.setPreviewPath(getImagePath(name.replace("mkv", "jpg")));
                                    mAdapter.add(preinfo);
                                    medias.add(preinfo);
                                }
                                preGetVideoInfo(medias);
                            }
                            break;
                        case ResultData.RESULT_FAIL:
//                            syncDownloadVideoToCache(medias, 0);
                            break;
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<VideolistInfo> call, @NonNull Throwable t) {

            }
        });
    }

    private void syncDownloadVideoToCache(final ArrayList<MediaInfo> medias, final int position) {
        if (position < medias.size()) {
            final Call<ResponseBody> downloadcall = mService.downloadFileWithDynamicUrl(medias.get(position).getPath());
            RestfulRetryHelper.enqueueWithRetry(downloadcall, 3, new Callback<ResponseBody>() {
                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                    if (response.isSuccessful()) {
                        try {
                            final File video = File.createTempFile(medias.get(position).getName().replace(".mkv", ""), ".mp4", MainActivity.this.getFilesDir());
                            InputStream inputStream = null;
                            OutputStream outputStream = null;
                            try {
                                byte[] fileReader = new byte[4096];
                                final long fileSize = response.body().contentLength();
                                long fileSizeDownloaded = 0;
                                inputStream = response.body().byteStream();
                                outputStream = new FileOutputStream(video);
                                while (true) {
                                    int read = inputStream.read(fileReader);
                                    if (read == -1) {
                                        break;
                                    }
                                    outputStream.write(fileReader, 0, read);
                                    fileSizeDownloaded += read;
                                    //FIXME how to notify to progressbar ?
                                    Log.d(TAG, "file download: " + fileSizeDownloaded + " of " + fileSize);
                                }
                                outputStream.flush();
                                final MediaInfo info = medias.get(position);
                                info.setCachePath(Uri.fromFile(video));
                                final int next = position + 1;
                                syncDownloadVideoToCache(medias, next);
                            } catch (IOException e) {

                            } finally {
                                if (inputStream != null) {
                                    inputStream.close();
                                }
                                if (outputStream != null) {
                                    outputStream.close();
                                }
                            }
                        } catch (IOException e) {

                        }

                    } else {
                        Log.d(TAG, "server contact failed");
                    }
                }

                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    Log.e(TAG, "error");
                }
            });
        }
    }

    private void tryStartMediaPlayer() {
        if (mSurface != null & mIP != null) {
            mMediaPlayer = new MediaPlayer(mBinding.getRoot().getContext());
            mMediaPlayer.setEventListener(this);
            mMediaPlayer.setVideoView(mBinding.video);
            mMediaPlayer.setOnVideoSizeChangedListener(this);
            mMediaPlayer.setOnHardwareAccelerationErrorListener(this);
            mMediaPlayer.setDataSource(Uri.parse("rtsp://" + String.format(Locale.getDefault(), mIP + ":%d", 554) + "/live/udp/ch1_0"), true);//FIXME
            mMediaPlayer.play();
        }
    }

    public class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.ViewHolder> {
        private final ArrayList<MediaInfo> mMedias = new ArrayList<MediaInfo>();

        public VideoAdapter() {
        }

        public void add(MediaInfo media) {
            mMedias.add(media);
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            return mMedias.size();
        }

        @Override
        public VideoAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new VideoAdapter.ViewHolder(ItemImageBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(VideoAdapter.ViewHolder holder, int position) {
            holder.onBind(mMedias.get(position));
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            public ItemImageBinding binding;
            private Handler mHandler = new Handler(Looper.getMainLooper());

            public ViewHolder(ItemImageBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }

            public ItemImageBinding getBinding() {
                return binding;
            }

            public void onBind(MediaInfo info) {
                this.binding.setMedia(info);
                mHandler.postDelayed(() -> Glide.with(getBinding().image.getContext())
                        .load(info.getPreviewPath())
                        .into(getBinding().image), 500);
            }
        }
    }


    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
        Log.d(TAG, this + "_onSurfaceTextureAvailable... : View width : " + width + " x View height : " + height);
        mSurface = new Surface(surfaceTexture);
        tryStartMediaPlayer();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

    }

    @Override
    public void eventHardwareAccelerationError() {

    }

    @Override
    public void onVideoSizeChanged(MediaPlayer mediaPlayer, int i, int i1) {

    }

    @Override
    public void onEvent(MediaPlayer.Event event) {

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
