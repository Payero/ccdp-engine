package com.axios.ccdp.messages;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.jms.TextMessage;

import org.apache.log4j.Logger;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Class used to define a generic message structure that can be used to convert
 * message objects to JSON and vice versa.  Each message has a unique message
 * type that defines the actual class to create an instance from.  Based on
 * that field this class generates the object an populates all the required
 * JSON fields.
 *
 * @author Oscar E. Ganteaume
 *
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class CcdpMessage implements Cloneable
{
  /**
   * Stores the name of the JSON field containing the message type
   */
  public static final String MSG_TYPE_FLD = "msg-type";
  /**
   * Stores the relationship between the message type and the class to generate
   * and instance object from it
   */
  @SuppressWarnings("rawtypes")
  private static Map<CcdpMessageType, Class> classMap;

  /**
   * Generates debug print statements based on the verbosity level.
   */
  protected static Logger logger =
      Logger.getLogger(CcdpMessage.class.getName());

  /**
   * Generates JSON objects and also converts them to text representation of the
   * object
   */
  protected static ObjectMapper mapper =  new ObjectMapper();
  /**
   * Contains the configuration to use for each instance or the class itself
   */
  protected Map<String, String> config = new HashMap<>();
  /**
   * The channel the data needs to be sent
   */
  protected String replyTo = null;

  /**
   * Populates the contents of the TextMessage as a JSON representation of the
   * object.
   *
   * @param msg the message that will be sent whose body needs to be filled
   * @param txtMsg the TextMessage to send
   *
   * @throws CcdpMessageException if there is a problem generating the message
   */
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

  /**
   * Creates a new CcdpMessage object of the appropriate class. It instantiates
   * an object of the given clazz type and populates it with the contents of
   * the txtMsg.
   *
   * @param <T> The actual object type to generate
   * @param txtMsg the message with the desired information
   * @param clazz the actual object type to create
   * @return a populated instance of the desired class
   *
   * @throws CcdpMessageException a CcdpMessageException is thrown if there is
   *         a problem creating the object
   */
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

  /**
   * Creates a new CcdpMessage object of the appropriate class. It instantiates
   * an object of the message type stored in the message itself.  After the
   * object is created then it populates it with the contents of the txtMsg.
   *
   * @param <T> The actual object type to generate
   * @param txtMsg the message with the desired information
   * @return a populated instance of the desired class
   *
   * @throws CcdpMessageException a CcdpMessageException is thrown if there is
   *         a problem creating the object
   */
  @SuppressWarnings("unchecked")
  public static <T extends CcdpMessage> T buildObject( TextMessage txtMsg)
                                                  throws CcdpMessageException
  {
    T ret = null;
    try
    {
      String body = txtMsg.getText();

      int msgTypeNum = -1;
      String keyFld = CcdpMessage.MSG_TYPE_FLD;
      // let's try option one: the message type is in the header
      if( txtMsg.propertyExists(keyFld) )
      {
        msgTypeNum = txtMsg.getIntProperty(keyFld);
      }
      else  // is not, is it in the actual body as json?
      {
        JsonNode obj = CcdpMessage.mapper.readTree(body);
        CcdpMessage.logger.debug("The Json Object " + obj.toString());

        // I am looking for the msg-type field, is it there?
        if( obj.has(keyFld) )
        {
          String val = obj.get(keyFld).asText();
          msgTypeNum = Integer.valueOf(val);
        }
        else
        {
          CcdpMessage.logger.error("The Message does not have message type field " + keyFld);
          CcdpMessage.logger.error("Payload: " + body );
        }
      }

      CcdpMessageType msgType = CcdpMessageType.get(msgTypeNum);

      ret = (T)mapper.readValue(body, CcdpMessage.classMap.get(msgType));

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

  /**
   * Gets the configuration used by the CcdpMessage objects
   *
   * @return configuration used by the CcdpMessage objects
   */
  public Map<String, String> getConfiguration()
  {
    return this.config;
  }

  /**
   * Sets the configuration used by the CcdpMessage objects
   *
   * @param config configuration used by the CcdpMessage objects
   */
  public void setConfiguration( Map<String, String> config )
  {
    this.config = config;
  }

  /**
   * Sets the optional field with the channel name this message needs to be sent
   *
   * @param to the channel of the intended recipient of this message
   */
  @JsonSetter("reply-to")
  public void setReplyTo( String to )
  {
    this.replyTo = to;
  }

  /**
   * Gets the optional field with the channel name this message needs to be sent
   *
   * @return the channel of the intended recipient of this message
   */
  @JsonGetter("reply-to")
  public String getReplyTo()
  {
    return this.replyTo;
  }

  /**
   * Gets the unique message type that identifies the actual class
   *
   * @return the unique message type that identifies the actual class
   */
  @PropertyNameGet(MSG_TYPE_FLD)
  @JsonGetter("msg-type")
  public abstract Integer getMessageType();

  /**
   * Sets the unique message type that identifies the actual class
   *
   * @param type the unique message type that identifies the actual class
   */
  @PropertyNameSet(MSG_TYPE_FLD)
  @JsonSetter("msg-type")
  public abstract void setMessageType(int type);

  /**
   * Facilitates the object generation routine by removing the need of receivers
   * to know all the message types. The classMap data structure relates a
   * message type with an actual class to create.  This is used by the
   * objectBuilder and it needs to be in sync with the enum CcdpMessageType
   */
  static
  {
    classMap = new HashMap<>();
    classMap.put(CcdpMessageType.ASSIGN_SESSION, AssignSessionMessage.class);
    classMap.put(CcdpMessageType.END_SESSION, EndSessionMessage.class);
    classMap.put(CcdpMessageType.ERROR_MSG, ErrorMessage.class);
    classMap.put(CcdpMessageType.KILL_TASK, KillTaskMessage.class);
    classMap.put(CcdpMessageType.PAUSE_THREAD, PauseThreadMessage.class);
    classMap.put(CcdpMessageType.RESOURCE_UPDATE, ResourceUpdateMessage.class);
    classMap.put(CcdpMessageType.RUN_TASK, RunTaskMessage.class);
    classMap.put(CcdpMessageType.SHUTDOWN, ShutdownMessage.class);
    classMap.put(CcdpMessageType.START_SESSION, StartSessionMessage.class);
    classMap.put(CcdpMessageType.START_THREAD, StartThreadMessage.class);
    classMap.put(CcdpMessageType.STOP_THREAD, StopThreadMessage.class);
    classMap.put(CcdpMessageType.TASK_UPDATE, TaskUpdateMessage.class);
    classMap.put(CcdpMessageType.THREAD_REQUEST, ThreadRequestMessage.class);
    classMap.put(CcdpMessageType.UNDEFINED, UndefinedMessage.class);
  }

  /**
   * An Enumerator class used to identify all the different messages used by
   * the system
   *
   * @author Oscar E. Ganteaume
   *
   */
  public enum CcdpMessageType
  {
    ASSIGN_SESSION(0),
    END_SESSION(1),
    ERROR_MSG(2),
    KILL_TASK(3),
    PAUSE_THREAD(4),
    RESOURCE_UPDATE(5),
    RUN_TASK(6),
    SHUTDOWN(7),
    START_SESSION(8),
    START_THREAD(9),
    STOP_THREAD(10),
    TASK_UPDATE(11),
    THREAD_REQUEST(12),
    UNDEFINED(13);

    /**
     * Stores a table containing the relationship between the integer and the
     * actual type
     */
    private static final Map<Integer, CcdpMessageType> lookup = new HashMap<>();

    // Populates all the values in the lookup table at startup
    static
    {
      for( CcdpMessageType mt : CcdpMessageType.values() )
      {
        lookup.put(mt.getValue(), mt);
      }
    }

    /**
     * Stores the unique message type that identifies each CcdpMessage class
     */
    public int msgType;

    /**
     * Instantiates a new object and sets the message type
     *
     * @param msgType the integer value of the CcdpMessage type
     */
    private CcdpMessageType( int msgType )
    {
      this.msgType = msgType;
    }
    /**
     * Gets the integer representation of the unique message type identifying
     * the message
     *
     * @return the integer representation of the unique message type identifying
     *         the message
     */
    public int getValue()
    {
      return this.msgType;
    }

    /**
     * Gets the actual object that is represented by the given integer
     *
     * @param msgType the integer representation of the object
     * @return the actual object that is represented by the given integer
     */
    public static CcdpMessageType get(int msgType)
    {
      return lookup.get(msgType);
    }
  }// end of the MessageType Enum
}
