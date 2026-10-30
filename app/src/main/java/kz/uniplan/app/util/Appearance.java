package kz.uniplan.app.util;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.WindowCompat;
import androidx.core.widget.CompoundButtonCompat;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputLayout;
import kz.uniplan.app.R;
import kz.uniplan.app.UniPlanApp;

/** Applies saved colors to Views without replacing activities or losing their forms. */
public final class Appearance {
    private Appearance() { }
    public static boolean isNight(Context context) {
        return (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
    }
    public static AppearancePalette palette(Context context) { return UniPlanApp.get(context).prefs().appearance(isNight(context)); }
    public static void applyRoot(View root) {
        root.setTag(R.id.appearance_screen_root,Boolean.TRUE);
        walk(root,palette(root.getContext()),palette(root.getContext()).background,false);
    }
    /** Adapter hook: call after binding. Subject color strips and ImageView tints are preserved. */
    public static void applyCard(MaterialCardView card) { AppearancePalette p=palette(card.getContext());walk(card,p,p.card,false); }
    public static void applyRow(View row) { AppearancePalette p=palette(row.getContext());walk(row,p,p.background,false); }
    public static void applyActivity(Activity activity) {
        View content=activity.findViewById(android.R.id.content);
        if(content==null)return;
        AppearancePalette p=palette(activity);
        View decor=activity.getWindow().getDecorView();
        if(!p.equals(decor.getTag(R.id.appearance_window_palette))){
            decor.setTag(R.id.appearance_window_palette,p);
            activity.getWindow().setBackgroundDrawable(new ColorDrawable(p.background));
            activity.getWindow().setStatusBarColor(p.background);activity.getWindow().setNavigationBarColor(p.background);
            WindowCompat.getInsetsController(activity.getWindow(),decor).setAppearanceLightStatusBars(AppearancePalette.ink(p.background)==Color.BLACK);
            WindowCompat.getInsetsController(activity.getWindow(),decor).setAppearanceLightNavigationBars(AppearancePalette.ink(p.background)==Color.BLACK);
        }
        walk(content,p,p.background,false);
    }
    /** A draft preview is excluded from the global pass until the user presses Apply. */
    public static void preview(View root,AppearancePalette palette) {
        root.setTag(R.id.appearance_preview_root,Boolean.TRUE);
        root.setTag(R.id.appearance_screen_root,Boolean.TRUE);
        walk(root,palette,palette.background,true);
    }
    private static ColorStateList colors(int enabled,int disabled) {
        return new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{disabled,enabled});
    }
    private static ColorStateList checked(int chosen,int normal) {
        return new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{chosen,normal});
    }
    private static final class Applied {
        final AppearancePalette palette;final int surface;
        Applied(AppearancePalette palette,int surface){this.palette=palette;this.surface=surface;}
        boolean same(AppearancePalette other,int background){return surface==background&&palette.equals(other);}
    }
    private static void walk(View view,AppearancePalette p,int inherited,boolean preview) {
        if(!preview&&Boolean.TRUE.equals(view.getTag(R.id.appearance_preview_root)))return;
        int surface=inherited;
        boolean screen=Boolean.TRUE.equals(view.getTag(R.id.appearance_screen_root));
        if(screen)surface=p.background;
        else if(view instanceof MaterialCardView||view instanceof BottomNavigationView)surface=p.card;
        else if(view instanceof Toolbar||view instanceof TabLayout)surface=p.background;
        else if(view.getId()==R.id.demoBanner)surface=AppearancePalette.blend(p.background,p.accent,0.13);
        else if(view instanceof MaterialButton||view instanceof TextInputLayout||view instanceof EditText||view instanceof CompoundButton||view instanceof ProgressBar)surface=inherited;
        else surface=opaqueBackground(view.getBackground(),inherited);
        Object previous=view.getTag(R.id.appearance_palette_tag);
        boolean changed=!(previous instanceof Applied)||!((Applied)previous).same(p,surface);
        int foreground=AppearancePalette.ink(surface),muted=p.mutedOn(surface),accent=p.accentOn(surface);
        if(changed) {
            view.setTag(R.id.appearance_palette_tag,new Applied(p,surface));
            if(screen)view.setBackgroundColor(surface);
            if(view.getId()==R.id.demoBanner)view.setBackgroundColor(surface);
            if(view instanceof MaterialCardView){
                MaterialCardView card=(MaterialCardView)view;card.setCardBackgroundColor(p.card);
                card.setRadius(p.cornerDp*view.getResources().getDisplayMetrics().density);
                card.setStrokeColor(AppearancePalette.blend(p.card,AppearancePalette.ink(p.card),0.18));
            }
            if(view instanceof Toolbar){
                Toolbar toolbar=(Toolbar)view;toolbar.setBackgroundColor(surface);toolbar.setTitleTextColor(foreground);toolbar.setSubtitleTextColor(muted);
            }
            if(view instanceof BottomNavigationView){
                BottomNavigationView nav=(BottomNavigationView)view;nav.setBackgroundTintList(ColorStateList.valueOf(surface));
                int indicator=AppearancePalette.blend(surface,p.accent,0.12);
                nav.setItemIconTintList(checked(p.accentOn(indicator),muted));nav.setItemTextColor(checked(accent,muted));
                nav.setItemActiveIndicatorColor(ColorStateList.valueOf(indicator));
            }
            if(view instanceof TabLayout){
                TabLayout tabs=(TabLayout)view;tabs.setBackgroundColor(surface);tabs.setTabTextColors(muted,accent);tabs.setSelectedTabIndicatorColor(accent);
            }
            if(view instanceof TextInputLayout){
                TextInputLayout input=(TextInputLayout)view;
                if(input.getBoxBackgroundMode()!=TextInputLayout.BOX_BACKGROUND_NONE)input.setBoxBackgroundColor(surface);
                input.setBoxStrokeColorStateList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_focused},new int[]{}},new int[]{accent,muted}));
                input.setDefaultHintTextColor(ColorStateList.valueOf(muted));input.setHintTextColor(ColorStateList.valueOf(accent));
                input.setHelperTextColor(ColorStateList.valueOf(muted));input.setCounterTextColor(ColorStateList.valueOf(muted));
                int error=AppearancePalette.legible(0xffb3261e,surface);input.setErrorTextColor(ColorStateList.valueOf(error));input.setCounterOverflowTextColor(ColorStateList.valueOf(error));
                input.setEndIconTintList(ColorStateList.valueOf(foreground));input.setStartIconTintList(ColorStateList.valueOf(foreground));
            }
            if(view instanceof MaterialButton)styleButton((MaterialButton)view,p,surface);
            else if(view instanceof TextView){
                TextView text=(TextView)view;text.setTextColor(colors(foreground,muted));text.setHintTextColor(muted);
                if(view instanceof EditText){
                    text.setHighlightColor((p.accent&0xffffff)|0x55000000);
                    if(Build.VERSION.SDK_INT>=29&&((EditText)view).getTextCursorDrawable()!=null)((EditText)view).getTextCursorDrawable().mutate().setTint(accent);
                }
            }
            if(view instanceof CompoundButton){
                CompoundButtonCompat.setButtonTintList((CompoundButton)view,checked(accent,muted));
                if(view instanceof MaterialCheckBox)((MaterialCheckBox)view).setButtonIconTintList(ColorStateList.valueOf(AppearancePalette.ink(accent)));
            }
            if(view instanceof ProgressBar){
                ProgressBar progress=(ProgressBar)view;progress.setIndeterminateTintList(ColorStateList.valueOf(accent));progress.setProgressTintList(ColorStateList.valueOf(accent));
                progress.setProgressBackgroundTintList(ColorStateList.valueOf(AppearancePalette.blend(surface,foreground,0.2)));
                if(view instanceof SeekBar)((SeekBar)view).setThumbTintList(ColorStateList.valueOf(accent));
            }
        }
        // NavigationUI may replace the Up icon without changing the toolbar's palette.
        if(view instanceof Toolbar){Toolbar toolbar=(Toolbar)view;if(toolbar.getNavigationIcon()!=null)toolbar.getNavigationIcon().mutate().setTint(foreground);if(toolbar.getOverflowIcon()!=null)toolbar.getOverflowIcon().mutate().setTint(foreground);}
        // These components already manage selected, focused, and error text states.
        if(view instanceof BottomNavigationView||view instanceof TabLayout)return;
        if(view instanceof TextInputLayout){EditText edit=((TextInputLayout)view).getEditText();if(edit!=null)walk(edit,p,surface,preview);return;}
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int index=0;index<group.getChildCount();index++)walk(group.getChildAt(index),p,surface,preview);}
    }
    private static int opaqueBackground(Drawable drawable,int fallback) {
        Integer color=null;
        if(drawable instanceof ColorDrawable)color=((ColorDrawable)drawable).getColor();
        else if(drawable instanceof MaterialShapeDrawable&&((MaterialShapeDrawable)drawable).getFillColor()!=null)color=((MaterialShapeDrawable)drawable).getFillColor().getDefaultColor();
        return color!=null&&Color.alpha(color)==255?color:fallback;
    }
    private static void styleButton(MaterialButton button,AppearancePalette p,int surface){
        Object stored=button.getTag(R.id.appearance_button_kind);
        int kind;
        if(stored instanceof Integer)kind=(Integer)stored;
        else{ColorStateList tint=button.getBackgroundTintList();kind=button.getStrokeWidth()>0?1:tint!=null&&Color.alpha(tint.getDefaultColor())==0?2:0;button.setTag(R.id.appearance_button_kind,kind);}
        int accent=p.accentOn(surface);
        int background=kind==0?p.accent:Color.TRANSPARENT;
        int foreground=kind==0?AppearancePalette.ink(p.accent):accent;
        int disabledBackground=kind==0?AppearancePalette.blend(p.accent,surface,0.55):Color.TRANSPARENT;
        int disabledText=kind==0?AppearancePalette.ink(disabledBackground):p.mutedOn(surface);
        button.setBackgroundTintList(colors(background,disabledBackground));button.setTextColor(colors(foreground,disabledText));
        button.setIconTint(colors(foreground,disabledText));if(kind==1)button.setStrokeColor(colors(accent,p.mutedOn(surface)));
        button.setCornerRadius(Math.round(p.cornerDp*button.getResources().getDisplayMetrics().density));
        button.setRippleColor(ColorStateList.valueOf((foreground&0xffffff)|0x26000000));
    }
}
