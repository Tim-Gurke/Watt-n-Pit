package de.wattpit.app.core;

/** Read-only BMS energy integration. Missing time never becomes measured energy. */
public final class RollerEnergy {
    public static final long MAX_GAP_MS=10000;
    private RollerEnergy(){}
    public static double inputWatts(double output,double idle,double efficiency){
        if(!Double.isFinite(output)||output<0||!Double.isFinite(idle)||idle<0||!Double.isFinite(efficiency)||efficiency<=0||efficiency>1)throw new IllegalArgumentException("Ungültiges Ladeprofil");
        return output>0?idle+output/efficiency:0;
    }
    public static final class Session {
        public long started,ended,lastAt,observedMillis,gapMillis;
        public double batteryWh,inputWh,lastPower;
        public boolean active;
        private long gapFrom;
        public void start(long now){started=now;ended=lastAt=observedMillis=gapMillis=0;batteryWh=inputWh=lastPower=0;active=true;gapFrom=now;}
        public void sample(long now,double watts,double idle,double efficiency){
            if(!active||now<started||!Double.isFinite(watts))return;
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
