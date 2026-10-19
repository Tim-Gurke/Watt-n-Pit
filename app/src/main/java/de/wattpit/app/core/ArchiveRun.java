package de.wattpit.app.core;

/** A choice belongs to one complete device queue, never to portable settings. */
public final class ArchiveRun {
    public static final class State {
        public final long id,startedAt;
        public final boolean busy,background;
        private State(long id,long startedAt,boolean busy,boolean background){this.id=id;this.startedAt=startedAt;this.busy=busy;this.background=background;}
    }
    private volatile State state=new State(0,0,false,false);
    public State state(){return state;}
    public synchronized void begin(long now){if(!state.busy)state=new State(state.id+1,now,true,false);}
    public synchronized boolean background(long id){if(!state.busy||state.id!=id)return false;state=new State(id,state.startedAt,true,true);return true;}
    public synchronized void finish(){state=new State(state.id,state.startedAt,false,false);}
}
