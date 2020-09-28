package com.sinovoice.example;

import android.content.Context;
import android.os.Environment;


import java.io.File;

import java.io.FileOutputStream;
import java.util.ArrayList;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 此类用于辅助演示如何在录音识别的同时，保存录音为pcm格式的数据，格式为16K16BIT 单声道
 */
public class VoiceCollector {
    private ArrayList<byte[]> mVoicelist;
    private byte[] mTotalBytes;
    private SimpleDateFormat formatter = new SimpleDateFormat ("yyyy-MM-dd-HH-mm-ss");
    private static VoiceCollector instance;
    private VoiceCollector() {
        mVoicelist = new ArrayList<byte[]>();
    }
    public static synchronized VoiceCollector getInstance() {
        if (instance == null) {
            instance = new VoiceCollector();
        }
        return instance;
    }

    /**
     * 可在{@link com.sinovoice.hcicloudsdk.recorder.AsrRecorderListener#onAudioRecorded(byte[], int)}回调实现中收集录音数据
     * @param data
     */
    public void collect(byte[] data) {
        if (data != null) {
            byte[] tmp = new byte[data.length];
            System.arraycopy(data, 0, tmp, 0, data.length);
            mVoicelist.add(tmp);
        }
    }
    public byte[] get() {
        int totalLength = 0;
        for (byte[] data : mVoicelist) {
            totalLength += data.length;
        }
        mTotalBytes = new byte[totalLength];
        int copiedlen = 0;
        for (byte[] data : mVoicelist) {
            System.arraycopy(data, 0, mTotalBytes, copiedlen, data.length);
            copiedlen += data.length;
        }
        return mTotalBytes;
    }
    public void savePCMData(Context context) {
        byte[] data = get();
        try {
            String savePath = Environment.getExternalStorageDirectory().getAbsolutePath()+ File.separator + "sinovoice"
                    + File.separator + context.getPackageName() ;
            File dirFile = new File(savePath);
            if(!dirFile.exists()){
                dirFile.mkdir();
            }

            Date curDate =  new Date(System.currentTimeMillis());
            String time =  formatter.format(curDate);
            File file = new File(savePath + File.separator + time + ".pcm");
            FileOutputStream os = new FileOutputStream(file);
            os.write(data);
            os.flush();
            os.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void clear(){
        if(mVoicelist != null) {
            mVoicelist.clear();
        }
    }
}
