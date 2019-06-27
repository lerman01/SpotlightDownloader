package spotlightextractor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
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
        ConcurrentHashMap<String, ImageData> oldImagesList = getImagesList();
        Worker.setImagesList(getImagesList());
        for (int i = 0; i < NUMBER_OF_TRIES; i++) {
            executor.execute(new Worker(httpclient));
        }
        executor.shutdown();
        while (!executor.isTerminated()) {
        }
        httpclient.close();
        saveAndPrintNewImages(oldImagesList);
        logger.info(">> Finished <<");
    }

    private static void createFolder() {
        String folderName = "images";
        File directory = new File(folderName);
        if (!directory.exists()) {
            directory.mkdir();
        }
    }

    private static ConcurrentHashMap<String, ImageData> getImagesList() throws IOException {
        File existsImagesDataFile = new File("imagesData.json");
        ObjectMapper objectMapper = new ObjectMapper();
        if (existsImagesDataFile.exists()) {
            TypeReference<ConcurrentHashMap<String, ImageData>> typeRef = new TypeReference<ConcurrentHashMap<String, ImageData>>() {
            };
            ConcurrentHashMap<String, ImageData> concurrentHashMap = objectMapper.readValue(new File("imagesData.json"), typeRef);
            return concurrentHashMap;
        } else {
            ConcurrentHashMap<String, ImageData> emptyData = new ConcurrentHashMap<>();
            objectMapper.writeValue(existsImagesDataFile, emptyData);
            return emptyData;
        }
    }

    private static void saveAndPrintNewImages(ConcurrentHashMap<String, ImageData> oldImages) throws IOException {
        ConcurrentHashMap<String, ImageData> newImages = Worker.getImagesList();
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.writeValue(new File("imagesData.json"), newImages);
        for (String newImageId : newImages.keySet()) {
            if (oldImages.get(newImageId) == null) {
                logger.info(String.format("## New Image: %s", newImages));
            }
        }

    }


}


