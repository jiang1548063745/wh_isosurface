package com.gis.isolines;

import com.alibaba.fastjson.JSONObject;
import com.gis.utils.CommonMethod;
import com.gis.utils.FeatureUtils;
import com.gis.utils.GeoJSONUtil;
import com.vividsolutions.jts.geom.Geometry;
import org.geotools.data.FeatureSource;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.geojson.feature.FeatureJSON;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeature;
import wContour.Contour;
import wContour.Global.Border;
import wContour.Global.PointD;
import wContour.Global.PolyLine;
import wContour.Global.Polygon;
import wContour.Interpolate;

import java.io.File;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.*;

/**
 * Created by admin on 2017/8/29.
 */
public class EquiSurfaceLine {
    private static String rootPath = System.getProperty("user.dir");

    /**
     * 生成等值面
     *
     * @param trainData    训练数据
     * @param dataInterval 数据间隔
     * @param size         大小，宽，高
     * @param boundryFile  四至
     * @param isclip       是否裁剪
     * @return
     */
    public String calEquiSurface(double[][] trainData,
                                 double[] dataInterval,
                                 int[] size,
                                 String boundryFile,
                                 boolean isclip) {
        String geojsonline = "";
        try {
            double _undefData = -9999.0;
            SimpleFeatureCollection polylineCollection;
            List<PolyLine> cPolylineList;
            List<Polygon> cPolygonList = new ArrayList<>();

            int width = size[0], height = size[1];
            double[] _X = new double[width];
            double[] _Y = new double[height];

            File file = new File(boundryFile);
            ShapefileDataStore shpDataStore;

            shpDataStore = new ShapefileDataStore(file.toURL());

            //设置编码
            Charset charset = Charset.forName("GBK");
            shpDataStore.setCharset(charset);
            String typeName = shpDataStore.getTypeNames()[0];
            SimpleFeatureSource featureSource = null;
            featureSource = shpDataStore.getFeatureSource(typeName);
            SimpleFeatureCollection fc = featureSource.getFeatures();

            double minX = fc.getBounds().getMinX();
            double minY = fc.getBounds().getMinY();
            double maxX = fc.getBounds().getMaxX();
            double maxY = fc.getBounds().getMaxY();

            Interpolate.CreateGridXY_Num(minX, minY, maxX, maxY, _X, _Y);
            double[][] _gridData;

            int nc = dataInterval.length;

            // TODO IDW 插值
            _gridData = Interpolate.Interpolation_IDW_Neighbor(trainData,
                    _X, _Y, 12, _undefData);// IDW插值

            int[][] S1 = new int[_gridData.length][_gridData[0].length];

            /**
             * double[][] S0,
             * double[] X,
             * double[] Y,
             * int[][] S1,
             * double undefData
             */
            // TODO 用未定义的数据跟踪网格数据的轮廓边界。
            List<Border> _borders = Contour.tracingBorders(_gridData, _X, _Y,
                    S1, _undefData);

            /**
             * double[][] S0,
             * double[] X,
             * double[] Y,
             * int nc,
             * double[] contour,
             * double undefData,
             * List<Border> borders,
             * int[][] S1
             */
            // TODO 使用未定义的数据从网格数据跟踪轮廓线
            cPolylineList = Contour.tracingContourLines(_gridData, _X, _Y, nc,
                    dataInterval, _undefData, _borders, S1);// 生成等值线

            // TODO 平滑线
            cPolylineList = Contour.smoothLines(cPolylineList);// 平滑

            geojsonline = getPolylineGeoJson(cPolylineList);

            if (isclip) {
                polylineCollection = GeoJSONUtil.readGeoJsonByString(geojsonline);
                FeatureSource dc = clipFeatureCollection(fc, polylineCollection);
                geojsonline = getPolylineGeoJson(dc.getFeatures());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return geojsonline;
    }


    public String getPolylineGeoJson(FeatureCollection fc) {
        FeatureJSON fjson = new FeatureJSON();
        StringBuffer sb = new StringBuffer();
        try {
            sb.append("{\"type\": \"FeatureCollection\",\"features\": ");
            FeatureIterator itertor = fc.features();
            List<String> list = new ArrayList<String>();
            while (itertor.hasNext()) {
                SimpleFeature feature = (SimpleFeature) itertor.next();
                StringWriter writer = new StringWriter();
                fjson.writeFeature(feature, writer);
                list.add(writer.toString());
            }
            itertor.close();
            sb.append(list.toString());
            sb.append("}");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return sb.toString();
    }

    public FeatureSource clipFeatureCollection(FeatureCollection fc,
                                               SimpleFeatureCollection gs) {
        FeatureSource cs = null;
        try {
            List<Map<String, Object>> values = new ArrayList<Map<String, Object>>();
            FeatureIterator contourFeatureIterator = gs.features();
            FeatureIterator dataFeatureIterator = fc.features();
            while (dataFeatureIterator.hasNext()) {
                Feature dataFeature = dataFeatureIterator.next();
                Geometry dataGeometry = (Geometry) dataFeature.getProperty(
                        "the_geom").getValue();
                while (contourFeatureIterator.hasNext()) {
                    Feature contourFeature = contourFeatureIterator.next();
                    Geometry contourGeometry = (Geometry) contourFeature
                            .getProperty("geometry").getValue();
                    double v = (Double) contourFeature.getProperty("value")
                            .getValue();
                    if (dataGeometry.intersects(contourGeometry)) {
                        Geometry geo = dataGeometry
                                .intersection(contourGeometry);
                        Map<String, Object> map = new HashMap<String, Object>();
                        map.put("the_geom", geo);
                        map.put("value", v);
                        values.add(map);
                    }

                }

            }

            contourFeatureIterator.close();
            dataFeatureIterator.close();

            SimpleFeatureCollection sc = FeatureUtils
                    .creatSimpleFeatureByFeilds(
                            "polygons",
                            "crs:4326,the_geom:LineString,value:double",
                            values);
            cs = FeatureUtils.creatFeatureSourceByCollection(sc);

        } catch (Exception e) {
            e.printStackTrace();
            return cs;
        }

        return cs;
    }


    public String getPolylineGeoJson(List<PolyLine> cPolylineList) {
        String geo = null;
        String geometry = " { \"type\":\"Feature\",\"geometry\":";
        String properties = ",\"properties\":{ \"value\":";

        String head = "{\"type\": \"FeatureCollection\"," + "\"features\": [";
        String end = "  ] }";
        if (cPolylineList == null || cPolylineList.size() == 0) {
            return null;
        }
        try {
            for (PolyLine pPolyline : cPolylineList) {
                List<Object> ptsTotal = new ArrayList<Object>();

                for (PointD ptD : pPolyline.PointList) {
                    List<Double> pt = new ArrayList<Double>();
                    pt.add(ptD.X);
                    pt.add(ptD.Y);
                    ptsTotal.add(pt);
                }

                JSONObject js = new JSONObject();
                js.put("type", "LineString");
                js.put("coordinates", ptsTotal);

                geo = geometry + js.toString() + properties + pPolyline.Value + "} }" + "," + geo;
            }
            if (geo.contains(",")) {
                geo = geo.substring(0, geo.lastIndexOf(","));
            }

            geo = head + geo + end;
        } catch (Exception e) {
            e.printStackTrace();
            return geo;
        }
        return geo;
    }

    public static void main(String[] args) {
        long start = System.currentTimeMillis();

        EquiSurfaceLine equiSurface = new EquiSurfaceLine();
        CommonMethod cm = new CommonMethod();

//        double[] bounds = {110.759, 31.23, 113.112, 32.6299};
        double[] bounds = {114.19757287848788, 30.651338316353982, 115.1174489047441, 31.327603106843195};

        double[][] trainData = new double[3][100];

        for (int i = 0; i < 100; i++) {
            double x = bounds[0] + new Random().nextDouble() * (bounds[2] - bounds[0]),
                    y = bounds[1] + new Random().nextDouble() * (bounds[3] - bounds[1]),
                    v = 0 + new Random().nextDouble() * (45 - 0);
            trainData[0][i] = x;
            trainData[1][i] = y;
            trainData[2][i] = v;
        }

        double[] dataInterval = new double[]{20, 25, 30, 35, 40, 45};

        String boundryFile = rootPath + "/src/main/shp/wh.shp";

        int[] size = new int[]{100, 100};

        boolean isclip = true;

        try {
            String strJson = equiSurface.calEquiSurface(trainData, dataInterval, size, boundryFile, isclip);
            String strFile = rootPath + "/src/main/shp/wh.json";
            cm.append2File(strFile, strJson, false);
            System.out.println(strFile + "差值成功, 共耗时" + (System.currentTimeMillis() - start) + "ms");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
