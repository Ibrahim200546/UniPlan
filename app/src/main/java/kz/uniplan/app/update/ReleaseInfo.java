package kz.uniplan.app.update;
import java.io.IOException;
import java.net.URI;
import org.json.JSONArray;
import org.json.JSONObject;

/** Release metadata is untrusted. */
public final class ReleaseInfo {
    public final long versionCode, size;
    public final String version, url, manifestUrl, sha256;
    private ReleaseInfo(long code,long size,String version,String url,String manifest,String digest) {
        versionCode=code; this.size=size; this.version=version; this.url=url; manifestUrl=manifest; sha256=digest;
    }
    public static ReleaseInfo parse(JSONObject release) throws Exception {
        if(release.optBoolean("draft")||release.optBoolean("prerelease")) throw new IOException("Unpublished release");
        JSONArray assets=release.getJSONArray("assets"); JSONObject manifest=null,apk=null;
        for(int i=0;i<assets.length();i++) {
            JSONObject asset=assets.getJSONObject(i);
            if("UniPlan.apk".equals(asset.optString("name"))) apk=asset;
            if("update.json".equals(asset.optString("name"))) manifest=asset;
        }
        if(apk==null||manifest==null) throw new IOException("Incomplete release");
        return new ReleaseInfo(0,apk.getLong("size"),release.getString("tag_name"),
            trustedUrl(apk.getString("browser_download_url")),trustedUrl(manifest.getString("browser_download_url")),"");
    }
    public ReleaseInfo withManifest(JSONObject manifest) throws Exception {
        long code=manifest.getLong("versionCode"); String digest=manifest.getString("sha256");
        if(code<=0||size<=0||size>150L*1024*1024||!digest.matches("[a-fA-F0-9]{64}")
            ||!"kz.uniplan.app".equals(manifest.getString("applicationId"))) throw new IOException("Invalid manifest");
        return new ReleaseInfo(code,size,manifest.getString("versionName"),url,manifestUrl,digest);
    }
    public static String trustedUrl(String value) throws Exception {
        URI uri=new URI(value);
        if(!"https".equals(uri.getScheme())||!"github.com".equals(uri.getHost())||uri.getUserInfo()!=null
            ||uri.getPort()!=-1||uri.getQuery()!=null||uri.getFragment()!=null
            ||!uri.getPath().startsWith("/Ibrahim200546/UniPlan/releases/download/")) throw new IOException("Invalid release URL");
        return value;
    }
}
