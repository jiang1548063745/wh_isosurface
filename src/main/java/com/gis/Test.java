package com.gis;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.gis.isolines.IsoLineUtils;
import com.gis.utils.CommonMethod;
import com.gis.utils.GeometryEnum;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;

import java.io.File;

public class Test {

    public static void main(String[] args) throws Exception{
        createIsoline();
    }

    private static void createIsoline() throws Exception {
        long start = System.currentTimeMillis();

        String rootPath = System.getProperty("user.dir");
        File file = new File(rootPath + "/src/main/shp/rain.json");
        String content = FileUtils.readFileToString(file, "UTF-8");
        JSONObject json  = JSON.parseObject(content);

        JSONArray jsonArray = json.getJSONArray("RECORDS");
        Object[] temp = jsonArray.toArray();


        CommonMethod cm = new CommonMethod();

        double[][] trainData = new double[3][500];

        for (int i = 0; i < temp.length; i++) {
            JSONObject obj = (JSONObject)temp[i];
            if (StringUtils.isNotEmpty((String) obj.get("ESLO")) && StringUtils.isNotEmpty((String) obj.get("NTLA"))) {
                trainData[0][i] = Double.parseDouble((String)obj.get("ESLO"));
                trainData[1][i] = Double.parseDouble((String)obj.get("NTLA"));
                trainData[2][i] = Double.parseDouble((String)obj.get("SR"));
            }
        }

        double[] dataInterval = new double[]{10, 25, 50, 100};

        String boundryFile = rootPath + "/src/main/shp/wh_area.shp";

        int[] size = new int[]{trainData[0].length, trainData[1].length};

        String strJson = IsoLineUtils.getEquiSurface(trainData, dataInterval, size, boundryFile, GeometryEnum.Polygon);
        String strFile = rootPath + "/src/main/json/wh_isosurface.json";
        cm.append2File(strFile, strJson, false);
        System.out.println(strFile + "差值成功, 共耗时" + (System.currentTimeMillis() - start) + "ms");
    }
}
