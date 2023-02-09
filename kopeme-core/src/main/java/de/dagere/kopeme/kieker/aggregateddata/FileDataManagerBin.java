package de.dagere.kopeme.kieker.aggregateddata;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import de.dagere.kopeme.kieker.writer.AggregatedTreeWriter;

public class FileDataManagerBin implements Runnable, Closeable {

   private final AggregatedTreeWriter aggregatedTreeWriter;

   private final File destinationFolder;
   private File currentDestination;
   private StatisticsBinWriter binWriter;
   private final Map<DataNode, WritingData> nodeMap = new ConcurrentHashMap<>();

   private int currentEntries = 0;
   private int fileIndex = 0;
   private boolean running = true;

   /**
    * @param aggregatedTreeWriter
    * @throws IOException
    */
   public FileDataManagerBin(final AggregatedTreeWriter aggregatedTreeWriter) throws IOException {
      this.aggregatedTreeWriter = aggregatedTreeWriter;
      this.destinationFolder = aggregatedTreeWriter.getResultFolder();
      currentDestination = new File(destinationFolder, "measurement-0.bin");
      binWriter = new StatisticsBinWriter(currentDestination);
   }

   public void finish() {
      running = false;
   }

   @Override
   public void run() {
      while (running) {
         try {
            System.out.println("Sleeping: " + aggregatedTreeWriter.getWriteInterval());
            Thread.sleep(aggregatedTreeWriter.getWriteInterval());
         } catch (final InterruptedException e) {
            System.out.println("Writing is finished...");
         }
         if (running) {
            try {
               writeAll();
            } catch (final IOException e) {
               e.printStackTrace();
            }
         }
      }
   }

   private synchronized void writeAll() throws IOException {
      for (final Map.Entry<DataNode, WritingData> value : nodeMap.entrySet()) {
         writeLine(value);

         if (currentEntries >= aggregatedTreeWriter.getEntriesPerFile()) {
            startNextFile();
         }
      }
      binWriter.flush();
   }

   private void writeLine(final Map.Entry<DataNode, WritingData> value) throws IOException {
      if (value.getValue().getCurrentStatistic() != null &&
            !Double.isNaN(value.getValue().getCurrentStatistic().getMean())
            && value.getValue().getCurrentStatistic().getN() != 0) {
         binWriter.writeHeader(value.getKey());
         binWriter.writeStatistics(value.getValue());
         // currentWriter.write('\n');
         currentEntries++;
         value.getValue().persistStatistic();
      }
   }

   private void startNextFile() throws IOException {
      currentEntries = 0;
      fileIndex++;
      binWriter.close();
      currentDestination = new File(destinationFolder, "measurement-" + fileIndex + ".bin");
      binWriter = new StatisticsBinWriter(currentDestination);
   }

   public synchronized void write(final DataNode node, final long duration) {
      final WritingData data = getData(node);
      data.addValue(duration);
   }

   private WritingData getData(final DataNode node) {
      WritingData data = nodeMap.get(node);
      if (data == null) {
         data = new WritingData(currentDestination, aggregatedTreeWriter.getStatisticConfig());
         nodeMap.put(node, data);
      }
      return data;
   }

   @Override
   public void close() throws IOException {
      System.out.println("Writing finally...");
      writeAll();
      binWriter.close();
      binWriter = null;
   }
}