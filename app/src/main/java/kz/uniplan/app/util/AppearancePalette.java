package kz.uniplan.app.util;

import java.util.Locale;
import java.util.Objects;

/** Immutable device appearance settings; color math is pure Java and unit-testable. */
public final class AppearancePalette {
    public final int background, card, accent, cornerDp;
    public AppearancePalette(int background, int card, int accent, int cornerDp) {
        if (cornerDp < 0 || cornerDp > 32) throw new IllegalArgumentException("Corner radius must be 0..32 dp");
        this.background = background | 0xff000000;
        this.card = card | 0xff000000;
        this.accent = accent | 0xff000000;
        this.cornerDp = cornerDp;
    }
    public static AppearancePalette defaults(boolean night) { return preset(0, night); }
    public static AppearancePalette preset(int index, boolean night) {
        if (night) switch (index) {
            case 1: return new AppearancePalette(0xff101b29,0xff1d2c40,0xffa7c8f8,18);
            case 2: return new AppearancePalette(0xff231b13,0xff34291e,0xffedc27e,18);
            case 3: return new AppearancePalette(0xff201a2b,0xff30263f,0xffd3bafa,18);
            default: return new AppearancePalette(0xff121c17,0xff22342b,0xffaecfbd,18);
        }
        switch (index) {
            case 1: return new AppearancePalette(0xfff2f6fc,0xffe4edfb,0xff315a8b,18);
            case 2: return new AppearancePalette(0xfffbf7ef,0xfff3e8d6,0xff815b29,18);
            case 3: return new AppearancePalette(0xfff8f5fc,0xffeee4f8,0xff75528f,18);
            default: return new AppearancePalette(0xfff8faf7,0xffe5efe7,0xff3d5c52,18);
        }
    }
    public static int parseHex(String input) {
        if (input == null || !input.trim().matches("#?[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Use #RRGGBB");
        String value = input.trim();
        return 0xff000000 | Integer.parseInt(value.startsWith("#") ? value.substring(1) : value,16);
    }
    public static String hex(int color) { return String.format(Locale.ROOT,"#%06X",color & 0xffffff); }
    private static double channel(int channel) { double value=channel/255.0; return value<=0.04045?value/12.92:Math.pow((value+0.055)/1.055,2.4); }
    private static double luminance(int color) { return 0.2126*channel((color>>16)&255)+0.7152*channel((color>>8)&255)+0.0722*channel(color&255); }
    public static double contrast(int first,int second) { double a=luminance(first),b=luminance(second);return (Math.max(a,b)+0.05)/(Math.min(a,b)+0.05); }
    public static int ink(int surface) { return contrast(0xff000000,surface)>=contrast(0xffffffff,surface)?0xff000000:0xffffffff; }
    /** Blend toward a contrasting endpoint only when a chosen accent is unreadable on a surface. */
    public static int legible(int preferred,int surface) {
        if(contrast(preferred,surface)>=4.5)return preferred|0xff000000;
        int endpoint=ink(surface);
        for(int step=1;step<=100;step++){int candidate=blend(preferred,endpoint,step/100.0);if(contrast(candidate,surface)>=4.5)return candidate;}
        return endpoint;
    }
    public static int blend(int first,int second,double amount) {
        double weight=Math.max(0,Math.min(1,amount));
        int r=(int)Math.round(((first>>16)&255)*(1-weight)+((second>>16)&255)*weight);
        int g=(int)Math.round(((first>>8)&255)*(1-weight)+((second>>8)&255)*weight);
        int b=(int)Math.round((first&255)*(1-weight)+(second&255)*weight);
        return 0xff000000|(r<<16)|(g<<8)|b;
    }
    public int accentOn(int surface){return legible(accent,surface);}
    public int mutedOn(int surface){return legible(blend(ink(surface),surface,0.25),surface);}
    @Override public boolean equals(Object other){if(!(other instanceof AppearancePalette))return false;AppearancePalette p=(AppearancePalette)other;return background==p.background&&card==p.card&&accent==p.accent&&cornerDp==p.cornerDp;}
    @Override public int hashCode(){return Objects.hash(background,card,accent,cornerDp);}
}
