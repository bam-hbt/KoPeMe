package de.dagere.kopeme.junit.rule;

import java.lang.reflect.Method;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.function.ThrowingRunnable;

import de.dagere.kopeme.datastorage.RunConfiguration;

/**
 * Saves all test runnables, i.e. the runnables that should be executed before and after the test and the test itself.
 * 
 * @author reichelt
 *
 */
public class TestRunnables {

   private static final Logger LOG = LogManager.getLogger(TestRunnables.class);

   private final ThrowingRunnable testRunnable, beforeRunnable, afterRunnable;

   /**
    * Initializes the TestRunnables
    * 
    * @param testRunnable Runnable for the test itself
    * @param testClass Class that should be tested
    * @param testObject Object that should be tested
    */
   public TestRunnables(final RunConfiguration config, final ThrowingRunnable testRunnable, final Class<?> testClass, final Object testObject) {
      final List<Method> beforeMethods = BeforeAfterMethodFinder.getBeforeNoMeasurements(testClass);
      final List<Method> afterMethods = BeforeAfterMethodFinder.getAfterNoMeasurements(testClass);
      LOG.debug("Klasse: {}", testClass);

      if (config.isExecuteBeforeClassInMeasurement()) {
         List<Method> beforeClassMethod = BeforeAfterMethodFinder.getBeforeWithMeasurements(testClass);
         List<Method> afterClassMethod = BeforeAfterMethodFinder.getAfterWithMeasurements(testClass);

         this.testRunnable = new BeforeAfterMethodRunnable(beforeClassMethod, testRunnable, afterClassMethod, testObject);
      } else {
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
   public ThrowingRunnable getTestRunnable() {
      return testRunnable;
   }

   /**
    * Returns the runnable, that should be run before the test
    * 
    * @return Before-Runnable
    */
   public ThrowingRunnable getBeforeRunnable() {
      return beforeRunnable;
   }

   /**
    * Returns the runnable, that should be run after the test
    * 
    * @return After-Runnable
    */
   public ThrowingRunnable getAfterRunnable() {
      return afterRunnable;
   }
}
