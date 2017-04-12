package com.axios.ccdp.mesos.test;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.log4j.Logger;

import com.axios.ccdp.mesos.fmwk.CcdpJob;
import com.axios.ccdp.mesos.utils.CcdpUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonArrayFormatVisitor;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.reactivex.Flowable;
import io.reactivex.schedulers.Schedulers;


public class CCDPTest
{

  /**
   * Generates debug print statements based on the verbosity level.
   */
  private Logger logger = Logger.getLogger(CCDPTest.class.getName());

  private static long start = System.currentTimeMillis();
  

  public CCDPTest() throws Exception
  {
    this.logger.debug("Running CCDP Test");
    
    
    String fname = "/data/users/oeg/workspace/CCDP/data/rand_time.json";
    ObjectMapper mapper = new ObjectMapper();
    JsonNode root = mapper.readTree(new File(fname));
    if( root.has("jobs") )
    {
      this.logger.debug("Found Jobs");
      JsonNode jobs = root.get("jobs");
      for( JsonNode job : jobs )
      {
        this.logger.debug("The Job: " + job.toString());
        CcdpJob tst = CcdpJob.fromJSON(job);
      }
    }
    
    
    
    
    boolean back = true;
    if( back )
      return;
    
    List<String> entries = new ArrayList<>();
    entries.add("One");
    entries.add("Two");
    entries.add("Three");
    entries.add("Four");
    entries.add("Howdies");
    
//    Observable<String> hello = Observable.just("Howdie")
//        .repeat(5)
//        .filter(event -> event.equals("Howdie"))
//        .flatMap(entry -> Observable.fromIterable(entries) )
//        .zipWith(entries, Tuple2::create);
//    
//    // using this form, it needs to be static function
//    hello.subscribe(CCDPTest::printMe);
    
    Thread.sleep(5_000);
    
    Flowable.range(1, 10)
    .observeOn(Schedulers.computation())
    .map(v -> v * v)
    .blockingSubscribe(System.out::println);
    
    Flowable.range(1, 10)
    .flatMap(v ->
        Flowable.just(v)
          .subscribeOn(Schedulers.computation())
          .map(w -> w * w)
    )
  .blockingSubscribe(System.out::println);
    
    
    
//    
//    List<String> words = Arrays.asList(
//        "the",
//        "quick",
//        "brown",
//        "fox",
//        "jumped",
//        "over",
//        "the",
//        "lazy",
//        "dogs"
//       );
    // The observable gets every word and is stored as 'word' and is passed to 
    // the lambda function that just prints it
//    Observable.just(words)
//    .subscribe(word-> this.nonStaticPrint(word));
    
//    Observable.fromIterable(words)
//    .flatMap(word -> Observable.fromArray(word.split("")))
//    .distinct()
//    .sorted()
//    .zipWith(Observable.range(1, Integer.MAX_VALUE), 
//        (string, count)->String.format("%2d. %s", count, string))
//    .subscribe(System.out::println);
//    
//    Observable.fromIterable(words)
//    .flatMap(word -> Observable.from(word.split("")))
//    .zipWith(Observable.range(1, Integer.MAX_VALUE), 
//        (string, count)->String.format("%2d. %s", count, string))
//    .subscribe(System.out::println);
//    
    
//    Observable.fromArray(words.toArray())
//    .flatMap(word -> Observable.from(word.split("")))
//    .distinct()
//    .sorted()
//    .zipWith(Observable.range(1, Integer.MAX_VALUE),
//      (string, count) -> String.format("%2d. %s", count, string))
//    .subscribe(System.out::println);
//    
    
    
//    Observable<Long> fast = Observable.interval(1, TimeUnit.SECONDS);
//    Observable<Long> slow = Observable.interval(3, TimeUnit.SECONDS);
//    
//    Observable<Long> clock = Observable.merge(
//        slow.filter(tick-> isSlowTickTime(tick)),
//        fast.filter(tick-> !isSlowTickTime(tick))
//        );
//    clock.subscribe(tick-> this.printTime());
//    
//    Thread.sleep(60_000);
//    
//    
//    Observable<Long> test = Observable.interval(2,  TimeUnit.SECONDS);
//    test.flatMap( this.flatThisMap() );
  }  

  public void flatThisMap()
  {
    
  }
  
  public static Boolean isSlowTickTime(Long tick) 
  {
    return (System.currentTimeMillis() - start) % 30_000 >= 15_000;
  }
  
  public void printTime( )
  {
    this.logger.debug(new Date());
  }
  
  public void printTime( Long time )
  {
    this.logger.debug(new Date());
  }
  
  public void nonStaticPrint(String name)
  {
    System.out.println("The Name " + name);
  }
  
  public static void printMe(String name)
  {
    System.out.println("The Name is " + name);
  }
  
  public static void main(String[] args) throws Exception
  {
    CcdpUtils.loadProperties(System.getProperty(CcdpUtils.CFG_KEY_CFG_FILE));
    CcdpUtils.configLogger();
    
    new CCDPTest();
  }
  
  

}

final class Tuple2<T1, T2> {
  
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
