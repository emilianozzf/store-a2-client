import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.regex.Pattern;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

public class MultithreadedClient {
  private BufferedWriter bw = new BufferedWriter(new FileWriter("report.csv"));

  public MultithreadedClient() throws IOException {
    bw.write("Start RequestType Latency ResponseCode\n");
  }

  synchronized public void writeRecordToFile(String record) throws IOException {
    this.bw.write(record+"\n");
  }

  public static void main(String[] args) throws IOException, InterruptedException {
    // Processes and validates the input parameters
    HashMap<String, String> argsMap = parseArgs(args);
    if (argsMap == null) {
      System.out.println("The command line arguments are not valid...");
      return;
    }
    int maxStore = Integer.parseInt(argsMap.get("maxStore"));
    int numCustomersPerStore = Integer.parseInt(argsMap.get("numCustomersPerStore"));
    int maxItemId = Integer.parseInt(argsMap.get("maxItemId"));
    int numPurchases = Integer.parseInt(argsMap.get("numPurchases"));
    int numItemsPurPurchase = Integer.parseInt(argsMap.get("numItemsPurPurchase"));
    String date = argsMap.get("date");
    String ipWithPort = argsMap.get("ipWithPort");

    long start = System.currentTimeMillis();
    MultithreadedClient multithreadedClient = new MultithreadedClient();
    // Creates a thread for every store (1..maxStores)
    ShopRunnable[] shopRunnables = new ShopRunnable[maxStore];
    for (int i = 0; i < maxStore; i ++) {
      shopRunnables[i] = new ShopRunnable(
          i+1,
          numCustomersPerStore,
          maxItemId,
          numPurchases,
          numItemsPurPurchase,
          date,
          ipWithPort,
          multithreadedClient);
    }
    Thread[] shopThreads = new Thread[maxStore];
    for (int i = 0; i < maxStore; i ++) {
      shopThreads[i] = new Thread(shopRunnables[i]);
      shopThreads[i].start();
    }

    try {
      for (int i = 0; i < maxStore; i++) {
        shopThreads[i].join();
      }
    } catch (InterruptedException e) {
    }
    long end = System.currentTimeMillis();
    multithreadedClient.bw.close();

    calculateAndPrintResult(maxStore, start, end);
  }

  private static void calculateAndPrintResult(int maxStore, long start, long end) {
    int numRequests = 0;
    int numSuccessfulRequests = 0;
    int numUnsuccessfulRequests = 0;
    DescriptiveStatistics stats = new DescriptiveStatistics();
    try (BufferedReader br = new BufferedReader(new FileReader("report.csv"))) {
      String line = br.readLine();
      while ((line = br.readLine()) != null) {
        String[] values = line.split(" ");
        numRequests += 1;
        if (values.length == 1 && values[0].equals("error")) numUnsuccessfulRequests += 1;
        if (values.length == 4) {
          if (values[3].equals("201")) {
            numSuccessfulRequests += 1;
          } else {
            numUnsuccessfulRequests += 1;
          }
        }
        if (values.length >= 3) {
          stats.addValue(Double.parseDouble(values[2]));
        }
      }
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }

    System.out.println("Multithreaded Client 2 Report:");
    System.out.println("Maximum number of stores: " + maxStore);
    System.out.println("Total number of requests sent: " + numRequests);
    System.out.println("Total number of successful requests sent: " + numSuccessfulRequests);
    System.out.println("Total number of unsuccessful requests sent: " + numUnsuccessfulRequests);
    System.out.println("Mean response time for POSTs (millisecs): " + stats.getMean());
    System.out.println("Median response time for POSTs (millisecs): " + stats.getPercentile(50));
    System.out.println("p99 (99th percentile) response time for POSTs (millisecs): " + stats.getPercentile(99));
    System.out.println("Max response time for POSTs (millisecs): " + stats.getMax());
    System.out.println("The total wall time (millisecs): " + (end - start));
    System.out.println("The throughput (requests/second): " + (stats.getN() / ((end - start) / 1000)));
  }

  private static HashMap<String, String> parseArgs(String[] args) {
    HashMap<String, String> argsMap = new HashMap<>();
    argsMap.put("numCustomersPerStore", "1000");
    argsMap.put("maxItemId", "100000");
    argsMap.put("numPurchases", "60");
    argsMap.put("numItemsPurPurchase", "5");
    argsMap.put("date", "20210101");

    if (args.length == 0 || args.length % 2 != 0) {
      System.out.println("Missing parameters...");
      return null;
    }

    for (int i = 0; i < args.length; i += 2) {
      switch (args[i]) {
        case "-ms":
          if (!is32Int(args[i + 1])) {
            System.out.println("maxStore should be a valid 32-bit integer...");
            return null;
          }
          argsMap.put("maxStore", args[i + 1]);
          break;
        case "-ncps":
          if (!is32Int(args[i + 1])) {
            System.out.println("numCustomersPerStore should be a valid 32-bit integer...");
            return null;
          }
          argsMap.put("numCustomersPerStore", args[i + 1]);
          break;
        case "-mii":
          if (!is32Int(args[i + 1])) {
            System.out.println("maxItemId should be a valid 32-bit integer...");
            return null;
          }
          argsMap.put("maxItemId", args[i + 1]);
          break;
        case "-np":
          if (!is32Int(args[i + 1])) {
            System.out.println("numPurchases should be a valid 32-bit integer...");
            return null;
          }
          argsMap.put("numPurchases", args[i + 1]);
          break;
        case "-nipp":
          if (!is32Int(args[i + 1])) {
            System.out.println("numItemsPurPurchase should be a valid 32-bit integer...");
            return null;
          }
          argsMap.put("numItemsPurPurchase", args[i + 1]);
          break;
        case "-d":
          if (!isValidDate(args[i + 1])) {
            System.out.println("date should be a valid date...");
            return null;
          }
          argsMap.put("date", args[i + 1]);
          break;
        case "-iwp":
          if (!isValidIpWithPort(args[i + 1])) {
            System.out.println("ipWithPort should be a valid ip with port");
            return null;
          }
          argsMap.put("ipWithPort", args[i + 1]);
          break;
        default:
          System.out.println("Unknown option: " + args[i] + "...");
          return null;
      }
    }

    if (!argsMap.containsKey("maxStore")) {
      System.out.println("Missing maxStore...");
      return null;
    }
    if (!argsMap.containsKey("ipWithPort")) {
      System.out.println("Missing ipWithPort...");
      return null;
    }

    return argsMap;
  }

  private static boolean is32Int(String s) {
    try {
      Integer.parseInt(s);
    } catch (NumberFormatException e) {
      return false;
    }
    return true;
  }

  private static boolean isValidDate(String s) {
    DateTimeFormatter dateFormatter = DateTimeFormatter.BASIC_ISO_DATE;
    try {
      LocalDate.parse(s, dateFormatter);
    } catch (DateTimeParseException e) {
      return false;
    }
    return true;
  }

  private static boolean isValidIpWithPort(String s) {
    Pattern p = Pattern.compile("^"
        + "(((?!-)[A-Za-z0-9-]{1,63}(?<!-)\\.)+[A-Za-z]{2,6}" // Domain name
        + "|"
        + "localhost" // localhost
        + "|"
        + "(([0-9]{1,3}\\.){3})[0-9]{1,3})" // Ip
        + ":"
        + "[0-9]{1,5}$"); // Port
    return p.matcher(s).matches();
  }
}