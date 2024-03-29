package com.axios.ccdp.impl.connections.amq;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Session;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.log4j.Logger;


public class AmqConnector
{

  /**
   * Generates debug print statements based on the verbosity level.
   */
  protected Logger logger = Logger.getLogger(AmqConnector.class.getName());
  /**
   * Factory used to generate all the connections
   */
  protected ConnectionFactory factory = null;
  /**
   * Stores the connection to the ActiveMQ server
   */
  protected Connection connection = null;
  /**
   * Stores the session connected to the ActiveMQ server
   */
  protected Session session = null;
  /**
   * Stores the destination or channel used to send or receive events
   */
  protected Destination destination = null;
  
  /**
   * Instantiates a new ActiveMQ connector
   */
  public AmqConnector()
  {
    
  }
  
  /**
   * Connects to the given channel
   * 
   * @param broker the server or broker to connect
   * @param name the channel to connect to either send or receive data
   * 
   * @return true if the connection was successful
   */
  protected boolean connect(String broker, String name)
  {
    this.logger.info("Connecting to: " + broker);
    
    try
    {
      factory = new ActiveMQConnectionFactory(broker);
      connection = factory.createConnection();
      connection.start();
      session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      destination = session.createQueue(name);
      
      return true;
    }
    catch( JMSException e)
    {
      this.logger.error("amq connect Message: " + e.getMessage(), e);
      return false;
    }
  }
  
  /**
   * Disconnects from the assigned channel.
   */
  protected void disconnect()
  {
    this.logger.info("Disconnecting");
    try
    {
      if( this.connection != null ) 
        this.connection.close();
      if( this.session != null )
        this.session.close();
    }
    catch ( JMSException e)
    {
      this.logger.error("amd disconnect Message: " + e.getMessage(), e);
    }
  }
}
