package com.company;

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
