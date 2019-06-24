package spotlightextractor;

import org.apache.commons.io.FilenameUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {

    private static Logger logger = LogManager.getLogger(Main.class);
    private static int WORKERS = 10;
    private static int NUMBER_OF_TRIES = 10;

    public final static void main(String[] args) throws Exception {
        if (args.length != 2) {
            logger.error("Missing arguments");
        } else {
            WORKERS = Integer.valueOf(args[0]);
            NUMBER_OF_TRIES = Integer.valueOf(args[1]);
            start();
        }
    }

    private static void start() throws IOException {
        createFolder();
        logger.info("Start fetching new images");
        CloseableHttpClient httpclient = HttpClients.createDefault();
        ExecutorService executor = Executors.newFixedThreadPool(WORKERS);
        List<String> oldImagesList = getFilesList();
        Worker.setFetchedImages(oldImagesList);
        for (int i = 0; i < NUMBER_OF_TRIES; i++) {
            executor.execute(new Worker(httpclient));
        }
        executor.shutdown();
        while (!executor.isTerminated()) {
        }
        httpclient.close();
        printNewImages(oldImagesList);
        logger.info(">> Finished <<");
    }

    private static void createFolder() {
        String folderName = "images";
        File directory = new File(folderName);
        if (!directory.exists()) {
            directory.mkdir();
        }
    }

    private static List<String> getFilesList() {
        List<File> images = Arrays.asList(new File("images").listFiles());
        List<String> imagesNamesList = new ArrayList<>();
        for (File image : images) {
            imagesNamesList.add(FilenameUtils.removeExtension(image.getName()));
        }
        return imagesNamesList;
    }

    private static void printNewImages(List<String> oldImagesList) {
        List<String> newFilesList = getFilesList();
        for (String newFile : newFilesList) {
            if (oldImagesList.indexOf(newFile) == -1) {
                logger.info(String.format("## New File: %s", newFile));
            }
        }

    }


}


