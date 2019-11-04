package com.gis.utils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class CommonMethod {
    public void append2File(String file, String content, boolean isclear) {
        FileWriter fw = null;
        try {
            //如果文件存在，则追加内容；如果文件不存在，则创建文件
            File f = new File(file);
            fw = new FileWriter(f, false);
            PrintWriter pw = new PrintWriter(fw);

            if(isclear){
                fw.write("");
            }

            pw.println(content);

            pw.flush();
            fw.flush();
            pw.close();
            fw.close();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }
}
