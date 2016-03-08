package de.dagere.kopeme.junit3;

import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.reflect.InvocationTargetException;

import junit.framework.AssertionFailedError;
import junit.framework.TestCase;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.dagere.kopeme.PerformanceTestUtils;
import de.dagere.kopeme.annotations.AnnotationDefaults;
import de.dagere.kopeme.annotations.PerformanceTest;
import de.dagere.kopeme.annotations.PerformanceTestingClass;
import de.dagere.kopeme.datacollection.DataCollectorList;
import de.dagere.kopeme.datacollection.TestResult;
import de.dagere.kopeme.datacollection.TimeDataCollector;
import de.dagere.kopeme.datastorage.SaveableTestData;
import de.dagere.kopeme.kieker.KoPeMeKiekerSupport;

/**
 * Base class for KoPeMe-JUnit3-Testcases.
 * 
 * @author reichelt
 *
 */
public abstract class KoPeMeTestcase extends TestCase {

	private static final int INTERRUPT_TRIES = 10;

	private static final Logger LOG = LogManager.getLogger(KoPeMeTestcase.class);

	private final PerformanceTest annoTestcase = AnnotationDefaults.of(PerformanceTest.class);
	private final PerformanceTestingClass annoTestClass = AnnotationDefaults.of(PerformanceTestingClass.class);

	/**
	 * Initializes the testcase.
	 */
	public KoPeMeTestcase() {
	}

	/**
	 * Initializes the testcase with its name.
	 * 
	 * @param name Name of the testcase
	 */
	public KoPeMeTestcase(final String name) {
		super(name);
	}

	/**
	 * Returns the count of warmup executions, default is 1.
	 * 
	 * @return Warmup executions
	 */
	protected int getWarmupExecutions() {
		return annoTestcase.warmupExecutions();
	}

	/**
	 * Returns the count of real executions.
	 * 
	 * @return real executions
	 */
	protected int getExecutionTimes() {
		return annoTestcase.executionTimes();
	}

	/**
	 * Returns weather full data should be logged.
	 * 
	 * @return Weather full data should be logged
	 */
	protected boolean logFullData() {
		return annoTestcase.logFullData();
	}

	/**
	 * Returns the time all testcase executions may take *in sum* in ms. -1 means unbounded; Standard is set to 120 s.
	 * 
	 * @return Maximal time of all test executions
	 */
	protected int getMaximalTime() {
		return annoTestClass.overallTimeout();
	}

	/**
	 * Gets the list of datacollectors for the current execution.
	 * 
	 * @return List of Datacollectors
	 */
	protected DataCollectorList getDataCollectors() {
		return DataCollectorList.STANDARD;
	}

	/**
	 * Should kieker monitoring be used.
	 * 
	 * @return
	 */
	protected boolean useKieker() {
		return annoTestcase.useKieker();
	}

	@Override
	public void runBare() throws InterruptedException {
		LOG.trace("Initialize JUnit-3-KoPeMe-Testcase");

		final int warmupExecutions = getWarmupExecutions(), executionTimes = getExecutionTimes();
		final boolean fullData = logFullData();
		final int timeoutTime = getMaximalTime();

		String testClassName = this.getClass().getName();
		final TestResult tr = new TestResult(testClassName, executionTimes, DataCollectorList.STANDARD);
		tr.setCollectors(getDataCollectors());

		try {
			KoPeMeKiekerSupport.INSTANCE.useKieker(useKieker(), testClassName, getName());
		} catch (Exception e) {
			System.err.println("Kieker has failed!");
			e.printStackTrace();
		}

		Thread thread = new Thread(new Runnable() {

			@Override
			public void run() {
				try {
					runTestCase(tr, warmupExecutions, executionTimes, fullData);
				} catch (InvocationTargetException e) {
					e.printStackTrace();
				} catch (IllegalAccessException e) {
					e.printStackTrace();
				} catch (AssertionFailedError e) {
					e.printStackTrace();
				} catch (Throwable e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				tr.finalizeCollection();
				LOG.debug("Test-call finished");
			}
		});

		thread.setUncaughtExceptionHandler(new UncaughtExceptionHandler() {

			@Override
			public void uncaughtException(final Thread t, final Throwable e) {
				if (e instanceof OutOfMemoryError) {
					while (t.isAlive()) {
						t.interrupt();
					}
				}
				e.printStackTrace();
				fail();
			}
		});

		LOG.debug("Waiting for test-completion for {}", timeoutTime);
		waitForTestEnd(timeoutTime, thread);
		// No matter how the test gets finished, saving should be done here
		LOG.trace("End-Testcase-Saving begins");
		PerformanceTestUtils.saveData(SaveableTestData.createFineTestData(getName(), getClass().getName(), tr, warmupExecutions, fullData));

		LOG.debug("KoPeMe-Test {} finished", getName());
	}

	private void waitForTestEnd(final int timeoutTime, final Thread thread) throws InterruptedException {
		thread.start();

		thread.join(timeoutTime);
		LOG.trace("Test should be finished...");
		if (thread.isAlive()) {
			int count = 0;
			while (thread.isAlive() && count < INTERRUPT_TRIES) {
				LOG.debug("Thread not finished, is kill now..");
				thread.interrupt();
				Thread.sleep(10);
				count++;
			}
			if (count == INTERRUPT_TRIES) {
				LOG.debug("Thread does not respond, so it is killed hard now.");
				count = 0;
				while (thread.isAlive() && count < 5) {
					thread.stop();
					Thread.sleep(10);
					count++;
				}
			}
		}
	}

	/**
	 * Runs the whole testcase.
	 * 
	 * @param tr Where the results should be saved
	 * @param warmupExecutions How many warmup executions should be done
	 * @param executionTimes How many normal executions should be done
	 * @param fullData Weather to log full data
	 * @throws Throwable
	 */
	private void runTestCase(final TestResult tr, final int warmupExecutions, final int executionTimes, final boolean fullData)
			throws Throwable {

		String fullName = this.getClass().getName() + "." + getName();
		try {
			for (int i = 0; i < warmupExecutions; i++) {
				setUp();
				LOG.info("-- Starting warmup execution " + fullName + " " + i + "/" + warmupExecutions + " --");
				KoPeMeTestcase.super.runTest();
				LOG.info("-- Stopping warmup execution " + i + "/" + warmupExecutions + " --");
				tearDown();
			}
		} catch (AssertionFailedError t) {
			t.printStackTrace(); // do nothing - functional warmup failure is ok
		}

		try {
			runMainExecution(fullName, tr, executionTimes);
		} catch (AssertionFailedError t) {
			tr.finalizeCollection();
			// PerformanceTestUtils.saveData(SaveableTestData.createAssertFailedTestData(getName(), getClass().getName(), tr, true));
			throw t;
		} catch (Throwable t) {
			tr.finalizeCollection();
			// PerformanceTestUtils.saveData(SaveableTestData.createErrorTestData(getName(), getClass().getName(), tr, true));
			throw t;
		}
	}

	/**
	 * Runs the main execution of the test, i.e.useKieker the execution where performance measures are counted.
	 * 
	 * @param testCase Runnable that should be run
	 * @param name Name of the test
	 * @param tr Where the results should be saved
	 * @param executionTimes How often the test should be executed
	 * @throws Throwable
	 */
	private void runMainExecution(final String name, final TestResult tr, final int executionTimes) throws Throwable {
		int executions;
		String firstPart = "--- Starting execution " + name + " ";
		String endPart = "/" + executionTimes + " ---";
		for (executions = 1; executions <= executionTimes; executions++) {
			LOG.debug(firstPart + executions + endPart);
			setUp();
			tr.startCollection();
			KoPeMeTestcase.super.runTest();
			tr.stopCollection();
			tearDown();
			tr.getValue(TimeDataCollector.class.getName());
			tr.setRealExecutions(executions);
			LOG.debug("--- Stopping execution " + executions + endPart);
			if (Thread.interrupted()) {
				return;
			} else {
				LOG.trace("Nicht interrupted!");
			}
		}
		LOG.debug("Executions: " + (executions - 1));
		tr.setRealExecutions(executions - 1);
	}
}
