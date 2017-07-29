package com.github.piasy.videocre.example;

import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import com.github.piasy.videocre.HwAvcEncoder;
import com.github.piasy.videocre.Mp4Recorder;
import com.github.piasy.videocre.VideoCapturers;
import com.github.piasy.videocre.VideoConfig;
import com.github.piasy.videocre.VideoSink;
import com.github.piasy.videocre.VideoSource;
import java.io.File;
import java.io.IOException;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;

public class VideoActivity extends AppCompatActivity {

    private VideoSource mVideoSource;
    private VideoSink mVideoSink;
    private SurfaceViewRenderer mVideoView;
    private Mp4Recorder mMp4Recorder;
    private Mp4Recorder mHdMp4Recorder;
    private HwAvcEncoder mHwAvcEncoder;
    private HwAvcEncoder mHdHwAvcEncoder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_video);

        VideoConfig config = VideoConfig.builder()
                .previewWidth(1280)
                .previewHeight(720)
                .outputWidth(448)
                .outputHeight(800)
                .fps(30)
                .outputBitrate(800)
                .build();
        VideoConfig hdConfig = VideoConfig.builder()
                .previewWidth(1280)
                .previewHeight(720)
                .outputWidth(720)
                .outputHeight(1280)
                .fps(30)
                .outputBitrate(2000)
                .build();
        VideoCapturer capturer = createVideoCapturer();

        mVideoView = (SurfaceViewRenderer) findViewById(R.id.mVideoView1);
        try {
            String filename = "video_source_record_" + System.currentTimeMillis();
            mMp4Recorder = new Mp4Recorder(
                    new File(Environment.getExternalStorageDirectory(), filename + ".mp4"));
            mHdMp4Recorder = new Mp4Recorder(
                    new File(Environment.getExternalStorageDirectory(), filename + "-hd.mp4"));
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "start Mp4Recorder fail!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        mHwAvcEncoder = new HwAvcEncoder(config, mMp4Recorder);
        mHdHwAvcEncoder = new HwAvcEncoder(hdConfig, mHdMp4Recorder);
        mVideoSink = new VideoSink(mVideoView, mHwAvcEncoder, mHdHwAvcEncoder);
        mVideoSource = new VideoSource(getApplicationContext(), config, capturer, mVideoSink);

        mVideoView.init(mVideoSource.getRootEglBase().getEglBaseContext(), null);
        mHwAvcEncoder.start(mVideoSource.getRootEglBase());
        mHdHwAvcEncoder.start(mVideoSource.getRootEglBase());

        initView();
    }

    @Override
    protected void onStart() {
        super.onStart();

        mVideoSource.start();
    }

    @Override
    protected void onStop() {
        super.onStop();

        mVideoSource.stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mVideoSource.destroy();
        mVideoView.release();
        mHwAvcEncoder.destroy();
        mHdHwAvcEncoder.destroy();
        mMp4Recorder.stop();
        mHdMp4Recorder.stop();
    }

    private void initView() {

        findViewById(R.id.mSwitch).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                mVideoSource.switchCamera();
            }
        });

        final TextView tvRotateDegree = (TextView) findViewById(R.id.mTvRotateDegree);
        ((SeekBar) findViewById(R.id.mRotateSeek)).setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(final SeekBar seekBar, final int progress,
                            final boolean fromUser) {
                        float rotateDegree = 360.0f * progress / 100;
                        tvRotateDegree.setText(String.format("rotate: %.1f", rotateDegree));

                        mVideoSink.rotate(rotateDegree);
                    }

                    @Override
                    public void onStartTrackingTouch(final SeekBar seekBar) {
                    }

                    @Override
                    public void onStopTrackingTouch(final SeekBar seekBar) {
                    }
                });
        ((CheckBox) findViewById(R.id.mCbFlipH)).setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(final CompoundButton buttonView,
                            final boolean isChecked) {
                        mVideoSink.flipHorizontal(isChecked);
                    }
                });
        ((CheckBox) findViewById(R.id.mCbFlipV)).setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(final CompoundButton buttonView,
                            final boolean isChecked) {
                        mVideoSink.flipVertical(isChecked);
                    }
                });
    }

    private VideoCapturer createVideoCapturer() {
        switch (MainActivity.sVideoSource) {
            case VideoSource.SOURCE_CAMERA1:
                return VideoCapturers.createCamera1Capturer(true);
            case VideoSource.SOURCE_CAMERA2:
                return VideoCapturers.createCamera2Capturer(this);
            case VideoSource.SOURCE_SCREEN:
                return null;
            case VideoSource.SOURCE_FILE:
                return VideoCapturers.createFileVideoCapturer("");
            default:
                return null;
        }
    }
}
