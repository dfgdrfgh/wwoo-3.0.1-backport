package com.zachminecraft.wwoocontent;

/** Tests the sampler packaged in the release JAR, independently of Minecraft boot. */
public final class CloudBlendCheck {
    private static final CloudBlend.BiomeColors BORDER = (qx,qz) -> qx < 0 ? 0xccff0000 : 0xcc0000ff;
    private static void require(boolean value,String message) {
        if (!value) throw new AssertionError(message);
    }
    private static void near(double actual,double expected,String message) {
        require(Math.abs(actual-expected)<1e-12,message+": "+actual+" != "+expected);
    }
    public static void main(String[] args) {
        for (int argb : new int[]{0xccffffff,0x66828282,0x11ffffff,0x66888899,0xff123456}) {
            var expected=CloudBlend.Color.fromArgb(argb);
            for(double x:new double[]{-30000000.25,-15.99,-4.01,-0.001,0,0.001,4,15.99,30000000.25}) {
                var actual=CloudBlend.sample(x,x*.25,(qx,qz)->argb);
                near(actual.red(),expected.red(),"constant red");near(actual.green(),expected.green(),"constant green");
                near(actual.blue(),expected.blue(),"constant blue");near(actual.alpha(),expected.alpha(),"constant alpha");
            }
        }
        var left=CloudBlend.sample(-16,0,BORDER);near(left.red(),1,"inside old biome");near(left.blue(),0,"no distant bleed");
        var center=CloudBlend.sample(0,0,BORDER);near(center.red(),.5,"boundary old contribution");near(center.blue(),.5,"boundary new contribution");
        var right=CloudBlend.sample(16,0,BORDER);near(right.red(),0,"fully inside new biome");near(right.blue(),1,"new biome reached");
        var previous=CloudBlend.sample(-20,0,BORDER);
        for(int step=1;step<=640;step++) {
            double x=-20+step/16.0;
            var current=CloudBlend.sample(x,0,BORDER);
            require(current.blue()+1e-12>=previous.blue(),"new color increases with depth");
            require(Math.abs(current.blue()-previous.blue())<.01,"no discontinuity while walking");
            near(current.alpha(),.8,"constant opacity across boundary");
            previous=current;
        }
        // Sample-window changes and negative quart/chunk boundaries stay continuous.
        for(int boundary=-10;boundary<=10;boundary++) {
            double x=boundary*4.0+2;
            var a=CloudBlend.sample(x-1e-7,-x-1e-7,(qx,qz)->qx+qz<0?0x22888899:0xccffffff);
            var b=CloudBlend.sample(x+1e-7,-x+1e-7,(qx,qz)->qx+qz<0?0x22888899:0xccffffff);
            require(Math.abs(a.red()-b.red())<1e-7 && Math.abs(a.alpha()-b.alpha())<1e-7,"continuous diagonal/grid boundary");
        }
        var transparent=CloudBlend.sample(0,0,(qx,qz)->qx<0?0xccffffff:0x00000000);
        near(transparent.red(),1,"no dark fringe from transparent neighbors");near(transparent.alpha(),.4,"opacity blends too");
        near(CloudBlend.sample(0,0,(qx,qz)->0).alpha(),0,"fully transparent biome");
        for(double x:new double[]{-17,-16.5,-.01,0,1,2,3,15.5,16}) {
            int[] calls={0};
            CloudBlend.sample(x,x,(qx,qz)-> {
                require(Math.abs(qx*4.0+2-x)<=16 && Math.abs(qz*4.0+2-x)<=16,"bounded sampling window");
                calls[0]++;return 0xccffffff;
            });
            require(calls[0]>0 && calls[0]<=64,"at most 64 biome samples per cached result");
        }
        require(CloudBlend.sample(3.25,-.5,BORDER).equals(CloudBlend.sample(3.25,-.5,BORDER)),"position-driven result independent of timing");
        System.out.println("CLOUD_BLEND_PASS: continuous spatial RGB/opacity, biome interiors, diagonal/negative boundaries, transparency, and bounded sampling");
    }
}
