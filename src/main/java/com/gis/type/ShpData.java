package com.gis.type;

import com.gis.utils.GeoJSONUtil;
import org.geotools.data.FeatureSource;
import org.geotools.data.FileDataStore;
import org.geotools.feature.FeatureCollection;
import org.geotools.filter.text.cql2.CQL;
import org.geotools.filter.text.cql2.CQLException;
import org.opengis.filter.Filter;

import java.io.File;
import java.io.IOException;

public class ShpData {

    public ShpData() {
    }

    public static FeatureSource shpfeatureSourcel = null;
    public static FeatureSource geojsonfeatureSourcel;
    public static FileDataStore shpstorel = null;
    public static FileDataStore geojsonstorel = null;

    private static String geoJsonFileName = "yx.geojson";

    static {
        String rootUrl = System.getProperty("user.dir");
        File geoJsonFile = new File(rootUrl+ "/src/main/shp/" + geoJsonFileName);
        if(!geoJsonFile.exists()){
            System.err.println("获取 geojsonfilel 出错! ShpData类");
        }

        geojsonfeatureSourcel = GeoJSONUtil.readGeoJsonByGeojsonToFeatureSource(rootUrl + "/src/main/shp/" + geoJsonFileName);
    }

    /**
     * 根据 cql获取要素集
     * @param cql ??
     * @return 要素集
     */
    public static FeatureCollection getFeatureSourceByGeoJson(String cql) {
        FeatureCollection fc = null;

        if (geojsonfeatureSourcel != null) {
            try {
                if (cql != null) {
                    Filter filter = CQL.toFilter(cql);
                    fc = geojsonfeatureSourcel.getFeatures(filter);
                } else {
                    fc = geojsonfeatureSourcel.getFeatures();
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (CQLException e) {
                e.printStackTrace();
            }
        } else {
            System.err.println("获取 getFeatures 出错! ShpData类");
        }
        return fc;
    }

    /**
     * 根据 cql获取要素集
     * @param cql ??
     * @return 要素集
     */
    public static FeatureCollection getFeatureSourceByShp(String cql) {
        FeatureCollection fc = null;
        if (shpstorel != null) {
            try {
                if (cql != null) {
                    Filter filter = CQL.toFilter(cql);
                    fc = shpstorel.getFeatureSource().getFeatures(filter);
                } else {
                    fc = shpstorel.getFeatureSource().getFeatures();
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (CQLException e) {
                e.printStackTrace();
            }
        } else {
            System.err.println("获取 getFeatures 出错! ShpData类");
        }
        return fc;
    }


    public static void main(String[] args) {
        new ShpData();
    }
}
