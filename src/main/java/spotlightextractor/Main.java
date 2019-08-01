package spotlightextractor;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class Main {

    private static Logger logger = LogManager.getLogger(Main.class);
    private static int WORKERS = 10;
    private static int DEFAULT_NUM_OF_WORKERS_TO_ADD = 100;
    private static ExecutorService EXECUTOR;
    private static CloseableHttpClient HTTP_CLIENT;


    public final static void main(String[] args) throws Exception {
        if (args.length >= 1) {
            WORKERS = Integer.valueOf(args[0]);
            start();
        }
    }

    private static void start() throws IOException {
        createFolder();
        Worker.initImageList();
        logger.info("Start fetching new images");
        HTTP_CLIENT = HttpClients.custom().disableDefaultUserAgent().build();
        EXECUTOR = Executors.newFixedThreadPool(WORKERS);
//        printWorkerSize();
        addWorkers();
        int activeCount = 0;
        int size = 0;
        ThreadPoolExecutor executor = (ThreadPoolExecutor) EXECUTOR;
        do {
            size = executor.getQueue().size();
            activeCount = executor.getActiveCount();
        }
        while (size > 0 && activeCount > 0);
        EXECUTOR.shutdown();
        while (!EXECUTOR.isTerminated()) {
        }
        HTTP_CLIENT.close();
        logger.info(">> Finished <<");
    }

    private static void createFolder() {
        String folderName = "images";
        File directory = new File(folderName);
        if (!directory.exists()) {
            directory.mkdir();
        }
    }

    public static void addWorkers() {
        int numOfWorkers = Worker.getImageList().size();
        if (numOfWorkers < DEFAULT_NUM_OF_WORKERS_TO_ADD) {
            numOfWorkers = DEFAULT_NUM_OF_WORKERS_TO_ADD;
        }
        for (int i = 0; i < numOfWorkers; i++) {
            if (!EXECUTOR.isShutdown() && ((ThreadPoolExecutor) EXECUTOR).getQueue().size() < numOfWorkers) {
                EXECUTOR.execute(new Worker(HTTP_CLIENT));
            }
        }
    }

    private static void printWorkerSize() {
        Timer timer = new Timer();

        timer.schedule(new TimerTask() {
            public void run() {
                int size = ((ThreadPoolExecutor) EXECUTOR).getQueue().size();
                int activeCount = ((ThreadPoolExecutor) EXECUTOR).getActiveCount();
                System.out.println("In Queue: " + size);
                System.out.println("Active: " + activeCount);
            }
        }, 0, 1000);
    }

}


