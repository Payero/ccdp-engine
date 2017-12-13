package com.axios.ccdp.messages;

/**
 * Simple class used to wrap all exceptions thrown during message manipulations
 * 
 * @author Oscar E. Ganteaume
 *
 */
public class CcdpMessageException extends Exception
{

  /**
   * Required by the Exception class
   */
  private static final long serialVersionUID = -5589619094898276833L;
  
  /**
   * Prints the given error message and the stack trace containing information
   * about what caused the issue
   *  
   * @param message the error message
   */
  public CcdpMessageException( String message )
  {
    super("Error generating CcdpMessage.  Error: " + message);
  }
  
  /**
   * Prints the given error message and the stack trace containing information
   * about what caused the issue
   *  
   * @param e the actual exception to throw
   */
  public CcdpMessageException( Exception e )
  {
    super(e);
  }
}
