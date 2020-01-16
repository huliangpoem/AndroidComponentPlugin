package com.malin.hook;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

@SuppressLint("PrivateApi")
class ProviderHelper {

    private static final String TAG = "ProviderHelper";

    /**
     * 在进程内部安装provider, 也就是调用 ActivityThread.installContentProviders方法
     */
    @SuppressLint("DiscouragedPrivateApi")
    static void installProviders(Context context, File apkFile) {
        try {

            //1.get ProviderInfo
            List<ProviderInfo> providerInfoList = parseProviders(apkFile);
            if (providerInfoList == null) {
                Log.e(TAG, "providerInfoList==null");
                return;
            }

            //2.set packageName
            for (ProviderInfo providerInfo : providerInfoList) {
                providerInfo.applicationInfo.packageName = context.getPackageName();
            }
            Log.d(TAG, providerInfoList.toString());

            //3.get currentActivityThread
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Method currentActivityThreadMethod = activityThreadClass.getDeclaredMethod("currentActivityThread");
            currentActivityThreadMethod.setAccessible(true);
            Object currentActivityThread = currentActivityThreadMethod.invoke(null);


            //4.call ActivityThread#installContentProviders()
            //private void installContentProviders(Context context, List<ProviderInfo> providers) {}//api-29
            //private void installContentProviders(Context context, List<ProviderInfo> providers) {}//api-28
            //private void installContentProviders(Context context, List<ProviderInfo> providers) {}//api-27
            //private void installContentProviders(Context context, List<ProviderInfo> providers) {}//api-26
            //private void installContentProviders(Context context, List<ProviderInfo> providers) {}//api-25
            //private void installContentProviders(Context context, List<ProviderInfo> providers) {}//api-24
            //private void installContentProviders(Context context, List<ProviderInfo> providers) {}//api-23
            //...
            //private void installContentProviders(Context context, List<ProviderInfo> providers) {}//api-15
            Method installContentProvidersMethod = activityThreadClass.getDeclaredMethod("installContentProviders", Context.class, List.class);
            installContentProvidersMethod.setAccessible(true);
            installContentProvidersMethod.invoke(currentActivityThread, context, providerInfoList);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    /**
     * 解析Apk文件中的 <provider>, 并存储起来
     * 主要是调用PackageParser类的generateProviderInfo方法
     *
     * @param apkFile 插件对应的apk文件
     */
    @SuppressLint("DiscouragedPrivateApi")
    @SuppressWarnings("JavaReflectionMemberAccess")
    private static List<ProviderInfo> parseProviders(File apkFile) {
        try {
            int version = Build.VERSION.SDK_INT;
            if (version < 15) return null;
            //1.获取PackageParser的Class对象
            //package android.content.pm
            //public class PackageParser
            Class<?> packageParserClass = Class.forName("android.content.pm.PackageParser");

            //2.获取parsePackage()方法的Method
            //public Package parsePackage(File packageFile, int flags) throws PackageParserException {}//api-29
            //...
            //public Package parsePackage(File packageFile, int flags) throws PackageParserException {}//api-21
            //public Package parsePackage(File sourceFile, String destCodePath, DisplayMetrics metrics, int flags) {}//api-19
            //...
            //public Package parsePackage(File sourceFile, String destCodePath, DisplayMetrics metrics, int flags) {}//api-15
            Method parsePackageMethod;
            if (Build.VERSION.SDK_INT >= 20) {
                parsePackageMethod = packageParserClass.getDeclaredMethod("parsePackage", File.class, int.class);
            } else {
                // 15<=Build.VERSION.SDK_INT <=19
                parsePackageMethod = packageParserClass.getDeclaredMethod("parsePackage", File.class, String.class, DisplayMetrics.class, int.class);
            }
            parsePackageMethod.setAccessible(true);

            //3.生成PackageParser对象实例
            Object packageParser;
            if (Build.VERSION.SDK_INT >= 20) {
                packageParser = packageParserClass.newInstance();
            } else {
                // 15<=Build.VERSION.SDK_INT <=19
                //public PackageParser(String archiveSourcePath) {}//api-19
                Constructor constructor = packageParserClass.getConstructor(String.class);
                constructor.setAccessible(true);
                String archiveSourcePath = apkFile.getCanonicalPath();
                packageParser = constructor.newInstance(archiveSourcePath);
            }

            //4.调用parsePackage获取到apk对象对应的Package对象(return information about intent receivers in the package)
            //Package为PackageParser的内部类;public final static class Package implements Parcelable {}
            Object packageObj;
            if (Build.VERSION.SDK_INT >= 20) {
                //public Package parsePackage(File packageFile, int flags) throws PackageParserException {}//api-29
                //public Package parsePackage(File packageFile, int flags) throws PackageParserException {}//api-21
                packageObj = parsePackageMethod.invoke(packageParser, apkFile, PackageManager.GET_RECEIVERS);
            } else {
                // 15<=Build.VERSION.SDK_INT <=19
                String destCodePath = apkFile.getCanonicalPath();
                DisplayMetrics displayMetrics = new DisplayMetrics();

                //public Package parsePackage(File sourceFile, String destCodePath, DisplayMetrics metrics, int flags) {}//api-19
                //public Package parsePackage(File sourceFile, String destCodePath, DisplayMetrics metrics, int flags) {}//api-15
                packageObj = parsePackageMethod.invoke(packageParser, apkFile, destCodePath, displayMetrics, PackageManager.GET_RECEIVERS);
            }

            if (packageObj == null) return null;

            //android.content.pm.PackageParser$Package
            // 读取Package对象里面的services字段
            // 接下来要做的就是根据这个List<Provider> 获取到Provider对应的ProviderInfo
            //public final ArrayList<Provider> providers = new ArrayList<Provider>(0);
            Field providersField = packageObj.getClass().getDeclaredField("providers");
            List<?> providers = (List<?>) providersField.get(packageObj);

            if (providers == null) {
                Log.e(TAG, "providers == null");
                return null;
            }


            Class<?> packageParser$ProviderClass = Class.forName("android.content.pm.PackageParser$Provider");


            //5.call generateProviderInfo()
            //public static final ProviderInfo generateProviderInfo(Provider p, int flags, PackageUserState state, int userId) {}//api-29
            //...
            //public static final ProviderInfo generateProviderInfo(Provider p, int flags, PackageUserState state, int userId) {}//api-17
            //public static final ProviderInfo generateProviderInfo(Provider p, int flags, boolean stopped,int enabledState, int userId) {}//api-16
            //public static final ProviderInfo generateProviderInfo(Provider p,int flags) {}//api-15

            Method generateProviderInfo;
            if (Build.VERSION.SDK_INT >= 17) {
                // 调用generateProviderInfo 方法, 把PackageParser.Provider转换成ProviderInfo
                Class<?> packageUserStateClass = Class.forName("android.content.pm.PackageUserState");
                Class<?> userHandler = Class.forName("android.os.UserHandle");

                //public static final int getCallingUserId(){}
                Method getCallingUserIdMethod = userHandler.getDeclaredMethod("getCallingUserId");
                getCallingUserIdMethod.setAccessible(true);

                Object userIdObj = getCallingUserIdMethod.invoke(null);
                if (!(userIdObj instanceof Integer)) return null;
                int userId = (Integer) userIdObj;

                //public PackageUserState() {}//api-23默认构造方法
                Object defaultUserState = packageUserStateClass.newInstance();
                // 需要调用 android.content.pm.PackageParser#generateProviderInfo


                //public static final ProviderInfo generateProviderInfo(Provider p, int flags, PackageUserState state, int userId) {}//api-29
                //...
                //public static final ProviderInfo generateProviderInfo(Provider p, int flags, PackageUserState state, int userId) {}//api-17
                //public static final ProviderInfo generateProviderInfo(Provider p, int flags, boolean stopped,int enabledState, int userId) {}//api-16
                //public static final ProviderInfo generateProviderInfo(Provider p,int flags) {}//api-15
                generateProviderInfo = packageParserClass.getDeclaredMethod(
                        "generateProviderInfo", packageParser$ProviderClass, int.class, packageUserStateClass, int.class);
                generateProviderInfo.setAccessible(true);

                List<ProviderInfo> ret = new ArrayList<>();
                // 解析出intent对应的Provider组件
                for (Object service : providers) {
                    ProviderInfo info = (ProviderInfo) generateProviderInfo.invoke(packageParser, service, 0, defaultUserState, userId);
                    ret.add(info);
                }
                return ret;
            } else if (Build.VERSION.SDK_INT == 16) {

                Class<?> userIdClass = Class.forName("android.os.UserId");
                // public static final int getCallingUserId(){}
                Method getCallingUserIdMethod = userIdClass.getDeclaredMethod("getCallingUserId");
                getCallingUserIdMethod.setAccessible(true);

                Object userIdObj = getCallingUserIdMethod.invoke(null);
                if (!(userIdObj instanceof Integer)) return null;
                int userId = (Integer) userIdObj;

                //public static final ProviderInfo generateProviderInfo(Provider p, int flags, boolean stopped,int enabledState, int userId) {}//api-16
                generateProviderInfo = packageParserClass.getDeclaredMethod(
                        "generateProviderInfo", packageParser$ProviderClass, int.class, boolean.class, int.class, int.class);
                generateProviderInfo.setAccessible(true);

                List<ProviderInfo> ret = new ArrayList<>();
                // 解析出intent对应的Provider组件
                for (Object service : providers) {
                    int enabledState = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
                    ProviderInfo info = (ProviderInfo) generateProviderInfo.invoke(packageParser, service, 0, false, enabledState, userId);
                    ret.add(info);
                }
                return ret;
            } else {
                //Build.VERSION.SDK_INT==15

                //public static final ProviderInfo generateProviderInfo(Provider p,int flags) {}//api-15
                generateProviderInfo = packageParserClass.getDeclaredMethod("generateProviderInfo", packageParser$ProviderClass, int.class);
                generateProviderInfo.setAccessible(true);

                List<ProviderInfo> ret = new ArrayList<>();
                // 解析出intent对应的Provider组件
                for (Object service : providers) {
                    ProviderInfo info = (ProviderInfo) generateProviderInfo.invoke(packageParser, service, 0);
                    ret.add(info);
                }
                return ret;
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }


}