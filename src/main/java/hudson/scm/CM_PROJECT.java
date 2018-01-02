/*******************************************************************************
 * Contributors: PTC 2016
 *******************************************************************************/
package hudson.scm;

public enum CM_PROJECT
{
  ID {
    @Override public String toString()
    {
      return "ID";
    }
  },
  TYPE {
    @Override public String toString()
    {
      return "TYPE";
    }
  },
  NAME {
    @Override public String toString()
    {
      return "NAME";
    }
  },
  MEMBER_ID {
    @Override public String toString()
    {
      return "MEMBER_ID";
    }
  },
  TIMESTAMP {
    @Override public String toString()
    {
      return "TIMESTAMP";
    }
  },
  DESCRIPTION {
    @Override public String toString()
    {
      return "DESCRIPTION";
    }
  },
  AUTHOR {
    @Override public String toString()
    {
      return "AUTHOR";
    }
  },
  CONFIG_PATH {
    @Override public String toString()
    {
      return "CONFIG_PATH";
    }
  },
  REVISION {
    @Override public String toString()
    {
      return "REVISION";
    }
  },
  OLD_REVISION {
    @Override public String toString()
    {
      return "OLD_REVISION";
    }
  },
  RELATIVE_FILE {
    @Override public String toString()
    {
      return "RELATIVEFILE";
    }
  },
  CHECKSUM {
    @Override public String toString()
    {
      return "CHECKSUM";
    }
  },
  DELTA {
    @Override public String toString()
    {
      return "DELTA";
    }
  },
  CPID {
    @Override public String toString()
    {
      return "CPID";
    }
  },
  CP_STATE {
    @Override public String toString()
    {
      return "CP_STATE";
    }
  },
  UNDEFINED {
    @Override public String toString()
    {
      return "UNDEFINED";
    }
  }
}
