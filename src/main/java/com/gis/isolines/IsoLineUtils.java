package com.gis.isolines;
import	java.io.IOException;

import com.alibaba.fastjson.JSONObject;
import com.gis.utils.FeatureUtils;
import com.gis.utils.GeoJSONUtil;
import com.gis.utils.GeometryEnum;
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

public class IsoLineUtils {

    private IsoLineUtils() {}

    private static final double UNDEF_DATA = -9999.0;

    /**
     *
     * @param trainData    训练数据
     * @param dataInterval 数据标尺
     * @param size         生成网格GRID 大小 宽高
     * @param borderFile   等值线所在区域数据文件地址
     * @return             geoJson
     */
    public static String getEquiSurface(double[][] trainData, double[] dataInterval,
                                        int[] size, String borderFile, GeometryEnum type) throws Exception{
        File file = new File(borderFile);
        String geoJson;

        int width = size[0];
        int height = size[1];

        double[] X = new double[width];
        double[] Y = new double[height];

        ShapefileDataStore shpDataStore = new ShapefileDataStore(file.toURI().toURL());
        Charset charset = Charset.forName("GBK");
        shpDataStore.setCharset(charset);

        String typeName = shpDataStore.getTypeNames()[0];

        // 获取 SHP 要素 集合
        SimpleFeatureSource featureSource = shpDataStore.getFeatureSource(typeName);
        SimpleFeatureCollection baseFeatureCollection = featureSource.getFeatures();

        // 获取边界
        double minX = baseFeatureCollection.getBounds().getMinX();
        double minY = baseFeatureCollection.getBounds().getMinY();
        double maxX = baseFeatureCollection.getBounds().getMaxX();
        double maxY = baseFeatureCollection.getBounds().getMaxY();

        // 创建网格X / Y坐标
        Interpolate.CreateGridXY_Num(minX, minY, maxX, maxY, X, Y);

        int nc = dataInterval.length;

        // IDW 插值
        double[][] gridData = Interpolate.Interpolation_IDW_Neighbor(trainData,  X, Y, 12, UNDEF_DATA);

        int[][] S1 = new int[gridData.length][gridData[0].length];

        // 用未定义的数据跟踪网格数据的轮廓边界。 网格数据从左到右，从下到上。 网格数据数组：第一个尺寸为Y，第二个尺寸为X。
        List<Border> borders = Contour.tracingBorders(gridData,  X, Y, S1, UNDEF_DATA);

        // 轮廓边界
        List<PolyLine> polylineList = Contour.tracingContourLines(gridData,  X, Y, nc, dataInterval, UNDEF_DATA, borders, S1);

        // 平滑曲线
        polylineList = Contour.smoothLines(polylineList);

        switch (type) {
            case Line:
                geoJson = polylineToGeoJson(polylineList);
                SimpleFeatureCollection lineCollection = GeoJSONUtil.readGeoJsonByString(geoJson);
                FeatureSource lineSource = clipFeatureCollection(baseFeatureCollection, lineCollection);
                geoJson = featureCollectionToGeoJson(lineSource.getFeatures());
                break;
            case Polygon:
                // 等值面生成
                List<Polygon> polygonList = Contour.tracingPolygons(gridData, polylineList, borders, dataInterval);
                // 等值面
                geoJson = polygonToGeoJson(polygonList);
                SimpleFeatureCollection polygonCollection = GeoJSONUtil.readGeoJsonByString(geoJson);
                FeatureSource polygonSource = clipFeatureCollection(baseFeatureCollection, polygonCollection);
                geoJson = featureCollectionToGeoJson(polygonSource.getFeatures());
                break;
            default:
                // 默认为等值线
                geoJson = null;
                break;
        }

        return geoJson;
    }

    /**
     * 裁剪区域
     * @param border  裁剪依据数据
     * @param data    需要裁剪的数据
     * @return 裁剪结果
     */
    private static FeatureSource clipFeatureCollection(FeatureCollection border, SimpleFeatureCollection data) {
        List<Map<String, Object>> values = new ArrayList<>();

        // 数据要素迭代器
        FeatureIterator dataFeatureIterator = data.features();
        // 边界要素迭代器
        FeatureIterator borderFeatureIterator = border.features();

        while (borderFeatureIterator.hasNext()) {
            // 边界要素
            Feature borderFeature = borderFeatureIterator.next();
            Geometry borderGeometry = (Geometry) borderFeature.getProperty("the_geom").getValue();

            while (dataFeatureIterator.hasNext()) {
                // 数据要素
                Feature dataFeature = dataFeatureIterator.next();
                Geometry dataGeometry = (Geometry) dataFeature.getProperty("geometry").getValue();
                double v = (Double) dataFeature.getProperty("hvalue").getValue();

                if (borderGeometry.intersects(dataGeometry)) {
                    // 两个几何的公共形状
                    Geometry intersection  = borderGeometry.intersection(dataGeometry);
                    Map<String, Object> map = new HashMap<>();
                    map.put("the_geom", intersection );
                    map.put("value", v);
                    values.add(map);
                }
            }
        }

        dataFeatureIterator.close();
        borderFeatureIterator.close();

        SimpleFeatureCollection sc = FeatureUtils.creatSimpleFeatureByFeilds("polygons",
                "crs:4326,the_geom:MultiPolygon,value:double", values);
        return FeatureUtils.creatFeatureSourceByCollection(sc);
    }

    /**
     * LineString要素集合转成geojson格式
     * @param polylineList 线要素集
     * @return GeoJson
     */
    private static String polylineToGeoJson(List<PolyLine> polylineList) {
        StringBuilder geo = new StringBuilder();
        String geometry = " { \"type\":\"Feature\",\"geometry\":";
        String properties = ",\"properties\":{ \"hvalue\":";

        String head = "{\"type\": \"FeatureCollection\"," + "\"features\": [";
        String end = "  ] }";

        if (polylineList.isEmpty()) {
            return null;
        }

        for (PolyLine pPolyline : polylineList) {
            List<Object> ptsTotal = new ArrayList<>();

            for (PointD ptD : pPolyline.PointList) {
                List<Double> pt = new ArrayList<>();
                pt.add(ptD.X);
                pt.add(ptD.Y);
                ptsTotal.add(pt);
            }

            JSONObject js = new JSONObject();
            js.put("type", "LineString");
            js.put("coordinates", ptsTotal);

            geo.insert(0, geometry + js.toString() + properties + pPolyline.Value + "} }" + ",");
        }

        if (geo.toString().contains(",")) {
            geo = new StringBuilder(geo.substring(0, geo.lastIndexOf(",")));
        }

        geo = new StringBuilder(head + geo + end);
        return geo.toString();
    }

    /**
     * 将Polygon要素集合转成geojson格式
     * @param polygonList  面要素集
     * @return  GeoJson
     */
    private static String polygonToGeoJson(List<Polygon> polygonList) {
        String geo;
        String geometry = "{ \"type\":\"Feature\",\"geometry\":";
        String properties = ",\"properties\":{ \"hvalue\":";

        String head = "{\"type\": \"FeatureCollection\"," + "\"features\":[";
        String end = "]}";

        if (polygonList == null || polygonList.isEmpty()) {
            return null;
        }

        StringBuilder geoBuilder = new StringBuilder();

        for (Polygon pPolygon : polygonList) {
            List<Object> ptsTotal = new ArrayList<>();
            List<Object> pts = new ArrayList<>();

            pPolygon.OutLine.PointList.stream().map(point -> {
                List<Double> pt = new ArrayList<>();
                pt.add(point.X);
                pt.add(point.Y);
                return pt;
            }).forEachOrdered(pts::add);
            ptsTotal.add(pts);

            if (pPolygon.HasHoles()) {
                pPolygon.HoleLines.stream().map(cptLine -> {
                    List<Object> cpts = new ArrayList<>();

                    cptLine.PointList.stream().map(ccptD -> {
                        List<Double> pt = new ArrayList<>();
                        pt.add(ccptD.X);
                        pt.add(ccptD.Y);
                        return pt;
                    }).forEachOrdered(cpts::add);
                    return cpts;
                }).filter(cpts -> !cpts.isEmpty()).forEachOrdered(ptsTotal::add);
            }

            JSONObject js = new JSONObject();
            js.put("type", "Polygon");
            js.put("coordinates", ptsTotal);

            double hv = pPolygon.HighValue;
            double lv = pPolygon.LowValue;

            if (hv == lv) {
                // 是否顺时针
                if (pPolygon.IsClockWise) {
                    // 是否居中
                    if (!pPolygon.IsHighCenter) {
                        hv = hv - 0.1;
                        lv = lv - 0.1;
                    }
                } else {
                    if (!pPolygon.IsHighCenter) {
                        hv = hv - 0.1;
                        lv = lv - 0.1;
                    }
                }
            } else {
                if (!pPolygon.IsClockWise) {
                    lv = lv + 0.1;
                } else {
                    if (pPolygon.IsHighCenter) {
                        hv = hv - 0.1;
                    }
                }

            }

            geoBuilder.insert(0, geometry + js.toString() + properties + hv
                    + ", \"lvalue\":" + lv + "}}" + ",");
        }
        geo = geoBuilder.toString();

        if (geo.contains(",")) {
            geo = geo.substring(0, geo.lastIndexOf(","));
        }

        geo = head + geo + end;

        return geo;
    }

    /**
     * FeatureCollection转换成GeoJson
     * @param featureCollection 要素集合
     * @return GeoJson
     * @throws IOException  流异常
     */
    private static String featureCollectionToGeoJson(FeatureCollection featureCollection) throws IOException {
        FeatureJSON featureJson = new FeatureJSON();
        StringBuilder sb = new StringBuilder();

        sb.append("{\"type\": \"FeatureCollection\",\"features\": ");
        FeatureIterator itertor = featureCollection.features();
        List<String> list = new ArrayList<>();
        while (itertor.hasNext()) {
            StringWriter writer = new StringWriter();
            SimpleFeature feature = (SimpleFeature) itertor.next();
            featureJson.writeFeature(feature, writer);
            list.add(writer.toString());
        }

        itertor.close();
        sb.append(list.toString());
        sb.append("}");

        return sb.toString();
    }
}
