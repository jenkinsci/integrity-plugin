package hudson.scm;

public enum CM_PROJECT 
{
	ID { public String toString(){ return "ID"; } },		
	TYPE { public String toString(){ return "TYPE"; } },		
	NAME { public String toString(){ return "NAME"; } },
	MEMBER_ID { public String toString(){ return "MEMBER_ID"; } },
	TIMESTAMP { public String toString(){ return "TIMESTAMP"; } },
	DESCRIPTION { public String toString(){ return "DESCRIPTION"; } },
	AUTHOR { public String toString(){ return "AUTHOR"; } },
	CONFIG_PATH { public String toString(){ return "CONFIG_PATH"; } },
	REVISION { public String toString(){ return "REVISION"; } },
	OLD_REVISION { public String toString(){ return "OLD_REVISION"; } },
	RELATIVE_FILE { public String toString(){ return "RELATIVEFILE"; } },
	CHECKSUM { public String toString(){ return "CHECKSUM"; } },
	DELTA { public String toString(){ return "DELTA"; } },
	UNDEFINED { public String toString(){ return "UNDEFINED"; } }
}
