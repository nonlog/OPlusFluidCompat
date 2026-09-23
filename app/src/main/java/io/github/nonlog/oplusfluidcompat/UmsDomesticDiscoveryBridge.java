package io.github.nonlog.oplusfluidcompat;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import io.github.libxposed.api.XposedModule;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import org.json.JSONObject;

/**
 * Restores the domestic UMS network branch which is compiled out of the inspected OOS 17.17.0
 * ConnectManager.  The HTTP stack, request/response models, signing/encryption interceptors and
 * SeedlingApi interface are all the ROM's own code.  The only network constant supplied here is
 * the production base URL embedded in the matching ColorOS 16 UMS 16.1.80 build.
 */
final class UmsDomesticDiscoveryBridge {
    private static final String TAG = "OPlusFluidCompat";
    private static final String CN_SERVICE_GOVERNANCE =
            "https://pantanal-sys-cn.allawntech.com/servicegov/capability/";
    private static final int BACKEND_OK = 200;
    private static final int BACKEND_NOT_FOUND = 1101404;

    private final XposedModule module;
    private final ClassLoader loader;
    private final BooleanSupplier runtimeAllowed;
    private final Set<String> logged = ConcurrentHashMap.newKeySet();

    private Class<?> continuationClass;
    private Class<?> failureClass;
    private Object suspendedMarker;
    private Method continuationGetContext;
    private Method continuationResumeWith;
    private Method failureFactory;

    private Constructor<?> resultConstructor;
    private Method resultSetData;
    private Method resultSetMessage;
    private Method resultSetCode;
    private Method resultSetDomainParams;
    private Method resultSetServiceIdRequests;

    private Method apiFactory;
    private Method apiDiscovery;
    private Method apiQueryV1;
    private Method apiQueryV3;
    private Method apiQueryMeta;
    private Method apiDownload;
    private Method requestBodyCreate;
    private Method mediaTypeParse;
    private volatile Object seedlingApi;

    UmsDomesticDiscoveryBridge(
            XposedModule module, ClassLoader loader, BooleanSupplier runtimeAllowed) {
        this.module = module;
        this.loader = loader;
        this.runtimeAllowed = runtimeAllowed;
    }

    boolean install() {
        try {
            resolveRuntime();
            installDomesticCommonHeadersHook();
            installDomesticBaseUrlHook();
            installConnectManagerHooks();
            logOnce("installed", "native UMS domestic discovery bridge installed: 6/6; "
                    + "CN common headers + ROM signing/encryption stack retained");
            return true;
        } catch (Throwable error) {
            module.log(Log.WARN, TAG, "UMS domestic discovery bridge unavailable", error);
            return false;
        }
    }

    private void resolveRuntime() throws Exception {
        continuationClass = Class.forName("kotlin.coroutines.c", false, loader);
        continuationGetContext = continuationClass.getDeclaredMethod("getContext");
        continuationResumeWith = continuationClass.getDeclaredMethod("resumeWith", Object.class);
        failureClass = Class.forName("kotlin.Result$Failure", false, loader);
        failureFactory = Class.forName("kotlin.b", false, loader)
                .getDeclaredMethod("a", Throwable.class);
        suspendedMarker = Class.forName("kotlin.coroutines.intrinsics.CoroutineSingletons", false, loader)
                .getDeclaredMethod("valueOf", String.class)
                .invoke(null, "COROUTINE_SUSPENDED");

        Class<?> resultClass = Class.forName(
                "com.pantanal.server.connect.modle.ResultEntity", false, loader);
        resultConstructor = resultClass.getDeclaredConstructor();
        resultSetData = resultClass.getDeclaredMethod("setData", Object.class);
        resultSetMessage = resultClass.getDeclaredMethod("setMessage", String.class);
        resultSetCode = resultClass.getDeclaredMethod("setCode", int.class);
        resultSetDomainParams = resultClass.getDeclaredMethod("setDomainParams", List.class);
        resultSetServiceIdRequests = resultClass.getDeclaredMethod("setServiceIdRequests", List.class);

        Class<?> seedlingApiClass = Class.forName(
                "com.pantanal.server.connect.ploy.http.SeedlingApi", false, loader);
        Class<?> apiServiceClass = Class.forName(
                "com.pantanal.server.network.ApiService", false, loader);
        apiFactory = apiServiceClass.getDeclaredMethod("a");
        if (!Modifier.isStatic(apiFactory.getModifiers())) {
            throw new NoSuchMethodException("ApiService.a must be static");
        }
        apiFactory.setAccessible(true);

        Class<?> requestBodyClass = Class.forName("okhttp3.a0", false, loader);
        Class<?> mediaTypeClass = Class.forName("okhttp3.u", false, loader);
        Class<?> mediaTypeCompanion = Class.forName("okhttp3.u$a", false, loader);
        mediaTypeParse = mediaTypeCompanion.getDeclaredMethod("b", String.class);
        requestBodyCreate = requestBodyClass.getDeclaredMethod(
                "create", String.class, mediaTypeClass);

        apiDiscovery = seedlingApiClass.getDeclaredMethod(
                "discovery", List.class, continuationClass);
        apiQueryV1 = seedlingApiClass.getDeclaredMethod(
                "querySubdomainEnable", List.class, continuationClass);
        apiQueryV3 = seedlingApiClass.getDeclaredMethod(
                "querySubdomainEnableV3", List.class, continuationClass);
        apiQueryMeta = seedlingApiClass.getDeclaredMethod(
                "queryMeta", requestBodyClass, continuationClass);
        apiDownload = seedlingApiClass.getDeclaredMethod(
                "downloadUpk", String.class, continuationClass);
    }

    /**
     * OOS 17.17 keeps the same interceptor slot as the C16 network stack, but xf.a is a no-op.
     * Restore only the public device/service headers emitted by C16 before the ROM's existing
     * signing and encryption interceptors run. Authentication/signature material is still
     * produced entirely by the target UMS implementation.
     */
    private void installDomesticCommonHeadersHook() throws Exception {
        Class<?> interceptorClass = Class.forName("xf.a", false, loader);
        Class<?> chainClass = Class.forName("okhttp3.t$a", false, loader);
        Class<?> requestClass = Class.forName("okhttp3.w", false, loader);
        Class<?> requestBuilderClass = Class.forName("okhttp3.w$a", false, loader);
        Method intercept = interceptorClass.getDeclaredMethod("intercept", chainClass);
        Method chainRequest = chainClass.getDeclaredMethod("request");
        Method chainProceed = chainClass.getDeclaredMethod("a", requestClass);
        Method requestNewBuilder = requestClass.getDeclaredMethod("b");
        Method addHeader = requestBuilderClass.getDeclaredMethod("a", String.class, String.class);
        Method buildRequest = requestBuilderClass.getDeclaredMethod("b");

        module.hook(intercept).intercept(chain -> {
            if (!runtimeAllowed.getAsBoolean()) return chain.proceed();
            Object nativeChain = chain.getArg(0);
            Object request;
            try {
                Application application = currentApplication();
                if (application == null) return chain.proceed();
                request = chainRequest.invoke(nativeChain);
                Object builder = requestNewBuilder.invoke(request);
                Map<String, String> headers = domesticHeaders(application);
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    addHeader.invoke(builder, entry.getKey(), entry.getValue());
                }
                request = buildRequest.invoke(builder);
            } catch (Throwable error) {
                module.log(Log.WARN, TAG,
                        "UMS domestic common headers unavailable; using export interceptor", error);
                return chain.proceed();
            }
            try {
                Object response = chainProceed.invoke(nativeChain, request);
                logOnce("common-headers",
                        "restore C16 UMS common request headers before native signing/encryption");
                return response;
            } catch (InvocationTargetException error) {
                throw error.getCause();
            }
        });
    }

    private Map<String, String> domesticHeaders(Application application) {
        LinkedHashMap<String, String> headers = new LinkedHashMap<>();
        String brand = Build.BRAND;
        if (brand == null || brand.isEmpty()) {
            brand = systemProperty("ro.product.brand.sub", "OPPO");
        }
        headers.put("brand", safe(brand));
        headers.put("model", safe(Build.MODEL));
        headers.put("osVersion", oplusOsRelease());
        headers.put("umsVersion", packageVersion(application, ChinaCompatibilityPolicy.UMS));
        headers.put("metisVersion", packageVersion(application, "com.oplus.metis"));
        headers.put("dtVersion", packageVersion(application, deepThinkerPackage()));
        headers.put("engineVersion", seedlingEngineVersion(application));
        headers.put("appKey", "ums-admin");
        headers.put("oaid", targetOaid());
        headers.put("sceneVersion", packageVersion(application, "com.coloros.sceneservice"));
        headers.put("device", isTablet() ? "TABLET" : "PHONE");
        headers.put("foldType", foldType());
        headers.put("osType", productType(application));
        headers.put("lang", language(application.getResources().getConfiguration()));
        return headers;
    }

    private String oplusOsRelease() {
        try {
            Class<?> version = Class.forName("com.oplus.os.OplusBuild$VERSION", false, loader);
            Field release = version.getDeclaredField("RELEASE");
            release.setAccessible(true);
            return safe(release.get(null));
        } catch (Throwable ignored) {
            return safe(Build.VERSION.RELEASE);
        }
    }

    private String packageVersion(Application application, String packageName) {
        if (packageName == null || packageName.isEmpty()) return "-1";
        try {
            PackageInfo info = application.getPackageManager().getPackageInfo(packageName, 0);
            return String.valueOf(info.getLongVersionCode());
        } catch (Throwable ignored) {
            return "-1";
        }
    }

    private String deepThinkerPackage() {
        try {
            Class<?> type = Class.forName(
                    "com.oplus.deepthinker.sdk.app.IOplusDeepThinkerManager", false, loader);
            Field field = type.getDeclaredField("SERVICE_PKG");
            field.setAccessible(true);
            Object value = field.get(null);
            if (value instanceof String && !((String) value).isEmpty()) return (String) value;
        } catch (Throwable ignored) {
        }
        return "com.oplus.deepthinker";
    }

    private String seedlingEngineVersion(Application application) {
        try {
            ApplicationInfo info = application.getPackageManager().getApplicationInfo(
                    application.getPackageName(), PackageManager.GET_META_DATA);
            Bundle metadata = info == null ? null : info.metaData;
            return String.valueOf(metadata == null ? 0 : metadata.getInt("seedlingEngineVersion", 0));
        } catch (Throwable ignored) {
            return "0";
        }
    }

    /** Reuse the OAID lazy value already shipped in this exact OOS UMS build. */
    private String targetOaid() {
        try {
            Class<?> holder = Class.forName("vf.a", true, loader);
            for (Field field : holder.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) || field.getType() == boolean.class) continue;
                field.setAccessible(true);
                Object lazy = field.get(null);
                if (lazy == null) continue;
                try {
                    Method getValue = lazy.getClass().getMethod("getValue");
                    Object value = getValue.invoke(lazy);
                    if (value != null) return String.valueOf(value);
                } catch (NoSuchMethodException ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    private boolean isTablet() {
        return systemProperty("ro.build.characteristics", "").toLowerCase(Locale.ROOT)
                .contains("tablet");
    }

    private String foldType() {
        String raw = systemProperty("ro.hw.foldtype", "");
        try {
            return String.valueOf(Integer.parseInt(raw));
        } catch (Throwable ignored) {
            return "0";
        }
    }

    private String productType(Application application) {
        PackageManager pm = application.getPackageManager();
        if (pm.hasSystemFeature("oplus.software.support_gp.product_full")) return "FULL";
        if (pm.hasSystemFeature("oplus.software.support_gp.product_light_h")) return "LIGHT_H";
        return "LIGHT";
    }

    private String language(Configuration configuration) {
        if (configuration == null || configuration.getLocales().isEmpty()) return "zh-CN";
        Locale locale = configuration.getLocales().get(0);
        return safe(locale.getLanguage()) + "-" + safe(locale.getCountry());
    }

    private String systemProperty(String key, String fallback) {
        try {
            return safe(Class.forName("android.os.SystemProperties")
                    .getMethod("get", String.class, String.class)
                    .invoke(null, key, fallback));
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static String safe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Application currentApplication() {
        try {
            return (Application) Class.forName("android.app.ActivityThread")
                    .getDeclaredMethod("currentApplication").invoke(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void installDomesticBaseUrlHook() throws Exception {
        Class<?> retrofitBuilder = Class.forName("retrofit2.Retrofit$Builder", false, loader);
        Method baseUrl = retrofitBuilder.getDeclaredMethod("baseUrl", String.class);
        module.hook(baseUrl).intercept(chain -> {
            String requested = (String) chain.getArg(0);
            if (runtimeAllowed.getAsBoolean() && (requested == null || requested.isEmpty())
                    && isApiServiceInitializer()) {
                logOnce("base-url", "restore native UMS service-governance endpoint to CN production");
                return chain.proceed(new Object[]{CN_SERVICE_GOVERNANCE});
            }
            return chain.proceed();
        });
    }

    private void installConnectManagerHooks() throws Exception {
        Class<?> connect = Class.forName(
                "com.pantanal.server.connect.support.ConnectManager", false, loader);
        Method download = connect.getDeclaredMethod(
                "downloadUpkFile", String.class, continuationClass);
        Method discovery = connect.getDeclaredMethod(
                "findSeedlingService", List.class, boolean.class, continuationClass);
        Method queryV1 = connect.getDeclaredMethod(
                "queryDomainEnable", List.class, continuationClass);
        Method queryGroup = connect.getDeclaredMethod(
                "queryDomainEnableGroup", List.class, continuationClass);
        Method queryV3 = connect.getDeclaredMethod(
                "queryDomainEnableV3", List.class, continuationClass);
        Method queryMeta = connect.getDeclaredMethod(
                "queryServicePkgMeta", String.class, int.class, continuationClass);

        module.hook(discovery).intercept(chain -> {
            if (!runtimeAllowed.getAsBoolean()) return chain.proceed();
            try {
                @SuppressWarnings("unchecked")
                List<Object> requests = (List<Object>) chain.getArg(0);
                boolean findById = Boolean.TRUE.equals(chain.getArg(1));
                Object continuation = chain.getArg(2);
                return invokeEnvelopeApi(
                        "discovery", apiDiscovery, new Object[]{requests}, continuation,
                        null, findById ? requests : null, true);
            } catch (Throwable error) {
                module.log(Log.WARN, TAG, "UMS domestic discovery call failed; using export fallback", error);
                return chain.proceed();
            }
        });

        module.hook(queryV1).intercept(chain -> {
            if (!runtimeAllowed.getAsBoolean()) return chain.proceed();
            try {
                @SuppressWarnings("unchecked")
                List<Object> requests = (List<Object>) chain.getArg(0);
                return invokeEnvelopeApi(
                        "query-v1", apiQueryV1, new Object[]{requests}, chain.getArg(1),
                        requests, null, true);
            } catch (Throwable error) {
                module.log(Log.WARN, TAG, "UMS domestic V1 query failed; using export fallback", error);
                return chain.proceed();
            }
        });

        module.hook(queryV3).intercept(chain -> {
            if (!runtimeAllowed.getAsBoolean()) return chain.proceed();
            try {
                @SuppressWarnings("unchecked")
                List<Object> requests = (List<Object>) chain.getArg(0);
                return invokeEnvelopeApi(
                        "query-v3", apiQueryV3, new Object[]{requests}, chain.getArg(1),
                        requests, null, true);
            } catch (Throwable error) {
                module.log(Log.WARN, TAG, "UMS domestic V3 query failed; using export fallback", error);
                return chain.proceed();
            }
        });

        module.hook(queryGroup).intercept(chain -> {
            if (!runtimeAllowed.getAsBoolean()) return chain.proceed();
            try {
                @SuppressWarnings("unchecked")
                List<Object> requests = (List<Object>) chain.getArg(0);
                return new GroupQuery(requests, chain.getArg(1)).start();
            } catch (Throwable error) {
                module.log(Log.WARN, TAG, "UMS domestic grouped query failed; using export fallback", error);
                return chain.proceed();
            }
        });

        module.hook(queryMeta).intercept(chain -> {
            if (!runtimeAllowed.getAsBoolean()) return chain.proceed();
            try {
                String serviceId = (String) chain.getArg(0);
                int versionCode = ((Number) chain.getArg(1)).intValue();
                JSONObject payload = new JSONObject();
                payload.put("servicePkgId", serviceId);
                if (versionCode != 0) payload.put("servicePkgVersion", String.valueOf(versionCode));
                Object mediaType = mediaTypeParse.invoke(null, "application/json");
                Object requestBody = requestBodyCreate.invoke(null, payload.toString(), mediaType);
                return invokeEnvelopeApi(
                        "query-meta", apiQueryMeta, new Object[]{requestBody}, chain.getArg(2),
                        null, null, false);
            } catch (Throwable error) {
                module.log(Log.WARN, TAG, "UMS domestic metadata query failed; using export fallback", error);
                return chain.proceed();
            }
        });

        module.hook(download).intercept(chain -> {
            if (!runtimeAllowed.getAsBoolean()) return chain.proceed();
            try {
                return invokeSuspend(
                        apiDownload, new Object[]{chain.getArg(0)}, chain.getArg(1),
                        response -> transformDownloadResponse("download", response));
            } catch (Throwable error) {
                module.log(Log.WARN, TAG, "UMS domestic UPK download failed; using export fallback", error);
                return chain.proceed();
            }
        });
    }

    private Object invokeEnvelopeApi(
            String operation,
            Method method,
            Object[] args,
            Object continuation,
            List<?> domainParams,
            List<?> serviceRequests,
            boolean emptyOnNotFound) throws Throwable {
        return invokeSuspend(method, args, continuation,
                response -> transformEnvelopeResponse(
                        operation, response, domainParams, serviceRequests, emptyOnNotFound));
    }

    private Object invokeSuspend(
            Method method, Object[] args, Object continuation, ResponseTransform transform)
            throws Throwable {
        Object callback = newContinuation(continuation, value -> {
            if (failureClass.isInstance(value)) {
                resumeRaw(continuation, value);
                return;
            }
            try {
                resumeRaw(continuation, transform.apply(value));
            } catch (Throwable error) {
                resumeFailure(continuation, error);
            }
        });
        Object immediate = invokeApi(method, args, callback);
        if (immediate == suspendedMarker) return suspendedMarker;
        return transform.apply(immediate);
    }

    private Object invokeApi(Method method, Object[] args, Object continuation) throws Throwable {
        Object[] call = new Object[args.length + 1];
        System.arraycopy(args, 0, call, 0, args.length);
        call[args.length] = continuation;
        try {
            return method.invoke(getSeedlingApi(), call);
        } catch (InvocationTargetException error) {
            throw error.getCause();
        }
    }

    private Object getSeedlingApi() throws Throwable {
        Object existing = seedlingApi;
        if (existing != null) return existing;
        synchronized (this) {
            existing = seedlingApi;
            if (existing != null) return existing;
            try {
                existing = apiFactory.invoke(null);
            } catch (InvocationTargetException error) {
                throw error.getCause();
            }
            if (existing == null) throw new IllegalStateException("SeedlingApi factory returned null");
            seedlingApi = existing;
            logOnce("api-ready", "native OOS SeedlingApi initialized on restored CN endpoint");
            return existing;
        }
    }

    private Object transformEnvelopeResponse(
            String operation,
            Object response,
            List<?> domainParams,
            List<?> serviceRequests,
            boolean emptyOnNotFound) throws Throwable {
        if (response == null) return newResult(null, "", -1, domainParams, serviceRequests);
        boolean successful = Boolean.TRUE.equals(invokeNoArgs(response, "isSuccessful"));
        int httpCode = ((Number) invokeNoArgs(response, "code")).intValue();
        if (!successful) {
            Object data = httpCode == 429 ? Collections.emptyList() : null;
            int code = httpCode == 429 ? 429 : -1;
            logResult(operation, httpCode, Integer.MIN_VALUE, data);
            return newResult(data, String.valueOf(invokeNoArgs(response, "message")), code,
                    domainParams, serviceRequests);
        }

        Object envelope = invokeNoArgs(response, "body");
        if (envelope == null) {
            logResult(operation, httpCode, Integer.MIN_VALUE, null);
            return newResult(null, "", 0, domainParams, serviceRequests);
        }
        Object rawCode = serializedField(envelope, "code");
        if (!(rawCode instanceof Number)) {
            throw new IllegalStateException("Seedling response has no serialized code field");
        }
        int backendCode = ((Number) rawCode).intValue();
        Object data = serializedField(envelope, "data");
        Object rawMessage = serializedField(envelope, "message");
        String message = rawMessage == null ? "" : String.valueOf(rawMessage);
        if (backendCode == BACKEND_OK) {
            logResult(operation, httpCode, backendCode, data);
            return newResult(data, message, 0, domainParams, serviceRequests);
        }
        if (emptyOnNotFound && backendCode == BACKEND_NOT_FOUND) {
            Object empty = Collections.emptyList();
            logResult(operation, httpCode, backendCode, empty);
            return newResult(empty, message, 0, domainParams, serviceRequests);
        }
        logResult(operation, httpCode, backendCode, null);
        return newResult(null, message, -1, domainParams, serviceRequests);
    }

    private Object transformDownloadResponse(String operation, Object response) throws Throwable {
        if (response == null) return newResult(null, "", -1, null, null);
        boolean successful = Boolean.TRUE.equals(invokeNoArgs(response, "isSuccessful"));
        int httpCode = ((Number) invokeNoArgs(response, "code")).intValue();
        Object body = successful ? invokeNoArgs(response, "body") : null;
        logResult(operation, httpCode, Integer.MIN_VALUE, body);
        if (!successful) return newResult(null, "", -1, null, null);
        return newResult(body, "", 0, null, null);
    }

    private Object newResult(
            Object data, String message, int code, List<?> domainParams, List<?> serviceRequests)
            throws Exception {
        Object result = resultConstructor.newInstance();
        resultSetData.invoke(result, data);
        resultSetMessage.invoke(result, message == null ? "" : message);
        resultSetCode.invoke(result, code);
        if (domainParams != null) {
            resultSetDomainParams.invoke(result, new ArrayList<>(domainParams));
        }
        if (serviceRequests != null) {
            resultSetServiceIdRequests.invoke(result, new ArrayList<>(serviceRequests));
        }
        return result;
    }

    private Object serializedField(Object owner, String serializedName) throws Exception {
        for (Field field : owner.getClass().getDeclaredFields()) {
            for (Annotation annotation : field.getDeclaredAnnotations()) {
                if (!"com.google.gson.annotations.SerializedName"
                        .equals(annotation.annotationType().getName())) continue;
                Method value = annotation.annotationType().getDeclaredMethod("value");
                if (!serializedName.equals(value.invoke(annotation))) continue;
                field.setAccessible(true);
                return field.get(owner);
            }
        }
        return null;
    }

    private Object invokeNoArgs(Object owner, String name) throws Throwable {
        try {
            return owner.getClass().getMethod(name).invoke(owner);
        } catch (InvocationTargetException error) {
            throw error.getCause();
        }
    }

    private Object newContinuation(Object contextDelegate, ResumeHandler handler) {
        ClassLoader proxyLoader = continuationClass.getClassLoader();
        if (proxyLoader == null) proxyLoader = loader;
        return Proxy.newProxyInstance(proxyLoader, new Class<?>[]{continuationClass},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getContext".equals(name)) {
                        return continuationGetContext.invoke(contextDelegate);
                    }
                    if ("resumeWith".equals(name)) {
                        handler.resume(args == null || args.length == 0 ? null : args[0]);
                        return null;
                    }
                    if ("toString".equals(name)) return "OPlusFluidCompatContinuation";
                    if ("hashCode".equals(name)) return System.identityHashCode(proxy);
                    if ("equals".equals(name)) {
                        return args != null && args.length == 1 && proxy == args[0];
                    }
                    return method.invoke(contextDelegate, args);
                });
    }

    private void resumeRaw(Object continuation, Object value) throws Throwable {
        try {
            continuationResumeWith.invoke(continuation, value);
        } catch (InvocationTargetException error) {
            throw error.getCause();
        }
    }

    private void resumeFailure(Object continuation, Throwable error) throws Throwable {
        Object failure;
        try {
            failure = failureFactory.invoke(null, error);
        } catch (InvocationTargetException wrapped) {
            throw wrapped.getCause();
        }
        resumeRaw(continuation, failure);
    }

    private boolean isApiServiceInitializer() {
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            if ("com.pantanal.server.network.ApiService".equals(frame.getClassName())) return true;
        }
        return false;
    }

    private void logResult(String operation, int httpCode, int backendCode, Object data) {
        int entries = data instanceof List<?> ? ((List<?>) data).size() : data == null ? 0 : 1;
        module.log(Log.INFO, TAG, "native UMS domestic " + operation + ": http=" + httpCode
                + " backend=" + (backendCode == Integer.MIN_VALUE ? "n/a" : backendCode)
                + " entries=" + entries);
    }

    private void logOnce(String key, String message) {
        if (logged.add(key)) module.log(Log.INFO, TAG, message);
    }

    private interface ResponseTransform {
        Object apply(Object response) throws Throwable;
    }

    private interface ResumeHandler {
        void resume(Object value) throws Throwable;
    }

    private final class GroupQuery {
        private final List<List<Object>> chunks = new ArrayList<>();
        private final Object outerContinuation;
        private final ArrayList<Object> results = new ArrayList<>();
        private int index;
        private boolean finished;

        GroupQuery(List<Object> requests, Object outerContinuation) {
            for (int start = 0; start < requests.size(); start += 10) {
                chunks.add(new ArrayList<>(
                        requests.subList(start, Math.min(start + 10, requests.size()))));
            }
            this.outerContinuation = outerContinuation;
        }

        Object start() throws Throwable {
            while (index < chunks.size()) {
                List<Object> chunk = chunks.get(index);
                Object callback = newContinuation(
                        outerContinuation, value -> onChunkResumed(chunk, value));
                Object value = invokeApi(apiQueryV3, new Object[]{chunk}, callback);
                if (value == suspendedMarker) return suspendedMarker;
                results.add(transformEnvelopeResponse(
                        "query-v3-group", value, chunk, null, true));
                index++;
            }
            finished = true;
            return new ArrayList<>(results);
        }

        private void onChunkResumed(List<Object> completedChunk, Object value) throws Throwable {
            if (finished) return;
            if (failureClass.isInstance(value)) {
                finished = true;
                resumeRaw(outerContinuation, value);
                return;
            }
            try {
                results.add(transformEnvelopeResponse(
                        "query-v3-group", value, completedChunk, null, true));
                index++;
                while (index < chunks.size()) {
                    List<Object> chunk = chunks.get(index);
                    Object callback = newContinuation(
                            outerContinuation, next -> onChunkResumed(chunk, next));
                    Object next = invokeApi(apiQueryV3, new Object[]{chunk}, callback);
                    if (next == suspendedMarker) return;
                    results.add(transformEnvelopeResponse(
                            "query-v3-group", next, chunk, null, true));
                    index++;
                }
                finished = true;
                resumeRaw(outerContinuation, new ArrayList<>(results));
            } catch (Throwable error) {
                finished = true;
                module.log(Log.WARN, TAG, "UMS grouped domestic query continuation failed", error);
                resumeFailure(outerContinuation, error);
            }
        }
    }
}
