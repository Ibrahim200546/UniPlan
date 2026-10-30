package kz.uniplan.app.ui.schedule;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import kz.uniplan.app.R;
import kz.uniplan.app.util.Validation;

/** Links from timetable records are opened only on an explicit button tap. */
public final class LessonLinks {
    private LessonLinks() { }
    public static boolean valid(String value) {
        if (value == null || value.trim().isEmpty()) return false;
        return Validation.webUrl(value.trim());
    }
    public static void open(Context context, String value) {
        if (!valid(value)) { Toast.makeText(context,R.string.lesson_invalid_link,Toast.LENGTH_LONG).show(); return; }
        try { context.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(value.trim()))); }
        catch (ActivityNotFoundException unavailable) { Toast.makeText(context,R.string.lesson_no_browser,Toast.LENGTH_LONG).show(); }
    }
}
