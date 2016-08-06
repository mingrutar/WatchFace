package com.coderming.weatherwatch.data;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import com.coderming.weatherwatch.Utility;

/**
 * Created by linna on 8/3/2016.
 */
public class TodayWeatherBrief {

    private static final String[] NOTIFY_WEATHER_PROJECTION = new String[] {
            com.coderming.weatherwatch.data.WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
            com.coderming.weatherwatch.data.WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            com.coderming.weatherwatch.data.WeatherContract.WeatherEntry.COLUMN_MIN_TEMP,
            com.coderming.weatherwatch.data.WeatherContract.WeatherEntry.COLUMN_SHORT_DESC
    };

    // these indices must match the projection
    private static final int INDEX_WEATHER_ID = 0;
    private static final int INDEX_MAX_TEMP = 1;
    private static final int INDEX_MIN_TEMP = 2;
    private static final int INDEX_SHORT_DESC = 3;

    private String DATA_FORMATTER = "%1.0f,%1.0f,%d,%s";

    int mWeatherId;
    double mHigh;
    double mLow;
    String mDesc;

    private TodayWeatherBrief( double high, double low, int weatherId, String desc) {
        mHigh = high;
        mLow = low;
        mWeatherId = weatherId;
        mDesc = desc;
    }
    public int getWeatherId() { return  mWeatherId; }
    public double getHigh() {return mHigh;}
    public double getLow() {return mLow;}
    public String getDesc() { return mDesc;}

    public static TodayWeatherBrief getTodayWeather(Context context) {
        String locationQuery = Utility.getPreferredLocation(context);
        Uri weatherUri = com.coderming.weatherwatch.data.WeatherContract.WeatherEntry.buildWeatherLocationWithDate(locationQuery, System.currentTimeMillis());

        // we'll query our contentProvider, as always
        Cursor cursor = context.getContentResolver().query(weatherUri, NOTIFY_WEATHER_PROJECTION, null, null, null);

        if (cursor.moveToFirst()) {
            int weatherId = cursor.getInt(INDEX_WEATHER_ID);
            double high = cursor.getDouble(INDEX_MAX_TEMP);
            double low = cursor.getDouble(INDEX_MIN_TEMP);
            String desc = cursor.getString(INDEX_SHORT_DESC);
            cursor.close();
            return new TodayWeatherBrief(high, low, weatherId, desc);
        } else {
            cursor.close();
            return null;
        }
    }

    @Override
    public String toString() {
        return String.format(DATA_FORMATTER, mHigh, mLow, mWeatherId, mDesc);
    }
}
