package kz.uniplan.app.util;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
public final class SettingsStore {
    private final Context context;
    private final SharedPreferences global;
    public SettingsStore(Context context) { this.context=context.getApplicationContext(); global=this.context.getSharedPreferences("device_settings",Context.MODE_PRIVATE); }
    public int theme() { return global.getInt("theme",0); }
    public String language() { return global.getString("language","kk"); }
    public void setTheme(int value) { global.edit().putInt("theme",value).apply(); apply(); }
    public void setLanguage(String value) { global.edit().putString("language", "ru".equals(value)?"ru":"kk").apply(); apply(); }
    private SharedPreferences account(String uid) { return context.getSharedPreferences("account_"+uid,Context.MODE_PRIVATE); }
    public boolean noteGrid(String uid) { return account(uid).getBoolean("noteGrid",false); }
    public void setNoteGrid(String uid,boolean value) { account(uid).edit().putBoolean("noteGrid",value).apply(); }
    /** Global device appearance; light and dark palettes are independent of each other and accounts. */
    public AppearancePalette appearance(boolean night) {
        AppearancePalette defaults=AppearancePalette.defaults(night); String prefix=appearancePrefix(night);
        return new AppearancePalette(global.getInt(prefix+"background",defaults.background),
                global.getInt(prefix+"card",defaults.card),global.getInt(prefix+"accent",defaults.accent),
                Math.max(0,Math.min(32,global.getInt(prefix+"corner",defaults.cornerDp))));
    }
    public void saveAppearance(boolean night,AppearancePalette palette) {
        if(palette==null)throw new IllegalArgumentException("Palette required");
        String prefix=appearancePrefix(night);
        global.edit().putInt(prefix+"background",palette.background).putInt(prefix+"card",palette.card)
                .putInt(prefix+"accent",palette.accent).putInt(prefix+"corner",palette.cornerDp).apply();
    }
    public void resetAppearance(boolean night) {
        String prefix=appearancePrefix(night);
        global.edit().remove(prefix+"background").remove(prefix+"card").remove(prefix+"accent").remove(prefix+"corner").apply();
    }
    private static String appearancePrefix(boolean night){return night?"appearance_night_":"appearance_day_";}
    public void apply() {
        AppCompatDelegate.setDefaultNightMode(theme()==1 ? AppCompatDelegate.MODE_NIGHT_NO : theme()==2 ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        LocaleListCompat locale=LocaleListCompat.forLanguageTags(language());
        if(!AppCompatDelegate.getApplicationLocales().equals(locale)) AppCompatDelegate.setApplicationLocales(locale);
    }
}
