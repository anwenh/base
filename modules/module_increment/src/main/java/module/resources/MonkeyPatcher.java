
/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package module.resources;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH;
import static android.os.Build.VERSION_CODES.JELLY_BEAN;
import static android.os.Build.VERSION_CODES.JELLY_BEAN_MR2;
import static android.os.Build.VERSION_CODES.KITKAT;
import static android.os.Build.VERSION_CODES.LOLLIPOP;
import static android.os.Build.VERSION_CODES.M;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Build;
import android.util.ArrayMap;
import android.util.Log;
import android.util.LongSparseArray;
import android.util.SparseArray;
import android.view.ContextThemeWrapper;

import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Code which handles live-patching resources in a running app
 */
public class MonkeyPatcher {
    private static final String LOG_TAG = "MonkeyPatcher";
    public static void monkeyPatchApplication(@Nullable Context context,
                                              @Nullable Application bootstrap,
                                              @Nullable Application realApplication,
                                              @Nullable String externalResourceFile) {
        try {
            // Find the ActivityThread instance for the current thread
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Object currentActivityThread = getActivityThread(context, activityThread);

            // Find the mInitialApplication field of the ActivityThread to the real application
            Field mInitialApplication = activityThread.getDeclaredField("mInitialApplication");
            mInitialApplication.setAccessible(true);
            Application initialApplication = (Application) mInitialApplication.get(currentActivityThread);
            if (realApplication != null && initialApplication == bootstrap) {
                //**2.?????????ActivityThread.mInitialApplication**
                mInitialApplication.set(currentActivityThread, realApplication);
            }

            // Replace all instance of the stub application in ActivityThread#mAllApplications with the
            // real one
            if (realApplication != null) {
                Field mAllApplications = activityThread.getDeclaredField("mAllApplications");
                mAllApplications.setAccessible(true);
                List<Application> allApplications = (List<Application>) mAllApplications
                        .get(currentActivityThread);
                for (int i = 0; i < allApplications.size(); i++) {
                    if (allApplications.get(i) == bootstrap) {
                        //**1.?????????ActivityThread.mAllApplications**
                        allApplications.set(i, realApplication);
                    }
                }
            }

            // Figure out how loaded APKs are stored.

            // API version 8 has PackageInfo, 10 has LoadedApk. 9, I don't know.
            Class<?> loadedApkClass;
            try {
                loadedApkClass = Class.forName("android.app.LoadedApk");
            } catch (ClassNotFoundException e) {
                loadedApkClass = Class.forName("android.app.ActivityThread$PackageInfo");
            }
            Field mApplication = loadedApkClass.getDeclaredField("mApplication");
            mApplication.setAccessible(true);
            Field mResDir = loadedApkClass.getDeclaredField("mResDir");
            mResDir.setAccessible(true);

            // 10 doesn't have this field, 14 does. Fortunately, there are not many Honeycomb devices
            // floating around.
            Field mLoadedApk = null;
            try {
                mLoadedApk = Application.class.getDeclaredField("mLoadedApk");
            } catch (NoSuchFieldException e) {
                // According to testing, it's okay to ignore this.
            }
            Log.e(LOG_TAG, "anwen " +mLoadedApk+  " activityThread "+activityThread);
            // Enumerate all LoadedApk (or PackageInfo) fields in ActivityThread#mPackages and
            // ActivityThread#mResourcePackages and do two things:
            //   - Replace the Application instance in its mApplication field with the real one
            //   - Replace mResDir to point to the external resource file instead of the .apk. This is
            //     used as the asset path for new Resources objects.
            //   - Set Application#mLoadedApk to the found LoadedApk instance
            for (String fieldName : new String[]{"mPackages", "mResourcePackages"}) {
                Field field = activityThread.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(currentActivityThread);

                for (Map.Entry<String, WeakReference<?>> entry :
                        ((Map<String, WeakReference<?>>) value).entrySet()) {
                    Object loadedApk = entry.getValue().get();
                    if (loadedApk == null) {
                        continue;
                    }

                    if (mApplication.get(loadedApk) == bootstrap) {
                        Log.e(LOG_TAG, "anwen realApplication" +realApplication+  " bootstrap "+bootstrap);

                        if (realApplication != null) {
                            //**3.?????????mApplication**
                            mApplication.set(loadedApk, realApplication);
                        }
                        if (externalResourceFile != null) {
                            //?????????????????????
                            mResDir.set(loadedApk, externalResourceFile);
                        }

                        if (realApplication != null && mLoadedApk != null) {
                            //**4.?????????mLoadedApk**
                            mLoadedApk.set(realApplication, loadedApk);
                        }
                    }
                }
            }
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }
    /**
     * This utility method has nothing to do with the MonkeyPatcher per se.
     * It simply calls the {@code currentActivityThread} method of {@code ActivityThread}.
     */
    @Nullable
    public static Object getActivityThread(@Nullable Context context,
                                           @Nullable Class<?> activityThread) {
        try {
            if (activityThread == null) {
                activityThread = Class.forName("android.app.ActivityThread");
            }
            Method m = activityThread.getMethod("currentActivityThread");
            m.setAccessible(true);
            Object currentActivityThread = m.invoke(null);
            if (currentActivityThread == null && context != null) {
                // In older versions of Android (prior to frameworks/base 66a017b63461a22842)
                // the currentActivityThread was built on thread locals, so we'll need to try
                // even harder
                Field mLoadedApk = context.getClass().getField("mLoadedApk");
                mLoadedApk.setAccessible(true);
                Object apk = mLoadedApk.get(context);
                Field mActivityThreadField = apk.getClass().getDeclaredField("mActivityThread");
                mActivityThreadField.setAccessible(true);
                currentActivityThread = mActivityThreadField.get(apk);
            }
            return currentActivityThread;
        } catch (Throwable ignore) {
            ignore.printStackTrace();
            return null;
        }
    }
    public static void monkeyPatchExistingResources(@Nullable Context context,
                                                    @Nullable String externalResourceFile,
                                                    @Nullable Collection<Activity> activities) {
        if (externalResourceFile == null) {
            return;
        }
        try {
            // Create a new AssetManager instance and point it to the resources installed under
            // /sdcard
            AssetManager newAssetManager = AssetManager.class.getConstructor().newInstance();
            Method mAddAssetPath = AssetManager.class.getDeclaredMethod("addAssetPath", String.class);
            mAddAssetPath.setAccessible(true);
            if (((Integer) mAddAssetPath.invoke(newAssetManager, externalResourceFile)) == 0) {
                throw new IllegalStateException("Could not create new AssetManager");
            }
            // Kitkat needs this method call, Lollipop doesn't. However, it doesn't seem to cause any harm
            // in L, so we do it unconditionally.
            Method mEnsureStringBlocks = AssetManager.class.getDeclaredMethod("ensureStringBlocks");
            mEnsureStringBlocks.setAccessible(true);
            mEnsureStringBlocks.invoke(newAssetManager);
            if (activities != null) {
                for (Activity activity : activities) {
                    Resources resources = activity.getResources();
                    try {
                        Field mAssets = Resources.class.getDeclaredField("mAssets");
                        mAssets.setAccessible(true);
                        mAssets.set(resources, newAssetManager);
                    } catch (Throwable ignore) {
                        Field mResourcesImpl = Resources.class.getDeclaredField("mResourcesImpl");
                        mResourcesImpl.setAccessible(true);
                        Object resourceImpl = mResourcesImpl.get(resources);
                        Field implAssets = resourceImpl.getClass().getDeclaredField("mAssets");
                        implAssets.setAccessible(true);
                        implAssets.set(resourceImpl, newAssetManager);
                    }
                    Resources.Theme theme = activity.getTheme();
                    try {
                        try {
                            Field ma = Resources.Theme.class.getDeclaredField("mAssets");
                            ma.setAccessible(true);
                            ma.set(theme, newAssetManager);
                        } catch (NoSuchFieldException ignore) {
                            Field themeField = Resources.Theme.class.getDeclaredField("mThemeImpl");
                            themeField.setAccessible(true);
                            Object impl = themeField.get(theme);
                            Field ma = impl.getClass().getDeclaredField("mAssets");
                            ma.setAccessible(true);
                            ma.set(impl, newAssetManager);
                        }
                        Field mt = ContextThemeWrapper.class.getDeclaredField("mTheme");
                        mt.setAccessible(true);
                        mt.set(activity, null);
                        Method mtm = ContextThemeWrapper.class.getDeclaredMethod("initializeTheme");
                        mtm.setAccessible(true);
                        mtm.invoke(activity);
                        if (SDK_INT < 24) { // As of API 24, mTheme is gone (but updates work
                                            // without these changes
                            Method mCreateTheme = AssetManager.class
                                    .getDeclaredMethod("createTheme");
                            mCreateTheme.setAccessible(true);
                            Object internalTheme = mCreateTheme.invoke(newAssetManager);
                            Field mTheme = Resources.Theme.class.getDeclaredField("mTheme");
                            mTheme.setAccessible(true);
                            mTheme.set(theme, internalTheme);
                        }
                    } catch (Throwable e) {
                        Log.e(LOG_TAG, "Failed to update existing theme for activity " + activity,
                                e);
                    }
                    pruneResourceCaches(resources);
                }
            }
            // Iterate over all known Resources objects
            Collection<WeakReference<Resources>> references;
            if (SDK_INT >= KITKAT) {
                // Find the singleton instance of ResourcesManager
                Class<?> resourcesManagerClass = Class.forName("android.app.ResourcesManager");
                Method mGetInstance = resourcesManagerClass.getDeclaredMethod("getInstance");
                mGetInstance.setAccessible(true);
                Object resourcesManager = mGetInstance.invoke(null);
                try {
                    Field fMActiveResources = resourcesManagerClass.getDeclaredField("mActiveResources");
                    fMActiveResources.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    ArrayMap<?, WeakReference<Resources>> arrayMap =
                            (ArrayMap<?, WeakReference<Resources>>) fMActiveResources.get(resourcesManager);
                    references = arrayMap.values();
                } catch (NoSuchFieldException ignore) {
                    Field mResourceReferences = resourcesManagerClass.getDeclaredField("mResourceReferences");
                    mResourceReferences.setAccessible(true);
                    //noinspection unchecked
                    references = (Collection<WeakReference<Resources>>) mResourceReferences.get(resourcesManager);
                }
            } else {
                Class<?> activityThread = Class.forName("android.app.ActivityThread");
                Field fMActiveResources = activityThread.getDeclaredField("mActiveResources");
                fMActiveResources.setAccessible(true);
                Object thread = getActivityThread(context, activityThread);
                @SuppressWarnings("unchecked")
                HashMap<?, WeakReference<Resources>> map =
                        (HashMap<?, WeakReference<Resources>>) fMActiveResources.get(thread);
                references = map.values();
            }
            for (WeakReference<Resources> wr : references) {
                Resources resources = wr.get();
                if (resources != null) {
                    // Set the AssetManager of the Resources instance to our brand new one
                    try {
                        Field mAssets = Resources.class.getDeclaredField("mAssets");
                        mAssets.setAccessible(true);
                        mAssets.set(resources, newAssetManager);
                    } catch (Throwable ignore) {
                        Field mResourcesImpl = Resources.class.getDeclaredField("mResourcesImpl");
                        mResourcesImpl.setAccessible(true);
                        Object resourceImpl = mResourcesImpl.get(resources);
                        try {
                            Field implAssets = resourceImpl.getClass().getDeclaredField("mAssets");
                            implAssets.setAccessible(true);
                            implAssets.set(resourceImpl, newAssetManager);
                        } catch (Throwable e) {
                            Field implAssets = resourceImpl.getClass().getSuperclass().getDeclaredField("mAssets");
                            implAssets.setAccessible(true);
                            implAssets.set(resourceImpl, newAssetManager);
                        }
                    }
                    resources.updateConfiguration(resources.getConfiguration(), resources.getDisplayMetrics());
                }
            }
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }
    private static void pruneResourceCaches(Object resources) {
        // Drain TypedArray instances from the typed array pool since these can hold on
        // to stale asset data
        if (SDK_INT >= LOLLIPOP) {
            try {
                Field typedArrayPoolField =
                        Resources.class.getDeclaredField("mTypedArrayPool");
                typedArrayPoolField.setAccessible(true);
                Object pool = typedArrayPoolField.get(resources);
                Class<?> poolClass = pool.getClass();
                Method acquireMethod = poolClass.getDeclaredMethod("acquire");
                acquireMethod.setAccessible(true);
                while (true) {
                    Object typedArray = acquireMethod.invoke(pool);
                    if (typedArray == null) {
                        break;
                    }
                }
            } catch (Throwable ignore) {
            }
        }
        if (SDK_INT >= Build.VERSION_CODES.M) {
            // Really should only be N; fix this as soon as it has its own API level
            try {
                Field mResourcesImpl = Resources.class.getDeclaredField("mResourcesImpl");
                mResourcesImpl.setAccessible(true);
                // For the remainder, use the ResourcesImpl instead, where all the fields
                // now live
                resources = mResourcesImpl.get(resources);
            } catch (Throwable ignore) {
            }
        }
        // Prune bitmap and color state lists etc caches
        Object lock = null;
        if (SDK_INT >= JELLY_BEAN_MR2) {
            try {
                Field field = resources.getClass().getDeclaredField("mAccessLock");
                field.setAccessible(true);
                lock = field.get(resources);
            } catch (Throwable ignore) {
            }
        } else {
            try {
                Field field = Resources.class.getDeclaredField("mTmpValue");
                field.setAccessible(true);
                lock = field.get(resources);
            } catch (Throwable ignore) {
            }
        }
        if (lock == null) {
            lock = MonkeyPatcher.class;
        }
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (lock) {
            // Prune bitmap and color caches
            pruneResourceCache(resources, "mDrawableCache");
            pruneResourceCache(resources,"mColorDrawableCache");
            pruneResourceCache(resources,"mColorStateListCache");
            if (SDK_INT >= M) {
                pruneResourceCache(resources, "mAnimatorCache");
                pruneResourceCache(resources, "mStateListAnimatorCache");
            } else if (SDK_INT == KITKAT) {
                pruneResourceCache(resources, "sPreloadedDrawables");
                pruneResourceCache(resources, "sPreloadedColorDrawables");
                pruneResourceCache(resources, "sPreloadedColorStateLists");
            }
        }
    }
    private static boolean pruneResourceCache( Object resources,
           String fieldName) {
        try {
            Class<?> resourcesClass = resources.getClass();
            Field cacheField;
            try {
                cacheField = resourcesClass.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignore) {
                cacheField = Resources.class.getDeclaredField(fieldName);
            }
            cacheField.setAccessible(true);
            Object cache = cacheField.get(resources);
            // Find the class which defines the onConfigurationChange method
            Class<?> type = cacheField.getType();
            if (SDK_INT < JELLY_BEAN) {
                if (cache instanceof SparseArray) {
                    ((SparseArray) cache).clear();
                    return true;
                } else if (SDK_INT >= ICE_CREAM_SANDWICH && cache instanceof LongSparseArray) {
                    // LongSparseArray has API level 16 but was private (and available inside
                    // the framework) in 15 and is used for this cache.
                    //noinspection AndroidLintNewApi
                    ((LongSparseArray) cache).clear();
                    return true;
                }
            } else if (SDK_INT < M) {
                // JellyBean, KitKat, Lollipop
                if ("mColorStateListCache".equals(fieldName)) {
                    // For some reason framework doesn't call clearDrawableCachesLocked on
                    // this field
                    if (cache instanceof LongSparseArray) {
                        //noinspection AndroidLintNewApi
                        ((LongSparseArray)cache).clear();
                    }
                } else if (type.isAssignableFrom(ArrayMap.class)) {
                    Method clearArrayMap = Resources.class.getDeclaredMethod(
                            "clearDrawableCachesLocked", ArrayMap.class, Integer.TYPE);
                    clearArrayMap.setAccessible(true);
                    clearArrayMap.invoke(resources, cache, -1);
                    return true;
                } else if (type.isAssignableFrom(LongSparseArray.class)) {
                    try {
                        Method clearSparseMap = Resources.class.getDeclaredMethod(
                                "clearDrawableCachesLocked", LongSparseArray.class, Integer.TYPE);
                        clearSparseMap.setAccessible(true);
                        clearSparseMap.invoke(resources, cache, -1);
                        return true;
                    } catch (NoSuchMethodException e) {
                        if (cache instanceof LongSparseArray) {
                            //noinspection AndroidLintNewApi
                            ((LongSparseArray)cache).clear();
                            return true;
                        }
                    }
                } else if (type.isArray() &&
                        type.getComponentType().isAssignableFrom(LongSparseArray.class)) {
                    LongSparseArray[] arrays = (LongSparseArray[])cache;
                    for (LongSparseArray array : arrays) {
                        if (array != null) {
                            //noinspection AndroidLintNewApi
                            array.clear();
                        }
                    }
                    return true;
                }
            } else {
                // Marshmallow: DrawableCache class
                while (type != null) {
                    try {
                        Method configChangeMethod = type.getDeclaredMethod(
                                "onConfigurationChange", Integer.TYPE);
                        configChangeMethod.setAccessible(true);
                        configChangeMethod.invoke(cache, -1);
                        return true;
                    } catch (Throwable ignore) {
                    }
                    type = type.getSuperclass();
                }
            }
        } catch (Throwable ignore) {
            // Not logging these; while there is some checking of SDK_INT here to avoid
            // doing a lot of unnecessary field lookups, it's not entirely accurate and
            // errs on the side of caution (since different devices may have picked up
            // different snapshots of the framework); therefore, it's normal for this
            // to attempt to look up a field for a cache that isn't there; only if it's
            // really there will it continue to flush that particular cache.
        }
        return false;
    }
}