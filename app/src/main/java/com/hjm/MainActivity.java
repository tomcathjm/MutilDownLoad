package com.hjm;

import android.content.Intent;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity {

    /**
     *  path_url 是我自己搭建的服务器上的放的一个QQ的apk文件（android.apk）
     */

    private String path_url = "http://192.168.1.6:8080/test/android.apk";
    private int THREAD_COUNT = 5;
    private String fileName = "android.apk";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button down = (Button) findViewById(R.id.down);
        down.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            downLoad(path_url);
                        } catch (final Exception e) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    }
                }).start();
            }
        });
    }


    /**
     * 1:在本地创建一个文件，大小于服务器文件的大小相同
     */
    public void downLoad(String path) throws Exception {

        URL url = new URL(path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(5000);
        conn.setRequestMethod("GET");
        int code = conn.getResponseCode();

        if (code == 200) {
            // 服务器放回的数据长度实际上就是文件的长度
            int length = conn.getContentLength();
            conn.disconnect();
            /**
             * RandomAccessFile 随机文件访问类
             *  多个线程操作同一个文件
             *  在本地创建一个大小和服务器文件大小一样的临时文件
             */
            RandomAccessFile randomFile = new RandomAccessFile("/sdcard/" + fileName, "rwd");
            randomFile.setLength(length); // 指定临时文件的长度
            randomFile.close();

            // 平均每个线程下载的文件的大小
            int blockSize = length / THREAD_COUNT;
            for (int threadID = 1; threadID <= THREAD_COUNT; threadID++) {
                // 线程下载开始的位置
                int startIndex = (threadID - 1) * blockSize;
                // 线程结束下载的位置
                int endIndex = (threadID * blockSize) - 1;
                // 最后一个线程可能下载的长度不平均 结束为止刚好等于 文件的长度
                if (threadID == THREAD_COUNT) {
                    endIndex = length;
                }
                downLoader(path, threadID, startIndex, endIndex);
            }
        } else {
        }
    }

    /**
     * 下载文件的自线程 每一个线程 下载对应位置的文件
     *
     * @param path
     * @param threadID
     * @param startIndex
     * @param endIndex
     */
    public void downLoader(final String path, final int threadID, final int startIndex, final int endIndex) {

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    int startSize = startIndex;
                    /**
                     * 下载之前先判断是否有下载记录 已经下载的长度
                     */
                    File tempFile = new File("/sdcard/" + threadID + ".txt");
                    if (tempFile.exists()) {
                        FileInputStream fis = new FileInputStream(tempFile);
                        byte[] tempBuffer = new byte[1024];
                        int lengh = fis.read(tempBuffer);
                        String downLoadIntLengh = new String(tempBuffer, 0, lengh);
                        int size = Integer.parseInt(downLoadIntLengh);
                        startSize = size;
                        fis.close();
                    }

                    URL url = new URL(path);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();

                    /**
                     * 当请求部分资源时 需要设置特定的请求头
                     *
                     *  Range  指定每个线程从文件的什么闻之开始下载
                     *
                     *  bytes  指定请求资源的范围
                     */
                    conn.setRequestProperty("Range", "bytes=" + startSize + "-" + endIndex);

                    conn.setConnectTimeout(5000);
                    conn.setRequestMethod("GET");
                    int code = conn.getResponseCode(); //  从服务器请求全部资源 code=200， 如果从服务器请求部分资源 code=206
                    InputStream is = conn.getInputStream(); // 此方法时请求一个完整的资源(由于已经设置了特殊的请求头，此时返回的是指定位置的 返回流 )

                    if (code == 200 || code == 206) {

                        // 随机写文件的时候，从哪个位置开始写
                        RandomAccessFile file = new RandomAccessFile("/sdcard/" + fileName, "rwd");
                        file.seek(startSize);

                        int len = 0;
                        byte[] buffer = new byte[1024];
                        int total = 0; // 已经下载的长度

                        while ((len = is.read(buffer)) != -1) {

                            file.write(buffer, 0, len);

                            //  每次都要保存下载完成的长度到文件中，记录下载记录
                            total += len;

                            RandomAccessFile recordFile = new RandomAccessFile("/sdcard/" + threadID + ".txt", "rwd");
                            recordFile.write(String.valueOf(total + startSize).getBytes());
                            recordFile.close();
                        }
                        is.close();
                        file.close();
                        conn.disconnect();
                    }

                } catch (Exception e) {

                } finally {
                    clearTempFile();
                }
            }
        }).start();
    }

    private int runningThread = THREAD_COUNT;

    public synchronized void clearTempFile() {

        /**
         * 下载完毕清除临时记录文件
         */

        if (runningThread == 1) {
            for (int i = 1; i <= THREAD_COUNT; i++) {
                File deleteFile = new File("/sdcard/" + i + ".txt");
                deleteFile.delete();
            }
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, fileName + " 下载完毕", Toast.LENGTH_SHORT).show();
                    /**
                     * 下载成功启动系统的安装界面去提示安装应用
                     */
                    String path = "/sdcard/" + fileName;
                    installApk(path);
                }
            });


        } else {
            runningThread--;
        }
    }

    public void installApk(String url) {
        File file = new File(url);
        Intent intent = new Intent();
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setAction(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.fromFile(file),
                "application/vnd.android.package-archive");
        startActivity(intent);

    }
}
