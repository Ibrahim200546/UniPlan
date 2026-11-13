package kz.uniplan.app.update;

import static org.junit.Assert.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28,manifest=Config.NONE)
public class ReleaseInfoTest {
    private JSONObject release() throws Exception {
        String root="https://github.com/Ibrahim200546/UniPlan/releases/download/v1.3.0/";
        return new JSONObject().put("tag_name","v1.3.0").put("assets",new JSONArray()
            .put(new JSONObject().put("name","UniPlan.apk").put("size",20000).put("browser_download_url",root+"UniPlan.apk"))
            .put(new JSONObject().put("name","update.json").put("browser_download_url",root+"update.json")));
    }
    private JSONObject manifest() throws Exception {
        return new JSONObject().put("applicationId","kz.uniplan.app").put("versionCode",1001)
            .put("versionName","1.3.0").put("sha256",new String(new char[64]).replace('\0','a'));
    }
    @Test public void completeReleaseUsesNumericVersion() throws Exception {
        ReleaseInfo info=ReleaseInfo.parse(release()).withManifest(manifest());
        assertEquals(1001,info.versionCode); assertEquals(20000,info.size); assertEquals("1.3.0",info.version);
    }
    @Test public void rejectsMissingManifest() throws Exception {
        JSONObject r=release(); r.getJSONArray("assets").remove(1);
        assertThrows(Exception.class,()->ReleaseInfo.parse(r));
    }
    @Test public void rejectsUnpublishedRelease() throws Exception {
        assertThrows(Exception.class,()->ReleaseInfo.parse(release().put("draft",true)));
        assertThrows(Exception.class,()->ReleaseInfo.parse(release().put("prerelease",true)));
    }
    @Test public void rejectsForeignUrls() {
        for(String url:new String[]{"https://evil.com/Ibrahim200546/UniPlan/releases/download/v/a.apk",
            "http://github.com/Ibrahim200546/UniPlan/releases/download/v/a.apk",
            "https://github.com/other/UniPlan/releases/download/v/a.apk",
            "https://github.com@evil.com/Ibrahim200546/UniPlan/releases/download/v/a.apk"})
            assertThrows(Exception.class,()->ReleaseInfo.trustedUrl(url));
    }
    @Test public void rejectsWrongPackageDigestAndSize() throws Exception {
        ReleaseInfo info=ReleaseInfo.parse(release());
        assertThrows(Exception.class,()->info.withManifest(manifest().put("applicationId","kz.uniplan.app.demo")));
        assertThrows(Exception.class,()->info.withManifest(manifest().put("sha256","broken")));
        JSONObject r=release(); r.getJSONArray("assets").getJSONObject(0).put("size",200L*1024*1024);
        assertThrows(Exception.class,()->ReleaseInfo.parse(r).withManifest(manifest()));
    }
}
