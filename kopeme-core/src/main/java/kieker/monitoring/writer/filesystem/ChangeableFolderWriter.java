package kieker.monitoring.writer.filesystem;

import java.io.File;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.logging.Logger;

import kieker.common.configuration.Configuration;
import kieker.common.record.IMonitoringRecord;
import kieker.common.record.misc.KiekerMetadataRecord;
import kieker.common.record.misc.RegistryRecord;
import kieker.monitoring.core.controller.IMonitoringController;
import kieker.monitoring.writer.AbstractMonitoringWriter;

/**
 * This class enables Kieker writing in different folders for KoPeMe purposes. It does so by creating a new {@link SyncFsWriter} with every new folder that is set to the
 * {@link ChangeableFolderWriter}. For storing all mapping data that is produced, every {@link RegistryRecord} that is measured is saved to a List and written to every new {@link SyncFsWriter} that is
 * created with a new folder.
 * 
 * @author reichelt
 *
 */
public class ChangeableFolderWriter extends AbstractMonitoringWriter {

	public static final String PREFIX = ChangeableFolderWriter.class.getName() + ".";
	public static final String CONFIG_PATH = PREFIX + "customStoragePath";
	public static final String CONFIG_MAXENTRIESINFILE = PREFIX + "maxEntriesInFile";
	public static final String CONFIG_MAXLOGSIZE = PREFIX + "maxLogSize";
	public static final String REAL_WRITER = PREFIX + "realwriter";

	public static final String CONFIG_MAXLOGFILES = PREFIX + "maxLogFiles";
	public static final String CONFIG_FLUSH = PREFIX + "flush";
	public static final String CONFIG_BUFFER = PREFIX + "bufferSize";

//	private static final Map<IMonitoringController, ChangeableFolderWriter> instanceMapping = new HashMap<>();
	private static ChangeableFolderWriter instance;
	
	public static synchronized ChangeableFolderWriter getInstance(final IMonitoringController controler) {
//		System.out.println("Instances: " + instanceMapping.size());
//		for (final IMonitoringController controller : instanceMapping.keySet()){
//			System.out.println("Get: " + System.identityHashCode(controller) + " " + controller.hashCode());
//		}
//		final ChangeableFolderWriter firstController = instanceMapping.values().iterator().next();
//		System.out.println(firstController.equals(controler));
//		final ChangeableFolderWriter writer = instanceMapping.get(controler);
//		System.out.println("Return: " + writer + " " + controler.hashCode());
		return instance;
	}

	private final List<KiekerMetadataRecord> mappingRecords = new LinkedList<>();
	private final Configuration configuration;
	private static final Logger LOG = Logger.getLogger(ChangeableFolderWriter.class.getName());
	private AbstractMonitoringWriter currentWriter = null; // no writer is needed, until data is saved to where it belongs

	public ChangeableFolderWriter(final Configuration configuration) {
		super(configuration);
		LOG.info("Init..");
		this.configuration = configuration;
		currentWriter = createWriter(configuration);
		instance = this;
	}

	private AbstractMonitoringWriter createWriter(final Configuration configuration) {
		final String writerName = configuration.getStringProperty(REAL_WRITER);
		if (writerName.equals(AsciiFileWriter.class.getSimpleName())) {
			final Configuration newConfig = toWriterConfiguration(configuration, AsciiFileWriter.class);
			final AsciiFileWriter asyncFsWriter = new AsciiFileWriter(newConfig);
			return asyncFsWriter;
		} else if (writerName.equals(BinaryFileWriter.class.getSimpleName())) {
			final Configuration newConfig = toWriterConfiguration(configuration, BinaryFileWriter.class);
			final BinaryFileWriter syncFsWriter = new BinaryFileWriter(newConfig);
			return syncFsWriter;
		} else {
			System.out.println("Defined writer " + writerName + " not found - using default " + AsciiFileWriter.class.getSimpleName());
			final Configuration newConfig = toWriterConfiguration(configuration, AsciiFileWriter.class);
			final AsciiFileWriter syncFsWriter = new AsciiFileWriter(newConfig);
			return syncFsWriter;
		}
	}

	Configuration toWriterConfiguration(final Configuration c, Class<?> writerClass) {
		final Configuration returnable = new Configuration();
		for (final Iterator<Entry<Object, Object>> iterator = c.entrySet().iterator(); iterator.hasNext();) {
			final Entry<Object, Object> entry = iterator.next();
			final String keyAsString = entry.getKey().toString();
			final String replacedPropertyName = keyAsString.replace(getClass().getName(), writerClass.getName());
			returnable.setProperty(replacedPropertyName, entry.getValue().toString());
		}
		return returnable;
	}

	@Override
	public void onStarting() {
		System.out.println("Initializing " + getClass());
		currentWriter.onStarting();
	}

	@Override
	public void writeMonitoringRecord(IMonitoringRecord record) {
		if (record instanceof KiekerMetadataRecord) {
			mappingRecords.add((KiekerMetadataRecord) record);
		}
//		System.out.println("Writing: " + record);
//		System.out.println(record.getClass());
		if (currentWriter != null) {
			currentWriter.writeMonitoringRecord(record);
		}
	}

	@Override
	public void onTerminating() {
		if (currentWriter != null) {
			currentWriter.onTerminating();
		}
	}

	public void setFolder(File writingFolder) {
		if (currentWriter != null) {
			currentWriter.onTerminating();
		}
		writingFolder.mkdirs();
		final String absolutePath = writingFolder.getAbsolutePath();
		configuration.setProperty(CONFIG_PATH, absolutePath);
		final AbstractMonitoringWriter writer = createWriter(configuration);
		// currentWriter.setController(monitoringController);
		for (final KiekerMetadataRecord record : mappingRecords) {
			LOG.info("Adding registry record: " + record);
			writer.writeMonitoringRecord(record);
		}
		currentWriter = writer;
	}

	// @Override
	// public synchronized boolean newMonitoringRecord(final IMonitoringRecord record) {
	// if (record instanceof RegistryRecord) {
	// mappingRecords.add((RegistryRecord) record);
	// }
	// if (currentWriter == null) {
	// return true;
	// } else {
	// return currentWriter.newMonitoringRecordNonBlocking(record);
	// }
	// }

	// @Override
	// public synchronized void terminate() {
	// if (currentWriter != null) {
	// currentWriter.terminate();
	// }
	// }

	// @Override
	// protected void init() throws Exception {
	// System.out.println("Initializing " + getClass());
	// currentWriter.setController(monitoringController);
	// instanceMapping.put(monitoringController, this);
	// }

	// public synchronized void setFolder(final File writingFolder) throws Exception {
	// if (currentWriter != null) {
	// currentWriter.terminate();
	// }
	// writingFolder.mkdirs();
	// final String absolutePath = writingFolder.getAbsolutePath();
	// configuration.setProperty(CONFIG_PATH, absolutePath);
	// currentWriter = createWriter(configuration);
	// currentWriter.setController(monitoringController);
	// for (final RegistryRecord record : mappingRecords) {
	// LOG.info("Adding registry record: " + record);
	// currentWriter.newMonitoringRecord(record);
	// }
	// }
	//
	// public IMonitoringController getController() {
	// return monitoringController;
	// }
}
