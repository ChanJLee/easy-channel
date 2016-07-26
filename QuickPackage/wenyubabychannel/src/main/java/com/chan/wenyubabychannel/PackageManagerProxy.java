package com.chan.wenyubabychannel;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Created by chan on 16/7/25.
 */
public class PackageManagerProxy implements InvocationHandler {
    private Object mPackageManager;
    private Context mContext;

    public PackageManagerProxy(Context context, Object packageManager) {
        mContext = context;
        mPackageManager = packageManager;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        if ("getApplicationInfo".equals(method.getName())) {
            return invokeGetApplicationInfo(method, args);
        }

        return method.invoke(mPackageManager, args);
    }

    private Object invokeGetApplicationInfo(Method method, Object[] args)
            throws InvocationTargetException, IllegalAccessException, PackageManager.NameNotFoundException, IOException {
        int mask = (int) args[1];

        Object result = method.invoke(mPackageManager, args);
        if (mask == PackageManager.GET_META_DATA) {
            ApplicationInfo applicationInfo = (ApplicationInfo) result;
            if (applicationInfo.metaData == null) {
                applicationInfo.metaData = new Bundle();
            }
            applicationInfo.metaData.putString("UMENG_CHANNEL", getChannel());
        }

        return result;
    }

    private String getChannel() {
        try {
            ApplicationInfo appInfo = mContext.getPackageManager()
                    .getApplicationInfo(mContext.getPackageName(), 0);

            File apk = new File(appInfo.sourceDir);
            RandomAccessFile randomAccessFile = new RandomAccessFile(apk, "r");
            randomAccessFile.seek(randomAccessFile.length() - 2);
            short offset = (short) randomAccessFile.read();

            randomAccessFile.seek(randomAccessFile.length() - offset);
            int magic = randomAccessFile.readInt();

            if (magic != 0x52560b0b) {
                return "known";
            }
            byte[] flavor = new byte[offset - 2 - 4];
            randomAccessFile.read(flavor);
            return new String(flavor);
        } catch (Exception e) {
            return "unknown";
        }
    }
}
