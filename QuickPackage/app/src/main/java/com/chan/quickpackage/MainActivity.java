package com.chan.quickpackage;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import java.io.File;
import java.io.RandomAccessFile;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        findViewById(R.id.id_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                new Thread(new Runnable() {
//                    @Override
//                    public void run() {
//                        try {
//                            ApplicationInfo appInfo = MainActivity.this.getPackageManager()
//                                    .getApplicationInfo(MainActivity.this.getPackageName(), 0);
//
//                            File apk = new File(appInfo.sourceDir);
//                            RandomAccessFile randomAccessFile = new RandomAccessFile(apk, "r");
//                            randomAccessFile.seek(randomAccessFile.length() - 2);
//                            short offset = (short) randomAccessFile.read();
//
//                            randomAccessFile.seek(randomAccessFile.length() - offset);
//                            int magic = randomAccessFile.readInt();
//
//                            if (magic != 0x52560b0b) {
//                                return;
//                            }
//                            byte[] flavor = new byte[offset - 2 - 4];
//                            randomAccessFile.read(flavor);
//                            sendMessage(new String(flavor));
//                        } catch (Exception e) {
//                            e.printStackTrace();
//                            sendMessage("发生错误");
//                        }
//                    }


//                }).start();
                try {
                    ApplicationInfo appInfo = MainActivity.this.getPackageManager()
                            .getApplicationInfo(MainActivity.this.getPackageName(), PackageManager.GET_META_DATA);
                    String what = appInfo.metaData.getString("UMENG_CHANNEL");
                    Toast.makeText(MainActivity.this, what, Toast.LENGTH_SHORT).show();
                } catch (PackageManager.NameNotFoundException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void sendMessage(String message) {
        Message msg = mHandler.obtainMessage();
        msg.obj = message;
        mHandler.sendMessage(msg);
    }

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Toast.makeText(MainActivity.this, (String) msg.obj, Toast.LENGTH_SHORT).show();
        }
    };
}
