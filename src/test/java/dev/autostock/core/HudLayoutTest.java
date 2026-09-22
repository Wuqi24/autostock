package dev.autostock.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HudLayoutTest {
    @Test void bothPanelsUseTheSameFixedHundredPixelWidth() {
        float[] defaults={-1,-1,-1,-1};
        var region=HudLayout.panelBounds(0,320,180,defaults);
        var task=HudLayout.panelBounds(1,320,180,defaults);
        assertEquals(100,region.width());
        assertEquals(region.width(),task.width());
        assertEquals(48,region.height());
        assertEquals(70,task.height());
    }
    @Test void longContentIsEllipsizedWithoutChangingPanelWidth() {
        String material="这是一个会远远超过任务 HUD 可用宽度的本地化材料名称";
        String binding="LEFT_CONTROL + LEFT_SHIFT + LEFT_ALT + R + T + Y + U + I";
        assertEquals(100,HudLayout.panelBounds(1,320,180,new float[]{-1,-1,-1,-1}).width());
        assertTrue(HudLayout.ellipsize(material,12,String::length).length()<=12);
        assertTrue(HudLayout.ellipsize(HudLayout.compactKeyLabel(binding),18,String::length).length()<=18);
        assertTrue(HudLayout.ellipsize(material,12,String::length).endsWith("…"));
    }
    @Test void formatsCompactCoordinatesAndTaskStates() {
        assertEquals("12,64,-325",HudLayout.coordinates(12,64,-325));
        assertEquals("运行中",HudLayout.taskStateLabel("RUNNING"));
        assertEquals("暂停",HudLayout.taskStateLabel("PAUSED"));
    }
    @Test void smallScreensClampBothPanelsInsideTheViewport() {
        float[] outside={1,1,1,1};
        for(int i=0;i<2;i++){
            var panel=HudLayout.panelBounds(i,120,80,outside);
            assertTrue(panel.x()>=0&&panel.y()>=0);
            assertTrue(panel.x()+panel.width()<=120);
            assertTrue(panel.y()+panel.height()<=80);
        }
    }
    @Test void keyLabelsUseShortModifierNames() {
        assertEquals("Alt+滚轮",HudLayout.compactKeyLabel("LEFT_ALT + 滚轮"));
        assertEquals("Ctrl+Shift+R",HudLayout.compactKeyLabel("RIGHT_CONTROL + LEFT_SHIFT + R"));
        assertEquals("Alt+滚轮 · R保存 · N关闭",HudLayout.regionHint("LEFT_ALT + 滚轮","R","N"));
    }
    @Test void screenEdgesSnapAtEightPixelsButNotNine() {
        var near=HudLayout.snap(new HudLayout.Rect(8,100,200,112),null,800,600,8);
        assertEquals(0,near.rect().x()); assertEquals(0,near.vertical()); assertTrue(near.screenX());
        var far=HudLayout.snap(new HudLayout.Rect(9,100,200,112),null,800,600,8);
        assertEquals(9,far.rect().x()); assertNull(far.vertical());
    }
    @Test void alignsOtherPanelEdgeAndCentre() {
        var other=new HudLayout.Rect(100,100,200,112);
        var edge=HudLayout.snap(new HudLayout.Rect(307,250,230,112),other,800,600,8);
        assertEquals(300,edge.rect().x());assertFalse(edge.screenX());
        var centre=HudLayout.snap(new HudLayout.Rect(88,250,230,112),other,800,600,8);
        assertEquals(85,centre.rect().x());assertEquals(200,centre.vertical());
    }
    @Test void offscreenDragClampsAndClearsMisleadingGuide() {
        var result=HudLayout.snap(new HudLayout.Rect(-400,900,200,112),null,427,239,8);
        assertEquals(new HudLayout.Rect(0,127,200,112),result.rect());
        assertNull(result.vertical());assertNull(result.horizontal());
    }
}
