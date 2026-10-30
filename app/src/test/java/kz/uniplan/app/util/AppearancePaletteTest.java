package kz.uniplan.app.util;

import static org.junit.Assert.*;
import org.junit.Test;

public class AppearancePaletteTest {
    @Test public void arbitraryBackgroundsAlwaysReceiveReadableText(){
        for(int r=0;r<=255;r+=17)for(int g=0;g<=255;g+=17)for(int b=0;b<=255;b+=17){
            int background=0xff000000|(r<<16)|(g<<8)|b;
            assertTrue(AppearancePalette.contrast(AppearancePalette.ink(background),background)>=4.5);
        }
    }
    @Test public void accentsAreAdjustedOnlyWhenNeededForContrast(){
        int[] colors={0xff000000,0xffffffff,0xff808080,0xffffff00,0xff0044ff,0xffff00ff,0xff00ff00};
        for(int surface:colors)for(int accent:colors){
            int actual=AppearancePalette.legible(accent,surface);
            assertTrue(AppearancePalette.contrast(actual,surface)>=4.5);
            if(AppearancePalette.contrast(accent,surface)>=4.5)assertEquals(accent,actual);
        }
    }
    @Test public void presetsWorkInBothThemesAndColorsRemainIndependent(){
        for(boolean night:new boolean[]{false,true})for(int index=0;index<4;index++){
            AppearancePalette p=AppearancePalette.preset(index,night);
            assertTrue(AppearancePalette.contrast(p.accentOn(p.background),p.background)>=4.5);
            assertTrue(AppearancePalette.contrast(p.accentOn(p.card),p.card)>=4.5);
            assertTrue(AppearancePalette.contrast(p.mutedOn(p.card),p.card)>=4.5);
            AppearancePalette changed=new AppearancePalette(0xff123456,p.card,p.accent,32);
            assertEquals(p.card,changed.card);assertEquals(p.accent,changed.accent);assertEquals(32,changed.cornerDp);
        }
    }
    @Test public void hexRequiresExactlySixRgbDigitsAndNormalizesOutput(){
        assertEquals(0xffabcdef,AppearancePalette.parseHex(" #aBcDeF "));
        assertEquals("#00FF0A",AppearancePalette.hex(AppearancePalette.parseHex("00ff0a")));
        for(String value:new String[]{"","#123","#00112233","red","#GG0000","#12 3456"}){
            try{AppearancePalette.parseHex(value);fail("Accepted invalid HEX "+value);}catch(IllegalArgumentException expected){}
        }
    }
    @Test public void invalidCornerRadiusCannotEnterSavedPalette(){
        for(int invalid:new int[]{-1,33,100})try{new AppearancePalette(0,0,0,invalid);fail("Invalid radius accepted");}catch(IllegalArgumentException expected){}
    }
}
