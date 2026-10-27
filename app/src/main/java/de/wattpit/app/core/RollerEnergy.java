package de.wattpit.app.core;

/** Read-only BMS energy integration. Missing time never becomes measured energy. */
public final class RollerEnergy {
    public static final long MAX_GAP_MS=10000;
    private RollerEnergy(){}
    public static double inputWatts(double output,double idle,double efficiency){
        if(!Double.isFinite(output)||output<0||!Double.isFinite(idle)||idle<0||!Double.isFinite(efficiency)||efficiency<=0||efficiency>1)throw new IllegalArgumentException("Ungültiges Ladeprofil");
        return output>0?idle+output/efficiency:0;
    }
    /** A charging MOS being enabled is not a charge: require fresh, positive measured power. */
    public static final class AutoCharge {
        public static final double START_WATTS=3,STOP_WATTS=1;
        public static final long START_MS=6000,STOP_MS=60000;
        public long startAt;
        public double startPower;
        public boolean suppressed;
        private long lastAt,quietAt;
        public void interrupt(){lastAt=quietAt=startAt=0;}
        public void suppress(){suppressed=true;interrupt();}
        /** 1 = confirmed start, -1 = confirmed end; an absent/old packet confirms neither. */
        public int sample(long now,double watts,boolean active){
            if(now<=0||!Double.isFinite(watts)||lastAt>0&&now<=lastAt)return 0;
            if(lastAt>0&&now-lastAt>MAX_GAP_MS){quietAt=startAt=0;}
            lastAt=now;
            if(active||suppressed){
                startAt=0;
                if(watts>STOP_WATTS){quietAt=0;return 0;}
                if(quietAt==0)quietAt=now;
                if(now-quietAt<STOP_MS)return 0;
                suppressed=false;quietAt=0;return active?-1:0;
            }
            quietAt=0;
            if(watts<START_WATTS){startAt=0;return 0;}
            if(startAt==0){startAt=now;startPower=watts;}
            return now-startAt>=START_MS?1:0;
        }
    }
    public static final class Session {
        public long started,ended,lastAt,observedMillis,gapMillis;
        public double batteryWh,inputWh,lastPower;
        public boolean active;
        private long gapFrom;
        public void start(long now){started=now;ended=lastAt=observedMillis=gapMillis=0;batteryWh=inputWh=lastPower=0;active=true;gapFrom=now;}
        public void sample(long now,double watts,double idle,double efficiency){
            if(!active||now<started||!Double.isFinite(watts)||lastAt==0&&now<gapFrom)return;
            double p=Math.max(0,watts);
            if(lastAt>0){long dt=now-lastAt;if(dt<=0)return;
                if(dt<=MAX_GAP_MS){batteryWh+=(lastPower+p)*.5*dt/3600000.0;
                    inputWh+=(inputWatts(lastPower,idle,efficiency)+inputWatts(p,idle,efficiency))*.5*dt/3600000.0;observedMillis+=dt;
                }else gapMillis+=dt;
            }else gapMillis+=now-gapFrom;
            lastAt=now;lastPower=p;
        }
        public void stop(long now){if(!active)return;gapMillis+=Math.max(0,now-(lastAt>0?lastAt:gapFrom));ended=now;active=false;lastAt=0;}
        public long pendingGap(long now){return active?Math.max(0,now-(lastAt>0?lastAt:gapFrom)):0;}
        public void interrupt(long now){if(active&&lastAt>0){gapMillis+=Math.max(0,now-lastAt);lastAt=0;gapFrom=now;}}
    }
}
