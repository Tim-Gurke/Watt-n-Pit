package de.wattpit.app.core;

/** Transport setup observed for both supported device families, without device identifiers. */
public final class ArchiveSession {
    private ArchiveSession() { }
    public static final class Step {
        public final String label;public final byte[] command;public final int reply;
        Step(String label,byte[] command,int reply){this.label=label;this.command=command;this.reply=reply;}
    }
    public static byte[] keepAlive(){return new byte[]{6,0,(byte)0x82,0x18,(byte)0x93,0x42,0x10,0x27};}
    public static Step[] setup(){return new Step[]{
        new Step("Protokoll begrüßen",new byte[]{1},2),
        new Step("Verbindungskontext 0 öffnen",new byte[]{3,0},7),
        new Step("Sitzung wachhalten",keepAlive(),0),
        new Step("Verbindungskontext 1 öffnen",new byte[]{3,1},7),
        new Step("Archivkontext 3 öffnen",new byte[]{3,3},7)
    };}
    public static int priority(String field){
        switch(field){case "solar":return 0;case "voltage":return 1;case "current":return 2;case "soc":return 3;default:return 4;}
    }
    public static String label(String field){
        switch(field){case "solar":return "Solarleistung";case "voltage":return "Batteriespannung";case "current":return "Batteriestrom";case "soc":return "Ladezustand";case "pvVoltage":return "PV-Spannung";case "load":return "Laststrom";case "temperature":return "Temperatur";case "aux":case "auxRaw":return "Zusatzeingang";default:return "Archivkanal";}
    }
    public static boolean accepted(Step step,ArchiveCodec.Message message){
        if(message.code!=step.reply)return false;
        if(step.reply==2)return message.data!=null&&message.data.length==9;
        byte[] b=message.data;
        if(b==null||b.length!=4||b[1]!=0||b[2]!=3||b[3]!=0)throw new IllegalArgumentException("Das Gerät hat die Archivsitzung nicht bestätigt.");
        return true;
    }
}
