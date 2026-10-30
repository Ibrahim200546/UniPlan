package kz.uniplan.app.ui.common;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import java.text.DateFormat;
import java.util.Date;
import java.util.function.Consumer;
import kz.uniplan.app.R;
public final class Ui {
    private Ui() {}
    public static void watch(EditText view,Consumer<String> callback) { view.addTextChangedListener(new TextWatcher() {
        public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
        public void onTextChanged(CharSequence s,int start,int before,int count) { callback.accept(s.toString()); }
        public void afterTextChanged(Editable value) {}
    }); }
    public static String dateTime(long timestamp) { return DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(new Date(timestamp)); }
    public static void go(Fragment fragment,int destination,long id) { Bundle args=new Bundle(); args.putLong("id",id); NavHostFragment.findNavController(fragment).navigate(destination,args); }
    public static String error(Context context,String code) {
        int id=R.string.error_unknown;
        if(code!=null) switch(code) {
            case "validation": id=R.string.error_validation; break;
            case "missing": id=R.string.error_missing; break;
            case "linked": id=R.string.error_linked; break;
            case "session": id=R.string.error_session; break;
            case "io": id=R.string.error_io; break;
        }
        return context.getString(id);
    }
    public static void insets(View view) {
        ViewCompat.setOnApplyWindowInsetsListener(view,(v,insets)->{
            Insets bars=insets.getInsets(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.displayCutout());
            Insets keyboard=insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left,bars.top,bars.right,Math.max(bars.bottom,keyboard.bottom));
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(view);
    }
}
