随着业务的增长，传统的多渠道打包方式已经不符合需求。比如，我们需要在360, 豌豆荚等平台发布新版本，就必须对每一个应用商店编译一份apk，然后发布。可是如果我们要发十来个应用商店呢？是不是还要再编译一次？然而我们只是改变了友盟的渠道号，就必须再打包一次，这对时间显然是种巨大浪费。所以我们必须寻找突破，最好是在原来的基础之上，仅仅需要一点点的修改，就能够做到快速多渠道打包。

一次偶然的机会（在地铁上，晕厥了一会儿），我想到：友盟对渠道的判断无非就是如下的代码：

```
 ApplicationInfo appInfo = MainActivity.this.getPackageManager()
                            .getApplicationInfo(MainActivity.this.getPackageName(), PackageManager.GET_META_DATA);
fianl String channel = appInfo.metaData.getString("UMENG_CHANNEL");
```

这里有的朋友可能会感到困惑，我提一下，在打包应用时，如果我们是要发布到QQ的应用宝，通常是在AndroidManifest.xml中修改如下的代码：

```
<meta-data android:value="QQ" android:name="UMENG_CHANNEL"/>
```
只要将value设置为QQ，只要安装了此应用的人，都会被认为是通过QQ应用宝安装的应用。

好了，科普完就讲正事。我们看看上面的代码，首先是MainActivity.this.getPackageManager()获得PackageManager，然后通过调用getApplicationInfo获得AndroidManifest.xml中的meta-data。之后我们的突破口就想办法从源码上下手吧


## 源码分析 ##
我们看下具体的代码：
![这里写图片描述](http://img.blog.csdn.net/20160726125000798)
这里不多讲了，显然是要到ContextImpl中去查看具体的实现，至于原因，我在之前插件化系列的文章中已经提及了，读者自行查阅。

ContextImpl.java:
![这里写图片描述](http://img.blog.csdn.net/20160726125133830)

这里是通过ActivityThread获得IPackageManager，看到以I开头就知道，它的类型肯定是接口类型，那么我们就有可能通过动态代理拦截getApplicationInfo方法，修改它的返回值，从而达到欺骗友盟的目的，然他误以为我们修改的"UMENG_CHANNEL"值就是从AndroidManifest.xml中读取的。

我们找到ActivityThread中去查看：
![这里写图片描述](http://img.blog.csdn.net/20160726125433248)
卧槽，太顺了，看到sPackManager就想到了：我们可以通过hook它，然后注入我们动态代理生成的对象，来达到欺骗友盟的目的。

## 碰到的问题 ##
那么问题来了，我们如何获得相应的渠道号，然后欺骗友盟呢？这显然是不能在代码里面写死的，因为这样就得每打包一个渠道就要编译一次。

## 解决方案 ##
1：每个APK其实是一个zip文件，而在zip文件的说明里面有这样一段，参考[文献](https://en.wikipedia.org/wiki/Zip_%28file_format%29)

![这里写图片描述](http://img.blog.csdn.net/20160726125923505)
在apk的末尾有一个注释字段，“它并不算是apk文件的一部分”，通俗的话来说就是：如果我们修改这个字段的值，并不会影响整个apk的签名，也就是不必再打包也能够直接安装。从图上看20offset开始，有两个字节用于确定comment的长度，我们先计算出要写入comment的内容长度（我们的渠道号），然后写到apk文件后面不就行了吗。

为了易于理解，我截两个图：
![这里写图片描述](http://img.blog.csdn.net/20160726130838353)
在这张图里面，是原始的apk， 我们可以看到末尾两个字节 是 0x00 0x00也就代表我们的注释是空的。

下面一张图是我在写入注释之后的apk：
![这里写图片描述](http://img.blog.csdn.net/20160726131023862)
可以看到 从12:EC00h的0x7 0x8位置标志我们的注释字段有8个字节长，数一下后面的内容正好就是八个字节

2：但是我们的应用如何读apk呢，毕竟它只是个安装包啊。其实很简单，我们每个安装过的应用最后都会在/data/apk/....这个路径下，获得它的方式很简单：

```
  ApplicationInfo appInfo = mContext.getPackageManager()
                    .getApplicationInfo(mContext.getPackageName(), 0);

  File apk = new File(appInfo.sourceDir);
```
值得注意的是，我们只有读取权限哦，但这已经足够了。


## 实现 ##
现在就剩下写入到apk注释字段的内容设计了。我是这么做的：
注释字段内容 = magic_number + 渠道号 + 注释字段长度

magic_number用于确定是否是我们自己的渠道号注释方式，最后的文件的末尾存放我们整个注释的大小，这样可以方便计算偏移，使用随机读取的时候可以很容易的读取到comment的内容。

好了我们看下具体的实现:

```
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) throws IOException {

        //原始apk的存放位置 这里有个坑 就是不能用已经渠道化的apk 也就是加入了某个渠道的apk
        File apk = new File("/Users/chan/Documents/开源代码/ChanWeather/app/app-release.apk");
        FileInputStream is = new FileInputStream(apk);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        int length = -1;

        //我们把文件的内容读出来
        byte[] cache = new byte[256];
        while ((length = is.read(cache)) != -1) {
            os.write(cache, 0, length);
        }
        byte[] copy = os.toByteArray();


        //你要加入的渠道
        List<String> flavors = new ArrayList<>();
        flavors.add("QQ");
        flavors.add("360Store");
        flavors.add("WanDouJia");
        flavors.add("ywy");

        //写在comment的头部
        //内容其实很随意 取你喜欢的名字就行 我这里用的是我gf的谐音
        byte[] magic = {0x52, 0x56, 0x0b, 0x0b};

        for (String flavor : flavors) {

            //渠道的长度
            byte[] content = flavor.getBytes();
            //渠道加上魔数的长度等于注释的长度
            short commentLength = (short) (content.length + magic.length);

            //末尾在存放整个的大小 方便之后文件指针的读取 所以真正的渠道号要再多两个字节
            commentLength += 2;

            //要用小端模式存放
            for (int i = 0; i < 2; ++i) {
                copy[copy.length - 2 + i] = (byte) (commentLength % 0xff);
                commentLength >>= 8;
            }

            //目的位置
            apk = new File("/Users/chan/Documents/开源代码/ChanWeather/app/app-{what}-release.apk".replace
                    ("{what}", flavor));

            FileOutputStream fileOutputStream = new FileOutputStream(apk);
            //先是存放的原始内容
            fileOutputStream.write(copy);
            //存放的是魔数
            fileOutputStream.write(magic);
            //写入内容
            fileOutputStream.write(content);
            //再把长度信息添加到末尾
            for (int i = 0; i < 2; ++i) {
               fileOutputStream.write(copy[copy.length - 2 + i]);
            }

            fileOutputStream.flush();
            fileOutputStream.close();
        }
    }


    /**
     * 测试用
     *
     * @param file
     * @throws IOException
     */
    private static void read(String file) throws IOException {
        File apk = new File(file);
        RandomAccessFile randomAccessFile = new RandomAccessFile(apk, "r");
        randomAccessFile.seek(randomAccessFile.length() - 2);
        short offset = (short) randomAccessFile.read();

        randomAccessFile.seek(randomAccessFile.length() - offset);
        int magic = randomAccessFile.readInt();

        if (magic != 0x52560b0b) {
            System.out.println("魔数不对");
        }

        byte[] flavor = new byte[offset - 2 - 4];
        randomAccessFile.read(flavor);

        String content = new String(flavor);
        System.out.println(content);
    }
}
```
我认为注释已经足够清楚，现在我们开始实现如何在android设备中欺骗友盟，替换成我们在注释中写入的渠道号

## 替换渠道号方法回顾##
我们之前分析：我们看到在ActivityThread中是通过一个静态域存放IPackageManager的，这很符合我们的hook规则，如果你还是不懂请参阅[以往的博客](http://blog.csdn.net/u013022222/article/details/51111814)
![这里写图片描述](http://img.blog.csdn.net/20160726163540757)
之后拦截 getApplicationInfo 方法，修改它的返回值内容，使得当客户端调用appInfo.meta.get("UMENG_CHANNEL")的时候永远都是我们替换的渠道号。我们下面便开始一步步实现我们的需求。

## 获得ActivityThread ##
首先这个类是hide的，所以只能通过反射拿到它的clazz，我们看下源码分析：
![这里写图片描述](http://img.blog.csdn.net/20160726162536767)
可以看到它是个静态对象，不过如果你是老乘客的话，应该在这里轻车熟路了，因为这个分析我做了不只是一遍。（不过它也只能是静态的啊，毕竟在android里面一个进程只对应这一个ActivityThread）
拿到它还是很容易的，不过这毕竟是个私有域，名字会变化的概率比较高，我们找下有没有可以返回它的共有方法，这样变动的可能性很小，很高兴这里是有的：
![这里写图片描述](http://img.blog.csdn.net/20160726163243661)
所以我们可以拿到ActivityThread了

```
 //获取ActivityThread实例
            Class<?> activityThreadClazz = Class.forName("android.app.ActivityThread", false, context.getClassLoader());
            Method currentActivityThreadMethod = activityThreadClazz.getDeclaredMethod("currentActivityThread");
            Object activityThreadObject = currentActivityThreadMethod.invoke(null);
```
## 替换IPackageManager ##
剩下的事情就是拿到sPackageManger，替换成我们的代理类，这个代理类拦截getApplicationInfo方法，修改它的返回值，使得友盟都是拿到的我们修改的值

```
    //获得原始的IPackageManager
            Method getPackageManagerMethod = activityThreadClazz.getDeclaredMethod("getPackageManager");
            Object packageManager = getPackageManagerMethod.invoke(activityThreadObject);

            //生成我们的代理类
            Class<?> iPackageManagerClazz = Class.forName("android.content.pm.IPackageManager", false, context.getClassLoader());
            Object proxy = Proxy.newProxyInstance(context.getClassLoader(),
                    new Class[] {iPackageManagerClazz}, new PackageManagerProxy(context, packageManager));

            //把原先的IPackageManager替换掉
            Field packageManagerField = activityThreadClazz.getDeclaredField("sPackageManager");
            packageManagerField.setAccessible(true);
            packageManagerField.set(activityThreadObject, proxy);
```

## 实现代理类替换渠道号 ##
现在就只剩下代理类的实现了，不懂的还是看我上面的文章链接，我在之前的几篇博文中已经都写出来了。

```
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

        //拦截getApplicationInfo方法
        if ("getApplicationInfo".equals(method.getName())) {
            return invokeGetApplicationInfo(method, args);
        }
        
        //其它的方法就让它自己过去吧
        return method.invoke(mPackageManager, args);
    }

    private Object invokeGetApplicationInfo(Method method, Object[] args)
            throws InvocationTargetException, IllegalAccessException, PackageManager.NameNotFoundException, IOException {
        
        //获得他的第二个参数值
        //不懂的看函数签名吧
        /**
         * Retrieve all of the information we know about a particular
         * package/application.
         *
         * <p>Throws {@link NameNotFoundException} if an application with the given
         * package name cannot be found on the system.
         *
         * @param packageName The full name (i.e. com.google.apps.contacts) of an
         *                    application.
         * @param flags Additional option flags. Use any combination of
         * {@link #GET_META_DATA}, {@link #GET_SHARED_LIBRARY_FILES},
         * {@link #GET_UNINSTALLED_PACKAGES} to modify the data returned.
         *
         * @return  {@link ApplicationInfo} Returns ApplicationInfo object containing
         *         information about the package.
         *         If flag GET_UNINSTALLED_PACKAGES is set and  if the package is not
         *         found in the list of installed applications,
         *         the application information is retrieved from the
         *         list of uninstalled applications(which includes
         *         installed applications as well as applications
         *         with data directory ie applications which had been
         *         deleted with {@code DONT_DELETE_DATA} flag set).
         *
         * @see #GET_META_DATA
         * @see #GET_SHARED_LIBRARY_FILES
         * @see #GET_UNINSTALLED_PACKAGES
         */
        //ApplicationInfo getApplicationInfo(String packageName, int flags)
        int mask = (int) args[1];

        Object result = method.invoke(mPackageManager, args);
        if (mask == PackageManager.GET_META_DATA) {
            ApplicationInfo applicationInfo = (ApplicationInfo) result;
            if (applicationInfo.metaData == null) {
                applicationInfo.metaData = new Bundle();
            }
            //把UMENG_CHANNEL这个key都是替换成我们自己的
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
```
## 获取渠道号 ##
上面的代码还有一处我是没有注释的，那就是获得channel的方法。要知道，在我们安装一个apk之后，系统都会在/data/apk/。。。保留一份拷贝，所以理所当然的我们可以读到那个apk文件：

```
 ApplicationInfo appInfo = mContext.getPackageManager()
                    .getApplicationInfo(mContext.getPackageName(), 0);

 File apk = new File(appInfo.sourceDir);
```

之后就是读取文件末尾两个字节的comment大小

```
	RandomAccessFile randomAccessFile = new RandomAccessFile(apk, "r");
            randomAccessFile.seek(randomAccessFile.length() - 2);
    short offset = (short) randomAccessFile.read();
```

然后验证magic number：

```
		    randomAccessFile.seek(randomAccessFile.length() - offset);
            int magic = randomAccessFile.readInt();

            if (magic != 0x52560b0b) {
                return "known";
            }
      
```

验证通过的话，那就放心的读渠道就行了

```
            byte[] flavor = new byte[offset - 2 - 4];
            randomAccessFile.read(flavor);
            return new String(flavor);
```

## 使用 ##
因为Hook了系统服务，所以还是越早Hook越好，我们在重载Application的方法：

```
public class BaseApplication extends Application {
   
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        try {
            YetWYCore.init(this);
        } catch (Exception e) {}
    }
}
```

效果图：
![这里写图片描述](http://img.blog.csdn.net/20160726165016918)
![这里写图片描述](http://img.blog.csdn.net/20160726165340655)
![这里写图片描述](http://img.blog.csdn.net/20160726170101075)
