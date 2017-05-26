package com.axios.ccdp.message;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.jms.TextMessage;

import org.apache.log4j.Logger;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.ObjectMapper;

@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class CcdpMessage implements Cloneable
{
  public static final String MSG_TYPE_FLD = "msg-type";
  
  /**
   * Generates debug print statements based on the verbosity level.
   */
  protected static Logger logger = 
      Logger.getLogger(CcdpMessage.class.getName());

  protected static ObjectMapper mapper =  new ObjectMapper();
  protected Map<String, String> config = new HashMap<>();
  protected String replyTo = null;
  
  public static void buildMessage(CcdpMessage msg, TextMessage txtMsg) 
      throws CcdpMessageException
  {
    try
    {
      txtMsg.setIntProperty(MSG_TYPE_FLD, msg.getMessageType() );
      String json = mapper.writeValueAsString(msg);
      txtMsg.setText(json);
    }
    catch( Exception e )
    {
      throw new CcdpMessageException(e);
    }
  }
  
  public static <T extends CcdpMessage> T buildObject( TextMessage txtMsg, 
                                  Class<T> clazz) throws CcdpMessageException
  {
    T ret = null;
    try
    {
      ret = mapper.readValue(txtMsg.getText(), clazz);
      @SuppressWarnings("unchecked")
      Enumeration<String> keys = txtMsg.getPropertyNames();
      Map<String, String> map = new HashMap<>();
      
      while( keys.hasMoreElements() )
      {
        String key = keys.nextElement();
        map.put(key, txtMsg.getStringProperty(key));
      }
      ret.setConfiguration(map);
    }
    catch( Exception e)
    {
      throw new CcdpMessageException(e.getMessage());
    }
    
    return ret;
  }
  
  
  public Map<String, String> getConfiguration()
  {
    return this.config;
  }
  
  public void setConfiguration( Map<String, String> config )
  {
    this.config = config;
  }
  
  @JsonSetter("reply-to")
  public void setReplyTo( String to )
  {
    this.replyTo = to;
  }
  
  @JsonGetter("reply-to")
  public String getReplyTo()
  {
    return this.replyTo;
  }
  
  @PropertyNameGet(MSG_TYPE_FLD)
  public abstract Integer getMessageType();
  
  
  public enum CcdpMessageType
  {
    UNDEFINED(-1),
    THREAD_REQUEST(0),
    RUN_TASK(1),
    KILL_TASK(2),
    TASK_UPDATE(3),
    RESOURCE_UPDATE(4),
    ASSIGN_SESSION(5),
    START_SESSION(6),
    END_SESSION(7);
    
    private static final Map<Integer, CcdpMessageType> lookup = new HashMap<>();
    
    static
    {
      for( CcdpMessageType mt : CcdpMessageType.values() )
      {
        lookup.put(mt.getValue(), mt);
      }
    }
    
    public int msgType;
    
    private CcdpMessageType( int msgType )
    {
      this.msgType = msgType;
    }
    
    public int getValue()
    {
      return this.msgType;
    }
    
    public static CcdpMessageType get(int msgType)
    {
      return lookup.get(msgType);
    }
  }// end of the MessageType Enum 
}
