package com.chan.quickpackage;

import android.app.Application;
import android.content.Context;

import com.chan.wenyubabychannel.WenYuCore;

import java.lang.reflect.InvocationTargetException;

/**
 * Created by chan on 16/7/25.
 */
public class BaseApplication extends Application {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        try {
            WenYuCore.init(this);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
    }
}
