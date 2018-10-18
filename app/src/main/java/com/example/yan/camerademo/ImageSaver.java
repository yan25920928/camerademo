package com.example.yan.camerademo;

import android.app.Application;
import android.media.Image;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 获取Image的帧数据，输出到指定的文件里，文件名用当前时间来生成
 * Application也可以不用外部传入，另一种方式可以提取一个公共方法getApplication()获取
 */
public class ImageSaver implements Runnable{

   private Image mImage;
   private File mFile;
   private Application mApplication;

   public ImageSaver(Image image, Application application){
       mImage = image;
       mApplication = application;
   }

    /**
     * IO流，保存Image数据
     */
    @Override
    public void run() {
        ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        FileOutputStream outputStream = null;

        //设置时间
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA);

        //设置名称
        String fname = "IMG"+sdf.format(new Date())+".jpg";
        mFile = new File(mApplication.getExternalFilesDir(null),fname);
        //写入操作
        try {
            outputStream = new FileOutputStream(mFile);
            outputStream.write(bytes);
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            //关闭资源
            mImage.close();
            if (null != outputStream){
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
