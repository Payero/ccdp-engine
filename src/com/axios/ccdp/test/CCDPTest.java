package com.axios.ccdp.test;



import org.apache.log4j.Logger;

import com.axios.ccdp.utils.CcdpUtils;
import com.axios.ccdp.utils.CcdpUtils.CcdpNodeType;


public class CCDPTest 
{

  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(CCDPTest.class.getName());
  
  
  public CCDPTest()
  {
    this.logger.debug("Running CCDP Test");
    try
    {
      this.runTest();
    }
    catch( Exception e )
    {
      this.logger.error("Message: " + e.getMessage(), e);
    }
    
  }
  
  
  private void runTest() throws Exception
  {
    this.logger.debug("Running the Test");
    
    
    
  }
  
  
  
  
  public static void main( String[] args ) throws Exception
  {
    String cfg_file = System.getProperty("ccdp.config.file");
    CcdpUtils.loadProperties(cfg_file);
    CcdpUtils.configLogger();
    
    new CCDPTest();
  }

}



final class Tuple2<T1, T2> 
{
  public final T1 _1;
  
  public final T2 _2;

  public Tuple2( final T1 v1,  final T2 v2) {
      _1 = v1;
      _2 = v2;
  }

  public static <T1, T2> Tuple2<T1, T2> create( final T1 v1,  final T2 v2) {
    System.out.println("T1: " + v1.getClass().getName() + " T2: "+ v2.getClass().getName());
    return new Tuple2<>(v1, v2);
  }

  public static <T1, T2> Tuple2<T1, T2> t( final T1 v1,  final T2 v2) {
      return create(v1, v2);
  }
}
