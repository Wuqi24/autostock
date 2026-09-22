package dev.autostock.core;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class RunOptionsTest {
 @Test void rejectsUnboundedClientSettings(){assertThrows(IllegalArgumentException.class,()->new RunOptions(65,10,100,true,true,false,false,false));assertThrows(IllegalArgumentException.class,()->new RunOptions(32,0,100,true,true,false,false,false));assertThrows(IllegalArgumentException.class,()->new RunOptions(32,10,201,true,true,false,false,false));}
 @Test void acceptsLimits(){assertEquals(8,new RunOptions(8,1,50,false,false,true,true,true).scanRange());assertEquals(1200,new RunOptions(64,1200,200,true,true,false,true,true).pickupInterval());}
}