package com.zachminecraft.wwoocontent;

/** Tests the timed transition packaged in the release JAR without Minecraft boot. */
public final class CloudBlendCheck {
    private static final int WHITE=0xccffffff, PALE=0x66828282, PURPLE=0x66888899;
    private static void require(boolean value,String message) {
        if (!value) throw new AssertionError(message);
    }
    private static void near(double actual,double expected,String message) {
        require(Math.abs(actual-expected)<1e-12,message+": "+actual+" != "+expected);
    }
    private static void same(CloudBlend.Color a,CloudBlend.Color b,String message) {
        near(a.red(),b.red(),message+" red");near(a.green(),b.green(),message+" green");
        near(a.blue(),b.blue(),message+" blue");near(a.alpha(),b.alpha(),message+" alpha");
    }
    public static void main(String[] args) {
        long start=10_000_000_000L;
        var white=CloudBlend.Color.fromArgb(WHITE);var pale=CloudBlend.Color.fromArgb(PALE);
        var blend=new CloudBlend();
        same(blend.update(WHITE,start),white,"initial palette exact");
        same(blend.update(PALE,start),white,"entry begins without a snap");
        var half=blend.update(PALE,start+CloudBlend.DURATION_NANOS/2);
        near(half.red(),(white.red()+pale.red())/2,"mid-fade RGB independent of opacity");
        near(half.alpha(),.6,"mid-fade opacity");
        same(blend.update(PALE,start+CloudBlend.DURATION_NANOS),pale,"stationary fade reaches exact destination");
        same(blend.update(PALE,start+10*CloudBlend.DURATION_NANOS),pale,"destination stays stable");

        // Reading every frame (or many times per frame) must not restart or accelerate it.
        for(int fps:new int[]{20,30,60,144,240}) {
            var timed=new CloudBlend();timed.update(WHITE,start);timed.update(PALE,start);
            double previous=1;
            for(int frame=0;frame<fps*2;frame++) {
                long now=start+(long)(frame*1_000_000_000.0/fps);
                var current=timed.update(PALE,now);
                same(timed.update(PALE,now),current,"same-frame reads coherent");
                require(current.red()<=previous+1e-12,"tint progresses toward destination");previous=current.red();
            }
            same(timed.update(PALE,start+CloudBlend.DURATION_NANOS),pale,"same duration at every frame rate");
        }
        var redirect=new CloudBlend();redirect.update(WHITE,start);redirect.update(PALE,start);
        var visible=redirect.update(PALE,start+750_000_000L);
        same(redirect.update(PURPLE,start+750_000_000L),visible,"retarget from currently visible color");
        same(redirect.update(PURPLE,start+750_000_000L+CloudBlend.DURATION_NANOS),CloudBlend.Color.fromArgb(PURPLE),"retarget completion");
        var reverse=new CloudBlend();reverse.update(WHITE,start);reverse.update(PALE,start);
        var turning=reverse.update(PALE,start+1_000_000_000L);
        same(reverse.update(WHITE,start+1_000_000_000L),turning,"reverse crossing has no snap");
        same(reverse.update(WHITE,start+3_000_000_000L),white,"reverse reaches original palette");
        reverse.reset();same(reverse.update(PALE,start+4_000_000_000L),pale,"new world has no old tint");
        var transparent=new CloudBlend();transparent.update(WHITE,start);transparent.update(0x00ffffff,start);
        near(transparent.update(0x00ffffff,start+CloudBlend.DURATION_NANOS).alpha(),0,"exact transparent destination");
        System.out.println("CLOUD_TRANSITION_PASS: biome-entry fade, stationary completion, retarget/reversal, frame-rate independence, exact palettes and reset");
    }
}
