package io.github.nonlog.oplusfluidcompat;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.util.Log;

import org.json.JSONObject;

public final class IntelligentIntentProvider extends ContentProvider {
    private static final String TAG = "OPlusFluidCompat";
    private static final String AMAP = "com.autonavi.minimap";
    private static final String DRIVE_INTENT = "Navigation.NotifyDrivingNavigationStatus";

    @Override
    public boolean onCreate() {
        Log.i(TAG, "IntelligentIntent provider created");
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        String caller = resolveCaller();
        if (!AMAP.equals(caller)) {
            Log.w(TAG, "IntelligentIntent rejected caller=" + caller + " uid=" + Binder.getCallingUid() + " method=" + method);
            return result(10101001, "caller not allowed", null);
        }
        try {
            if ("queryFeature".equals(method) && "querySupportIntent".equals(arg)) {
                String intentName = extras != null ? extras.getString("intentName") : null;
                boolean supported = DRIVE_INTENT.equals(intentName);
                JSONObject data = new JSONObject();
                data.put("querySupportIntent", supported);
                Log.i(TAG, "IntelligentIntent query caller=" + caller + " intentName=" + intentName + " supported=" + supported);
                return result(0, "success", data.toString());
            }
            if ("shareIntent".equals(method)) {
                String intentData = extras != null ? extras.getString("intentData") : null;
                Log.i(TAG, "IntelligentIntent share caller=" + caller + " intentData=" + intentData);
                return result(0, "success", null);
            }
            if ("deleteIntent".equals(method)) {
                String intentName = extras != null ? extras.getString("intentName") : null;
                Object entityIds = extras != null ? extras.getStringArrayList("entityIds") : null;
                Log.i(TAG, "IntelligentIntent delete caller=" + caller + " intentName=" + intentName + " entityIds=" + entityIds);
                return result(0, "success", null);
            }
            Log.w(TAG, "IntelligentIntent unsupported method=" + method + " arg=" + arg);
            return result(10505001, "unsupported method", null);
        } catch (Throwable t) {
            Log.e(TAG, "IntelligentIntent call failed method=" + method, t);
            return result(10505002, "internal error", null);
        }
    }

    private String resolveCaller() {
        try {
            String callingPackage = getCallingPackage();
            if (callingPackage != null) return callingPackage;
        } catch (Throwable ignored) {
        }
        if (getContext() == null) return null;
        try {
            String[] pkgs = getContext().getPackageManager().getPackagesForUid(Binder.getCallingUid());
            if (pkgs == null) return null;
            for (String pkg : pkgs) if (AMAP.equals(pkg)) return pkg;
            return pkgs.length > 0 ? pkgs[0] : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Bundle result(int code, String message, String dataJson) {
        try {
            JSONObject out = new JSONObject();
            out.put("code", code);
            out.put("message", message);
            if (dataJson != null) out.put("data", dataJson);
            Bundle bundle = new Bundle();
            bundle.putString("result", out.toString());
            return bundle;
        } catch (Throwable t) {
            Bundle bundle = new Bundle();
            bundle.putString("result", "{\"code\":" + code + ",\"message\":\"\"}");
            return bundle;
        }
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
