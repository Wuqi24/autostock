package dev.autostock.core;
public record RunOptions(int scanRange,int pickupInterval,int moveSpeed,boolean autoPath,boolean dodgeMobs,boolean silent,boolean loop,boolean autoTrigger,boolean quickShulker){
 public RunOptions(int scanRange,int pickupInterval,int moveSpeed,boolean autoPath,boolean dodgeMobs,boolean silent,boolean loop,boolean autoTrigger){this(scanRange,pickupInterval,moveSpeed,autoPath,dodgeMobs,silent,loop,autoTrigger,true);}
 public static RunOptions defaults(){return new RunOptions(64,1,100,true,false,false,false,false);}
 public RunOptions {if(scanRange<8||scanRange>64||pickupInterval<1||pickupInterval>1200||moveSpeed<50||moveSpeed>200)throw new IllegalArgumentException("扫描范围 8–64 格，取货间隔 1–1200 刻，移动速度 50%–200%");}
}