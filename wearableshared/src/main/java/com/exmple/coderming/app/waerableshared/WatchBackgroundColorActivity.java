package com.exmple.coderming.app.waerableshared;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.ColorInt;
import android.support.v4.util.Pair;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class WatchBackgroundColorActivity extends AppCompatActivity {
    private static final String LOG_TAG = WatchBackgroundColorActivity.class.getSimpleName();

    public static final String BG_COLOR_KEY = "BG_COLOR_KEY";
    private static final String seperator = "_";
    private static final String SELECTED_COLOR = "SELECTED_COLOR";

    int[] mColorResIds = new int[] {R.color.bg_Red,
            R.color.bg_Green,
            R.color.bg_Steel,
            R.color.bg_Brown,
            R.color.bg_Purple,
            R.color.Black,
            R.color.Navy};

    ListView mColorListView;
    @ColorInt int mLastColor;       // black as default

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_watch_background_color);

        mColorListView = (ListView) findViewById(R.id.color_list);
        final List<Pair<Integer, String>> colorIdNames = new ArrayList<>();
        for (int color : mColorResIds) {
            String str = getResources().getResourceEntryName(color);
            String[] tokens = str.split(seperator);
            str = (tokens.length == 2) ?  "Dark " + tokens[1] : str;
            colorIdNames.add(new Pair(color, str));
        }
        MyArrayAdapter adapter = new MyArrayAdapter(this, R.layout.color_item, colorIdNames) ;
        mColorListView.setAdapter(adapter);
        mColorListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int pos, long l) {
                if ((pos >=0) && (pos < mColorResIds.length)) {
                    @ColorInt int color = getResources().getColor(mColorResIds[pos]);
                    if (mLastColor != color) {
                        WearableProxy.getInstance().updateBGColor(color);
                        mLastColor = color;
                    }
                }
            }
        });
        mColorListView.requestFocus();
    }

    /**
     * Dispatch onStart() to all fragments.  Ensure any created loaders are
     * now started.
     */
    @Override
    protected void onStart() {
        super.onStart();
        @ColorInt int defaultBG =  getResources().getColor(R.color.default_background);
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mLastColor = sharedPreferences.getInt(BG_COLOR_KEY, defaultBG);
    }

    @Override
    protected void onStop() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPreferences.edit().putInt(BG_COLOR_KEY, mLastColor).apply();
        super.onStop();
    }
    static class MyArrayAdapter extends ArrayAdapter<Pair<Integer, String>> {
        List<Pair<Integer, String>> mColorIdNames;

        public MyArrayAdapter(Context context, int resource, List<Pair<Integer, String>> colorIdNames) {
            super(context, resource, colorIdNames);
            mColorIdNames = colorIdNames;
        }
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;
            if (row == null) {
                row = LayoutInflater.from(getContext()).inflate(R.layout.color_item, parent, false);
            }
            View view = row.findViewById(R.id.color_swapt);
            GradientDrawable drawable = (GradientDrawable) view.getBackground();

            Pair<Integer, String> pair = mColorIdNames.get(position);
            @ColorInt int color = getContext().getResources().getColor(pair.first);
            drawable.setColor(color);

            TextView textView = (TextView) row.findViewById(R.id.color_name);
            textView.setText(pair.second);
            return row;
        }
    }

}
