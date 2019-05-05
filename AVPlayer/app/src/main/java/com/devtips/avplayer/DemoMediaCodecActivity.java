package com.devtips.avplayer;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.qmuiteam.qmui.widget.QMUITopBarLayout;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Date;

import static android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM;
import static android.media.MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED;
import static android.media.MediaCodec.INFO_OUTPUT_FORMAT_CHANGED;
import static android.media.MediaCodec.INFO_TRY_AGAIN_LATER;

/**
 * @PACKAGE_NAME: com.devtips.avplayer
 * @Package: com.devtips.avplayer
 * @ClassName: DemoMediaExtractorActivity
 * @Author: ligh
 * @CreateDate: 2019/4/14 11:13 AM
 * @Version: 1.0
 * @Description:
 */
public class DemoMediaCodecActivity extends Activity {
    private static final String TAG = "DemoMediaExtractor";

    private QMUITopBarLayout mTopBar;
    private TextView mInfoTextView;
    private Button mDecodeButton;

    private Handler mHandler;

    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE" };

    private FileOutputStream pcmFileOutputSteam;
    private FileOutputStream yuvFileOutputSteam;

    ByteBuffer[] mDecodeOutputBuffers;

    public static void verifyStoragePermissions(Activity activity) {
        try {
            //检测是否有写的权限
            int permission = ActivityCompat.checkSelfPermission(activity,
                    "android.permission.WRITE_EXTERNAL_STORAGE");
            if (permission != PackageManager.PERMISSION_GRANTED) {
                // 没有写的权限，去申请写的权限，会弹出对话框
                ActivityCompat.requestPermissions(activity, PERMISSIONS_STORAGE,REQUEST_EXTERNAL_STORAGE);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_demo_media_codec);


        verifyStoragePermissions(this);

        mHandler = new Handler();

        mInfoTextView = findViewById(R.id.info_textview);
        mDecodeButton = findViewById(R.id.start_decode_btn);
        this.initTopBar();
    }

    private void initTopBar() {
        mTopBar = findViewById(R.id.topbar);
        mTopBar.setBackgroundResource(com.qmuiteam.qmui.R.color.qmui_config_color_blue);
        TextView textView = mTopBar.setTitle("MediaCodec 示例");
        textView.setTextColor(Color.WHITE);
    }

    /**
     * 喂入数据到解码器
     *
     * @return true 喂入成功
     * @since v3.0.1
     */
    public boolean feedInputBuffer(MediaExtractor source, MediaCodec codec) {

        if (source == null || codec == null) return false;

        int inIndex = codec.dequeueInputBuffer(0);
        if (inIndex < 0)  return false;

        ByteBuffer codecInputBuffer = codec.getInputBuffers()[inIndex];
        codecInputBuffer.position(0);
        int sampleDataSize = source.readSampleData(codecInputBuffer,0);

        if (sampleDataSize <=0 ) {

            // 通知解码器结束
            if (inIndex >= 0)
                codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            return false;
        }

        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        bufferInfo.offset = 0;
        bufferInfo.presentationTimeUs = source.getSampleTime();
        bufferInfo.size = sampleDataSize;
        bufferInfo.flags = source.getSampleFlags();

        switch (inIndex)
        {
            case INFO_TRY_AGAIN_LATER: return true;
            default:
            {

                codec.queueInputBuffer(inIndex,
                        bufferInfo.offset,
                        bufferInfo.size,
                        bufferInfo.presentationTimeUs,
                        bufferInfo.flags
                );

                source.advance();

                return true;
            }
        }

    }

    /**
     * 吐出解码后的数据
     *
     * @return true 有可用数据吐出
     * @since v3.0.1
     */
    public boolean drainOutputBuffer(MediaCodec mediaCodec) {

        if (mediaCodec == null) return false;

        final
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int outIndex =  mediaCodec.dequeueOutputBuffer(info, 0);

        if ((info.flags & BUFFER_FLAG_END_OF_STREAM) != 0) {
            mediaCodec.releaseOutputBuffer(outIndex, false);
            return false;
        }

        switch (outIndex)
        {
            case INFO_OUTPUT_BUFFERS_CHANGED: return true;
            case INFO_TRY_AGAIN_LATER: return true;
            case INFO_OUTPUT_FORMAT_CHANGED:return true;
            default:
            {
                if (outIndex >= 0 && info.size > 0)
                {
                    try{
                        mDecodeOutputBuffers = mediaCodec.getOutputBuffers();// MediaCodec将解码后的数据放到此ByteBuffer[]中
                        ByteBuffer outBuffer = mDecodeOutputBuffers[outIndex];  // 拿到用于存放PCM数据的Buffer
                        byte [] chunkPCM = new byte[info.size];// BufferInfo内定义了此数据块的大小
                        outBuffer.get(chunkPCM);
                        outBuffer.clear();// 数据取出后一定记得清空此Buffer MediaCodec是循环使用这些Buffer的，不清空下次会得到同样的数据

                        if (pcmFileOutputSteam != null){
                            pcmFileOutputSteam.write(chunkPCM);
                        }
                    }
                    catch (Exception e){
                        Log.e(TAG, "cqd, failed.", e);

                    }

                    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                    bufferInfo.presentationTimeUs = info.presentationTimeUs;
                    bufferInfo.size = info.size;
                    bufferInfo.flags = info.flags;
                    bufferInfo.offset = info.offset;

                    // 释放
                    mediaCodec.releaseOutputBuffer(outIndex, false);

                    Log.i(TAG,String.format("pts:%s",info.presentationTimeUs));

                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mInfoTextView.setText(String.format("正在解码中..\npts:%s",info.presentationTimeUs));
                        }
                    });

                }

                return true;
            }
        }
    }

    /**
     * 启动解码器
     */
    private void doDecoder(){

        // step 1：创建一个媒体分离器
        MediaExtractor extractor = new MediaExtractor();
        // step 2：为媒体分离器装载媒体文件路径
        // 指定文件路径.
//        Uri videoPathUri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.img_video);
        Uri videoPathUri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.v1556618086240_1556618086309);

        Log.d(TAG, "cqd, doDecoder, videoPathUri = " + videoPathUri);
        try {
            extractor.setDataSource(this, videoPathUri, null);
        } catch (IOException e) {
            e.printStackTrace();
        }

        Date date = new Date();
        String pcmFilePath = Environment.getExternalStorageDirectory().getPath() + "/DCIM/" + date.getTime() + "_mediaCodec.pcm";
        Log.d(TAG, "cqd, pcmFilePath = " + pcmFilePath);
        try {
            File file = new File(pcmFilePath);
            pcmFileOutputSteam = new FileOutputStream(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        // step 3：获取并选中指定类型的轨道
        // 媒体文件中的轨道数量 （一般有视频，音频，字幕等）
        int trackCount = extractor.getTrackCount();
        // mime type 指示需要分离的轨道类型
        String videoMimeType = "video/";
        String audioMimeTyep = "audio/";
        MediaFormat trackFormat = null;
        // 记录轨道索引id，MediaExtractor 读取数据之前需要指定分离的轨道索引
        int videoTrackID = -1;
        int audioTrackID = -1;
        for (int i = 0; i < trackCount; i++) {
            trackFormat = extractor.getTrackFormat(i);
            String key_mime = trackFormat.getString(MediaFormat.KEY_MIME);
            if (key_mime.startsWith(videoMimeType)) {
                videoTrackID = i;
            }
            else if(key_mime.startsWith(audioMimeTyep)){
                audioTrackID = i;
            }
        }
        // 媒体文件中存在视频轨道
        // step 4：选中指定类型的轨道
        if (audioTrackID != -1)
            extractor.selectTrack(audioTrackID);

        // step 5：根据 MediaFormat 创建解码器
        MediaCodec mediaCodec = null;
        try {
            mediaCodec = MediaCodec.createDecoderByType(trackFormat.getString(MediaFormat.KEY_MIME));
            mediaCodec.configure(trackFormat,null,null,0);
            mediaCodec.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        while (true) {
            // step 6: 向解码器喂入数据
            boolean ret = feedInputBuffer(extractor,mediaCodec);
            // step 7: 从解码器吐出数据
            boolean decRet = drainOutputBuffer(mediaCodec);
            if (!ret && !decRet)break;;
        }

        // step 8: 释放资源

        // 释放分离器，释放后 extractor 将不可用
        extractor.release();
        // 释放解码器
        mediaCodec.release();

        try {
            pcmFileOutputSteam.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                mDecodeButton.setEnabled(true);
                mInfoTextView.setText("解码完成");
            }
        });

    }

    /** 启动视频解码 */
    public void startDecode(View sender) {
        mDecodeButton.setEnabled(false);

        new Thread(new Runnable() {
            @Override
            public void run() {
                doDecoder();
            }
        }).start();

    }

}
