package kz.uniplan.app.update;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;
import kz.uniplan.app.BuildConfig;
import kz.uniplan.app.R;

/** Published APKs only. Android always asks before installation. */
public final class AppUpdates {
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor();
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static boolean busy;
    private AppUpdates() {}
    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("app_updates",Context.MODE_PRIVATE); }
    private static File apk(Context c) { return new File(new File(c.getFilesDir(),"updates"),"UniPlan.apk"); }
    private static boolean alive(Activity a) { return !a.isFinishing()&&!a.isDestroyed(); }
    public static void resume(Activity activity) {
        if(!"kz.uniplan.app".equals(activity.getPackageName())) return;
        if(prefs(activity).getBoolean("awaitingPermission",false)) {
            prefs(activity).edit().putBoolean("awaitingPermission",false).apply();
            if(Build.VERSION.SDK_INT<26||activity.getPackageManager().canRequestPackageInstalls()) install(activity);
            else prefs(activity).edit().putLong("readyVersion",0).apply();
            return;
        }
        if(prefs(activity).getLong("readyVersion",0)>BuildConfig.VERSION_CODE && apk(activity).isFile()) {
            install(activity); return;
        }
        if(System.currentTimeMillis()-prefs(activity).getLong("lastCheck",0)>6*60*60*1000L) check(activity,false);
    }
    public static void check(Activity activity,boolean manual) {
        if(busy||!"kz.uniplan.app".equals(activity.getPackageName())) return;
        busy=true; Context context=activity.getApplicationContext();
        prefs(context).edit().putLong("lastCheck",System.currentTimeMillis()).apply();
        if(manual) notice(activity,R.string.update_checking);
        WORKER.execute(() -> {
            ReleaseInfo info=null; boolean failed=false;
            try {
                ReleaseInfo assets=ReleaseInfo.parse(json("https://api.github.com/repos/Ibrahim200546/UniPlan/releases/latest"));
                info=assets.withManifest(json(assets.manifestUrl));
                prefs(context).edit().putLong("lastCheck",System.currentTimeMillis()).apply();
            } catch(Exception error) { failed=true; }
            ReleaseInfo update=info; boolean error=failed;
            MAIN.post(() -> {
                busy=false; if(!alive(activity)) return;
                if(error) { if(manual) notice(activity,R.string.update_failed); return; }
                if(update.versionCode<=BuildConfig.VERSION_CODE) { if(manual) notice(activity,R.string.update_current); return; }
                new MaterialAlertDialogBuilder(activity).setTitle(R.string.update_available)
                    .setMessage(activity.getString(R.string.update_offer,update.version))
                    .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.update_download,(d,w)->download(activity,update)).show();
            });
        });
    }
    private static HttpURLConnection open(String raw) throws Exception {
        URL url=new URL(raw);
        for(int redirects=0;redirects<6;redirects++) {
            String host=url.getHost();
            if(!"https".equals(url.getProtocol())||url.getUserInfo()!=null
                ||!(host.equals("api.github.com")||host.equals("github.com")||host.equals("release-assets.githubusercontent.com")||host.equals("objects.githubusercontent.com"))) throw new IOException("Invalid host");
            HttpURLConnection c=(HttpURLConnection)url.openConnection();
            c.setConnectTimeout(20000); c.setReadTimeout(30000); c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent","UniPlan/"+BuildConfig.VERSION_NAME);
            int status=c.getResponseCode();
            if(status>=300&&status<400) { String next=c.getHeaderField("Location"); c.disconnect(); if(next==null) throw new IOException("Missing redirect"); url=new URL(url,next); continue; }
            if(status!=200) { c.disconnect(); throw new IOException("Download failed"); }
            return c;
        }
        throw new IOException("Too many redirects");
    }
    private static JSONObject json(String url) throws Exception {
        HttpURLConnection c=open(url);
        try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192]; int n;
            while((n=in.read(buffer))!=-1) { if(out.size()+n>1024*1024) throw new IOException("Metadata too large"); out.write(buffer,0,n); }
            return new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));
        } finally { c.disconnect(); }
    }
    private static void download(Activity activity,ReleaseInfo info) {
        if(busy) return; busy=true; Context context=activity.getApplicationContext();
        notice(activity,R.string.update_downloading);
        WORKER.execute(() -> {
            boolean success=false; File file=apk(context);
            try {
                File directory=file.getParentFile(); if(!directory.isDirectory()&&!directory.mkdirs()) throw new IOException("Cannot create directory");
                HttpURLConnection c=open(info.url); MessageDigest digest=MessageDigest.getInstance("SHA-256"); long count=0;
                try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(file)) {
                    byte[] buffer=new byte[65536]; int n;
                    while((n=in.read(buffer))!=-1) { count+=n; if(count>info.size) throw new IOException("APK too large"); out.write(buffer,0,n); digest.update(buffer,0,n); }
                } finally { c.disconnect(); }
                if(count!=info.size||!hex(digest.digest()).equalsIgnoreCase(info.sha256)) throw new IOException("Checksum mismatch");
                verify(context,file,info.versionCode); success=true;
                prefs(context).edit().putLong("readyVersion",info.versionCode).apply();
            } catch(Exception error) { if(file.exists()&&!file.delete()) file.deleteOnExit(); }
            boolean ready=success;
            MAIN.post(() -> { busy=false; if(alive(activity)) { if(ready) install(activity); else notice(activity,R.string.update_failed); } });
        });
    }
    @SuppressWarnings("deprecation")
    private static void verify(Context context,File file,long expected) throws Exception {
        PackageManager pm=context.getPackageManager();
        int flags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
        PackageInfo archive=pm.getPackageArchiveInfo(file.getAbsolutePath(),flags),installed=pm.getPackageInfo(context.getPackageName(),flags);
        if(archive==null||!context.getPackageName().equals(archive.packageName)) throw new IOException("Wrong package");
        long code=Build.VERSION.SDK_INT>=28?archive.getLongVersionCode():archive.versionCode;
        if(code!=expected||code<=BuildConfig.VERSION_CODE) throw new IOException("Wrong version");
        Signature[] next=Build.VERSION.SDK_INT>=28?archive.signingInfo.getApkContentsSigners():archive.signatures;
        Signature[] current=Build.VERSION.SDK_INT>=28?installed.signingInfo.getApkContentsSigners():installed.signatures;
        if(next==null||current==null||next.length!=1||current.length!=1||!Arrays.equals(next[0].toByteArray(),current[0].toByteArray())) throw new IOException("Wrong signer");
    }
    private static String hex(byte[] bytes) { StringBuilder out=new StringBuilder(); for(byte b:bytes) out.append(String.format(java.util.Locale.ROOT,"%02x",b&255)); return out.toString(); }
    private static void install(Activity activity) {
        try {
            File file=apk(activity); long version=prefs(activity).getLong("readyVersion",0); verify(activity,file,version);
            // Consume the prompt before leaving this Activity, including when the user cancels.
            prefs(activity).edit().putLong("readyVersion",0).apply();
            if(Build.VERSION.SDK_INT>=26&&!activity.getPackageManager().canRequestPackageInstalls()) {
                new MaterialAlertDialogBuilder(activity).setTitle(R.string.update_available).setMessage(R.string.update_permission)
                    .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.ok,(d,w)-> {
                        prefs(activity).edit().putLong("readyVersion",version).putBoolean("awaitingPermission",true).apply();
                        activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.getPackageName())));
                    }).show(); return;
            }
            Uri uri=FileProvider.getUriForFile(activity,activity.getPackageName()+".updates",file);
            activity.startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
        } catch(Exception error) { prefs(activity).edit().putLong("readyVersion",0).apply(); notice(activity,R.string.update_failed); }
    }
    private static void notice(Activity activity,int text) { Toast.makeText(activity,text,Toast.LENGTH_LONG).show(); }
}
