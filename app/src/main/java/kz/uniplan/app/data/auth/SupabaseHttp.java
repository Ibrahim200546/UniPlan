package kz.uniplan.app.data.auth;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Small HTTPS transport. Never logs headers, credentials, or response payloads. */
public final class SupabaseHttp {
    public interface Transport { Response request(String method,String path,JSONObject payload,String bearer) throws IOException; }
    public static final class Response {
        public final int status;
        public final String body;
        public final JSONObject json;
        public Response(int status,String body) {
            this.status=status;this.body=body==null?"":body;
            JSONObject object;try{object=this.body.trim().startsWith("{")?new JSONObject(this.body):new JSONObject();}catch(JSONException failure){object=new JSONObject();}
            this.json=object;
        }
        public boolean successful(){return status>=200&&status<300;}
        public JSONArray array() throws JSONException{return new JSONArray(body);}
    }
    private final String baseUrl;
    private final String publishableKey;
    public SupabaseHttp(String baseUrl,String publishableKey){
        this.baseUrl=baseUrl==null?"":baseUrl.replaceAll("/+$","");this.publishableKey=publishableKey==null?"":publishableKey;
    }
    public boolean configured() {
        try {
            java.net.URI uri=new java.net.URI(baseUrl);
            return "https".equals(uri.getScheme()) && uri.getHost()!=null && uri.getRawUserInfo()==null
                    && (uri.getPath()==null || uri.getPath().isEmpty()) && uri.getQuery()==null && uri.getFragment()==null
                    && publishableKey.matches("sb_publishable_[A-Za-z0-9_-]+");
        } catch(Exception invalid) { return false; }
    }
    public Response request(String method,String path,JSONObject payload,String bearer) throws IOException {
        if(!configured()||path==null||!path.startsWith("/")||path.startsWith("//"))throw new IOException("Supabase configuration unavailable");
        URL url=new URL(baseUrl+path);
        if(url.getUserInfo()!=null)throw new IOException("Invalid API URL");
        HttpURLConnection connection=(HttpURLConnection)url.openConnection();
        connection.setConnectTimeout(20000);connection.setReadTimeout(20000);connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod(method);connection.setRequestProperty("apikey",publishableKey);connection.setRequestProperty("Accept","application/json");
        if(bearer!=null&&!bearer.isEmpty())connection.setRequestProperty("Authorization","Bearer "+bearer);
        try {
            if(payload!=null){byte[] bytes=payload.toString().getBytes(StandardCharsets.UTF_8);connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json; charset=utf-8");connection.setFixedLengthStreamingMode(bytes.length);try(OutputStream output=connection.getOutputStream()){output.write(bytes);}}
            int status=connection.getResponseCode();InputStream stream=status>=400?connection.getErrorStream():connection.getInputStream();
            if(stream==null)return new Response(status,"");
            try(InputStream input=stream;ByteArrayOutputStream output=new ByteArrayOutputStream()){
                byte[] buffer=new byte[8192];int count;
                while((count=input.read(buffer))!=-1){if(output.size()+count>8*1024*1024)throw new IOException("API response exceeds size limit");output.write(buffer,0,count);}
                return new Response(status,new String(output.toByteArray(),StandardCharsets.UTF_8));
            }
        }finally{connection.disconnect();}
    }
}
