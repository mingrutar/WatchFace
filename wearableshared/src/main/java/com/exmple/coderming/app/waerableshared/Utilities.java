package com.exmple.coderming.app.waerableshared;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

/**
 * Created by linna on 7/29/2016.
 */
public class Utilities {
    private static final String LOG_TAG = Utilities.class.getSimpleName();

    public static final String PATH_WITH_FEATURE = "/watch_face_config/weather";
    public static final String PATH_ACK_MESSAGE = "/watch_face_config/watchack";
    public static final String PATH_LAUNCH_APP_MESSAGE = "/watch_face_config/launchapp";
    public static final String PATH_PING_WATCH = "/watch_face_init/ping";

    public static final String WEATHER_KEY = "WEATHER_KEY";
    public static final String BG_COLOR_KEY = "BG_COLOR_KEY";
    public static final String IS_METRIC_KEY = "IS_METRIC_KEY";

    public static final String WEATHER_DATA_FORMATTER = "%1.0f,%1.0f,%d,%s";

    public static int getIconResourceForWeatherCondition(int weatherId) {
        // Based on weather code data found at:
        // http://bugs.openweathermap.org/projects/api/wiki/Weather_Condition_Codes
        if (weatherId >= 200 && weatherId <= 232) {
            return R.drawable.ic_storm;
        } else if (weatherId >= 300 && weatherId <= 321) {
            return R.drawable.ic_light_rain;
        } else if (weatherId >= 500 && weatherId <= 504) {
            return R.drawable.ic_rain;
        } else if (weatherId == 511) {
            return R.drawable.ic_snow;
        } else if (weatherId >= 520 && weatherId <= 531) {
            return R.drawable.ic_rain;
        } else if (weatherId >= 600 && weatherId <= 622) {
            return R.drawable.ic_snow;
        } else if (weatherId >= 701 && weatherId <= 761) {
            return R.drawable.ic_fog;
        } else if (weatherId == 761 || weatherId == 781) {
            return R.drawable.ic_storm;
        } else if (weatherId == 800) {
            return R.drawable.ic_clear;
        } else if (weatherId == 801) {
            return R.drawable.ic_light_clouds;
        } else if (weatherId >= 802 && weatherId <= 804) {
            return R.drawable.ic_cloudy;
        }
        return -1;
    }
    public static boolean isMetric(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getString(context.getString(R.string.pref_units_key),
                context.getString(R.string.pref_units_metric))
                .equals(context.getString(R.string.pref_units_metric));
    }
    public static String formatTemperature(Context context, double temperature, boolean isMetric) {
        // Data stored in Celsius by default.  If user prefers to see in Fahrenheit, convert
        // the values here.
          String suffix = "\u00B0";
          if (!isMetric) {
              temperature =  (temperature * 1.8) + 32;
          }
          // For presentation, assume the user doesn't care about tenths of a degree.
          return String.format(context.getString(R.string.format_temperature), temperature);
    }
}
