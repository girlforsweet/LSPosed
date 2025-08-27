package android.app;

import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.res.CompatibilityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.view.Display;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

class ReflectionUtils {
    private static final Map<String, Class<?>> CLASS_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, java.lang.reflect.Field> FIELD_CACHE = new ConcurrentHashMap<>();

    static Class<?> findClass(String className, ClassLoader loader) {
        return CLASS_CACHE.computeIfAbsent(className, name -> {
            try {
                return XposedHelpers.findClass(name, loader);
            } catch (Throwable t) {
                XposedBridge.log("Failed to find class: " + name + ", error: " + t.getMessage());
                return null;
            }
        });
    }

    static java.lang.reflect.Field findFieldIfExists(Class<?> clazz, String fieldName) {
        if (clazz == null) return null;
        String key = clazz.getName() + "#" + fieldName;
        return FIELD_CACHE.computeIfAbsent(key, k -> {
            try {
                return XposedHelpers.findFieldIfExists(clazz, fieldName);
            } catch (Throwable t) {
                XposedBridge.log("Failed to find field: " + fieldName + ", error: " + t.getMessage());
                return null;
            }
        });
    }

    static Object getObjectField(Object obj, String fieldName) {
        try {
            return XposedHelpers.getObjectField(obj, fieldName);
        } catch (Throwable t) {
            XposedBridge.log("Failed to get field: " + fieldName + ", error: " + t.getMessage());
            return null;
        }
    }

    static Object newInstance(Class<?> clazz, Object... args) {
        try {
            return XposedHelpers.newInstance(clazz, args);
        } catch (Throwable t) {
            XposedBridge.log("Failed to create instance of " + clazz.getName() + ", error: " + t.getMessage());
            return null;
        }
    }

    static void setFloatField(Object obj, String fieldName, float value) {
        try {
            XposedHelpers.setFloatField(obj, fieldName, value);
        } catch (Throwable t) {
            XposedBridge.log("Failed to set field: " + fieldName + ", error: " + t.getMessage());
        }
    }
}

public final class AndroidAppHelper {
    private AndroidAppHelper() {}

    private static final String CLASS_RESOURCES_KEY_NAME = "android.content.res.ResourcesKey";
    private static final String FIELD_RESOURCES_MANAGER = "mResourcesManager";
    private static final String FIELD_RESOURCE_IMPLS = "mResourceImpls";
    private static final String FIELD_RESOURCES_IMPL = "mResourcesImpl";
    private static final String FIELD_APPLICATION_SCALE = "applicationScale";
    private static final String METHOD_GET_THEME_CONFIG = "getThemeConfig";

    private static final Class<?> CLASS_RESOURCES_KEY;
    private static final boolean HAS_IS_THEMEABLE;
    private static final boolean HAS_THEME_CONFIG_PARAMETER;

    static {
        CLASS_RESOURCES_KEY = ReflectionUtils.findClass(CLASS_RESOURCES_KEY_NAME, null);
        HAS_IS_THEMEABLE = ReflectionUtils.findFieldIfExists(CLASS_RESOURCES_KEY, "mIsThemeable") != null;
        HAS_THEME_CONFIG_PARAMETER = HAS_IS_THEMEABLE && XposedHelpers.findMethodExactIfExists("android.app.ResourcesManager", null, METHOD_GET_THEME_CONFIG) != null;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static Map<Object, WeakReference> getResourcesMap(ActivityThread activityThread) {
        Object resourcesManager = ReflectionUtils.getObjectField(activityThread, FIELD_RESOURCES_MANAGER);
        return (Map) ReflectionUtils.getObjectField(resourcesManager, FIELD_RESOURCE_IMPLS);
    }

    private static Object createResourcesKey(String resDir, String[] splitResDirs, String[] overlayDirs, String[] libDirs, int displayId, Configuration overrideConfiguration, CompatibilityInfo compatInfo) {
        return ReflectionUtils.newInstance(CLASS_RESOURCES_KEY, resDir, splitResDirs, overlayDirs, libDirs, displayId, overrideConfiguration, compatInfo);
    }

    public static void addActiveResource(String resDir, float scale, boolean isThemeable, Resources resources) {
        addActiveResource(resDir, resources);
    }

    public static void addActiveResource(String resDir, Resources resources) {
        ActivityThread thread = ActivityThread.currentActivityThread();
        if (thread == null) {
            return;
        }

        Object resourcesKey;
        CompatibilityInfo compatInfo = (CompatibilityInfo) ReflectionUtils.newInstance(CompatibilityInfo.class);
        ReflectionUtils.setFloatField(compatInfo, FIELD_APPLICATION_SCALE, resources.hashCode());
        resourcesKey = createResourcesKey(resDir, null, null, null, Display.DEFAULT_DISPLAY, null, compatInfo);

        if (resourcesKey != null) {
            Object resImpl = ReflectionUtils.getObjectField(resources, FIELD_RESOURCES_IMPL);
            getResourcesMap(thread).put(resourcesKey, new WeakReference<>(resImpl));
        }
    }

    public static String currentProcessName() {
        String processName = ActivityThread.currentPackageName();
        if (processName == null)
            return "android";
        return processName;
    }

    public static ApplicationInfo currentApplicationInfo() {
        ActivityThread am = ActivityThread.currentActivityThread();
        if (am == null)
            return null;

        Object boundApplication = ReflectionUtils.getObjectField(am, "mBoundApplication");
        if (boundApplication == null)
            return null;

        return (ApplicationInfo) ReflectionUtils.getObjectField(boundApplication, "appInfo");
    }

    public static String currentPackageName() {
        ApplicationInfo ai = currentApplicationInfo();
        return (ai != null) ? ai.packageName : "android";
    }

    public static Application currentApplication() {
        return ActivityThread.currentApplication();
    }

    @Deprecated
    public static SharedPreferences getSharedPreferencesForPackage(String packageName, String prefFileName, int mode) {
        XposedBridge.log("Warning: getSharedPreferencesForPackage is deprecated. Use XSharedPreferences instead.");
        return new XSharedPreferences(packageName, prefFileName);
    }

    @Deprecated
    public static SharedPreferences getDefaultSharedPreferencesForPackage(String packageName) {
        XposedBridge.log("Warning: getDefaultSharedPreferencesForPackage is deprecated. Use XSharedPreferences instead.");
        return new XSharedPreferences(packageName);
    }

    @Deprecated
    public static void reloadSharedPreferencesIfNeeded(SharedPreferences pref) {
        if (pref instanceof XSharedPreferences) {
            ((XSharedPreferences) pref).reload();
        }
    }
}
