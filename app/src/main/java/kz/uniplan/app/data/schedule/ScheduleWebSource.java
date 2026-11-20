package kz.uniplan.app.data.schedule;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import kz.uniplan.app.domain.schedule.ScheduleImport;
import kz.uniplan.app.util.Validation;

/** Fetches only the page the user requests. No browser or app credentials are attached. */
public final class ScheduleWebSource {
    private static final int MAX_BYTES=2*1024*1024;
    public static final class SourceException extends IOException {
        public final String code;
        SourceException(String code){super(code);this.code=code;}
    }
    public String load(String address) throws IOException {
        URL url=checked(address.trim());
        for(int redirects=0;redirects<=5;redirects++) {
            HttpURLConnection connection=(HttpURLConnection)url.openConnection();
            connection.setConnectTimeout(12000);connection.setReadTimeout(12000);connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent","UniPlan/1.3 (schedule import)");
            connection.setRequestProperty("Accept","text/html, text/plain;q=0.9");
            try {
                int status=connection.getResponseCode();
                if(status>=300&&status<=399){String location=connection.getHeaderField("Location");if(location==null)throw new SourceException("network");url=checked(new URL(url,location).toString());continue;}
                if(status==401||status==403)throw new SourceException("login");
                if(status!=200)throw new SourceException("network");
                String type=connection.getContentType();
                if(type!=null&&!type.toLowerCase(Locale.ROOT).contains("html")&&!type.toLowerCase(Locale.ROOT).startsWith("text/"))throw new SourceException("format");
                if(connection.getContentLengthLong()>MAX_BYTES)throw new SourceException("limit");
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                try(InputStream stream=connection.getInputStream()){
                    byte[] chunk=new byte[8192];int size;
                    while((size=stream.read(chunk))!=-1){if(Thread.currentThread().isInterrupted())throw new IOException("Cancelled");if(bytes.size()+size>MAX_BYTES)throw new SourceException("limit");bytes.write(chunk,0,size);}
                }
                Document doc=Jsoup.parse(new ByteArrayInputStream(bytes.toByteArray()),null,url.toString());
                if(doc.select("table").isEmpty()&&!doc.select("input[type=password]").isEmpty())throw new SourceException("login");
                // Keep the draft small; preserve absolute links before dropping the surrounding page.
                for(org.jsoup.nodes.Element link:doc.select("a[href]"))link.attr("href",link.absUrl("href"));
                String result=doc.select("table").isEmpty()?doc.body().wholeText():doc.select("table").outerHtml();
                if(result.length()>ScheduleImport.MAX_INPUT)throw new SourceException("limit");
                return result;
            } finally {connection.disconnect();}
        }
        throw new SourceException("network");
    }
    private static URL checked(String address) throws IOException {
        if(!Validation.webUrl(address)||!address.toLowerCase(Locale.ROOT).startsWith("https://"))throw new SourceException("url");
        return new URL(address);
    }
}
