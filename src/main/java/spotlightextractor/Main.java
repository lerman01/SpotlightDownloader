package spotlightextractor;

import org.apache.commons.io.FileUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

public class Main {

    public static final String EXIF_TOOL_PATH = "exiftool.exe";

    private static Logger logger = LogManager.getLogger(Main.class);
    private static int WORKERS = 10;
    private static int NUMBER_OF_TRIES = 10;
    private static ConcurrentHashMap<String, String> oldFetchedImages = new ConcurrentHashMap<>();

    public final static void main(String[] args) throws Exception {
        if (args.length != 2) {
            logger.error("Missing arguments");
        } else {
            WORKERS = Integer.valueOf(args[0]);
            NUMBER_OF_TRIES = Integer.valueOf(args[1]);
            extractExifTool();
            start();
            deleteExifTool();
        }
    }

    private static void start() throws IOException {
        createFolder();
        logger.info("Start loading current images data...");
        initFetchedData();
        Worker.setFetchedImages(oldFetchedImages);

        logger.info("Start fetching new images");
        CloseableHttpClient httpclient = HttpClients.createDefault();
        ExecutorService executor = Executors.newFixedThreadPool(WORKERS);
        for (int i = 0; i < NUMBER_OF_TRIES; i++) {
            executor.execute(new Worker(httpclient));
        }
        executor.shutdown();
        while (!executor.isTerminated()) {
        }
        httpclient.close();
        printNewImages();
        logger.info(">> Finished <<");
    }

    private static void initFetchedData() {
        ExecutorService executor = Executors.newFixedThreadPool(10);
        try (Stream<Path> walk = Files.walk(Paths.get("images"))) {
            walk.forEach((file) -> {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        if (!Files.isDirectory(file)) {
                            StringBuilder builder = null;
                            try {
                                Process process = new ProcessBuilder(EXIF_TOOL_PATH, "-title", "images\\" + file.getFileName()).start();
                                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                                builder = new StringBuilder();
                                String line = null;
                                while ((line = reader.readLine()) != null) {
                                    builder.append(line);
                                    builder.append(System.getProperty("line.separator"));
                                }
                                if (builder.length() > 0) {
                                    String imageId = builder.substring(builder.indexOf(": ") + 2, builder.indexOf("\r"));
                                    oldFetchedImages.put(imageId, file.getFileName().toString());
                                } else {
                                    logger.error(String.format("Failed to retrive imaage id: %s", file.getFileName()));
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                });
            });
            executor.shutdown();
            while (!executor.isTerminated()) {
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private static void createFolder() {
        String folderName = "images";
        File directory = new File(folderName);
        if (!directory.exists()) {
            directory.mkdir();
        }
    }

    private static void extractExifTool() throws IOException {
        URL url = Main.class.getClassLoader().getResource("exiftool.exe");
        FileOutputStream output = new FileOutputStream("exiftool.exe");
        InputStream input = url.openStream();
        byte[] buffer = new byte[4096];
        int bytesRead = input.read(buffer);
        while (bytesRead != -1) {
            output.write(buffer, 0, bytesRead);
            bytesRead = input.read(buffer);
        }
        output.close();
        input.close();
    }

    private static void deleteExifTool() {
        File file = new File("exiftool.exe");
        FileUtils.deleteQuietly(file);
    }

    private static void printNewImages() {
        ConcurrentHashMap<String, String> fetchedImages = Worker.getFetchedImages();
        for (String imageId : fetchedImages.keySet()) {
            if (oldFetchedImages.get(imageId) == null) {
                logger.info(String.format("## New image = %s", fetchedImages.get(imageId)));
            }
        }
    }

}


