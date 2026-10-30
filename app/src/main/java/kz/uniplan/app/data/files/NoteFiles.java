package kz.uniplan.app.data.files;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import kz.uniplan.app.data.auth.AuthRepository;
import kz.uniplan.app.data.auth.AuthRepository.Session;
import kz.uniplan.app.data.local.AppDatabase;
import kz.uniplan.app.data.repository.StudyRepository.Result;

/** Files are explicit copies, never an alternative source of saved note records. */
public final class NoteFiles {
    private interface Operation<T> { T run(String uid) throws IOException; }
    private final Context context;
    private final AuthRepository auth;
    private final AppDatabase db;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    public NoteFiles(Context context,AuthRepository auth) { this(context,auth,AppDatabase.get(context)); }
    public NoteFiles(Context context,AuthRepository auth,AppDatabase db) {
        this.context=context.getApplicationContext();this.auth=auth;this.db=db;
    }
    private <T> void execute(String expectedOwner,Operation<T> action,Result<T> callback) {
        Session captured=auth.current();
        if(captured==null || (expectedOwner!=null && !captured.uid.equals(expectedOwner))) {
            main.post(() -> callback.done(null,"session"));return;
        }
        worker.execute(() -> {
            if(auth.current()!=captured) { main.post(() -> callback.done(null,"session"));return; }
            T value=null;String error=null;
            try { value=action.run(captured.uid); }
            catch(IOException|SecurityException failure) { error="io"; }
            catch(RuntimeException failure) { error="unknown"; }
            T result=value;String resultError=error;
            main.post(() -> {
                if(auth.current()!=captured) callback.done(null,"session");
                else callback.done(result,resultError);
            });
        });
    }
    /** Hashing prevents UID characters from becoming path syntax. It is not encryption. */
    public static String ownerDirectory(String uid) {
        try {
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(uid.getBytes(StandardCharsets.UTF_8));
            StringBuilder result=new StringBuilder();
            for(byte value:digest) result.append(String.format(java.util.Locale.ROOT,"%02x",value & 255));
            return result.toString();
        } catch(NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private File copyFile(String uid,long noteId,boolean external,boolean writing) throws IOException {
        if(noteId<=0 || db.studyDao().note(uid,noteId)==null) throw new IOException("Note unavailable");
        File root;
        if(external) {
            root=context.getExternalFilesDir(null);
            if(root==null) throw new IOException("External directory unavailable");
            String state=Environment.getExternalStorageState(root);
            if(!Environment.MEDIA_MOUNTED.equals(state) &&
                    (writing || !Environment.MEDIA_MOUNTED_READ_ONLY.equals(state))) throw new IOException("External storage unavailable");
        } else root=context.getFilesDir();
        File directory=new File(new File(root,"note_copies"),ownerDirectory(uid));
        if(writing && !directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create copy directory");
        return new File(directory,"note_"+noteId+".txt");
    }
    public void save(long noteId,String text,boolean external,Result<Boolean> callback) {
        execute(null,uid -> {
            AtomicFile file=new AtomicFile(copyFile(uid,noteId,external,true));
            FileOutputStream output=null;
            try {
                output=file.startWrite();
                output.write((text==null?"":text).getBytes(StandardCharsets.UTF_8));
                file.finishWrite(output);
                return true;
            } catch(IOException failure) {
                if(output!=null) file.failWrite(output);
                throw failure;
            }
        },callback);
    }
    public void read(long noteId,boolean external,Result<String> callback) {
        execute(null,uid -> {
            AtomicFile file=new AtomicFile(copyFile(uid,noteId,external,false));
            try(InputStream input=file.openRead(); Reader reader=new InputStreamReader(input,StandardCharsets.UTF_8)) {
                StringBuilder result=new StringBuilder();char[] buffer=new char[4096];int count;
                while((count=reader.read(buffer))!=-1) result.append(buffer,0,count);
                return result.toString();
            }
        },callback);
    }
    public void export(Uri uri,String text,Result<Boolean> callback) { export(uri,text,null,callback); }
    public void export(Uri uri,String text,String expectedOwnerUid,Result<Boolean> callback) {
        if(uri==null) { main.post(() -> callback.done(false,null));return; }
        execute(expectedOwnerUid,uid -> {
            try(OutputStream output=context.getContentResolver().openOutputStream(uri,"wt")) {
                if(output==null) throw new IOException("Document provider did not return a stream");
                output.write((text==null?"":text).getBytes(StandardCharsets.UTF_8));
                output.flush();
            }
            return true;
        },callback);
    }
}
