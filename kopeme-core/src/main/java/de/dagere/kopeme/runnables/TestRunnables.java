package de.dagere.kopeme.runnables;

import java.lang.reflect.Method;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.dagere.kopeme.datastorage.RunConfiguration;

/**
 * Saves all test runnables, i.e. the runnables that should be executed before and after the test and the test itself.
 * 
 * @author reichelt
 *
 */
public class TestRunnables implements TestRunnable {

   private static final Logger LOG = LogManager.getLogger(TestRunnables.class);

   private final KoPeMeThrowingRunnable testRunnable, beforeRunnable, afterRunnable;

   public TestRunnables(final RunConfiguration config, final KoPeMeThrowingRunnable testRunnable, final Class<?> testClass, final Object testObject,
         List<Method> beforeClassMethod, List<Method> afterClassMethod) {
      final List<Method> beforeMethods = BeforeAfterMethodFinder.getBeforeNoMeasurements(testClass);
      final List<Method> afterMethods = BeforeAfterMethodFinder.getAfterNoMeasurements(testClass);
      LOG.debug("Klasse: {}", testClass);

      if (config.isExecuteBeforeClassInMeasurement()) {
         this.testRunnable = new BeforeAfterMethodRunnable(beforeClassMethod, testRunnable, afterClassMethod, testObject);
      } else {
         BeforeAfterMethodFinder.checkNoBeforeWithMeasurement(testClass);
         
         this.testRunnable = testRunnable;
      }

      beforeRunnable = new ListOfMethodRunnable(beforeMethods, testObject);
      afterRunnable = new ListOfMethodRunnable(afterMethods, testObject);
   }

   /**
    * Returns the test Runnable
    * 
    * @return Test-Runnable
    */
   @Override
   public KoPeMeThrowingRunnable getTestRunnable() {
      return testRunnable;
   }

   /**
    * Returns the runnable, that should be run before the test
    * 
    * @return Before-Runnable
    */
   @Override
   public KoPeMeThrowingRunnable getBeforeRunnable() {
      return beforeRunnable;
   }

   /**
    * Returns the runnable, that should be run after the test
    * 
    * @return After-Runnable
    */
   @Override
   public KoPeMeThrowingRunnable getAfterRunnable() {
      return afterRunnable;
   }

   @Override
   public Throwable getThrowable() {
      return null;
   }
}
