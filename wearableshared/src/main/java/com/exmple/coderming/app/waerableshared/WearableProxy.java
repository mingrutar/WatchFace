package com.exmple.coderming.app.waerableshared;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.CapabilityApi;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by linna on 8/5/2016.
 */
public class WearableProxy implements
    CapabilityApi.CapabilityListener,
    MessageApi.MessageListener,
    DataApi.DataListener,
    GoogleApiClient.ConnectionCallbacks,
    GoogleApiClient.OnConnectionFailedListener {

        private static final String LOG_TAG = WearableProxy.class.getSimpleName();

        private Collection<String> mNodes;
        private GoogleApiClient mGoogleApiClient;
        private static WearableProxy mInstance;
        private Context mContext;
        private Map<String, DataMap> mCached;

        private WearableProxy() {
            mCached = new HashMap<>();
        }

    public static WearableProxy getInstance() {
        if (mInstance == null) {
            synchronized (LOG_TAG) {
                mInstance = new WearableProxy();
            }
            if (mInstance == null)
                mInstance = new WearableProxy();
        }
        return mInstance;
    }

    public void initiate(Context context) {
        mContext = context;
        mGoogleApiClient = new GoogleApiClient.Builder(context)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        mGoogleApiClient.connect();
    }

    public void release() {
        if ((mGoogleApiClient != null) && (mGoogleApiClient.isConnected())) {
            Wearable.DataApi.removeListener(mGoogleApiClient, this);
            Wearable.MessageApi.removeListener(mGoogleApiClient, this);
            Wearable.CapabilityApi.removeListener(mGoogleApiClient, this);
            mGoogleApiClient.disconnect();
        }
    }
    private String DATA_FORMATTER = "%1.0f,%1.0f,%d,%s";
    public boolean updateWeatherData(double high, double low, int wcode, String desc) {
        if (mContext == null) {
            Log.i(LOG_TAG, "Cannot proceed. Call 'WearableProxy.getInstance().initiate(context)' first ");
            return false ;
        }
        String weather = String.format(DATA_FORMATTER, high, low, wcode, desc);
//
//    }
//    private boolean updateWeatherData(String weather) {
//        String str = String.format(Utilities.WEATHER_DATA_FORMATTER, high, low, weatherCode, desc);
        DataMap dataMap = new DataMap();
        dataMap.putString(Utilities.WEATHER_KEY, weather);
        return process(dataMap, Utilities.WEATHER_KEY);
    }

    public boolean updateUnitSelection(boolean isMetric) {
        if (mContext == null) {
            Log.i(LOG_TAG, "Cannot proceed. Call 'WearableProxy.getInstance().initiate(context)' first ");
            return false ;
        }
        DataMap dataMap = new DataMap();
        dataMap.putBoolean(Utilities.IS_METRIC_KEY, isMetric);
        return process(dataMap, Utilities.IS_METRIC_KEY);
    }

    public boolean updateBGColor(@ColorInt int color) {
        if (mContext == null) {
            Log.i(LOG_TAG, "Cannot proceed. Call 'WearableProxy.getInstance().initiate(context)' first ");
            return false;
        }
        DataMap dataMap = new DataMap();
        dataMap.putInt(Utilities.BG_COLOR_KEY, color);
        return process(dataMap, Utilities.BG_COLOR_KEY);
    }
    private boolean process(DataMap dataMap, String cacheKey) {
        if (hasPeer()) {
            sendWeatherConfigData(dataMap);
            return true;
        } else if ((mGoogleApiClient != null) && mGoogleApiClient.isConnected()) {
            mCached.put(cacheKey, dataMap);
            searchPeer();
            return false;           //searchPeer(dataMap);
        } else {
            mCached.put(cacheKey, dataMap);
            return true;
        }
    }
    private void sendWeatherConfigData(DataMap dataMap) {
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(Utilities.PATH_WITH_FEATURE);
        DataMap configToPut = putDataMapRequest.getDataMap();
        configToPut.putAll(dataMap);
        PutDataRequest request = putDataMapRequest.asPutDataRequest();
        request.setUrgent();
        Wearable.DataApi.putDataItem(mGoogleApiClient, request);
    }

    private ResultCallback<CapabilityApi.GetAllCapabilitiesResult> mCallback = new ResultCallback<CapabilityApi.GetAllCapabilitiesResult>() {
        @Override
        public void onResult( CapabilityApi.GetAllCapabilitiesResult getAllCapabilitiesResult) {
            if (!getAllCapabilitiesResult.getStatus().isSuccess()) {
                Log.e(LOG_TAG, "Failed to get capabilities");
                return;
            }
            Map<String, CapabilityInfo> capabilitiesMap = getAllCapabilitiesResult.getAllCapabilities();
            if (!capabilitiesMap.isEmpty()) {
                Set<String> allNodes = new HashSet<>();
                for (Map.Entry<String, CapabilityInfo> entry : capabilitiesMap.entrySet()) {
                    CapabilityInfo capabilityInfo = entry.getValue();
                    if (capabilityInfo != null) {
                        Set<Node> nodes = entry.getValue().getNodes();
                        String nodeStr = TextUtils.join(",", nodes);
                        for (Node node : nodes) {
                            if (node.isNearby()) {
                                allNodes.add(node.getId());
                            }
                        }
                        synchronized (LOG_TAG) {
                            mNodes = allNodes;
                        }
                        updateCache();
                    } else
                        Log.i(LOG_TAG, "CapacityFounder::onResult: " + entry.getKey() + "=null");
                }
            }
        }
    };

    private void findNodes() {
        PendingResult<CapabilityApi.GetAllCapabilitiesResult> pendingCapabilityResult =
                Wearable.CapabilityApi.getAllCapabilities(mGoogleApiClient, CapabilityApi.FILTER_REACHABLE);
        pendingCapabilityResult.setResultCallback(mCallback);
    }
    private void searchPeer() {
        Wearable.NodeApi.getConnectedNodes(mGoogleApiClient)
                .setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>() {
                    @Override
                    public void onResult(@NonNull NodeApi.GetConnectedNodesResult getConnectedNodesResult) {
                        Collection<Node> nodes = getConnectedNodesResult.getNodes();
                        Set<String> allNodes = new HashSet<>();
                        String nodeStr = TextUtils.join(",", nodes);
                        for (Node node : nodes) {
                            allNodes.add(node.getId());
                        }
                        synchronized (LOG_TAG) {
                            mNodes = allNodes;
                        }
                        updateCache();
                    }
                });
    }
    private boolean hasPeer() {
        return (mNodes != null) && mNodes.size() > 0;
    }
    private void updateCache() {
        if ( (mCached.size() > 0) && hasPeer()) {
            DataMap merged = new DataMap();
            for(Map.Entry<String, DataMap> entry : mCached.entrySet()) {
                merged.putAll(entry.getValue());
            }
            sendWeatherConfigData(merged);
            mCached.clear();
        }
    }
    @Override
    public void onConnected(@Nullable Bundle bundle) {
        searchPeer();
        Wearable.DataApi.addListener(mGoogleApiClient, this);
        Wearable.MessageApi.addListener(mGoogleApiClient, this);
        Wearable.CapabilityApi.addListener(
                mGoogleApiClient, this, Uri.parse("wear://"), CapabilityApi.FILTER_REACHABLE);
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.i(LOG_TAG, "----Google API Client was onConnectionSuspended");
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {
        Log.i(LOG_TAG, "-----onDataChanged: " + dataEventBuffer);
        String str="";
        for (DataEvent event : dataEventBuffer) {
            if (event.getType() == DataEvent.TYPE_CHANGED) {
                str = "DataItem Changed: " + event.getDataItem().toString();
            } else if (event.getType() == DataEvent.TYPE_DELETED) {
                str = "DataItem Deleted: " + event.getDataItem().toString();
            }
            Log.i(LOG_TAG, str);
        }
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        String path = messageEvent.getPath();
        if (Utilities.PATH_PING_WATCH.equals(path)) {
            String nodeId = messageEvent.getSourceNodeId();
            if (!mNodes.contains(nodeId)) {
                mNodes.add(nodeId);
                updateCache();
            }
        } else if ( Utilities.PATH_ACK_MESSAGE.equals(path) ) {
            Log.w(LOG_TAG, "onMessageReceived: Watch ACK receiving config data: requestId="
                    + messageEvent.getRequestId() + ": "+messageEvent.toString() );
        } else if (Utilities.PATH_LAUNCH_APP_MESSAGE.equals(path)) {
           Intent startIntent = new Intent(mContext, WatchBackgroundColorActivity.class);
            startIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP );
            mContext.startActivity(startIntent);
        }
    }
    @Override
    public void onCapabilityChanged(CapabilityInfo capabilityInfo) {
        if (capabilityInfo != null) {
            Set<Node> nodes = capabilityInfo.getNodes();
            Set<String> allNotes = new HashSet<>();
            for (Node node : nodes) {
                if (node.isNearby()) {
                    allNotes.add(node.getId());
                }
            }
            String nodeStr = TextUtils.join(",", allNotes);
            synchronized (LOG_TAG) {
                mNodes = allNotes;
            }
        } else
            Log.i(LOG_TAG, "onCapabilityChanged: capabilityInfo is null: ");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Wearable.DataApi.removeListener(mGoogleApiClient, this);
        Wearable.MessageApi.removeListener(mGoogleApiClient, this);
        Wearable.CapabilityApi.removeListener(mGoogleApiClient, this);
    }
}
