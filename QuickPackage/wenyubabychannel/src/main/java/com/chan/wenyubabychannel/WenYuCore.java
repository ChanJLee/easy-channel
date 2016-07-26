package com.chan.wenyubabychannel;

import android.content.Context;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Created by chan on 16/7/25.
 */
public class WenYuCore {
    public static void init(Context context) throws ClassNotFoundException, NoSuchMethodException,
            InvocationTargetException, IllegalAccessException, NoSuchFieldException {
        Class<?> activityThreadClazz = Class.forName("android.app.ActivityThread", false, context.getClassLoader());
        Method currentActivityThreadMethod = activityThreadClazz.getDeclaredMethod("currentActivityThread");
        Object activityThreadObject = currentActivityThreadMethod.invoke(null);

        Method getPackageManagerMethod = activityThreadClazz.getDeclaredMethod("getPackageManager");
        Object packageManager = getPackageManagerMethod.invoke(activityThreadObject);

        Class<?> iPackageManagerClazz = Class.forName("android.content.pm.IPackageManager", false, context.getClassLoader());

        Object proxy = Proxy.newProxyInstance(context.getClassLoader(),
                new Class[] {iPackageManagerClazz}, new PackageManagerProxy(context, packageManager));

        Field packageManagerField = activityThreadClazz.getDeclaredField("sPackageManager");
        packageManagerField.setAccessible(true);
        packageManagerField.set(activityThreadObject, proxy);
    }
}
